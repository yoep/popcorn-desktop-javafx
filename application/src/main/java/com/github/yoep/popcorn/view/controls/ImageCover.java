package com.github.yoep.popcorn.view.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;

/**
 * Displays an image which fully covers the pane size.
 */
@Slf4j
public class ImageCover extends AnchorPane {
    public static final String COVER_TYPE_PROPERTY = "coverType";
    private static final String STYLE_CLASS = "image-cover";

    private final ChangeListener<Number> parentWidthListener = (observable, oldValue, newValue) -> onParentWidthChanged(newValue);
    private final ChangeListener<Number> parentHeightListener = (observable, oldValue, newValue) -> onParentHeightChanged(newValue);
    private final ObjectProperty<CoverType> coverType = new SimpleObjectProperty<>(this, COVER_TYPE_PROPERTY, CoverType.ALL);

    private final ImageView imageView = new ImageView();
    private final Rectangle clipView = new Rectangle();

    //region Constructors

    public ImageCover() {
        init();
    }

    //endregion

    //region Properties

    /**
     * Get the cover type of this image cover.
     *
     * @return Returns the cover type.
     */
    public CoverType getCoverType() {
        return coverType.get();
    }

    /**
     * Get the cover type property.
     *
     * @return Returns the cover type property.
     */
    public ObjectProperty<CoverType> coverTypeProperty() {
        return coverType;
    }

    /**
     * Set the cover type of this image cover.
     *
     * @param coverType The new cover type.
     */
    public void setCoverType(CoverType coverType) {
        this.coverType.set(coverType);
    }


    //endregion

    //region Methods

    /**
     * Set the image which needs to cover the pane.
     *
     * @param image The image to cover the pane with.
     */
    public void setImage(final Image image) {
        if (image != null) {
            if (!image.isError()) {
                imageView.setImage(image);
            } else {
                handleImageError(image);
            }
        } else {
            reset();
        }
    }

    /**
     * Reset the image cover to a blank canvas.
     */
    public void reset() {
        // remove the image
        imageView.setImage(null);
    }

    //endregion

    //region Functions

    private void init() {
        initializeAnchorPane();
        initializeImageView();
        initializeListeners();
        initializeCover();
    }

    private void initializeAnchorPane() {
        parentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                var parent = (Pane) oldValue;

                parent.widthProperty().removeListener(parentWidthListener);
                parent.heightProperty().removeListener(parentHeightListener);
            }

            if (newValue != null) {
                var parent = (Pane) newValue;

                parent.widthProperty().addListener(parentWidthListener);
                parent.heightProperty().addListener(parentHeightListener);
            }
        });
    }

    private void initializeImageView() {
        imageView.setManaged(false);
        imageView.setPreserveRatio(true);
        imageView.setClip(clipView);
        imageView.imageProperty().addListener((observable, oldValue, newValue) -> resizeImage());
    }

    private void initializeListeners() {
        this.widthProperty().addListener((observable, oldValue, newValue) -> resizeImage());
        this.heightProperty().addListener((observable, oldValue, newValue) -> resizeImage());
        this.coverTypeProperty().addListener((observable, oldValue, newValue) -> resizeImage());
    }

    private void initializeCover() {
        getStyleClass().add(STYLE_CLASS);

        getChildren().add(imageView);
    }

    private void onParentWidthChanged(Number newValue) {
        var width = newValue.doubleValue();
        var parent = getParent();

        if (parent instanceof Pane) {
            var pane = (Pane) getParent();
            var padding = pane.getPadding();

            width -= padding.getLeft();
            width -= padding.getRight();
        }

        this.setMaxWidth(width);
        this.setMinWidth(width);
        this.setPrefWidth(width);

        clipView.setWidth(width);
        requestLayout();
    }

    private void onParentHeightChanged(Number newValue) {
        var height = newValue.doubleValue();
        var parent = getParent();

        if (parent instanceof Pane) {
            var pane = (Pane) getParent();
            var padding = pane.getPadding();

            height -= padding.getTop();
            height -= padding.getBottom();
        }

        this.setMaxHeight(height);
        this.setMinHeight(height);
        this.setPrefHeight(height);

        clipView.setHeight(height);
        requestLayout();
    }

    private void resizeImage() {
        var image = imageView.getImage();
        var paneWidth = this.getWidth();
        var paneHeight = this.getHeight();

        // check if we need to recalculate the image size
        if (image == null || paneWidth == 0 || paneHeight == 0)
            return;

        var scale = getScale();
        var imageWidth = image.getWidth();
        var imageHeight = image.getHeight();
        var fitWidth = imageWidth * scale;
        var fitHeight = imageHeight * scale;

        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
        imageView.setX((paneWidth - fitWidth) / 2);
        imageView.setY((paneHeight - fitHeight) / 2);
    }

    private double getScale() {
        var image = imageView.getImage();
        var imageWidth = image.getWidth();
        var imageHeight = image.getHeight();
        var paneWidth = this.getWidth();
        var paneHeight = this.getHeight();

        switch (getCoverType()) {
            case HEIGHT:
                // make the scale fit to the height
                return paneHeight / imageHeight;
            case WIDTH:
                // make the scale fit to the width
                return paneWidth / imageWidth;
            case ALL:
            default:
                return Math.max(paneWidth / imageWidth, paneHeight / imageHeight);
        }
    }

    private static void handleImageError(Image image) {
        var exception = image.getException();
        var message = MessageFormat.format("Failed to load image cover for url \"{0}\", {1}", image.getUrl(), exception.getMessage());

        log.warn(message, exception);
    }

    //endregion

    public enum CoverType {
        /**
         * Cover both width & height.
         */
        ALL,
        /**
         * Cover only the height.
         */
        HEIGHT,
        /**
         * Cover only the width.
         */
        WIDTH
    }
}
