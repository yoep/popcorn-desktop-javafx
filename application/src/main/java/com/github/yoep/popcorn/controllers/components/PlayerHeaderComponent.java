package com.github.yoep.popcorn.controllers.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.PlayVideoActivity;
import com.github.yoep.popcorn.activities.PlayerCloseActivity;
import com.github.yoep.popcorn.torrent.TorrentService;
import com.github.yoep.popcorn.torrent.controls.StreamInfo;
import com.github.yoep.popcorn.torrent.controls.StreamInfoCell;
import com.github.yoep.popcorn.torrent.listeners.TorrentListener;
import com.github.yoep.popcorn.torrent.models.StreamStatus;
import com.github.yoep.popcorn.torrent.models.Torrent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerHeaderComponent implements Initializable {
    private final List<PlayerHeaderListener> listeners = new ArrayList<>();
    private final ActivityManager activityManager;
    private final TorrentService torrentService;
    private final LocaleText localeText;

    @FXML
    private Label title;
    @FXML
    private Label quality;
    @FXML
    private StreamInfo streamInfo;

    //region Methods

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeStreamInfo();
    }

    public boolean isStreamInfoShowing() {
        return streamInfo.isShowing();
    }

    /**
     * Register a new listener to this instance.
     *
     * @param listener The listener to register.
     */
    public void addListener(PlayerHeaderListener listener) {
        Assert.notNull(listener, "listener cannot be null");
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Remove the given listener from this instance.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(PlayerHeaderListener listener) {
        Assert.notNull(listener, "listener cannot be null");
        synchronized (listeners) {
            listeners.remove(listener);
        }
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
        activityManager.register(PlayerCloseActivity.class, this::onClose);
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
                // no-op
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

    private void initializeStreamInfo() {
        streamInfo.setFactory(cell -> new StreamInfoCell(localeText.get("torrent_" + cell)));
    }

    private void onPlayVideo(PlayVideoActivity activity) {
        // update information
        Platform.runLater(() -> {
            title.setText(activity.getMedia().getTitle());

            activity.getQuality().ifPresent(quality -> {
                this.quality.setText(quality);
                this.quality.setVisible(true);
            });
        });
    }

    private void onClose(PlayerCloseActivity activity) {
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
        synchronized (listeners) {
            listeners.forEach(PlayerHeaderListener::onClose);
        }
    }
}
