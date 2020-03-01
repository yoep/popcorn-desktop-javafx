package com.github.yoep.popcorn.media.providers;

import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.ShowSerieDetailsActivity;
import com.github.yoep.popcorn.config.properties.PopcornProperties;
import com.github.yoep.popcorn.config.properties.ProviderProperties;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.media.providers.models.Show;
import com.github.yoep.popcorn.models.Category;
import com.github.yoep.popcorn.models.Genre;
import com.github.yoep.popcorn.models.SortBy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ShowProviderService extends AbstractProviderService<Show> {
    private static final Category CATEGORY = Category.SERIES;
    private final ProviderProperties providerConfig;

    public ShowProviderService(RestTemplate restTemplate, ActivityManager activityManager, PopcornProperties popcornConfig) {
        super(restTemplate, activityManager);
        this.providerConfig = popcornConfig.getProvider(CATEGORY.getProviderName());
    }

    @Override
    public boolean supports(Category category) {
        return category == CATEGORY;
    }

    @Override
    public CompletableFuture<Show[]> getPage(Genre genre, SortBy sortBy, int page) {
        return CompletableFuture.completedFuture(getPage(genre, sortBy, StringUtils.EMPTY, page));
    }

    @Override
    public CompletableFuture<Show[]> getPage(Genre genre, SortBy sortBy, int page, String keywords) {
        return CompletableFuture.completedFuture(getPage(genre, sortBy, keywords, page));
    }

    @Override
    public CompletableFuture<Show> getDetails(String imdbId) {
        return CompletableFuture.completedFuture(getDetailsInternal(imdbId));
    }

    //TODO: add UI feedback for the user if the API call fails
    @Override
    public void showDetails(Media media) {
        var show = getDetailsInternal(media.getId());

        activityManager.register((ShowSerieDetailsActivity) () -> show);
    }

    public Show[] getPage(Genre genre, SortBy sortBy, String keywords, int page) {
        URI uri = getUriFor(providerConfig.getUrl(), "shows", genre, sortBy, keywords, page);

        log.debug("Retrieving show provider page \"{}\"", uri);
        ResponseEntity<Show[]> shows = restTemplate.getForEntity(uri, Show[].class);

        return Optional.ofNullable(shows.getBody())
                .orElse(new Show[0]);
    }

    private Show getDetailsInternal(String imdbId) {
        var uri = UriComponentsBuilder.fromUri(providerConfig.getUrl())
                .path("show/{imdb_id}")
                .build(imdbId);

        log.debug("Retrieving show details \"{}\"", uri);
        var response = restTemplate.getForEntity(uri, Show.class);

        if (response.getStatusCodeValue() < 200 || response.getStatusCodeValue() >= 300)
            throw new MediaException(
                    MessageFormat.format("Failed to retrieve the details of {0}, unexpected status code {1}", imdbId, response.getStatusCodeValue()));

        if (response.getBody() == null)
            throw new MediaException(MessageFormat.format("Failed to retrieve the details of {0}, response body is null", imdbId));

        return response.getBody();
    }
}
