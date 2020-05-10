package com.github.yoep.video.vlc;

import com.github.yoep.video.adapter.VideoPlayerException;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;

import javax.annotation.PostConstruct;

import static uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurfaceFactory.videoSurfaceForImageView;

@Slf4j
@ToString
@EqualsAndHashCode(callSuper = true)
public class VideoPlayerVlc extends AbstractVideoPlayer {
    private final ImageView videoSurface = new ImageView();

    private final MediaPlayerFactory mediaPlayerFactory;

    //region Constructors

    /**
     * Instantiate a new video player.
     */
    public VideoPlayerVlc() {
        mediaPlayerFactory = new MediaPlayerFactory();
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();

        initialize();
    }

    //endregion

    //region Getters

    @Override
    public Node getVideoSurface() {
        return videoSurface;
    }

    //endregion

    //region VideoPlayer

    @Override
    public void dispose() {
        stop();
        mediaPlayer.release();
        mediaPlayerFactory.release();
    }

    @Override
    public void play(String url) {
        checkInitialized();

        invokeOnVlc(() -> mediaPlayer.media().play(url, VLC_OPTIONS));
    }

    @Override
    public void pause() {
        checkInitialized();

        invokeOnVlc(() -> mediaPlayer.controls().pause());
    }

    @Override
    public void resume() {
        checkInitialized();

        invokeOnVlc(() -> mediaPlayer.controls().play());
    }

    @Override
    public void seek(long time) {
        checkInitialized();

        invokeOnVlc(() -> mediaPlayer.controls().setTime(time));
    }

    @Override
    public void stop() {
        checkInitialized();

        invokeOnVlc(() -> mediaPlayer.controls().stop());
        reset();
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        log.trace("Initializing VLC player");

        try {
            this.mediaPlayer.videoSurface().set(videoSurfaceForImageView(videoSurface));

            initialized = true;
            log.trace("VLC player initialization done");
        } catch (Exception ex) {
            log.error("Failed to initialize VLC player, " + ex.getMessage(), ex);
            setError(new VideoPlayerException(ex.getMessage(), ex));
        }
    }

    //endregion

    //region Functions

    @Override
    protected void initialize() {
        super.initialize();
        initializeListeners();
    }

    private void initializeListeners() {
    }

    //endregion
}
