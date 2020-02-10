package com.github.yoep.popcorn.trakt;

import com.github.yoep.popcorn.config.properties.PopcornProperties;
import com.github.yoep.popcorn.media.watched.WatchedService;
import com.github.yoep.popcorn.settings.SettingsService;
import com.github.yoep.popcorn.settings.models.TraktSettings;
import com.github.yoep.popcorn.trakt.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraktService {
    private final OAuth2RestOperations traktTemplate;
    private final PopcornProperties properties;
    private final SettingsService settingsService;
    private final WatchedService watchedService;
    private final TaskExecutor taskExecutor;

    //region Methods

    /**
     * Check if the user is authorized for trakt.
     *
     * @return Returns true if the user already has an access token for trakt, else false.
     */
    public boolean isAuthorized() {
        return getSettings().getAccessToken().isPresent();
    }

    /**
     * Authorize the user against the trakt API.
     * This method will automatically update the trakt settings with the access token on success.
     *
     * @return Returns true if the user has been authenticated, else false.
     */
    @Async
    public CompletableFuture<Boolean> authorize() {
        log.trace("Trying to authorize on trakt.tv");
        try {
            getWatchedMovies();
            log.debug("Trakt.tv authorization succeeded");
            return CompletableFuture.completedFuture(true);
        } catch (RestClientException ex) {
            log.warn("Trakt.tv authorization failed");
            return CompletableFuture.completedFuture(false);
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    public void getWatchlist() {
        var uri = UriComponentsBuilder.fromUri(properties.getTrakt().getUrl())
                .path("sync/watchlist")
                .build(Collections.emptyMap());

        ResponseEntity<String> response = traktTemplate.getForEntity(uri, String.class);
    }

    /**
     * Forget the current authorized trakt user.
     * This will remove the access token from the settings.
     */
    public void forget() {
        log.trace("Forgetting the authorization of trakt.tv");
        getSettings().setAccessToken(null);
    }

    //endregion

    //region PostConstructor

    @PostConstruct
    private void init() {
        // check if the user is authorized
        // if so, run a synchronization at the start of the application
        if (isAuthorized())
            taskExecutor.execute(this::synchronize);
    }

    private void synchronize() {
        log.debug("Starting Trakt.tv synchronisation");
        try {
            syncMovies();
        } catch (Exception ex) {
            log.error("Failed to sync trakt.tv movies, " + ex.getMessage(), ex);
        }

        try {
            syncShows();
        } catch (Exception ex) {
            log.error("Failed to sync trakt.tv shows, " + ex.getMessage(), ex);
        }
    }

    //endregion

    //region Functions

    private List<WatchedMovie> getWatchedMovies() {
        log.trace("Retrieving watched movies from trakt.tv");
        return asList(getWatched("movies", WatchedMovie[].class));
    }

    private List<WatchedShow> getWatchedShows() {
        log.trace("Retrieving watched shows from trakt.tv");
        return asList(getWatched("shows", WatchedShow[].class));
    }

    private <T> T getWatched(String item, Class<T> type) {
        var url = UriComponentsBuilder.fromUri(properties.getTrakt().getUrl())
                .path("/sync/watched/{item}")
                .buildAndExpand(item)
                .toUriString();

        log.trace("Requesting watched trakt.tv items from {}", url);
        return traktTemplate.getForEntity(url, type).getBody();
    }

    private void syncMovies() {
        var movies = getWatchedMovies();

        // synchronize movies locally
        try {
            log.trace("Synchronizing {} trakt.tv movies to local DB", movies.size());
            movies.stream()
                    .map(WatchedMovie::getMovie)
                    .forEach(watchedService::addToWatchList);
            log.debug("Trakt.tv movies to local DB sync completed");
        } catch (Exception ex) {
            log.error("Failed to synchronize movies to local DB with error: " + ex.getMessage(), ex);
        }

        // synchronize movies to remote
        try {
            log.trace("Gathering movies that need to be synced to trakt.tv");
            List<String> moviesToSync = watchedService.getWatchedMovies().stream()
                    .filter(key -> movies.stream()
                            .noneMatch(movie -> movie.getMovie().getId().equals(key)))
                    .collect(Collectors.toList());

            if (moviesToSync.size() > 0) {
                addMoviesToTraktWatchlist(moviesToSync);
                log.debug("Local DB movies to trakt.tv sync completed");
            }
        } catch (Exception ex) {
            log.error("Failed to synchronize movies to trakt.tv with error: " + ex.getMessage(), ex);
        }

        log.info("Trakt.tv movie synchronisation completed");
    }

    private void syncShows() {
        var shows = getWatchedShows();

        // synchronize shows to remote
        try {
            log.trace("Gathering shows that need to be synced to trakt.tv");
            List<String> showsToSync = watchedService.getWatchedShows().stream()
                    .filter(key -> shows.stream()
                            .noneMatch(show -> key.equals(String.valueOf(show.getShow().getIds().getTvdb()))))
                    .collect(Collectors.toList());

            if (showsToSync.size() > 0) {
                addShowsToTraktWatchlist(showsToSync);
                log.debug("Local DB shows to trakt.tv sync completed");
            }
        } catch (Exception ex) {
            log.error("Failed to synchronize shows to trakt.tv with error: " + ex.getMessage(), ex);
        }

        log.info("Trakt.tv show synchronisation completed");
    }

    private void addMoviesToTraktWatchlist(List<String> keys) {
        log.trace("Synchronizing {} local DB movies to trakt.tv", keys.size());
        AddToWatchlistRequest request = AddToWatchlistRequest.builder()
                .movies(keys.stream()
                        .map(this::toMovie)
                        .collect(Collectors.toList()))
                .build();

        executeWatchlistRequest(request);
    }

    private void addShowsToTraktWatchlist(List<String> keys) {
        log.trace("Synchronizing {} local DB shows to trakt.tv", keys.size());
        AddToWatchlistRequest request = AddToWatchlistRequest.builder()
                .shows(keys.stream()
                        .map(this::toShow)
                        .collect(Collectors.toList()))
                .build();

        executeWatchlistRequest(request);
    }

    private void executeWatchlistRequest(AddToWatchlistRequest request) {
        String url = UriComponentsBuilder.fromUri(properties.getTrakt().getUrl())
                .path("/sync/watchlist")
                .toUriString();

        ResponseEntity<Void> response = traktTemplate.postForEntity(url, request, Void.class);
        log.debug("Trakt.tv responded with status code {}", response.getStatusCodeValue());
    }

    private TraktMovie toMovie(String key) {
        return TraktMovie.builder()
                .ids(TraktMovieIds.builder()
                        .imdb(key)
                        .build())
                .build();
    }

    private TraktShow toShow(String key) {
        return TraktShow.builder()
                .ids(TraktShowIds.builder()
                        .tvdb(Integer.parseInt(key))
                        .build())
                .build();
    }

    private TraktSettings getSettings() {
        return settingsService.getSettings().getTraktSettings();
    }

    //endregion
}
