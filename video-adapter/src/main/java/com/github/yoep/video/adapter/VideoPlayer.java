package com.github.yoep.video.adapter;

import com.github.yoep.video.adapter.state.PlayerState;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.layout.Pane;

/**
 * Adapter definition of a video player.
 */
public interface VideoPlayer {
    String PLAYER_STATE_PROPERTY = "playerState";
    String TIME_PROPERTY = "time";
    String DURATION_PROPERTY = "duration";

    //region Properties

    /**
     * Get the current state of the video player.
     *
     * @return Returns the current state of the video player.
     */
    PlayerState getPlayerState();

    /**
     * Get the video player state property.
     *
     * @return Returns
     */
    ObjectProperty<PlayerState> playerStateProperty();

    /**
     * Get the current playback time of the video player.
     *
     * @return Returns the milliseconds of the media playback.
     */
    long getTime();

    /**
     * Get the time property of the video player.
     *
     * @return Returns the time property.
     */
    LongProperty timeProperty();

    /**
     * Get the length of the current media playback.
     *
     * @return Returns the length in milliseonds of the media playback.
     */
    long getDuration();

    /**
     * Get the length property of the video player.
     *
     * @return Returns the length property.
     */
    LongProperty durationProperty();

    //endregion

    //region Getters & Setters

    /**
     * Check if the video player has been initialized.
     *
     * @return Returns true if the video player has been initialized, else false.
     */
    boolean isInitialized();

    /**
     * Get the last error that occurred in the media player.
     *
     * @return Returns the last error of the media player (can be null).
     */
    Throwable getError();

    //endregion

    //region Methods

    /**
     * Initialize the video player.
     * This will use the video pane for rendering the video or adding additional controls.
     *
     * @param videoPane The pane that will be used by the video player for displaying the video.
     */
    void initialize(Pane videoPane);

    /**
     * Dispose the video player instance.
     */
    void dispose();

    /**
     * Play the given media url in the video player.
     * This will interrupt any media that is currently being played.
     *
     * @param url The media url to play.
     * @throws VideoPlayerNotInitializedException Is thrown when the video player has not yet been initialized.
     */
    void play(String url) throws VideoPlayerNotInitializedException;

    /**
     * Pause the media playback of the video player.
     *
     * @throws VideoPlayerNotInitializedException Is thrown when the video player has not yet been initialized.
     */
    void pause() throws VideoPlayerNotInitializedException;

    /**
     * Resume the media playback.
     *
     * @throws VideoPlayerNotInitializedException Is thrown when the video player has not yet been initialized.
     */
    void resume() throws VideoPlayerNotInitializedException;

    /**
     * Seek the given time in the current media playback.
     *
     * @param time The time to seek for in the current playback.
     * @throws VideoPlayerNotInitializedException Is thrown when the video player has not yet been initialized.
     */
    void seek(long time) throws VideoPlayerNotInitializedException;

    /**
     * Stop the current media playback in the video player.
     */
    void stop();

    //endregion
}
