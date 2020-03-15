package com.github.yoep.popcorn.media.providers;

import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.ShowMovieDetailsActivity;
import com.github.yoep.popcorn.config.properties.PopcornProperties;
import com.github.yoep.popcorn.config.properties.ProviderProperties;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.media.providers.models.Movie;
import com.github.yoep.popcorn.view.models.Category;
import com.github.yoep.popcorn.view.models.Genre;
import com.github.yoep.popcorn.view.models.SortBy;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MovieProviderService extends AbstractProviderService<Movie> {
    private static final Category CATEGORY = Category.MOVIES;
    private final ProviderProperties providerConfig;

    public MovieProviderService(RestTemplate restTemplate, ActivityManager activityManager, PopcornProperties popcornConfig) {
        super(restTemplate, activityManager);
        this.providerConfig = popcornConfig.getProvider(CATEGORY.getProviderName());
    }

    @Override
    public boolean supports(Category category) {
        return category == CATEGORY;
    }

    @Override
    public CompletableFuture<Movie[]> getPage(Genre genre, SortBy sortBy, int page) {
        return CompletableFuture.completedFuture(getPage(genre, sortBy, Strings.EMPTY, page));
    }

    @Override
    public CompletableFuture<Movie[]> getPage(Genre genre, SortBy sortBy, int page, String keywords) {
        return CompletableFuture.completedFuture(getPage(genre, sortBy, keywords, page));
    }

    @Override
    public CompletableFuture<Movie> getDetails(String imdbId) {
        var uri = UriComponentsBuilder.fromUri(providerConfig.getUrl())
                .path("/movie/{id}")
                .build(imdbId);

        log.debug("Retrieving movie details \"{}\"", uri);
        var response = restTemplate.getForEntity(uri, Movie.class);

        if (response.getBody() == null) {
            return CompletableFuture.failedFuture(new MediaException(MessageFormat.format("Failed to retrieve the details of {0}", imdbId)));
        }

        return CompletableFuture.completedFuture(response.getBody());
    }

    @Override
    public void showDetails(Media media) {
        final Movie movie = (Movie) media;

        activityManager.register((ShowMovieDetailsActivity) () -> movie);
    }

    public Movie[] getPage(Genre genre, SortBy sortBy, String keywords, int page) {
        var uri = getUriFor(providerConfig.getUrl(), "movies", genre, sortBy, keywords, page);

        log.debug("Retrieving movie provider page \"{}\"", uri);
        ResponseEntity<Movie[]> items = restTemplate.getForEntity(uri, Movie[].class);

        return Optional.ofNullable(items.getBody())
                .orElse(new Movie[0]);
    }
}
