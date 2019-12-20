package com.github.yoep.popcorn.services.providers;

import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.models.Genre;
import com.github.yoep.popcorn.models.SortBy;
import com.github.yoep.popcorn.services.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Abstract implementation of {@link ProviderService}.
 *
 * @param <T> The media type this provider returns.
 */
@RequiredArgsConstructor
public abstract class AbstractProviderService<T extends Media> implements ProviderService<T> {
    protected final RestTemplate restTemplate;
    protected final ActivityManager activityManager;

    protected URI getUriFor(String resource, Genre genre, SortBy sortBy, String keywords, int page) {
        return UriComponentsBuilder.fromUri(getBaseUrl())
                .path("/{resource}/{page}")
                .queryParam("sort", sortBy.getKey())
                .queryParam("order", -1)
                .queryParam("genre", genre.getKey())
                .queryParam("keywords", keywords)
                .build(resource, page);
    }

    /**
     * Get the base url of the API to call.
     *
     * @return Returns the base url of the API to call.
     */
    protected abstract URI getBaseUrl();
}