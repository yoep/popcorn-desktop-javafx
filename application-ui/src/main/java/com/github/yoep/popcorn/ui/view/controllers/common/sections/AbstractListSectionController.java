package com.github.yoep.popcorn.ui.view.controllers.common.sections;

import com.github.spring.boot.javafx.text.LocaleText;
import com.github.spring.boot.javafx.view.ViewLoader;
import com.github.yoep.popcorn.ui.events.CategoryChangedEvent;
import com.github.yoep.popcorn.ui.events.GenreChangeEvent;
import com.github.yoep.popcorn.ui.events.SearchEvent;
import com.github.yoep.popcorn.ui.events.SortByChangeEvent;
import com.github.yoep.popcorn.ui.media.providers.ProviderService;
import com.github.yoep.popcorn.ui.media.providers.models.Media;
import com.github.yoep.popcorn.ui.messages.ListMessage;
import com.github.yoep.popcorn.ui.view.controls.InfiniteScrollItemFactory;
import com.github.yoep.popcorn.ui.view.controls.InfiniteScrollPane;
import com.github.yoep.popcorn.ui.view.models.Category;
import com.github.yoep.popcorn.ui.view.models.Genre;
import com.github.yoep.popcorn.ui.view.models.SortBy;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractListSectionController implements Initializable {
    protected final List<ProviderService<? extends Media>> providerServices;
    protected final ViewLoader viewLoader;
    protected final LocaleText localeText;

    protected Category category;
    protected Genre genre;
    protected SortBy sortBy;
    protected String search;

    protected CompletableFuture<? extends Page<? extends Media>> currentLoadRequest;

    @FXML
    protected InfiniteScrollPane<Media> scrollPane;
    @FXML
    protected Pane failedPane;
    @FXML
    protected Label failedText;
    @FXML
    protected Pane overlay;

    private ProgressIndicator loadingIndicator;

    //region Constructors

    protected AbstractListSectionController(List<ProviderService<? extends Media>> providerServices,
                                            ViewLoader viewLoader,
                                            LocaleText localeText) {
        Assert.notNull(providerServices, "providerServices cannot be null");
        Assert.notNull(viewLoader, "viewLoader cannot be null");
        Assert.notNull(localeText, "localeText cannot be null");
        this.providerServices = providerServices;
        this.viewLoader = viewLoader;
        this.localeText = localeText;
    }

    //endregion

    //region Methods

    @EventListener
    public void onCategoryChanged(CategoryChangedEvent event) {
        this.category = event.getCategory();
        // reset the genre & sort by as they might be different in the new category
        // these will be automatically filled in again as the category change also triggers a GenreChangeActivity & SortByChangeActivity
        this.genre = null;
        this.sortBy = null;

        reset();
    }

    @EventListener
    public void onGenreChange(GenreChangeEvent event) {
        this.genre = event.getGenre();

        reset();
        invokeNewPageLoad();
    }

    @EventListener
    public void onSortByChange(SortByChangeEvent event) {
        this.sortBy = event.getSortBy();

        reset();
        invokeNewPageLoad();
    }

    @EventListener
    public void onSearch(SearchEvent event) {
        var newValue = event.getValue().orElse(null);

        if (Objects.equals(search, newValue))
            return;

        this.search = newValue;
        reset();
        invokeNewPageLoad();
    }

    //endregion

    //region Initializable

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeScrollPane();
        initializeFailedPane();
        initializeOverlay();
    }

    protected void initializeScrollPane() {
        scrollPane.setLoaderFactory(() -> viewLoader.load("common/components/loading-card.component.fxml"));
        scrollPane.setItemFactory(new InfiniteScrollItemFactory<>() {
            @Override
            public CompletableFuture<List<Media>> loadPage(int page) {
                return loadItems(page)
                        .exceptionally(throwable -> onMediaRequestFailed(throwable))
                        .thenApply(Arrays::asList);
            }

            @Override
            public Node createCell(Media item) {
                return creatItemNode(item);
            }
        });
    }

    protected void initializeFailedPane() {
        failedPane.setVisible(false);
    }

    protected void initializeOverlay() {
        overlay.setVisible(false);
    }

    //endregion

    //region Functions

    /**
     * Create a new FX node for the given item.
     *
     * @param item The item to create a node for.
     * @return Returns the node for the given item.
     */
    protected abstract Node creatItemNode(Media item);

    /**
     * Load the items for the given page.
     *
     * @param page The page items to load.
     * @return Returns the completable future of the loading action.
     */
    protected CompletableFuture<Media[]> loadItems(final int page) {
        // hide the failed pane in case it might be visible from the last failure
        Platform.runLater(() -> failedPane.setVisible(false));

        // cancel the current load request if one is present
        // and has not yet been completed
        if (currentLoadRequest != null && !currentLoadRequest.isDone())
            currentLoadRequest.cancel(true);

        log.trace("Retrieving media page {} for {} category", page, category);
        var provider = providerServices.stream()
                .filter(e -> e.supports(category))
                .findFirst();

        if (provider.isPresent()) {
            return retrieveMediaPage(provider.get(), page);
        } else {
            log.error("No provider service found for \"{}\" category", category);
            return CompletableFuture.completedFuture(new Media[0]);
        }
    }

    protected CompletableFuture<Media[]> retrieveMediaPage(ProviderService<? extends Media> provider, int page) {
        if (StringUtils.isEmpty(search)) {
            currentLoadRequest = provider.getPage(genre, sortBy, page);
        } else {
            currentLoadRequest = provider.getPage(genre, sortBy, page, search);
        }

        return currentLoadRequest
                .thenApply(this::onMediaRequestCompleted)
                .exceptionally(this::onMediaRequestFailed);
    }

    protected void invokeNewPageLoad() {
        if (scrollPane != null && category != null && genre != null && sortBy != null)
            scrollPane.loadNewPage();
    }

    protected void reset() {
        if (currentLoadRequest != null)
            currentLoadRequest.cancel(true);

        scrollPane.reset();
    }

    protected void showOverlay() {
        // check if the overlay is already shown
        // if so, ignore the action
        if (overlay.isVisible())
            return;

        if (loadingIndicator == null)
            loadingIndicator = new ProgressIndicator();

        overlay.getChildren().clear();
        overlay.getChildren().add(loadingIndicator);
        overlay.setVisible(true);
    }

    protected void onItemClicked(Media media) {
        showOverlay();
        providerServices.stream()
                .filter(e -> e.supports(category))
                .findFirst()
                .ifPresent(provider -> showMediaDetails(media, provider));
    }

    private void hideOverlay() {
        overlay.setVisible(false);

        overlay.getChildren().clear();
        loadingIndicator = null;
    }

    private Media[] onMediaRequestFailed(Throwable throwable) {
        releaseCurrentLoadRequest();

        // check if the media request was cancelled
        // if so, ignore this failure
        if (throwable instanceof CancellationException || throwable instanceof CompletionException) {
            log.trace("Media request has been cancelled by the user");
            return new Media[0];
        }

        var rootCause = throwable.getCause();
        var message = new AtomicReference<>(localeText.get(ListMessage.GENERIC));
        log.error("Failed to retrieve media list, " + rootCause.getMessage(), throwable);

        if (rootCause instanceof HttpStatusCodeException) {
            HttpStatusCodeException ex = (HttpStatusCodeException) rootCause;
            message.set(localeText.get(ListMessage.API_UNAVAILABLE, ex.getStatusCode()));
        }

        Platform.runLater(() -> {
            failedText.setText(message.get());
            failedPane.setVisible(true);
        });

        return new Media[0];
    }

    private Media[] onMediaRequestCompleted(final Page<? extends Media> page) {
        releaseCurrentLoadRequest();

        // filter out any duplicate items
        return page.get()
                .filter(e -> !scrollPane.contains(e))
                .toArray(Media[]::new);
    }

    private void showMediaDetails(Media media, ProviderService<? extends Media> provider) {
        provider.showDetails(media)
                .whenComplete((loaded, throwable) -> Platform.runLater(this::hideOverlay));
    }

    private void releaseCurrentLoadRequest() {
        if (!this.currentLoadRequest.isDone()) {
            log.warn("Unable to release the current load request, load request is still in progress");
            return;
        }

        this.currentLoadRequest = null;
    }

    //endregion
}
