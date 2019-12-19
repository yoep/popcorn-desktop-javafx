package com.github.yoep.popcorn.messages;

import com.github.spring.boot.javafx.text.Message;
import lombok.Getter;

@Getter
public enum MediaMessage implements Message {
    SEASONS("media_seasons"),
    PLURAL("media_plural");

    private final String key;

    MediaMessage(String key) {
        this.key = key;
    }
}
