package com.voxstream.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Preloader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Preloader for VoxStream Application.
 * Shows a splash screen during application startup for better user experience.
 * macOS Catalina+ compatible with proper styling and animations.
 */
public class VoxStreamPreloader extends Preloader {

    private static final Logger logger = LoggerFactory.getLogger(VoxStreamPreloader.class);
    private Stage preloaderStage;
    private ProgressBar progressBar;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.preloaderStage = primaryStage;

        logger.info("Starting VoxStream Preloader...");

        // Create splash screen layout
        VBox splashLayout = createSplashLayout();

        Scene splashScene = new Scene(splashLayout, 400, 300);
        splashScene.getStylesheets().add(getClass().getResource("/css/preloader.css").toExternalForm());

        primaryStage.setScene(splashScene);
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.centerOnScreen();
        primaryStage.setTitle("VoxStream Loading...");

        primaryStage.show();
        logger.info("Preloader displayed successfully");
    }

    private VBox createSplashLayout() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("splash-container");

        // App logo/icon (placeholder for now)
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/voxstream-logo.png"));
            logoView.setImage(logo);
            logoView.setFitWidth(100);
            logoView.setFitHeight(100);
            logoView.setPreserveRatio(true);
        } catch (Exception e) {
            logger.warn("Logo image not found, using placeholder");
            // Create a simple text logo as fallback
            Label logoLabel = new Label("VoxStream");
            logoLabel.getStyleClass().add("logo-text");
            layout.getChildren().add(logoLabel);
        }

        if (logoView.getImage() != null) {
            layout.getChildren().add(logoView);
        }

        // App title
        Label titleLabel = new Label("VoxStream");
        titleLabel.getStyleClass().add("title");

        // Version label
        Label versionLabel = new Label("Version 1.0.0-SNAPSHOT");
        versionLabel.getStyleClass().add("version");

        // Progress bar
        progressBar = new ProgressBar(0.0);
        progressBar.setPrefWidth(300);
        progressBar.getStyleClass().add("progress");

        // Status label
        statusLabel = new Label("Initializing...");
        statusLabel.getStyleClass().add("status");

        layout.getChildren().addAll(titleLabel, versionLabel, progressBar, statusLabel);

        return layout;
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification info) {
        if (info.getType() == StateChangeNotification.Type.BEFORE_LOAD) {
            updateProgress(0.1, "Loading application...");
        } else if (info.getType() == StateChangeNotification.Type.BEFORE_INIT) {
            updateProgress(0.3, "Initializing Spring context...");
        } else if (info.getType() == StateChangeNotification.Type.BEFORE_START) {
            updateProgress(0.7, "Starting user interface...");
        }
    }

    @Override
    public void handleProgressNotification(ProgressNotification info) {
        updateProgress(info.getProgress(), "Loading components...");
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification info) {
        if (info instanceof ProgressNotification) {
            ProgressNotification pn = (ProgressNotification) info;
            updateProgress(pn.getProgress(), "Starting application...");
        }
    }

    private void updateProgress(double progress, String message) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        if (statusLabel != null) {
            statusLabel.setText(message);
        }

        // Close preloader when complete
        if (progress >= 1.0) {
            if (preloaderStage != null) {
                preloaderStage.hide();
            }
        }
    }
}
