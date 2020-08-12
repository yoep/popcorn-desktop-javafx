package com.github.yoep.popcorn.ui.activities;

import com.github.yoep.popcorn.ui.media.providers.models.Media;
import com.github.yoep.popcorn.ui.subtitles.Subtitle;

import javax.validation.constraints.NotNull;
import java.util.Optional;

public interface PlayMediaActivity extends PlayVideoActivity {
    /**
     * Get the media that needs to be played.
     *
     * @return Returns the media that needs to be played.
     */
    @NotNull
    Media getMedia();

    /**
     * Get the video quality of the media.
     *
     * @return Returns the quality of the media.
     */
    String getQuality();

    /**
     * Get the subtitle that needs to be added to the playback of the media.
     * When no subtitle was selected, it will be by default {@link Subtitle#none()}.
     * If it is {@link Optional#empty()} it probably means that this activity is a trailer activity.
     *
     * @return Returns the subtitle for the playback if present, else {@link Optional#empty()}.
     */
    Optional<Subtitle> getSubtitle();
}
