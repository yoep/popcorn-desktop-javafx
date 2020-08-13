package com.github.yoep.popcorn.ui.view.services;

import com.github.yoep.popcorn.ui.activities.ActivityManager;
import com.github.yoep.popcorn.ui.activities.LoadMediaTorrentActivity;
import com.github.yoep.popcorn.ui.activities.PlayMediaActivity;
import com.github.yoep.popcorn.ui.media.providers.models.Episode;
import com.github.yoep.popcorn.ui.media.providers.models.Media;
import com.github.yoep.popcorn.ui.media.providers.models.MediaTorrentInfo;
import com.github.yoep.popcorn.ui.settings.SettingsService;
import com.github.yoep.popcorn.ui.subtitles.models.SubtitleInfo;
import com.github.yoep.popcorn.ui.view.listeners.AbstractVideoPlayerListener;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * The {@link PlayNextService} is responsible for determining if the playing next should be activated for the current playback.
 * This service listens on the {@link PlayMediaActivity} and bases itself around the {@link Media} type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayNextService {
    public static final String NEXT_EPISODE_PROPERTY = "nextEpisode";
    public static final String PLAYING_IN_PROPERTY = "playingIn";
    public static final int COUNTDOWN_FROM = 60;

    private final ActivityManager activityManager;
    private final VideoPlayerService videoPlayerService;
    private final SettingsService settingsService;

    private final ReadOnlyObjectWrapper<Episode> nextEpisode = new ReadOnlyObjectWrapper<>(this, NEXT_EPISODE_PROPERTY);
    private final ReadOnlyLongWrapper playingIn = new ReadOnlyLongWrapper(this, PLAYING_IN_PROPERTY, COUNTDOWN_FROM);

    private String quality;
    private long duration;

    //region Properties

    /**
     * Get the next episode that should be played for the current playback.
     *
     * @return Returns the next episode if available, else {@link Optional#empty()}.
     */
    public Optional<Episode> getNextEpisode() {
        return Optional.ofNullable(nextEpisode.get());
    }

    /**
     * Get the next episode property.
     *
     * @return Returns the next episode property.
     */
    public ReadOnlyObjectProperty<Episode> nextEpisodeProperty() {
        return nextEpisode.getReadOnlyProperty();
    }

    /**
     * Get the playing in value of the next episode.
     *
     * @return Returns the current playing in value.
     */
    public long getPlayingIn() {
        return playingIn.get();
    }

    /**
     * Get the playing in property for the next episode.
     *
     * @return Returns the playing in property.
     */
    public ReadOnlyLongProperty playingInProperty() {
        return playingIn.getReadOnlyProperty();
    }

    //endregion

    //region Methods

    /**
     * Play the next episode now and stop the playing in countdown.
     */
    public void playNextEpisodeNow() {
        onPlayNextEpisode();
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeListeners();
        initializeVideoPlayerListeners();
    }

    private void initializeListeners() {
        activityManager.register(PlayMediaActivity.class, this::onPlayMedia);
    }

    private void initializeVideoPlayerListeners() {
        videoPlayerService.addListener(new AbstractVideoPlayerListener() {
            @Override
            public void onTimeChanged(Number newValue) {
                PlayNextService.this.onTimeChanged(newValue.longValue());
            }

            @Override
            public void onDurationChanged(Number newValue) {
                PlayNextService.this.onDurationChanged(newValue.longValue());
            }
        });
    }

    //endregion

    //region Functions

    void onPlayMedia(PlayMediaActivity activity) {
        // check if the play next option is enabled
        // if not, ignore this event
        if (isPlayNextDisabled()) {
            return;
        }

        var media = activity.getMedia();

        // check if the current media is an episode
        // if not, ignore the update of information
        if (!isEpisode(media)) {
            return;
        }

        var episode = (Episode) media;
        var show = episode.getShow();
        var nextEpisodeIndex = episode.getEpisode();

        if (nextEpisodeIndex <= show.getEpisodes().size()) {
            this.nextEpisode.set(show.getEpisodes().get(nextEpisodeIndex));
            this.quality = activity.getQuality();
        }
    }

    void onTimeChanged(long time) {
        // check if the next episode to be played is known and the play next option is enabled
        // if not, ignore this event
        if (getNextEpisode().isEmpty() || isPlayNextDisabled()) {
            return;
        }

        var remainingTime = (duration - time) / 1000;

        if (remainingTime <= COUNTDOWN_FROM) {
            playingIn.set(remainingTime);

            if (remainingTime == 0) {
                onPlayNextEpisode();
            }
        }
    }

    void onDurationChanged(long newValue) {
        this.duration = newValue;
    }

    private void onPlayNextEpisode() {
        // check if the next episode is known
        // if not, ignore this action
        if (getNextEpisode().isEmpty()) {
            log.warn("Unable to play next episode, nex episode is unwknown");
            return;
        }

        var episode = getNextEpisode().get();
        var quality = this.quality;

        // close the current video player
        videoPlayerService.close();

        // start loading the next episode
        activityManager.register(new LoadMediaTorrentActivity() {
            @Override
            public MediaTorrentInfo getTorrent() {
                return episode.getTorrents().get(quality);
            }

            @Override
            public Media getMedia() {
                return episode;
            }

            @Override
            public String getQuality() {
                return quality;
            }

            @Override
            public Optional<SubtitleInfo> getSubtitle() {
                return Optional.empty();
            }
        });
    }

    private boolean isPlayNextDisabled() {
        var settings = settingsService.getSettings();

        return !settings.getPlaybackSettings().isAutoPlayNextEpisodeEnabled();
    }

    private boolean isEpisode(Media media) {
        return media instanceof Episode;
    }

    //endregion
}
