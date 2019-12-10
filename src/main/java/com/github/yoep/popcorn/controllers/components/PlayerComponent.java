package com.github.yoep.popcorn.controllers.components;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.yoep.popcorn.activities.*;
import com.github.yoep.popcorn.media.video.VideoPlayer;
import com.github.yoep.popcorn.media.video.state.PlayerState;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.URL;
import java.util.ResourceBundle;

@Component
@RequiredArgsConstructor
public class PlayerComponent implements Initializable {
    private final PauseTransition idle = new PauseTransition(Duration.seconds(3));
    private final ActivityManager activityManager;

    private VideoPlayer videoPlayer;

    @FXML
    private Pane videoView;
    @FXML
    private Label title;
    @FXML
    private Label timeInfo;
    @FXML
    private Icon playPauseIcon;
    @FXML
    private Icon fullscreenIcon;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeVideoPlayer();
        initializeListeners();
    }

    @PreDestroy
    private void dispose() {
        videoPlayer.dispose();
    }

    private void initializeVideoPlayer() {
        if (videoPlayer != null)
            return;

        this.videoPlayer = new VideoPlayer(videoView);
        this.videoPlayer.addListener((oldState, newState) -> {
            switch (newState) {
                case PLAYING:
                    Platform.runLater(() -> playPauseIcon.setText(Icon.PAUSE_UNICODE));
                    break;
                case PAUSED:
                    Platform.runLater(() -> playPauseIcon.setText(Icon.PLAY_UNICODE));
                    break;
            }
        });
    }

    private void initializeListeners() {
        activityManager.register(PlayMediaTrailerActivity.class, activity -> {
            videoPlayer.play(activity.getUrl());
            Platform.runLater(() -> title.setText(activity.getMedia().getTitle()));
        });
        activityManager.register(FullscreenActivity.class, activity -> {
            if (activity.isFullscreen()) {
                Platform.runLater(() -> fullscreenIcon.setText(Icon.COLLAPSE_UNICODE));
            } else {
                Platform.runLater(() -> fullscreenIcon.setText(Icon.EXPAND_UNICODE));
            }
        });
    }

    private void changePlayPauseState() {
        if (videoPlayer.getPlayerState() == PlayerState.PAUSED) {
            videoPlayer.resume();
        } else {
            videoPlayer.pause();
        }
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
        activityManager.register(new ToggleFullscreenActivity() {
        });
    }

    @FXML
    private void close() {
        videoPlayer.stop();
        activityManager.register(new PlayerCloseActivity() {
        });
    }
}
