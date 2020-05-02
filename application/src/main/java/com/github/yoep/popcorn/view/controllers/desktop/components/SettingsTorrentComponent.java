package com.github.yoep.popcorn.view.controllers.desktop.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.settings.SettingsService;
import com.github.yoep.popcorn.settings.models.TorrentSettings;
import com.github.yoep.popcorn.view.controls.DelayedTextField;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
public class SettingsTorrentComponent extends AbstractSettingsComponent implements Initializable {
    private final DirectoryChooser cacheChooser = new DirectoryChooser();

    @FXML
    private DelayedTextField downloadLimit;
    @FXML
    private DelayedTextField uploadLimit;
    @FXML
    private DelayedTextField connectionLimit;
    @FXML
    private TextField cacheDirectory;
    @FXML
    private CheckBox clearCache;

    public SettingsTorrentComponent(ActivityManager activityManager, LocaleText localeText, SettingsService settingsService) {
        super(activityManager, localeText, settingsService);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeDownloadLimit();
        initializeUploadLimit();
        initializeConnectionLimit();
        initializeCacheDirectory();
        initializeClearCache();
    }

    private void initializeDownloadLimit() {
        var settings = getSettings();

        downloadLimit.setTextFormatter(numericTextFormatter());
        downloadLimit.setValue(String.valueOf(settings.getDownloadRateLimit()));
        downloadLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            try {
                settings.setDownloadRateLimit(Integer.parseInt(newValue));
                showNotification();
            } catch (NumberFormatException ex) {
                log.warn("Download rate limit is invalid, " + ex.getMessage(), ex);
            }
        });
    }

    private void initializeUploadLimit() {
        var settings = getSettings();

        uploadLimit.setTextFormatter(numericTextFormatter());
        uploadLimit.setValue(String.valueOf(settings.getUploadRateLimit()));
        uploadLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            try {
                settings.setUploadRateLimit(Integer.parseInt(newValue));
                showNotification();
            } catch (NumberFormatException ex) {
                log.warn("Upload rate limit is invalid, " + ex.getMessage(), ex);
            }
        });
    }

    private void initializeConnectionLimit() {
        var settings = getSettings();

        connectionLimit.setTextFormatter(numericTextFormatter());
        connectionLimit.setValue(String.valueOf(settings.getConnectionsLimit()));
        connectionLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            try {
                settings.setConnectionsLimit(Integer.parseInt(newValue));
                showNotification();
            } catch (NumberFormatException ex) {
                log.warn("Connection limit is invalid, " + ex.getMessage(), ex);
            }
        });
    }

    private void initializeCacheDirectory() {
        var settings = getSettings();
        var directory = settings.getDirectory();

        cacheChooser.setInitialDirectory(directory);
        cacheDirectory.setText(directory.getAbsolutePath());
        cacheDirectory.textProperty().addListener((observable, oldValue, newValue) -> {
            File newDirectory = new File(newValue);

            if (newDirectory.isDirectory()) {
                settings.setDirectory(newDirectory);
                cacheChooser.setInitialDirectory(newDirectory);
                showNotification();
            }
        });
    }

    private void initializeClearCache() {
        var settings = getSettings();

        clearCache.setSelected(settings.isAutoCleaningEnabled());
        clearCache.selectedProperty().addListener((observable, oldValue, newValue) -> onClearCacheChanged(newValue));
    }

    private void onClearCacheChanged(Boolean newValue) {
        var settings = getSettings();

        settings.setAutoCleaningEnabled(newValue);
        showNotification();
    }

    private TorrentSettings getSettings() {
        return settingsService.getSettings().getTorrentSettings();
    }

    @FXML
    private void onCacheDirectoryClicked(MouseEvent event) {
        Node node = (Node) event.getSource();
        File newDirectory = cacheChooser.showDialog(node.getScene().getWindow());

        if (newDirectory != null && newDirectory.isDirectory()) {
            cacheDirectory.setText(newDirectory.getAbsolutePath());
        }
    }
}
