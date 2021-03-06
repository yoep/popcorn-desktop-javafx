package com.github.yoep.popcorn.ui.events;

import com.github.yoep.popcorn.ui.media.providers.models.Show;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.util.Assert;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ShowSerieDetailsEvent extends ShowDetailsEvent {
    /**
     * The media to show the details of.
     */
    private final Show media;

    public ShowSerieDetailsEvent(Object source, Show media) {
        super(source);
        Assert.notNull(media, "media cannot be null");
        this.media = media;
    }
}
