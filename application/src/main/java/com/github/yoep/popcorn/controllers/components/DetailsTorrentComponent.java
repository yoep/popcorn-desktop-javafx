package com.github.yoep.popcorn.controllers.components;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.CloseOverlayActivity;
import com.github.yoep.popcorn.activities.LoadUrlTorrentActivity;
import com.github.yoep.popcorn.activities.ShowTorrentDetailsActivity;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import static java.util.Arrays.asList;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetailsTorrentComponent implements Initializable {
    private static final List<String> SUPPORTED_FILES = asList("mp4", "m4v", "avi", "mov", "mkv", "wmv");
    private final ActivityManager activityManager;

    private TorrentInfo torrentInfo;

    @FXML
    private ListView<String> fileList;
    @FXML
    private Pane fileShadow;

    //region Methods

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeFileShadow();
        initializeFileList();
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeListeners();
    }

    private void initializeListeners() {
        activityManager.register(ShowTorrentDetailsActivity.class, this::onShowTorrentDetails);
    }

    //endregion

    //region Functions

    private void initializeFileShadow() {
        // inner shadows cannot be defined in CSS, so this needs to be done in code
        fileShadow.setEffect(new InnerShadow(BlurType.THREE_PASS_BOX, Color.color(0, 0, 0, 0.8), 10.0, 0.0, 0.0, 0.0));
    }

    private void initializeFileList() {
        fileList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                var files = torrentInfo.files();

                for (int i = 0; i < files.numFiles(); i++) {
                    if (files.fileName(i).equals(newValue)) {
                        onFileClicked(files.fileName(i), i);
                        break;
                    }
                }
            }
        });
    }

    private void onShowTorrentDetails(ShowTorrentDetailsActivity activity) {
        log.debug("Processing details of torrent info {}", activity.getTorrentInfo());
        this.torrentInfo = activity.getTorrentInfo();
        var fileNames = new ArrayList<String>();
        var files = torrentInfo.files();

        for (int i = 0; i < files.numFiles(); i++) {
            String fileName = files.fileName(i);
            String extension = FilenameUtils.getExtension(fileName);

            if (SUPPORTED_FILES.contains(extension.toLowerCase()))
                fileNames.add(fileName);
        }

        // sort files to make it easier fot the user
        Collections.sort(fileNames);

        Platform.runLater(() -> {
            fileList.getItems().clear();
            fileList.getItems().addAll(fileNames);
        });
    }

    private void onFileClicked(String filename, int fileIndex) {
        activityManager.register(new LoadUrlTorrentActivity() {
            @Override
            public TorrentInfo getTorrentInfo() {
                return torrentInfo;
            }

            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public int getFileIndex() {
                return fileIndex;
            }
        });
    }

    private void reset() {
        this.torrentInfo = null;
        Platform.runLater(() -> fileList.getItems().clear());
    }

    private void close() {
        reset();

        activityManager.register(new CloseOverlayActivity() {
        });
    }

    @FXML
    private void onClose() {
        close();
    }

    //endregion
}
