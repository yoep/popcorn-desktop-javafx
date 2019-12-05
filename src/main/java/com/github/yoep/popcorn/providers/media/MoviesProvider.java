package com.github.yoep.popcorn.providers.media;

import com.github.yoep.popcorn.config.properties.PopcornProperties;
import com.github.yoep.popcorn.config.properties.ProviderProperties;
import com.github.yoep.popcorn.providers.media.models.Movie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class MoviesProvider {
    private final RestTemplate restTemplate;
    private final ProviderProperties providerConfig;

    public MoviesProvider(RestTemplate restTemplate, PopcornProperties popcornConfig) {
        this.restTemplate = restTemplate;
        this.providerConfig = popcornConfig.getProvider("movies");
    }

    public List<Movie> getPage(int page) {
        URI uri = UriComponentsBuilder.fromUri(providerConfig.getUrl())
                .path("/{page}")
                .queryParam("sort", "trending")
                .queryParam("order", -1)
                .queryParam("genre", "all")
                .queryParam("keywords", "")
                .build(page);

        ResponseEntity<Movie[]> movies = restTemplate.getForEntity(uri, Movie[].class);

        return Optional.ofNullable(movies.getBody())
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }
}
