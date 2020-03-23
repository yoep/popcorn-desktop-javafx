package com.github.yoep.popcorn.view.controllers.desktop.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.view.controllers.common.SimpleItemListener;
import com.github.yoep.popcorn.view.services.ImageService;
import javafx.fxml.FXML;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class SimpleMediaCardComponent extends AbstractMediaCardComponent {
    private final List<SimpleItemListener> listeners = new ArrayList<>();

    public SimpleMediaCardComponent(Media media, LocaleText localeText, ImageService imageService, SimpleItemListener... listeners) {
        super(media, localeText, imageService);
        this.listeners.addAll(asList(listeners));
    }

    @FXML
    private void showDetails() {
        listeners.forEach(listener -> listener.onClicked(media));
    }
}