package com.github.yoep.popcorn.ui.view.controllers.common.components;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.ui.events.CloseDetailsEvent;
import com.github.yoep.popcorn.ui.media.providers.models.Media;
import com.github.yoep.popcorn.ui.media.providers.models.MediaTorrentInfo;
import com.github.yoep.popcorn.ui.settings.SettingsService;
import com.github.yoep.popcorn.ui.settings.models.PlaybackSettings;
import com.github.yoep.popcorn.ui.view.controls.BackgroundImageCover;
import com.github.yoep.popcorn.ui.view.controls.HealthIcon;
import com.github.yoep.popcorn.ui.view.controls.ImageCover;
import com.github.yoep.popcorn.ui.view.controls.Stars;
import com.github.yoep.popcorn.ui.view.services.HealthService;
import com.github.yoep.popcorn.ui.view.services.ImageService;
import com.github.yoep.torrent.adapter.model.TorrentHealth;
import javafx.fxml.FXML;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractDetailsComponent<T extends Media> {
    private static final String POSTER_HOLDER_URI = "/images/posterholder.png";
    private static final Image POSTER_HOLDER = loadPosterHolder();
    private static final Pattern QUALITY_PATTERN = Pattern.compile("(?i)[0-9]+p?");

    protected final LocaleText localeText;
    protected final ImageService imageService;
    protected final HealthService healthService;
    protected final SettingsService settingsService;

    protected T media;

    @FXML
    protected HealthIcon health;
    @FXML
    protected Stars ratingStars;
    @FXML
    protected Pane posterHolder;
    @FXML
    protected ImageCover poster;
    @FXML
    protected BackgroundImageCover backgroundImage;

    //region Methods

    /**
     * Invoked when the details are being closed.
     * This method will reset the details events.
     */
    @EventListener(CloseDetailsEvent.class)
    public void onCloseDetails() {
        reset();
    }

    /**
     * Load the details of the given {@link Media} item.
     *
     * @param media The media item to load the details of.
     */
    protected void load(T media) {
        // always reset the details when a new show is being loaded
        // as the onCloseDetails might have been bypassed through another event
        reset();

        Assert.notNull(media, "media cannot be null");
        this.media = media;

        loadBackgroundImage();
        loadPosterImage();
        loadStars();
    }

    /**
     * Load the media poster for the given media.
     *
     * @param media The media to load the poster of.
     * @return Returns the completable future of the poster load action.
     */
    protected abstract CompletableFuture<Optional<Image>> loadPoster(Media media);

    /**
     * Load the stars component.
     * This will set the rating of the stars that needs to be shown.
     */
    protected void loadStars() {
        ratingStars.setRating(media.getRating());
    }

    /**
     * Switch the health icon to the current media torrent info.
     *
     * @param torrentInfo The media torrent info to display the health status of.
     */
    protected void switchHealth(MediaTorrentInfo torrentInfo) {
        this.health.setUpdating(true);

        // set the health based on the API information
        var health = healthService.calculateHealth(torrentInfo.getSeed(), torrentInfo.getPeer());
        updateHealthIcon(health);

        // request the real-time health
        healthService.getTorrentHealth(torrentInfo.getUrl()).whenComplete((torrentHealth, throwable) -> {
            if (throwable == null) {
                updateHealthIcon(torrentHealth);
                this.health.setUpdating(false);
            } else if (!(throwable instanceof CancellationException)) {
                removeHealthState();
                log.error("Failed to retrieve health info, " + throwable.getMessage(), throwable);
            }
        });
    }

    /**
     * Get the video qualities/resolutions for the given torrent set.
     * This method will order the qualities/resolutions from lowest to highest resolution.
     *
     * @param torrents The torrent set for the video playback.
     * @return Returns the list of qualities/resolutions ordered from lowest to highest.
     */
    protected List<String> getVideoResolutions(Map<String, MediaTorrentInfo> torrents) {
        return torrents.keySet().stream()
                // filter out the 0 quality
                .filter(e -> !e.equals("0"))
                // filter out any specials, e.g. "3D"
                .filter(e -> QUALITY_PATTERN.matcher(e).matches())
                .sorted(Comparator.comparing(this::toResolution))
                .collect(Collectors.toList());
    }

    /**
     * Get the default video quality/resolution that should be selected in the media details.
     * This method expects the available resolutions list to be ordered from lowest to highest (see {@link #getVideoResolutions(Map)}.
     *
     * @param availableResolutions The available video qualities/resolutions of the media item.
     * @return Returns the quality/resolution that should be selected by default.
     */
    protected String getDefaultVideoResolution(List<String> availableResolutions) {
        var settings = getPlaybackSettings();
        var defaultQuality = settings.getQuality();

        if (defaultQuality != null) {
            // check if we can find the request playback quality within the available resolutions
            var defaultResolution = getResolutionForPlaybackQuality(availableResolutions, defaultQuality)
                    .orElseGet(() -> defaultQuality.lower() // if not found, try the quality below the current one if possible
                            .flatMap(e -> getResolutionForPlaybackQuality(availableResolutions, e))
                            .orElse(null));

            // check if the default resolution could be found
            // if so, return the found resolution
            // otherwise, return the highest available resolution
            if (defaultResolution != null)
                return defaultResolution;
        }

        // return the highest resolution by default
        return availableResolutions.get(availableResolutions.size() - 1);
    }

    /**
     * Create a new instant {@link Tooltip} for the given text.
     * This will create a {@link Tooltip} with {@link Tooltip#setShowDelay(Duration)} of {@link Duration#ZERO},
     * {@link Tooltip#setShowDuration(Duration)} of {@link Duration#INDEFINITE},
     * {@link Tooltip#setHideDelay(Duration)} of {@link Duration#ZERO}.
     *
     * @param text The text of the tooltip.
     * @return Returns the instant Tooltip.
     */
    protected Tooltip instantTooltip(String text) {
        return instantTooltip(new Tooltip(text));
    }

    /**
     * Update the given tooltip so it's shown instantly.
     * This will update the {@link Tooltip} with {@link Tooltip#setShowDelay(Duration)} of {@link Duration#ZERO},
     * {@link Tooltip#setShowDuration(Duration)} of {@link Duration#INDEFINITE},
     * {@link Tooltip#setHideDelay(Duration)} of {@link Duration#ZERO}.
     *
     * @param tooltip The tooltip to update.
     * @return Returns same tooltip instance..
     */
    protected Tooltip instantTooltip(Tooltip tooltip) {
        tooltip.setShowDelay(Duration.ZERO);
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.setHideDelay(Duration.ZERO);
        return tooltip;
    }

    /**
     * Get the rating text to display.
     *
     * @return Returns the rating display text.
     */
    protected String getRatingText() {
        var score = media.getRating().getPercentage() / 10;
        return score + "/10";
    }

    /**
     * Reset the details component back to it's idle state.
     */
    protected void reset() {
        this.media = null;
        this.poster.reset();
    }

    //endregion

    //region Functions

    private void loadPosterImage() {
        // set the poster holder as the default image
        poster.setImage(POSTER_HOLDER);

        loadPoster(media).whenComplete((image, throwable) -> {
            if (throwable == null) {
                // replace the poster holder with the actual image if present
                image.ifPresent(e -> poster.setImage(e));
            } else {
                log.error(throwable.getMessage(), throwable);
            }
        });
    }

    private void loadBackgroundImage() {
        backgroundImage.reset();
        imageService.loadFanart(media).whenComplete((bytes, throwable) -> {
            if (throwable == null) {
                bytes.ifPresent(e -> backgroundImage.setBackgroundImage(e));
            } else {
                log.error(throwable.getMessage(), throwable);
            }
        });
    }

    private void updateHealthIcon(TorrentHealth health) {
        var healthTooltip = new Tooltip(getHealthTooltip(health));

        healthTooltip.setWrapText(true);
        instantTooltip(healthTooltip);
        Tooltip.install(this.health, healthTooltip);

        if (removeHealthState()) {
            log.trace("Health states have been removed from the health icon");
        }

        this.health.getStyleClass().add(health.getState().getStyleClass());
    }

    private boolean removeHealthState() {
        return this.health.getStyleClass().removeIf(e -> !e.equals("health"));
    }

    private String getHealthTooltip(TorrentHealth health) {
        return localeText.get(health.getState().getKey()) + " - Ratio: " + String.format("%1$,.2f", health.getRatio()) + "\n" +
                "Seeds: " + health.getSeeds() + " - Peers: " + health.getPeers();
    }

    private Integer toResolution(String quality) {
        return Integer.parseInt(quality.replaceAll("[a-z]", ""));
    }

    private Optional<String> getResolutionForPlaybackQuality(List<String> availableResolutions, PlaybackSettings.Quality quality) {
        return availableResolutions.stream()
                .filter(e -> toResolution(e) == quality.getRes())
                .findFirst();
    }

    private PlaybackSettings getPlaybackSettings() {
        return settingsService.getSettings().getPlaybackSettings();
    }

    private static Image loadPosterHolder() {
        try {
            var resource = new ClassPathResource(POSTER_HOLDER_URI);

            if (resource.exists()) {
                return new Image(resource.getInputStream());
            } else {
                log.warn("Poster holder url \"{}\" does not exist", POSTER_HOLDER_URI);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        return null;
    }

    //endregion
}
