package com.github.yoep.popcorn.watched;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yoep.popcorn.PopcornTimeApplication;
import com.github.yoep.popcorn.media.providers.models.Episode;
import com.github.yoep.popcorn.media.providers.models.Media;
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
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchedService {
    private static final String NAME = "watched.json";

    private final List<String> cache = new ArrayList<>();
    private final ObjectMapper objectMapper;

    //region Methods

    /**
     * Check if the given media has been watched already.
     *
     * @param media The media to check the watched state for.
     * @return Returns true if the media has already been watched, else false.
     */
    public boolean isWatched(Media media) {
        Assert.notNull(media, "media cannot be null");
        String key = media.getImdbId();

        return isWatched(key);
    }

    /**
     * Check if the given episode has been watched already.
     *
     * @param episode The episode to verify.
     * @return Returns true if the episode has been watched, else false.
     */
    public boolean isWatched(Episode episode) {
        Assert.notNull(episode, "episode cannot be null");
        String key = String.valueOf(episode.getTvdbId());

        return isWatched(key);
    }

    /**
     * Add the media item to the watched list.
     *
     * @param media the media item to add.
     */
    public void addToWatchList(Media media) {
        Assert.notNull(media, "media cannot be null");
        String key = media.getImdbId();

        addToWatchList(key);
    }

    /**
     * Add the episode item to the watched list.
     *
     * @param episode The episode to add.
     */
    public void addToWatchList(Episode episode) {
        Assert.notNull(episode, "episode cannot be null");
        String key = String.valueOf(episode.getTvdbId());

        addToWatchList(key);
    }

    /**
     * Remove the media item from the watched list.
     *
     * @param media The media item to remove.
     */
    public void removeFromWatchList(Media media) {
        Assert.notNull(media, "media cannot be null");
        synchronized (cache) {
            cache.remove(media.getImdbId());
        }
    }

    /**
     * Remove the episode from the watched list.
     *
     * @param episode The episode to remove.
     */
    public void removeFromWatchList(Episode episode) {
        Assert.notNull(episode, "episode cannot be null");
        synchronized (cache) {
            cache.remove(String.valueOf(episode.getTvdbId()));
        }
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        cache.addAll(loadWatched());
    }

    //endregion

    //region PreDestroy

    @PreDestroy
    private void destroy() {
        save(cache);
    }

    //endregion

    //region Functions

    private void addToWatchList(String key) {
        // prevent keys from being added twice
        if (cache.contains(key))
            return;

        synchronized (cache) {
            cache.add(key);
        }
    }

    private boolean isWatched(String key) {
        synchronized (cache) {
            return cache.contains(key);
        }
    }

    private void save(List<String> watched) {
        File file = getFile();

        try {
            log.debug("Saving watched items to {}", file.getAbsolutePath());
            FileUtils.writeStringToFile(file, objectMapper.writeValueAsString(watched), Charset.defaultCharset());
        } catch (IOException ex) {
            log.error("Failed to save the watched items with error" + ex.getMessage(), ex);
        }
    }

    private List<String> loadWatched() {
        File file = getFile();

        if (file.exists()) {
            try {
                log.debug("Loading watched items from {}", file.getAbsolutePath());

                return asList(objectMapper.readValue(file, String[].class));
            } catch (IOException ex) {
                log.error("Unable to read watched items file at " + file.getAbsolutePath(), ex);
            }
        }

        return Collections.emptyList();
    }

    private File getFile() {
        return new File(PopcornTimeApplication.APP_DIR + NAME);
    }

    //endregion
}
