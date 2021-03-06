package com.github.yoep.popcorn.ui.media.favorites;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yoep.popcorn.ui.PopcornTimeApplication;
import com.github.yoep.popcorn.ui.media.favorites.models.Favorable;
import com.github.yoep.popcorn.ui.media.favorites.models.Favorites;
import com.github.yoep.popcorn.ui.media.providers.ProviderService;
import com.github.yoep.popcorn.ui.media.providers.models.Movie;
import com.github.yoep.popcorn.ui.media.providers.models.Show;
import com.github.yoep.popcorn.ui.utils.FileService;
import com.github.yoep.popcorn.ui.utils.IdleTimer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {
    static final int UPDATE_CACHE_AFTER_HOURS = 72;
    static final String NAME = "favorites.json";
    static final String FAVORITES_PATH = PopcornTimeApplication.APP_DIR + NAME;
    private static final int IDLE_TIME = 10;

    private final IdleTimer idleTimer = new IdleTimer(Duration.ofSeconds(IDLE_TIME));
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final FileService fileService;
    private final ProviderService<Movie> movieProviderService;
    private final ProviderService<Show> showProviderService;
    private final Object cacheLock = new Object();

    /**
     * The currently loaded favorable cache.
     * This cache is saved and unloaded after {@link #IDLE_TIME} seconds to free up memory.
     */
    private Favorites cache;
    private int cacheHash;

    /**
     * Check if the given {@link Favorable} is liked by the user.
     *
     * @param favorable The favorable to check.
     * @return Returns true if the favorable is liked, else false.
     */
    public boolean isLiked(Favorable favorable) {
        Assert.notNull(favorable, "favorable cannot be null");
        loadFavorites();

        synchronized (cacheLock) {
            return cache.getAll().stream()
                    .anyMatch(e -> e.getId().equals(favorable.getId()));
        }
    }

    /**
     * Get all the {@link Favorable} items that are liked by the user.
     *
     * @return Returns the list of liked items by the user.
     */
    public List<Favorable> getAll() {
        loadFavorites();

        synchronized (cacheLock) {
            return cache.getAll();
        }
    }

    /**
     * Add the given {@link Favorable} to the favorites.
     *
     * @param favorable The favorable to add.
     */
    public void addToFavorites(Favorable favorable) {
        Assert.notNull(favorable, "favorable cannot be null");
        loadFavorites();

        synchronized (cacheLock) {
            favorable.setLiked(true);

            // verify that the favorable doesn't already exist
            if (!isLiked(favorable)) {
                cache.add(favorable);
            }
        }
    }

    /**
     * Remove the given favorable from favorites.
     *
     * @param favorable The favorable to remove.
     */
    public void removeFromFavorites(Favorable favorable) {
        Assert.notNull(favorable, "favorable cannot be null");
        loadFavorites();

        synchronized (cacheLock) {
            cache.remove(favorable);
            favorable.setLiked(false);
        }
    }

    //region PostConstruct

    @PostConstruct
    void init() {
        initializeIdleTimer();
        initializeCacheRefresh();
    }

    private void initializeIdleTimer() {
        idleTimer.setOnTimeout(this::onSave);
    }

    private void initializeCacheRefresh() {
        try {
            if (isCacheUpdateRequired()) {
                // update the cache on a separate thread to not block the startup process
                log.trace("Favorites cache update is required, starting cache update thread");
                taskExecutor.execute(this::updateCache);
            }
        } catch (FavoriteException ex) {
            log.error("Failed to refresh favorites cache, " + ex.getMessage(), ex);
        }
    }

    //endregion

    //region PreDestroy

    @PreDestroy
    private void destroy() {
        onSave();
    }

    //endregion

    //region Functions

    private void save(Favorites favorites) {
        try {
            log.debug("Saving favorites to {}", fileService.getAbsolutePath(FAVORITES_PATH));
            fileService.save(FAVORITES_PATH, objectMapper.writeValueAsString(favorites));
        } catch (IOException ex) {
            log.error("Failed to save the favorites with error" + ex.getMessage(), ex);
        }
    }

    private void loadFavorites() {
        idleTimer.runFromStart();

        // check if the cache has already been loaded
        // if so, do nothing
        synchronized (cacheLock) {
            if (cache != null)
                return;
        }

        var file = getFile();

        if (file.exists()) {
            try {
                log.debug("Loading favorites from {}", file.getAbsolutePath());

                synchronized (cacheLock) {
                    cache = objectMapper.readValue(file, Favorites.class);
                    cacheHash = cache.hashCode();
                }
            } catch (JsonMappingException ex) {
                throw new FavoriteException("Failed to load favorites, file has been corrupted: " + ex.getMessage(), ex);
            } catch (IOException ex) {
                throw new FavoriteAccessException(file, ex);
            }
        } else {
            // build a new cache as now favorite database file is found
            log.debug("Favorites file was not found, creating a new blank cache for favorites");
            synchronized (cacheLock) {
                cache = Favorites.builder().build();
                cacheHash = cache.hashCode();
            }
        }
    }

    private void updateCache() {
        log.debug("Starting favorites cache update");
        loadFavorites();

        idleTimer.stop();
        updateMoviesCache();
        updateSeriesCache();

        synchronized (cacheLock) {
            cache.setLastCacheUpdate(LocalDateTime.now());
        }

        idleTimer.runFromStart();
        log.info("Favorite cache has been updated");
    }

    private void updateMoviesCache() {
        log.trace("Updating movies favorite cache");
        var newMoviesCache = cache.getMovies().stream()
                .map(e -> movieProviderService.getDetails(e.getImdbId()))
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        synchronized (cacheLock) {
            cache.setMovies(newMoviesCache);
        }
    }

    private void updateSeriesCache() {
        log.trace("Updating shows favorite cache");
        var newShowsCache = cache.getShows().stream()
                .map(e -> showProviderService.getDetails(e.getImdbId()))
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // remove the nested episodes & overview text from the cache
        newShowsCache.forEach(show -> {
            show.setEpisodes(null);
            show.setSynopsis(null);
        });

        synchronized (cacheLock) {
            cache.setShows(newShowsCache);
        }
    }

    private boolean isCacheUpdateRequired() {
        var cacheUpdateDateTime = LocalDateTime.now().minusHours(UPDATE_CACHE_AFTER_HOURS);

        loadFavorites();

        boolean shouldUpdate;

        synchronized (cacheLock) {
            shouldUpdate = cache.getLastCacheUpdate() == null ||
                    cache.getLastCacheUpdate().isBefore(cacheUpdateDateTime);
        }

        return shouldUpdate;
    }

    private File getFile() {
        return fileService.getFile(FAVORITES_PATH);
    }

    private void onSave() {
        if (cache == null)
            return;

        synchronized (cacheLock) {
            // check if the cache was modified
            // if not, the cache will only be removed from memory but not saved again
            if (cache.hashCode() != cacheHash)
                save(cache);

            cache = null;
        }
    }

    //endregion
}
