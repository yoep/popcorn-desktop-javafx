package com.github.yoep.popcorn.controllers.components;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.controls.Stars;
import com.github.yoep.popcorn.messages.MediaMessage;
import com.github.yoep.popcorn.media.providers.models.Images;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.media.providers.models.Show;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.Assert;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static java.util.Arrays.asList;

@Slf4j
public class MediaCardComponent extends AbstractCardComponent implements Initializable {
    private static final String LIKED_STYLE_CLASS = "liked";
    private static final String WATCHED_STYLE_CLASS = "watched";

    private final List<ItemListener> listeners = new ArrayList<>();
    private final BooleanProperty favoriteProperty = new SimpleBooleanProperty();
    private final LocaleText localeText;
    private final TaskExecutor taskExecutor;
    private final Media media;

    private Thread imageLoadingThread;
    private ChangeListener<Boolean> listener;

    @FXML
    private Pane posterItem;
    @FXML
    private Label title;
    @FXML
    private Label year;
    @FXML
    private Label seasons;
    @FXML
    private Label ratingValue;
    @FXML
    private Icon favorite;
    @FXML
    private Stars ratingStars;

    public MediaCardComponent(Media media, LocaleText localeText, TaskExecutor taskExecutor, ItemListener... listeners) {
        this.media = media;
        this.localeText = localeText;
        this.taskExecutor = taskExecutor;
        this.listeners.addAll(asList(listeners));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeImage();
        initializeText();
        initializeStars();
        initializeFavorite();
        initializeView();
    }

    /**
     * Set if this media card is liked by the user.
     *
     * @param value The favorite value.
     */
    public void setIsFavorite(boolean value) {
        favoriteProperty.set(value);
    }

    /**
     * Add a listener to this instance.
     *
     * @param listener The listener to add.
     */
    public void addListener(ItemListener listener) {
        Assert.notNull(listener, "listener cannot be null");
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private void initializeImage() {
        imageLoadingThread = new Thread(() -> {
            try {
                // set as default the poster holder image
                Image image = new Image(getPosterHolderResource().getInputStream(), POSTER_WIDTH, POSTER_HEIGHT, true, true);
                setBackgroundImage(image, false);

                //try to load the actual image
                Optional.ofNullable(media.getImages())
                        .map(Images::getPoster)
                        .filter(e -> !e.equalsIgnoreCase("n/a"))
                        .ifPresent(e -> setBackgroundImage(new Image(e, POSTER_WIDTH, POSTER_HEIGHT, true, true), true));
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        });

        // run this on a separate thread for easier UI loading
        taskExecutor.execute(imageLoadingThread);
    }

    private void initializeText() {
        double rating = (double) media.getRating().getPercentage() / 10;

        title.setText(media.getTitle());
        year.setText(media.getYear());
        ratingValue.setText(rating + "/10");

        if (media instanceof Show) {
            Show show = (Show) media;
            String text = localeText.get(MediaMessage.SEASONS, show.getNumberOfSeasons());

            if (show.getNumberOfSeasons() > 1) {
                text += localeText.get(MediaMessage.PLURAL);
            }

            seasons.setText(text);
        }

        Tooltip.install(title, new Tooltip(media.getTitle()));
    }

    private void initializeStars() {
        ratingStars.setRating(media.getRating());
    }

    private void initializeFavorite() {
        switchFavorite(favoriteProperty.get());
        favoriteProperty.addListener((observable, oldValue, newValue) -> switchFavorite(newValue));
    }

    private void initializeView() {
        switchWatched(media.watchedProperty().get());
        listener = (observable, oldValue, newValue) -> switchWatched(newValue);

        media.watchedProperty().addListener(listener);
    }

    private void switchFavorite(boolean isFavorite) {
        if (isFavorite) {
            favorite.getStyleClass().add(LIKED_STYLE_CLASS);
        } else {
            favorite.getStyleClass().remove(LIKED_STYLE_CLASS);
        }
    }

    private void switchWatched(boolean isWatched) {
        if (isWatched) {
            posterItem.getStyleClass().add(WATCHED_STYLE_CLASS);
        } else {
            posterItem.getStyleClass().remove(WATCHED_STYLE_CLASS);
        }
    }

    @FXML
    private void onWatchedClicked(MouseEvent event) {
        event.consume();
        boolean newValue = !media.isWatched();

        synchronized (listeners) {
            listeners.forEach(e -> e.onWatchedChanged(media, newValue));
        }
    }

    @FXML
    private void onFavoriteClicked(MouseEvent event) {
        event.consume();
        boolean newValue = !favoriteProperty.get();

        favoriteProperty.set(newValue);
        synchronized (listeners) {
            listeners.forEach(e -> e.onFavoriteChanged(media, newValue));
        }
    }

    @FXML
    private void showDetails() {
        synchronized (listeners) {
            listeners.forEach(e -> e.onClicked(media));
        }
    }
}
