package com.github.yoep.popcorn.ui.view.controllers.desktop.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.ui.settings.SettingsService;
import com.github.yoep.popcorn.ui.settings.models.PlaybackSettings;
import com.github.yoep.popcorn.ui.view.controllers.common.components.AbstractSettingsComponent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
public class SettingsPlaybackComponent extends AbstractSettingsComponent implements Initializable {

    @FXML
    private ComboBox<PlaybackSettings.Quality> quality;
    @FXML
    private CheckBox fullscreen;
    @FXML
    private CheckBox autoPlayNextEpisode;

    //region Constructors

    public SettingsPlaybackComponent(ApplicationEventPublisher eventPublisher, LocaleText localeText, SettingsService settingsService) {
        super(eventPublisher, localeText, settingsService);
    }

    //endregion

    //region Initializable

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeQuality();
        initializeFullscreen();
        initializeAutoPlayNextEpisode();
    }

    private void initializeQuality() {
        var settings = getPlaybackSettings();
        var items = quality.getItems();

        items.add(null);
        items.addAll(PlaybackSettings.Quality.values());

        quality.setCellFactory(param -> createQualityCell());
        quality.setButtonCell(createQualityCell());
        quality.getSelectionModel().select(settings.getQuality());
        quality.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> onQualityChanged(newValue));
    }

    private void initializeFullscreen() {
        var settings = getPlaybackSettings();

        fullscreen.setSelected(settings.isFullscreen());
        fullscreen.selectedProperty().addListener((observable, oldValue, newValue) -> onFullscreenChanged(newValue));
    }

    private void initializeAutoPlayNextEpisode() {
        var settings = getPlaybackSettings();

        autoPlayNextEpisode.setSelected(settings.isAutoPlayNextEpisodeEnabled());
        autoPlayNextEpisode.selectedProperty().addListener((observable, oldValue, newValue) -> onAutoPlayNextEpisodeChanged(newValue));
    }

    //endregion

    //region Functions

    private ListCell<PlaybackSettings.Quality> createQualityCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PlaybackSettings.Quality item, boolean empty) {
                super.updateItem(item, empty);

                if (!empty) {
                    if (item != null) {
                        setText(item.toString());
                    } else {
                        setText("-");
                    }
                } else {
                    setText(null);
                }
            }
        };
    }

    void onQualityChanged(PlaybackSettings.Quality newValue) {
        var settings = getPlaybackSettings();

        settings.setQuality(newValue);
        showNotification();
    }

    void onFullscreenChanged(Boolean newValue) {
        var settings = getPlaybackSettings();

        settings.setFullscreen(newValue);
        showNotification();
    }

    void onAutoPlayNextEpisodeChanged(Boolean newValue) {
        var settings = getPlaybackSettings();

        settings.setAutoPlayNextEpisodeEnabled(newValue);
        showNotification();
    }

    private PlaybackSettings getPlaybackSettings() {
        return settingsService.getSettings().getPlaybackSettings();
    }

    //endregion
}
