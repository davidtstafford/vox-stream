package com.voxstream.frontend.controller;

import java.net.URL;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.voxstream.core.config.VoxStreamConfiguration;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/**
 * Main controller for the VoxStream JavaFX application.
 * Handles the primary interface and navigation between different screens.
 */
@Component
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private BorderPane rootPane;
    @FXML
    private MenuBar menuBar;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab connectionsTab;
    @FXML
    private Tab eventsTab;
    @FXML
    private Tab ttsTab;
    @FXML
    private Tab viewersTab;
    @FXML
    private Tab settingsTab;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator statusIndicator;

    @Autowired
    private VoxStreamConfiguration configuration;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing Main Controller...");

        setupMenuBar();
        setupTabPane();
        setupStatusBar();

        // Update status
        updateStatus("Application Ready", false);

        logger.info("Main Controller initialized successfully");
    }

    private void setupMenuBar() {
        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitItem);

        // View Menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem darkModeItem = new CheckMenuItem("Dark Mode");
        darkModeItem.setOnAction(e -> toggleDarkMode(darkModeItem.isSelected()));
        viewMenu.getItems().add(darkModeItem);

        // Help Menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
    }

    private void setupTabPane() {
        // Disable tabs initially until connections are established
        eventsTab.setDisable(true);
        ttsTab.setDisable(true);
        viewersTab.setDisable(true);

        // Add tab selection listener
        mainTabPane.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    if (newTab != null) {
                        logger.debug("Switched to tab: {}", newTab.getText());
                        updateStatus("Viewing " + newTab.getText(), false);
                    }
                });
    }

    private void setupStatusBar() {
        statusIndicator.setVisible(false);
        statusIndicator.setMaxSize(16, 16);
    }

    private void updateStatus(String message, boolean showProgress) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusIndicator.setVisible(showProgress);
        });
    }

    private void toggleDarkMode(boolean darkMode) {
        // TODO: Implement dark mode theme switching
        logger.info("Dark mode toggled: {}", darkMode);
        updateStatus(darkMode ? "Dark mode enabled" : "Light mode enabled", false);
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About VoxStream");
        alert.setHeaderText("VoxStream v" + configuration.getVersion());
        alert.setContentText(
                "VoxStream - Streaming Platform Integration Application\\n\\n" +
                        "A Java-based application for integrating with streaming platforms,\\n" +
                        "providing Text-to-Speech capabilities, event management,\\n" +
                        "and viewer interaction features.\\n\\n" +
                        "Compatible with macOS Catalina (10.15) and up, Windows 10 and up.\\n\\n" +
                        "© 2025 VoxStream Project");
        alert.showAndWait();
    }

    @FXML
    private void handleConnectionsTab() {
        logger.debug("Connections tab selected");
        updateStatus("Configure streaming platform connections", false);
    }

    @FXML
    private void handleEventsTab() {
        logger.debug("Events tab selected");
        updateStatus("View live events and history", false);
    }

    @FXML
    private void handleTTSTab() {
        logger.debug("TTS tab selected");
        updateStatus("Configure Text-to-Speech settings", false);
    }

    @FXML
    private void handleViewersTab() {
        logger.debug("Viewers tab selected");
        updateStatus("Manage viewers and permissions", false);
    }

    @FXML
    private void handleSettingsTab() {
        logger.debug("Settings tab selected");
        updateStatus("Application settings and preferences", false);
    }

    /**
     * Enable tabs after successful platform connection
     */
    public void enableTabs() {
        Platform.runLater(() -> {
            eventsTab.setDisable(false);
            ttsTab.setDisable(false);
            viewersTab.setDisable(false);
            updateStatus("Connected - All features available", false);
        });
    }

    /**
     * Disable tabs when platform connection is lost
     */
    public void disableTabs() {
        Platform.runLater(() -> {
            eventsTab.setDisable(true);
            ttsTab.setDisable(true);
            viewersTab.setDisable(true);
            updateStatus("Disconnected - Limited functionality", false);
        });
    }
}
