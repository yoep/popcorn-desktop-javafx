package com.github.yoep.popcorn.ui.view.services;

import com.github.spring.boot.javafx.view.ViewManager;
import com.github.yoep.popcorn.ui.events.PlayerStoppedEvent;
import com.github.yoep.popcorn.ui.settings.OptionsService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * The {@link FullscreenService} manages the full screen mode of the application.
 * This service can be used to retrieve the current full screen mode as well as update it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullscreenService {
    public static final String FULLSCREEN_PROPERTY = "fullscreen";

    private final ViewManager viewManager;
    private final OptionsService optionsService;

    private final BooleanProperty fullscreen = new SimpleBooleanProperty(this, FULLSCREEN_PROPERTY, false);

    private Stage primaryStage;
    private long lastChange;

    //region Properties

    /**
     * Check if the application is currently in fullscreen mode.
     *
     * @return Returns true if fullscreen is active, else false.
     */
    public boolean isFullscreen() {
        return fullscreen.get();
    }

    /**
     * Get the fullscreen property of the application.
     *
     * @return Returns the fullscreen property.
     */
    public ReadOnlyBooleanProperty fullscreenProperty() {
        return fullscreen;
    }

    //endregion

    //region Methods

    /**
     * The fullscreen mode of the application.
     *
     * @param enabled The indication if the fullscreen mode should be active or not.
     */
    public void fullscreen(final boolean enabled) {
        Platform.runLater(() -> {
            lastChange = System.currentTimeMillis();
            primaryStage.setFullScreen(enabled);
        });
    }

    /**
     * Toggle the fullscreen mode of the application.
     */
    public void toggle() {
        // check if no duplicate screen toggle command has been received
        if (System.currentTimeMillis() - lastChange < 300)
            return;

        Platform.runLater(() -> {
            lastChange = System.currentTimeMillis();
            primaryStage.setFullScreen(!primaryStage.isFullScreen());
        });
    }

    @EventListener(PlayerStoppedEvent.class)
    public void onClosePlayer() {
        if (!optionsService.options().isKioskMode()) {
            Platform.runLater(() -> primaryStage.setFullScreen(false));
        }
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeViewManagerListeners();
    }

    private void initializeViewManagerListeners() {
        viewManager.primaryStageProperty().addListener((observable, oldValue, newValue) -> registerListener(newValue));
    }

    //endregion

    //region Functions

    private void registerListener(Stage primaryStage) {
        var options = optionsService.options();

        // store the primary screen for later use
        log.trace("Primary stage is being registered");
        this.primaryStage = primaryStage;

        if (options.isKioskMode()) {
            log.trace("Kiosk mode is activated, disabling the fullscreen exit key");
            primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
            primaryStage.setFullScreen(true);
        } else {
            primaryStage.setFullScreenExitKeyCombination(KeyCombination.valueOf(KeyCode.F11.getName()));
        }

        fullscreen.bind(primaryStage.fullScreenProperty());
        fullscreen.addListener((observable, oldValue, newValue) -> lastChange = System.currentTimeMillis());
    }

    //endregion
}
