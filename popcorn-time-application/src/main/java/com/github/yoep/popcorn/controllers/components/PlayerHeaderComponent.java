package com.github.yoep.popcorn.controllers.components;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.yoep.popcorn.activities.PlayerCloseActivity;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.PlayVideoActivity;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerHeaderComponent {
    private final List<PlayerHeaderListener> listeners = new ArrayList<>();
    private final ActivityManager activityManager;

    @FXML
    private Label title;
    @FXML
    private Label quality;
    @FXML
    private Icon playerStats;

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

    @PostConstruct
    private void init() {
        activityManager.register(PlayVideoActivity.class, this::onPlayVideo);
        activityManager.register(PlayerCloseActivity.class, this::onClose);
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
            playerStats.setVisible(false);
        });
    }

    @FXML
    private void close() {
        synchronized (listeners) {
            listeners.forEach(PlayerHeaderListener::onClose);
        }
    }
}