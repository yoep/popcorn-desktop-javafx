package com.github.yoep.popcorn.ui.view.controllers.desktop.sections;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.ui.settings.SettingsService;
import com.github.yoep.popcorn.ui.view.controllers.common.sections.AbstractPlayerSectionController;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerManagerService;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerService;
import com.github.yoep.popcorn.ui.view.services.VideoPlayerSubtitleService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlayerSectionController extends AbstractPlayerSectionController {
    //region Constructors

    public PlayerSectionController(SettingsService settingsService,
                                   VideoPlayerService videoPlayerService,
                                   VideoPlayerManagerService videoPlayerManagerService,
                                   VideoPlayerSubtitleService videoPlayerSubtitleService,
                                   LocaleText localeText) {
        super(settingsService, videoPlayerService, videoPlayerManagerService, videoPlayerSubtitleService, localeText);
    }

    //endregion

    //region AbstractPlayerSectionController

    @Override
    protected PauseTransition getIdleTimer() {
        return new PauseTransition(Duration.seconds(3));
    }

    @Override
    protected PauseTransition getOffsetTimer() {
        return new PauseTransition(Duration.seconds(2));
    }

    @Override
    protected void onPlayerKeyReleased(KeyEvent event) {
        super.onPlayerKeyReleased(event);
        switch (event.getCode()) {
            case LEFT:
            case KP_LEFT:
                videoPlayerService.videoTimeOffset(-5000);
                event.consume();
                break;
            case RIGHT:
            case KP_RIGHT:
                videoPlayerService.videoTimeOffset(5000);
                event.consume();
                break;
        }
    }

    //endregion

    //region Functions

    @FXML
    private void onPlayerClick(MouseEvent event) {
        event.consume();
        videoPlayerService.togglePlayPause();
    }

    //endregion
}
