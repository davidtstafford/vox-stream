package com.voxstream.frontend.controller;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.config.keys.CoreConfigKeys;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

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

    @FXML
    private Spinner<Integer> eventPurgeSpinner;
    @FXML
    private Spinner<Integer> maxEventsSpinner;
    @FXML
    private Spinner<Integer> ttsPurgeSpinner;
    @FXML
    private Spinner<Integer> webPortSpinner;
    @FXML
    private CheckBox enableCorsCheckbox;

    @Autowired
    private VoxStreamConfiguration configuration;
    @Autowired
    private ConfigurationService configurationService;

    private String currentExportHashCached = "";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing Main Controller...");

        setupMenuBar();
        setupTabPane();
        setupStatusBar();
        initSettingsControls();
        refreshExportHashCache();

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

    private void initSettingsControls() {
        if (eventPurgeSpinner != null) {
            eventPurgeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440,
                    configurationService.get(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN)));
        }
        if (maxEventsSpinner != null) {
            maxEventsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 1_000_000,
                    configurationService.get(CoreConfigKeys.EVENT_BUS_MAX_EVENTS)));
        }
        if (ttsPurgeSpinner != null) {
            ttsPurgeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440,
                    configurationService.get(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN)));
        }
        if (webPortSpinner != null) {
            webPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535,
                    configurationService.get(CoreConfigKeys.WEB_OUTPUT_PORT)));
        }
        if (enableCorsCheckbox != null) {
            enableCorsCheckbox.setSelected(configurationService.get(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS));
        }
    }

    @FXML
    private void handleSaveSettings() {
        try {
            configurationService.set(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, eventPurgeSpinner.getValue());
            configurationService.set(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, maxEventsSpinner.getValue());
            configurationService.set(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, ttsPurgeSpinner.getValue());
            configurationService.set(CoreConfigKeys.WEB_OUTPUT_PORT, webPortSpinner.getValue());
            configurationService.set(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS, enableCorsCheckbox.isSelected());
            refreshExportHashCache();
            updateStatus("Settings saved", false);
        } catch (Exception ex) {
            logger.error("Failed to save settings", ex);
            updateStatus("Failed to save settings: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleResetDefaults() {
        eventPurgeSpinner.getValueFactory().setValue(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN.getDefaultValue());
        maxEventsSpinner.getValueFactory().setValue(CoreConfigKeys.EVENT_BUS_MAX_EVENTS.getDefaultValue());
        ttsPurgeSpinner.getValueFactory().setValue(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN.getDefaultValue());
        webPortSpinner.getValueFactory().setValue(CoreConfigKeys.WEB_OUTPUT_PORT.getDefaultValue());
        enableCorsCheckbox.setSelected(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS.getDefaultValue());
        updateStatus("Defaults restored (unsaved)", false);
    }

    @FXML
    private void handleExportSettings() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Settings");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            fc.setInitialFileName("voxstream-settings.json");
            File file = fc.showSaveDialog(rootPane.getScene().getWindow());
            if (file == null) {
                updateStatus("Export cancelled", false);
                return;
            }
            Map<String, Object> data = new HashMap<>();
            for (var key : CoreConfigKeys.ALL) {
                @SuppressWarnings("unchecked")
                var typed = (com.voxstream.core.config.keys.ConfigKey<Object>) key;
                Object val = configurationService.get(typed);
                data.put(key.getName(), val);
            }
            String json = toJson(data);
            try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
                fw.write(json);
            }
            // compute and persist export hash
            String hash = sha256(json);
            configurationService.set(CoreConfigKeys.LAST_EXPORT_HASH, hash);
            currentExportHashCached = hash;
            updateStatus("Settings exported", false);
        } catch (Exception ex) {
            logger.error("Export failed", ex);
            updateStatus("Export failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleImportSettings() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Import Settings");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File file = fc.showOpenDialog(rootPane.getScene().getWindow());
            if (file == null) {
                updateStatus("Import cancelled", false);
                return;
            }
            Map<String, Object> incoming;
            try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
                incoming = parseJson(fr); // returns map
            }
            int applied = 0;
            for (var entry : incoming.entrySet()) {
                var key = CoreConfigKeys.byName(entry.getKey());
                if (key == null)
                    continue; // unknown key skip
                Object rawVal = entry.getValue();
                try {
                    Object casted = castValue(rawVal, key.getType());
                    // simple validation range examples
                    if (key == CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN
                            || key == CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN) {
                        int v = (Integer) casted;
                        if (v < 1 || v > 1440)
                            throw new IllegalArgumentException("purge interval out of range");
                    }
                    if (key == CoreConfigKeys.WEB_OUTPUT_PORT) {
                        int p = (Integer) casted;
                        if (p < 1024 || p > 65535)
                            throw new IllegalArgumentException("port out of range");
                    }
                    @SuppressWarnings("unchecked")
                    var typed = (com.voxstream.core.config.keys.ConfigKey<Object>) key;
                    configurationService.set(typed, casted);
                    applied++;
                } catch (Exception ex) {
                    logger.warn("Skipping invalid imported value for {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            initSettingsControls();
            refreshExportHashCache();
            updateStatus("Imported " + applied + " settings", false);
        } catch (Exception ex) {
            logger.error("Import failed", ex);
            updateStatus("Import failed: " + ex.getMessage(), false);
        }
    }

    private void refreshExportHashCache() {
        try {
            Map<String, Object> data = new HashMap<>();
            for (var key : CoreConfigKeys.ALL) {
                @SuppressWarnings("unchecked")
                var typed = (com.voxstream.core.config.keys.ConfigKey<Object>) key;
                Object val = configurationService.get(typed);
                data.put(key.getName(), val);
            }
            String json = toJson(data);
            currentExportHashCached = configurationService.get(CoreConfigKeys.LAST_EXPORT_HASH);
            String currentHash = sha256(json);
            if (!currentExportHashCached.equals(currentHash)) {
                updateStatus("Settings changed since last export", false);
            }
        } catch (Exception e) {
            logger.debug("Unable to refresh export hash cache: {}", e.getMessage());
        }
    }

    private String sha256(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String toJson(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + formatValue(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String formatValue(Object v) {
        if (v instanceof Number || v instanceof Boolean)
            return v.toString();
        return "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Map<String, Object> parseJson(FileReader reader) throws Exception {
        // Minimal JSON object parser (flat key->primitive) to avoid extra deps in
        // frontend module
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[2048];
        int r;
        while ((r = reader.read(buf)) != -1)
            sb.append(buf, 0, r);
        String json = sb.toString().trim();
        Map<String, Object> result = new HashMap<>();
        if (json.isEmpty() || json.equals("{}"))
            return result;
        if (json.charAt(0) != '{' || json.charAt(json.length() - 1) != '}')
            throw new IllegalArgumentException("Invalid JSON");
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty())
            return result;
        // split by commas not inside quotes (simple approach assumes no nested
        // structures)
        int idx = 0;
        boolean inStr = false;
        StringBuilder token = new StringBuilder();
        java.util.List<String> parts = new java.util.ArrayList<>();
        while (idx < body.length()) {
            char c = body.charAt(idx);
            if (c == '"' && (idx == 0 || body.charAt(idx - 1) != '\\'))
                inStr = !inStr;
            if (c == ',' && !inStr) {
                parts.add(token.toString());
                token.setLength(0);
            } else
                token.append(c);
            idx++;
        }
        parts.add(token.toString());
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2)
                continue;
            String key = stripQuotes(kv[0].trim());
            String valRaw = kv[1].trim();
            Object val;
            if (valRaw.equalsIgnoreCase("true") || valRaw.equalsIgnoreCase("false"))
                val = Boolean.valueOf(valRaw);
            else if (valRaw.matches("-?\\d+"))
                val = Integer.valueOf(valRaw);
            else
                val = stripQuotes(valRaw);
            result.put(key, val);
        }
        return result;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s;
    }

    private Object castValue(Object raw, Class<?> type) {
        if (type == Integer.class && raw instanceof Number)
            return ((Number) raw).intValue();
        if (type == Boolean.class && raw instanceof Boolean)
            return raw;
        if (type == String.class)
            return String.valueOf(raw);
        throw new IllegalArgumentException("Unsupported type for import: " + type.getSimpleName());
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
