package com.github.yoep.popcorn.watched;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yoep.popcorn.PopcornTimeApplication;
import com.github.yoep.popcorn.activities.ActivityManager;
import com.github.yoep.popcorn.activities.ClosePlayerActivity;
import com.github.yoep.popcorn.providers.models.Media;
import com.github.yoep.popcorn.providers.models.MediaType;
import com.github.yoep.popcorn.watched.models.Watchable;
import com.github.yoep.popcorn.watched.models.Watched;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchedService {
    private static final String NAME = "watched.json";
    private static final int WATCHED_PERCENTAGE_THRESHOLD = 90;
    private static final int IDLE_TIME = 10;

    private final PauseTransition idleTimer = new PauseTransition(Duration.seconds(IDLE_TIME));
    private final ActivityManager activityManager;
    private final ObjectMapper objectMapper;
    private final Object cacheLock = new Object();

    /**
     * The currently loaded watched cache.
     * This cache is saved and unloaded after {@link #IDLE_TIME} seconds to free up memory.
     */
    private Watched cache;

    //region Methods

    /**
     * Check if the given watchable has been watched already.
     *
     * @param watchable The watchable to check the watched state for.
     * @return Returns true if the watchable has already been watched, else false.
     */
    public boolean isWatched(Watchable watchable) {
        Assert.notNull(watchable, "watchable cannot be null");
        String key = watchable.getId();

        return isWatched(key);
    }

    /**
     * Get the watched movie items.
     *
     * @return Returns a list of movie ID's that have been watched.
     */
    public List<String> getWatchedMovies() {
        loadWatchedFileToCache();
        List<String> movies;

        synchronized (cacheLock) {
            movies = new ArrayList<>(cache.getMovies());
        }

        return movies;
    }

    /**
     * Add the watchable item to the watched list.
     *
     * @param watchable the watchable item to add.
     */
    public void addToWatchList(Watchable watchable) {
        Assert.notNull(watchable, "watchable cannot be null");
        String key = watchable.getId();

        addToWatchList(key, watchable.getType());
        watchable.setWatched(true);
    }

    /**
     * Remove the watchable item from the watched list.
     *
     * @param watchable The watchable item to remove.
     */
    public void removeFromWatchList(Watchable watchable) {
        Assert.notNull(watchable, "watchable cannot be null");
        String key = watchable.getId();
        loadWatchedFileToCache();

        synchronized (cacheLock) {
            cache.remove(key);
            watchable.setWatched(false);
        }
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeIdleTimer();
        initializeListeners();
    }

    private void initializeIdleTimer() {
        idleTimer.setOnFinished(e -> onSave());
    }

    private void initializeListeners() {
        activityManager.register(ClosePlayerActivity.class, activity -> {
            // check if the media is present
            // if not, the played video might have been a trailer or video file
            if (activity.getMedia().isEmpty())
                return;

            long time = activity.getTime();
            long duration = activity.getDuration();

            // check if both the time and duration of the video are known
            // if not, the close activity media is not eligible for being auto marked as watched
            if (time == ClosePlayerActivity.UNKNOWN || duration == ClosePlayerActivity.UNKNOWN)
                return;

            double percentageWatched = ((double) time / duration) * 100;
            Media media = activity.getMedia().get();

            // check if the media has been watched for the percentage threshold
            // if so, mark the media as watched
            log.trace("Media playback of \"{}\" ({}) has been watched for {}%", media.getTitle(), media.getId(), percentageWatched);
            if (percentageWatched >= WATCHED_PERCENTAGE_THRESHOLD) {
                log.debug("Marking media \"{}\" ({}) automatically as watched", media.getTitle(), media.getId());
                addToWatchList(media);
            }
        });
    }

    //endregion

    //region PreDestroy

    @PreDestroy
    private void destroy() {
        onSave();
    }

    //endregion

    //region Functions

    private void addToWatchList(String key, MediaType type) {
        loadWatchedFileToCache();

        synchronized (cacheLock) {
            // prevent keys from being added twice
            if (cache.contains(key))
                return;

            if (type == MediaType.MOVIE) {
                cache.addMovie(key);
            } else {
                cache.addShow(key);
            }
        }
    }

    private boolean isWatched(String key) {
        loadWatchedFileToCache();

        synchronized (cacheLock) {
            return cache.contains(key);
        }
    }

    private void save(Watched watched) {
        File file = getFile();

        try {
            log.debug("Saving watched items to {}", file.getAbsolutePath());
            FileUtils.writeStringToFile(file, objectMapper.writeValueAsString(watched), Charset.defaultCharset());
        } catch (IOException ex) {
            log.error("Failed to save the watched items with error" + ex.getMessage(), ex);
        }
    }

    private void loadWatchedFileToCache() {
        idleTimer.playFromStart();

        // check if cache is still present
        // if so, return the cache
        if (cache != null) {
            log.trace("Not updating cache as it's already present");
            return;
        }

        File file = getFile();

        if (file.exists()) {
            try {
                log.debug("Loading watched items from {}", file.getAbsolutePath());

                synchronized (cacheLock) {
                    cache = objectMapper.readValue(file, Watched.class);
                }
            } catch (IOException ex) {
                log.error("Unable to read watched items file at " + file.getAbsolutePath(), ex);
            }
        } else {
            synchronized (cacheLock) {
                cache = Watched.builder().build();
            }
        }
    }

    private File getFile() {
        return new File(PopcornTimeApplication.APP_DIR + NAME);
    }

    private void onSave() {
        if (cache == null)
            return;

        synchronized (cacheLock) {
            save(cache);
            cache = null;
        }
    }

    //endregion
}
