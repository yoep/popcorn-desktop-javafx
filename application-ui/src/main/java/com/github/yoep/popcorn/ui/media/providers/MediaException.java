package com.github.yoep.popcorn.ui.media.providers;

import com.github.yoep.popcorn.ui.media.providers.models.Media;
import lombok.Getter;

@Getter
public class MediaException extends RuntimeException {
    private final Media media;

    public MediaException(String message) {
        super(message);
        this.media = null;
    }

    public MediaException(Media media, String message) {
        super(message);
        this.media = media;
    }

    public MediaException(Media media, String message, Throwable cause) {
        super(message, cause);
        this.media = media;
    }
}
