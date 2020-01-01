package com.github.yoep.video.javafx;

import com.github.yoep.video.adapter.VideoPlayerNotInitializedException;
import com.github.yoep.video.adapter.state.PlayerState;
import com.github.yoep.video.youtube.VideoPlayerYoutube;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VideoPlayerFX extends VideoPlayerYoutube {
    private MediaView mediaView;
    private MediaPlayer mediaPlayer;

    //region VideoPlayer

    @Override
    public void initialize(Pane videoPane) {
        super.initialize(videoPane);

        Platform.runLater(() -> {
            mediaView = new MediaView();
            videoPane.getChildren().add(mediaView);
        });
    }

    @Override
    public void dispose() {
        mediaPlayer.dispose();
        mediaView = null;
    }

    @Override
    public void play(String url) throws VideoPlayerNotInitializedException {
        super.play(url);

        if (!isYoutubeUrl(url)) {
            hide();

            mediaPlayer = new MediaPlayer(new Media(url));
            initializeMediaPlayerEvents();
            mediaPlayer.play();
        }
    }

    @Override
    public void pause() throws VideoPlayerNotInitializedException {
        super.pause();

        if (isYoutubePlayerActive())
            return;

        mediaPlayer.pause();
    }

    @Override
    public void resume() throws VideoPlayerNotInitializedException {
        super.resume();

        if (isYoutubePlayerActive())
            return;

        mediaPlayer.play();
    }

    @Override
    public void seek(long time) throws VideoPlayerNotInitializedException {
        super.seek(time);

        if (isYoutubePlayerActive())
            return;

        mediaPlayer.seek(Duration.millis(time));
    }

    @Override
    public void stop() {
        super.stop();

        if (isYoutubePlayerActive() || mediaPlayer == null)
            return;

        mediaPlayer.stop();
        mediaPlayer = null;
    }

    //endregion

    //region Functions

    private void initializeMediaPlayerEvents() {
        if (mediaPlayer == null)
            return;

        setTime((long) mediaPlayer.getCurrentTime().toMillis());
        setDuration((long) mediaPlayer.getTotalDuration().toMillis());
        setPlayerState(convertStatus(mediaPlayer.getStatus()));

        mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> setTime((long) newValue.toMillis()));
        mediaPlayer.totalDurationProperty().addListener((observable, oldValue, newValue) -> setDuration((long) newValue.toMillis()));
        mediaPlayer.statusProperty().addListener((observable, oldValue, newValue) -> setPlayerState(convertStatus(newValue)));
        mediaPlayer.setOnEndOfMedia(() -> setPlayerState(PlayerState.FINISHED));
        mediaPlayer.setOnError(this::onError);
    }

    private PlayerState convertStatus(MediaPlayer.Status status) {
        switch (status) {
            case PLAYING:
                return PlayerState.PLAYING;
            case PAUSED:
                return PlayerState.PAUSED;
            case STOPPED:
                return PlayerState.STOPPED;
            case UNKNOWN:
            default:
                return PlayerState.UNKNOWN;
        }
    }

    private void onError() {
        MediaException error = mediaPlayer.getError();
        log.error("JavaFX player encountered an error, " + error.getMessage(), error);

        setPlayerState(PlayerState.ERROR);
    }

    //endregion
}
