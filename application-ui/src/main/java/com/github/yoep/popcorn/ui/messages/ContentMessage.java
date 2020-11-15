package com.github.yoep.popcorn.ui.messages;

import com.github.spring.boot.javafx.text.Message;
import lombok.Getter;

@Getter
public enum ContentMessage implements Message {
    CONTENT_PANE_FAILED("content_pane_switch_failed");

    private final String key;

    ContentMessage(String key) {
        this.key = key;
    }
}
