package com.github.yoep.popcorn.view.controllers.desktop.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.ClosePlayerActivity;
import com.github.yoep.popcorn.activities.PlayMediaActivity;
import com.github.yoep.popcorn.activities.PlayVideoActivity;
import com.github.yoep.popcorn.torrent.TorrentService;
import com.github.yoep.popcorn.torrent.controls.StreamInfo;
import com.github.yoep.popcorn.torrent.controls.StreamInfoCell;
import com.github.yoep.popcorn.torrent.listeners.TorrentListener;
import com.github.yoep.popcorn.torrent.models.StreamStatus;
import com.github.yoep.popcorn.torrent.models.Torrent;
import com.github.yoep.popcorn.view.services.VideoPlayerService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@RequiredArgsConstructor
public class PlayerHeaderComponent implements Initializable {
    private final ActivityManager activityManager;
    private final TorrentService torrentService;
    private final VideoPlayerService videoPlayerService;
    private final LocaleText localeText;

    @FXML
    private Label title;
    @FXML
    private Label quality;
    @FXML
    private StreamInfo streamInfo;

    //region Initializable

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeStreamInfo();
    }

    private void initializeStreamInfo() {
        streamInfo.setFactory(cell -> new StreamInfoCell(localeText.get("torrent_" + cell)));
        streamInfo.setVisible(false);
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeActivityListeners();
        initializeTorrentListeners();
    }

    private void initializeActivityListeners() {
        activityManager.register(PlayVideoActivity.class, this::onPlayVideo);
        activityManager.register(ClosePlayerActivity.class, this::onClose);
    }

    private void initializeTorrentListeners() {
        torrentService.addListener(new TorrentListener() {
            @Override
            public void onLoadError(String message) {
                // no-op
            }

            @Override
            public void onStreamStarted(Torrent torrent) {
                // no-op
            }

            @Override
            public void onStreamError(Torrent torrent, Exception e) {
                // no-op
            }

            @Override
            public void onStreamReady(Torrent torrent) {
                Platform.runLater(() -> streamInfo.setVisible(true));
            }

            @Override
            public void onStreamProgress(Torrent torrent, StreamStatus status) {
                streamInfo.update(status);
            }

            @Override
            public void onStreamStopped() {
                // no-op
            }
        });
    }

    //endregion

    //region Functions

    private void onPlayVideo(PlayVideoActivity activity) {
        // set the title of the video as it should be always present
        Platform.runLater(() -> {
            this.title.setText(activity.getTitle());
            this.quality.setVisible(false);
        });

        // check if the video contains media information
        // if so, update additional information of the media
        if (activity instanceof PlayMediaActivity) {
            var mediaActivity = (PlayMediaActivity) activity;
            onPlayMedia(mediaActivity);
        }
    }

    private void onPlayMedia(PlayMediaActivity activity) {
        Platform.runLater(() -> {
            this.quality.setText(activity.getQuality());
            this.quality.setVisible(true);
        });
    }

    private void onClose(ClosePlayerActivity activity) {
        reset();
    }

    private void reset() {
        Platform.runLater(() -> {
            title.setText(null);
            quality.setText(null);
            quality.setVisible(false);
            streamInfo.setVisible(false);
        });
    }

    @FXML
    private void close() {
        videoPlayerService.close();
    }

    //endregion
}
