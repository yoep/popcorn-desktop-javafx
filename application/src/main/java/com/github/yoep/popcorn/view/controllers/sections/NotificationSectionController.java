package com.github.yoep.popcorn.view.controllers.sections;

import com.github.spring.boot.javafx.view.ViewLoader;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.NotificationActivity;
import com.github.yoep.popcorn.view.controllers.components.NotificationComponent;
import javafx.animation.Animation;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class NotificationSectionController {
    private static final int SAFETY_OFFSET = 10;

    private final ActivityManager activityManager;
    private final ViewLoader viewLoader;

    @FXML
    private Pane rootPane;

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeEventListeners();
    }

    private void initializeEventListeners() {
        activityManager.register(NotificationActivity.class, this::displayNotification);
    }

    //endregion

    //region Functions

    private void displayNotification(NotificationActivity notificationActivity) {
        var notificationPane = loadNotificationPane(notificationActivity);
        var transition = new TranslateTransition(Duration.seconds(1), notificationPane);

        Platform.runLater(() -> {
            notificationPane.setVisible(false);
            rootPane.getChildren().add(notificationPane);

            notificationPane.widthProperty().addListener((observable, oldValue, newValue) -> {
                if (transition.getStatus() != Animation.Status.RUNNING) {
                    notificationPane.setTranslateX(newValue.doubleValue() + SAFETY_OFFSET);
                    transition.setToX(0);
                    transition.playFromStart();
                    notificationPane.setVisible(true);
                }
            });
        });
    }

    private Pane loadNotificationPane(NotificationActivity notificationActivity) {
        var controller = new NotificationComponent(notificationActivity);

        controller.setOnClose(this::closeNotification);

        return viewLoader.load("common/components/notification.component.fxml", controller);
    }

    private void closeNotification(ActionEvent action) {
        var notificationPane = (Pane) action.getSource();
        var transition = new TranslateTransition(Duration.seconds(1), notificationPane);

        Platform.runLater(() -> {
            transition.setToX(notificationPane.getWidth() + SAFETY_OFFSET);
            transition.setOnFinished(event -> rootPane.getChildren().removeIf(e -> e == notificationPane));
            transition.playFromStart();
        });
    }

    //endregion
}
