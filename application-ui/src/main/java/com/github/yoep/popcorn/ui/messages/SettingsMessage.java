package com.github.yoep.popcorn.ui.messages;

import com.github.spring.boot.javafx.text.Message;
import lombok.Getter;

@Getter
public enum SettingsMessage implements Message {
    TRAKT_LOGIN_TITLE("settings_trakt_login_title"),
    SETTINGS_SAVED("settings_saved");

    private final String key;

    SettingsMessage(String key) {
        this.key = key;
    }
}
