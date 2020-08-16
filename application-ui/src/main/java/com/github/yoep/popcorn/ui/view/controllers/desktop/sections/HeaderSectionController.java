package com.github.yoep.popcorn.ui.view.controllers.desktop.sections;

import com.github.spring.boot.javafx.font.controls.Icon;
import com.github.spring.boot.javafx.text.LocaleText;
import com.github.yoep.popcorn.ui.config.properties.PopcornProperties;
import com.github.yoep.popcorn.ui.config.properties.ProviderProperties;
import com.github.yoep.popcorn.ui.events.*;
import com.github.yoep.popcorn.ui.settings.SettingsService;
import com.github.yoep.popcorn.ui.settings.models.ApplicationSettings;
import com.github.yoep.popcorn.ui.settings.models.StartScreen;
import com.github.yoep.popcorn.ui.settings.models.TraktSettings;
import com.github.yoep.popcorn.ui.view.controllers.common.sections.AbstractFilterSectionController;
import com.github.yoep.popcorn.ui.view.controls.SearchField;
import com.github.yoep.popcorn.ui.view.controls.SearchListener;
import com.github.yoep.popcorn.ui.view.models.Category;
import com.github.yoep.popcorn.ui.view.models.Genre;
import com.github.yoep.popcorn.ui.view.models.SortBy;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class HeaderSectionController extends AbstractFilterSectionController implements Initializable {
    private static final String STYLE_ACTIVE = "active";

    private final ApplicationEventPublisher eventPublisher;
    private final PopcornProperties properties;
    private final LocaleText localeText;

    private Label lastKnownSelectedCategory;

    @FXML
    private Pane headerPane;
    @FXML
    private Label moviesCategory;
    @FXML
    private Label seriesCategory;
    @FXML
    private Label favoritesCategory;
    @FXML
    private ComboBox<Genre> genreCombo;
    @FXML
    private ComboBox<SortBy> sortByCombo;
    @FXML
    private SearchField search;
    @FXML
    private Icon watchlistIcon;
    @FXML
    private Icon torrentCollectionIcon;
    @FXML
    private Icon settingsIcon;

    //region Constructors

    public HeaderSectionController(ApplicationEventPublisher eventPublisher, PopcornProperties properties, LocaleText localeText,
                                   SettingsService settingsService) {
        super(settingsService);
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.localeText = localeText;
    }

    //endregion

    //region Methods

    @EventListener(CloseSettingsEvent.class)
    public void onCloseSettings() {
        onSettingsClosed();
    }

    //endregion

    //region Initializable

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeComboListeners();
        initializeSearchListener();
        initializeIcons();
        initializeSceneListener(headerPane);
    }

    //endregion

    //region Functions

    @Override
    protected void initializeStartScreen(StartScreen startScreen) {
        log.trace("Initializing start screen");

        switch (startScreen) {
            case SERIES:
                switchCategory(seriesCategory);
                break;
            case FAVORITES:
                switchCategory(favoritesCategory);
                break;
            default:
                switchCategory(moviesCategory);
                break;
        }
    }

    private void initializeComboListeners() {
        genreCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> switchGenre(newValue));
        sortByCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> switchSortBy(newValue));
    }

    private void initializeSearchListener() {
        search.addListener(new SearchListener() {
            @Override
            public void onSearchValueChanged(String newValue) {
                eventPublisher.publishEvent(new SearchEvent(this, newValue));
            }

            @Override
            public void onSearchValueCleared() {
                eventPublisher.publishEvent(new SearchEvent(this, null));
            }
        });
    }

    private void initializeIcons() {
        var traktSettings = getSettings().getTraktSettings();

        watchlistIcon.setVisible(traktSettings.getAccessToken().isPresent());
        traktSettings.addListener(evt -> {
            if (evt.getPropertyName().equals(TraktSettings.ACCESS_TOKEN_PROPERTY)) {
                watchlistIcon.setVisible(traktSettings.getAccessToken().isPresent());
            }
        });
    }

    private void updateGenres(Category category) {
        ProviderProperties providerProperties = properties.getProvider(category.getProviderName());
        List<Genre> genres = providerProperties.getGenres().stream()
                .map(e -> new Genre(e, localeText.get("genre_" + e)))
                .sorted()
                .collect(Collectors.toList());

        genreCombo.getItems().clear();
        genreCombo.getItems().addAll(genres);
        genreCombo.getSelectionModel().select(0);
    }

    private void updateSortBy(Category category) {
        ProviderProperties providerProperties = properties.getProvider(category.getProviderName());
        List<SortBy> sortBy = providerProperties.getSortBy().stream()
                .map(e -> new SortBy(e, localeText.get("sort-by_" + e)))
                .collect(Collectors.toList());

        sortByCombo.getItems().clear();
        sortByCombo.getItems().addAll(sortBy);
        sortByCombo.getSelectionModel().select(0);
    }

    private void switchCategory(Label item) {
        final AtomicReference<Category> category = new AtomicReference<>();
        this.lastKnownSelectedCategory = item;

        removeAllActiveStates();
        activateItem(item);

        if (item == moviesCategory) {
            category.set(Category.MOVIES);
        }
        if (item == seriesCategory) {
            category.set(Category.SERIES);
        }
        if (item == favoritesCategory) {
            category.set(Category.FAVORITES);
        }

        // invoke the chang activity first before changing the genre & sort by
        log.trace("Category is being changed to \"{}\"", category.get());
        eventPublisher.publishEvent(new CategoryChangedEvent(this, category.get()));

        // clear the current search
        search.clear();

        // set the category specific genres and sort by filters
        updateGenres(category.get());
        updateSortBy(category.get());
    }

    private void switchGenre(Genre genre) {
        if (genre == null)
            return;

        log.trace("Genre is being changed to \"{}\"", genre);
        eventPublisher.publishEvent(new GenreChangeEvent(this, genre));
    }

    private void switchSortBy(SortBy sortBy) {
        if (sortBy == null)
            return;

        log.trace("SortBy is being changed to \"{}\"", sortBy);
        eventPublisher.publishEvent(new SortByChangeEvent(this, sortBy));
    }

    private void switchIcon(Icon icon) {
        removeAllActiveStates();

        activateItem(icon);
    }

    private void activateItem(Label item) {
        if (item == null)
            return;

        item.getStyleClass().add(STYLE_ACTIVE);
    }

    private void removeAllActiveStates() {
        // categories
        moviesCategory.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));
        seriesCategory.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));
        favoritesCategory.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));

        // icons
        watchlistIcon.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));
        torrentCollectionIcon.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));
        settingsIcon.getStyleClass().removeIf(e -> e.equals(STYLE_ACTIVE));
    }

    private void onSettingsClosed() {
        Platform.runLater(() -> {
            removeAllActiveStates();
            activateItem(lastKnownSelectedCategory);
        });
    }

    private ApplicationSettings getSettings() {
        return settingsService.getSettings();
    }

    @FXML
    private void onCategoryClicked(MouseEvent event) {
        switchCategory((Label) event.getSource());
    }

    @FXML
    private void onGenreClicked() {
        genreCombo.show();
    }

    @FXML
    private void onSortClicked() {
        sortByCombo.show();
    }

    @FXML
    private void onWatchlistClicked() {
        switchIcon(watchlistIcon);
        eventPublisher.publishEvent(new ShowWatchlistEvent(this));
    }

    @FXML
    private void onTorrentCollectionClicked() {
        switchIcon(torrentCollectionIcon);
        eventPublisher.publishEvent(new ShowTorrentCollectionEvent(this));
    }

    @FXML
    private void onSettingsClicked() {
        switchIcon(settingsIcon);
        eventPublisher.publishEvent(new ShowSettingsEvent(this));
    }

    //endregion
}
