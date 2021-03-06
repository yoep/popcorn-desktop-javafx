package com.github.yoep.popcorn.ui.view.controllers.common.components;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.yoep.popcorn.ui.events.ClosePlayerEvent;
import com.github.yoep.popcorn.ui.events.PlayTorrentEvent;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerManagerService;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerService;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerSubtitleService;
import com.github.yoep.torrent.adapter.listeners.AbstractTorrentListener;
import com.github.yoep.torrent.adapter.listeners.TorrentListener;
import com.github.yoep.torrent.adapter.model.DownloadStatus;
import com.github.yoep.torrent.adapter.model.Torrent;
import com.github.yoep.video.adapter.state.PlayerState;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractPlayerControlsComponent {
    protected final VideoPlayerService videoPlayerService;
    protected final VideoPlayerManagerService videoPlayerManagerService;
    protected final VideoPlayerSubtitleService videoPlayerSubtitleService;

    private final ChangeListener<PlayerState> playerStateListener = (observable, oldValue, newValue) -> onPlayerStateChanged(newValue);
    private final ChangeListener<Number> timeListener = (observable, oldValue, newValue) -> onTimeChanged(newValue);
    private final ChangeListener<Number> durationListener = (observable, oldValue, newValue) -> onDurationChanged(newValue);
    private final TorrentListener torrentListener = createTorrentListener();

    @FXML
    protected Icon playPauseIcon;
    @FXML
    protected Label timeLabel;
    @FXML
    protected Label durationLabel;

    private Torrent torrent;

    //region Methods

    @EventListener(ClosePlayerEvent.class)
    public void onClose() {
        if (this.torrent != null) {
            this.torrent.removeListener(torrentListener);
        }

        reset();
    }

    @EventListener
    public void onPlayTorrent(PlayTorrentEvent activity) {
        this.torrent = activity.getTorrent();
        this.torrent.addListener(torrentListener);
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeVideoListeners();
    }

    protected void initializeVideoListeners() {
        videoPlayerManagerService.videoPlayerProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.playerStateProperty().removeListener(playerStateListener);
                oldValue.timeProperty().removeListener(timeListener);
                oldValue.durationProperty().removeListener(durationListener);
            }

            newValue.playerStateProperty().addListener(playerStateListener);
            newValue.timeProperty().addListener(timeListener);
            newValue.durationProperty().addListener(durationListener);
        });
    }

    //endregion

    //region Functions

    /**
     * Reset this component to it's idle state.
     */
    protected void reset() {
        this.torrent = null;

        Platform.runLater(() -> {
            timeLabel.setText(formatTime(0));
            durationLabel.setText(formatTime(0));
        });
    }

    protected String formatTime(long time) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(time),
                TimeUnit.MILLISECONDS.toSeconds(time) % 60);
    }

    protected void onTimeChanged(Number newValue) {
        Platform.runLater(() -> {
            long time = newValue.longValue();

            timeLabel.setText(formatTime(time));
        });
    }

    protected void onDurationChanged(Number newValue) {
        Platform.runLater(() -> {
            long duration = newValue.longValue();

            durationLabel.setText(formatTime(duration));
        });
    }

    /**
     * Invoked when the progress has been changed.
     *
     * @param newValue The new load progress.
     */
    protected abstract void onProgressChanged(double newValue);

    private void onPlayerStateChanged(PlayerState newValue) {
        switch (newValue) {
            case PLAYING:
                Platform.runLater(() -> playPauseIcon.setText(Icon.PAUSE_UNICODE));
                break;
            case PAUSED:
                Platform.runLater(() -> playPauseIcon.setText(Icon.PLAY_UNICODE));
                break;
        }
    }

    private TorrentListener createTorrentListener() {
        return new AbstractTorrentListener() {
            @Override
            public void onDownloadProgress(DownloadStatus status) {
                onProgressChanged(status.getProgress());
            }
        };
    }


    //endregion
}
