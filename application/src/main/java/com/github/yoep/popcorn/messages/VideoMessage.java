package com.github.yoep.popcorn.messages;

import com.github.spring.boot.javafx.text.Message;
import lombok.Getter;

@Getter
public enum VideoMessage implements Message {
    SUBTITLES_OFFSET("video_subtitle_offset"),
    VIDEO_ERROR("video_unexpected_error");

    private final String key;

    VideoMessage(String key) {
        this.key = key;
    }
}