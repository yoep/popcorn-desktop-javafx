package com.github.yoep.popcorn.view.controls;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.layout.StackPane;

public class ProgressControl extends StackPane {
    public static final String DURATION_PROPERTY = "duration";
    public static final String TIME_PROPERTY = "time";

    private static final String STYLE_CLASS = "progress";
    private static final String LOAD_PROGRESS_STYLE_CLASS = "load-progress";
    private static final String PLAY_PROGRESS_STYLE_CLASS = "play-progress";

    private final LongProperty duration = new SimpleLongProperty(this, DURATION_PROPERTY);
    private final LongProperty time = new SimpleLongProperty(this, TIME_PROPERTY);

    private final StackPane playProgress = new StackPane();
    private final StackPane loadProgress = new StackPane();

    public ProgressControl() {
        init();
    }

    //region Properties

    public long getDuration() {
        return duration.get();
    }

    public LongProperty durationProperty() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration.set(duration);
    }

    public long getTime() {
        return time.get();
    }

    public LongProperty timeProperty() {
        return time;
    }

    public void setTime(long time) {
        this.time.set(time);
    }

    //endregion

    //region Functions

    private void init() {
        initializePlayProgress();
        initializeLoadProgress();

        this.getStyleClass().add(STYLE_CLASS);
        this.getChildren().addAll(loadProgress, playProgress);
    }

    private void initializePlayProgress() {
        playProgress.getStyleClass().add(PLAY_PROGRESS_STYLE_CLASS);
    }

    private void initializeLoadProgress() {
        loadProgress.getStyleClass().add(LOAD_PROGRESS_STYLE_CLASS);
    }

    //endregion
}