package com.github.yoep.popcorn.ui.trakt.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TraktType {
    @JsonProperty("movie")
    MOVIE,
    @JsonProperty("show")
    SHOW,
    @JsonProperty("season")
    SEASON,
    @JsonProperty("episode")
    EPISODE
}
