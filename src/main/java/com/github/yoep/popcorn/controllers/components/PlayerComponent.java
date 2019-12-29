package com.github.yoep.popcorn.controllers.components;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.yoep.popcorn.activities.*;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.media.video.VideoPlayer;
import com.github.yoep.popcorn.media.video.controls.SubtitleTrack;
import com.github.yoep.popcorn.media.video.state.PlayerState;
import com.github.yoep.popcorn.media.video.time.TimeListener;
import com.github.yoep.popcorn.subtitle.SubtitleService;
import com.github.yoep.popcorn.subtitle.models.SubtitleInfo;
import com.github.yoep.popcorn.torrent.TorrentService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerComponent implements Initializable {
    private static final int OVERLAY_FADE_DURATION = 1500;

    private final PauseTransition idleTimer = new PauseTransition(Duration.seconds(3));
    private final ActivityManager activityManager;
    private final TaskExecutor taskExecutor;
    private final TorrentService torrentService;
    private final SubtitleService subtitleService;

    private Media media;
    private VideoPlayer videoPlayer;
    private long videoChangeTime;

    @FXML
    private Pane playerPane;
    @FXML
    private Pane playerHeader;
    @FXML
    private Pane playerVideoOverlay;
    @FXML
    private Pane playerControls;
    @FXML
    private Pane videoView;
    @FXML
    private SubtitleTrack subtitleTrack;
    @FXML
    private Label currentTime;
    @FXML
    private Label duration;
    @FXML
    private Slider slider;
    @FXML
    private Icon playPauseIcon;
    @FXML
    private Icon fullscreenIcon;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.trace("Initializing video player component for JavaFX");
        initializeSceneEvents();
        initializeVideoPlayer();
        initializeSlider();
    }

    /**
     * Close the video player.
     * This will create a {@link PlayerCloseActivity} with the last known information about the video player state.
     */
    void close() {
        log.trace("Video player is being closed");
        activityManager.register(new PlayerCloseActivity() {
            @Override
            public Media getMedia() {
                return media;
            }

            @Override
            public long getTime() {
                return 0;
            }

            @Override
            public long getLength() {
                return 0;
            }
        });

        onClose();
    }

    @PostConstruct
    private void init() {
        initializeListeners();
    }

    @PreDestroy
    private void dispose() {
        videoPlayer.dispose();
    }

    private void initializeSceneEvents() {
        playerPane.setOnKeyReleased(event -> {
            switch (event.getCode()) {
                case LEFT:
                case KP_LEFT:
                    increaseVideoTime(-5000);
                    break;
                case RIGHT:
                case KP_RIGHT:
                    increaseVideoTime(5000);
                    break;
                case SPACE:
                    changePlayPauseState();
                    break;
                case F11:
                    toggleFullscreen();
                    break;
            }
        });
        idleTimer.setOnFinished(e -> onHideOverlay());
        playerPane.addEventHandler(Event.ANY, e -> onShowOverlay());
    }

    private void initializeVideoPlayer() {
        if (videoPlayer != null)
            return;

        this.videoPlayer = new VideoPlayer(videoView);
        this.videoPlayer.addListener((oldState, newState) -> {
            log.debug("Video player state changed to {}", newState);

            switch (newState) {
                case PLAYING:
                    Platform.runLater(() -> playPauseIcon.setText(Icon.PAUSE_UNICODE));
                    break;
                case PAUSED:
                    Platform.runLater(() -> playPauseIcon.setText(Icon.PLAY_UNICODE));
                    break;
                case FINISHED:
                    break;
                case STOPPED:
                    onVideoStopped();
                    break;
            }
        });
        this.videoPlayer.addListener(new TimeListener() {
            @Override
            public void onTimeChanged(long newTime) {
                subtitleTrack.onTimeChanged(newTime);
                Platform.runLater(() -> {
                    currentTime.setText(formatTime(newTime));
                    slider.setValue(newTime);
                });
            }

            @Override
            public void onLengthChanged(long newLength) {
                Platform.runLater(() -> {
                    duration.setText(formatTime(newLength));
                    slider.setMax(newLength);
                });
            }
        });
    }

    private void initializeListeners() {
        activityManager.register(PlayVideoActivity.class, this::onPlayVideo);
        activityManager.register(FullscreenActivity.class, this::onFullscreenChanged);
    }

    private void initializeSlider() {
        slider.valueChangingProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                videoPlayer.pause();
            } else {
                videoPlayer.resume();
            }
        });
        slider.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            if (slider.isValueChanging()) {
                videoPlayer.setTime(newValue.longValue());
            }
        });
        slider.setOnMouseReleased(event -> setVideoTime(slider.getValue() + 1));
    }

    private void onPlayVideo(PlayVideoActivity activity) {
        log.debug("Received play video activity for url {}, quality {} and media {}", activity.getUrl(), activity.getQuality().orElse("-"), activity.getMedia());
        this.media = activity.getMedia();
        this.videoChangeTime = System.currentTimeMillis();

        // check if a subtitle was selected
        if (activity.getSubtitle().isPresent()) {
            // download the subtitle before starting the playback
            SubtitleInfo subtitle = activity.getSubtitle().get();
            log.debug("Downloading subtitle \"{}\" for video playback", subtitle);

            subtitleService.downloadAndParse(subtitle).whenComplete((subtitles, throwable) -> {
                if (throwable != null) {
                    log.error("Video subtitle failed, " + throwable.getMessage(), throwable);
                } else {
                    log.debug("Successfully retrieved parsed subtitle");
                    subtitleTrack.setSubtitles(subtitles);
                    playUrl(activity.getUrl());
                }
            });
        } else {
            // instant play video
            playUrl(activity.getUrl());
        }
    }

    private void onClose() {
        reset();

        torrentService.stopStream();
    }

    private void playUrl(String url) {
        this.videoPlayer.play(url);
    }

    private void onFullscreenChanged(FullscreenActivity activity) {
        if (activity.isFullscreen()) {
            Platform.runLater(() -> fullscreenIcon.setText(Icon.COLLAPSE_UNICODE));
        } else {
            Platform.runLater(() -> fullscreenIcon.setText(Icon.EXPAND_UNICODE));
        }
    }

    private void onHideOverlay() {
        if (videoPlayer.getPlayerState() != PlayerState.PLAYING)
            return;

        log.debug("Hiding video player overlay");
        playerPane.setCursor(Cursor.NONE);
        playerVideoOverlay.setCursor(Cursor.NONE);

        FadeTransition transitionHeader = new FadeTransition(Duration.millis(OVERLAY_FADE_DURATION), playerHeader);
        FadeTransition transitionControls = new FadeTransition(Duration.millis(OVERLAY_FADE_DURATION), playerControls);

        transitionHeader.setToValue(0.0);
        transitionControls.setToValue(0.0);

        transitionHeader.play();
        transitionControls.play();
    }

    private void onShowOverlay() {
        playerPane.setCursor(Cursor.DEFAULT);
        playerVideoOverlay.setCursor(Cursor.HAND);

        playerHeader.setOpacity(1.0);
        playerControls.setOpacity(1.0);

        idleTimer.playFromStart();
    }

    private void onVideoStopped() {
        // check if the video has been started for more than 1.5 sec before exiting the video player
        // this should fix the issue of the video player closing directly in some cases
        if (System.currentTimeMillis() - videoChangeTime <= 1500)
            return;

        close();
    }

    private void reset() {
        log.trace("Video player component is being reset");
        this.media = null;
        this.videoChangeTime = 0;

        Platform.runLater(() -> {
            slider.setValue(0);
            currentTime.setText(formatTime(0));
            duration.setText(formatTime(0));
            subtitleTrack.clear();
        });

        taskExecutor.execute(() -> videoPlayer.stop());
    }

    private void changePlayPauseState() {
        if (videoPlayer.getPlayerState() == PlayerState.PAUSED) {
            log.trace("Video player state is being changed to \"resume\"");
            videoPlayer.resume();
        } else {
            log.trace("Video player state is being changed to \"paused\"");
            videoPlayer.pause();
        }
    }

    private void toggleFullscreen() {
        log.trace("Toggling full screen mode");
        activityManager.register(new ToggleFullscreenActivity() {
        });
    }

    private void increaseVideoTime(double amount) {
        log.trace("Increasing video time with {}", amount);
        double newSliderValue = slider.getValue() + amount;
        double maxSliderValue = slider.getMax();

        if (newSliderValue > maxSliderValue)
            newSliderValue = maxSliderValue;

        setVideoTime(newSliderValue);
    }

    private void setVideoTime(double time) {
        slider.setValueChanging(true);
        slider.setValue(time);
        slider.setValueChanging(false);
    }

    private String formatTime(long time) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(time),
                TimeUnit.MILLISECONDS.toSeconds(time) % 60);
    }

    @FXML
    private void onPlayerClick() {
        changePlayPauseState();
    }

    @FXML
    private void onPlayPauseClicked() {
        changePlayPauseState();
    }

    @FXML
    private void onFullscreenClicked() {
        toggleFullscreen();
    }
}
