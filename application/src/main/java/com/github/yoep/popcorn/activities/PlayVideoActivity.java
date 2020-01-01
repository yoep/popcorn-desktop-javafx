package com.github.yoep.popcorn.activities;

import com.github.yoep.popcorn.subtitle.models.SubtitleInfo;
import com.github.yoep.popcorn.media.providers.models.Episode;

import java.util.Optional;

public interface PlayVideoActivity extends PlayMediaActivity {
    /**
     * Get the url to play the media of.
     *
     * @return Return the media url.
     */
    String getUrl();

    /**
     * Get the video quality of the media.
     *
     * @return Returns the quality if known for the media, else {@link Optional#empty()}.
     */
    Optional<String> getQuality();

    /**
     * Get the episode of the media that is being played.
     *
     * @return The media episode if available, else {@link Optional#empty()}.
     */
    Optional<Episode> getEpisode();

    /**
     * Get the subtitle that needs to be added to the playback of the video.
     *
     * @return Returns the subtitle for the playback if present, else {@link Optional#empty()}.
     */
    Optional<SubtitleInfo> getSubtitle();
}