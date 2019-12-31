package com.github.yoep.popcorn.controllers.components;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Component
public class SettingsSubtitlesComponent implements Initializable {
    @FXML
    private ComboBox defaultSubtitle;
    @FXML
    private ComboBox fontFamily;
    @FXML
    private ComboBox decoration;
    @FXML
    private ComboBox fontSize;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }
}
