package com.github.yoep.popcorn.controllers;

import com.github.spring.boot.javafx.ui.scale.ScaleAwareImpl;
import com.github.spring.boot.javafx.view.ViewLoader;
import com.github.yoep.popcorn.activities.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

@Controller
@RequiredArgsConstructor
public class MainController extends ScaleAwareImpl implements Initializable {
    private final ActivityManager activityManager;
    private final ViewLoader viewLoader;
    private final TaskExecutor taskExecutor;

    private Pane contentPane;
    private Pane settingsPane;
    private Pane playerPane;

    @FXML
    private Pane pane;

    //region Methods

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        switchSection(ActiveSection.CONTENT);
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializePanes();
        initializeListeners();
    }

    private void initializePanes() {
        // load the content pane on the main thread
        // this blocks Spring from completing the startup stage while this pane is being loaded
        contentPane = viewLoader.load("sections/content.section.fxml");

        // load the other panes on a different thread
        taskExecutor.execute(() -> settingsPane = viewLoader.load("sections/settings.section.fxml"));
        taskExecutor.execute(() -> playerPane = viewLoader.load("sections/player.section.fxml"));
    }

    private void initializeListeners() {
        activityManager.register(PlayMediaActivity.class, activity -> switchSection(ActiveSection.PLAYER));
        activityManager.register(ShowSettingsActivity.class, activity -> switchSection(ActiveSection.SETTINGS));
        activityManager.register(CloseSettingsActivity.class, activity -> switchSection(ActiveSection.CONTENT));
        activityManager.register(PlayerCloseActivity.class, activity -> switchSection(ActiveSection.CONTENT));
    }

    //endregion

    //region Functions

    private void switchSection(ActiveSection activeSection) {
        AtomicReference<Pane> content = new AtomicReference<>();

        switch (activeSection) {
            case CONTENT:
                content.set(contentPane);
                break;
            case SETTINGS:
                content.set(settingsPane);
                break;
            case PLAYER:
                content.set(playerPane);
                break;
        }

        Platform.runLater(() -> {
            pane.getChildren().clear();
            pane.getChildren().add(content.get());
        });
    }

    //endregion

    private enum ActiveSection {
        CONTENT,
        SETTINGS,
        PLAYER
    }
}
