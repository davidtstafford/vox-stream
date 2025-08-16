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

import com.voxstream.core.bus.EventBus;
import com.voxstream.core.bus.EventSubscription;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.config.profile.ProfileService;
import com.voxstream.core.event.Event;
import com.voxstream.core.event.EventType;
import com.voxstream.core.platform.PlatformConnectionManager;
import com.voxstream.core.platform.PlatformConnectionRegistry;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;
import com.voxstream.platform.api.PlatformStatus;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
    // Profile UI
    @FXML
    private ListView<String> profileListView;
    @FXML
    private TextField newProfileNameField;

    // Connection management UI
    @FXML
    private TableView<ConnectionRow> connectionsTable;
    @FXML
    private TableColumn<ConnectionRow, Boolean> enabledCol;
    @FXML
    private TableColumn<ConnectionRow, String> platformCol;
    @FXML
    private TableColumn<ConnectionRow, String> userCol;
    @FXML
    private TableColumn<ConnectionRow, String> statusCol;
    @FXML
    private TableColumn<ConnectionRow, String> sinceCol;
    @FXML
    private TableColumn<ConnectionRow, String> retriesCol;
    @FXML
    private TableColumn<ConnectionRow, String> backoffCol;
    @FXML
    private TableColumn<ConnectionRow, Void> actionsCol;
    @FXML
    private TextField twitchClientIdField;
    @FXML
    private TextField twitchClientSecretField;
    @FXML
    private TextField twitchScopesField;
    @FXML
    private TextField twitchRedirectPortField;
    @FXML
    private Label twitchConfigStatusLabel;

    // System log UI
    @FXML
    private TableView<SystemLogRow> systemLogTable;
    @FXML
    private TableColumn<SystemLogRow, String> logTimeCol;
    @FXML
    private TableColumn<SystemLogRow, String> logPlatformCol;
    @FXML
    private TableColumn<SystemLogRow, String> logStateCol;
    @FXML
    private TableColumn<SystemLogRow, String> logDetailCol;

    @FXML
    private VBox twitchConfigBox; // new dynamic config container
    @FXML
    private Label selectedPlatformHeader;
    @FXML
    private Label noConfigPlaceholder;
    @FXML
    private TitledPane platformConfigPane;
    @FXML
    private ComboBox<String> logFilterCombo;
    @FXML
    private CheckBox globalPlatformsEnabledCheckbox;

    @Autowired
    private VoxStreamConfiguration configuration;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private PlatformConnectionManager platformConnectionManager;
    @Autowired
    private EventBus eventBus;
    @Autowired
    private TwitchOAuthService twitchOAuthService;
    @Autowired
    private PlatformConnectionRegistry platformConnectionRegistry;

    private String currentExportHashCached = "";
    private final ObservableList<ConnectionRow> connectionRows = FXCollections.observableArrayList();
    private final ObservableList<SystemLogRow> systemLogRows = FXCollections.observableArrayList();
    @SuppressWarnings("unused")
    private com.voxstream.core.bus.SubscriptionHandle statusSubHandle;
    @SuppressWarnings("unused")
    private com.voxstream.core.bus.SubscriptionHandle systemLogSubHandle;

    @FXML
    private Button connectSelectedButton;
    @FXML
    private Button disconnectSelectedButton;
    @FXML
    private Button reconnectSelectedButton;
    @FXML
    private Button refreshConnectionsButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing Main Controller...");

        setupMenuBar();
        setupTabPane();
        setupStatusBar();
        initSettingsControls();
        initConnectionTable();
        initSystemLogTable();
        initGlobalPlatformsToggle();
        refreshExportHashCache();
        refreshProfiles();
        highlightDefaultProfile();
        loadTwitchConfigFields();
        startPlatformStatusSubscription();
        startSystemLogSubscription();

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
                "VoxStream - Streaming Platform Integration Application\n\n" +
                        "A Java-based application for integrating with streaming platforms,\n" +
                        "providing Text-to-Speech capabilities, event management,\n" +
                        "and viewer interaction features.\n\n" +
                        "Compatible with macOS Catalina (10.15) and up, Windows 10 and up.\n\n" +
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

    private void initConnectionTable() {
        if (connectionsTable == null)
            return;
        connectionsTable.setEditable(true); // allow checkbox edits
        if (enabledCol != null) {
            enabledCol.setCellValueFactory(data -> data.getValue().enabledProperty());
            enabledCol.setCellFactory(col -> new CheckBoxTableCell<>());
            enabledCol.setEditable(true);
        }
        platformCol.setCellValueFactory(new PropertyValueFactory<>("platform"));
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        // Add icon/emojis to status column for clearer visual indicators
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(statusEmoji(item) + " " + item);
                }
            }
        });
        sinceCol.setCellValueFactory(new PropertyValueFactory<>("connectedSince"));
        retriesCol.setCellValueFactory(new PropertyValueFactory<>("retries"));
        if (backoffCol != null) {
            backoffCol.setCellValueFactory(new PropertyValueFactory<>("backoffMs"));
        }
        setupActionsColumn();
        connectionsTable.setItems(connectionRows);
        connectionsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onConnectionSelected(n));
        refreshConnections();
    }

    private void onConnectionSelected(ConnectionRow row) {
        boolean hasSelection = row != null;
        if (connectSelectedButton != null)
            connectSelectedButton.setDisable(!hasSelection);
        if (disconnectSelectedButton != null)
            disconnectSelectedButton.setDisable(!hasSelection);
        if (reconnectSelectedButton != null)
            reconnectSelectedButton.setDisable(!hasSelection);
        if (platformConfigPane != null)
            platformConfigPane.setExpanded(hasSelection);
        if (selectedPlatformHeader != null) {
            String headerText = "No platform selected";
            if (hasSelection && row != null) {
                headerText = row.getPlatform() + " Settings";
            }
            selectedPlatformHeader.setText(headerText);
        }
        if (twitchConfigBox != null) {
            boolean twitch = hasSelection && row != null && "twitch".equals(row.getPlatform());
            twitchConfigBox.setVisible(twitch);
            twitchConfigBox.setManaged(twitch);
            if (twitch) {
                try {
                    twitchOAuthService.start();
                } catch (Exception ignored) {
                }
            }
        }
        if (noConfigPlaceholder != null) {
            boolean show = hasSelection && row != null && !("twitch".equals(row.getPlatform()));
            noConfigPlaceholder.setVisible(show);
            noConfigPlaceholder.setManaged(show);
        }
    }

    private void setupActionsColumn() {
        if (actionsCol == null)
            return;
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button connectBtn = new Button("▶ Connect");
            private final Button disconnectBtn = new Button("■ Disconnect");
            private final Button reconnectBtn = new Button("↻ Reconnect");
            private final Button healthBtn = new Button("❤ Health");
            private final HBox box = new HBox(4, connectBtn, disconnectBtn, reconnectBtn, healthBtn);
            {
                connectBtn.setTooltip(new Tooltip("Start connection / OAuth if needed"));
                disconnectBtn.setTooltip(new Tooltip("Gracefully disconnect"));
                reconnectBtn.setTooltip(new Tooltip("Force a disconnect then connect"));
                healthBtn.setTooltip(new Tooltip("Run platform health check"));
                connectBtn.setOnAction(e -> {
                    ConnectionRow row = getTableView().getItems().get(getIndex());
                    try {
                        platformConnectionManager.start();
                    } catch (Exception ignored) {
                    }
                    platformConnectionManager.ensurePlatformInitialized(row.getPlatform());
                    var opt = platformConnectionManager.connection(row.getPlatform());
                    if (opt.isEmpty()) {
                        updateStatus("No connection instance (ensure enabled checkbox is checked)", false);
                        return;
                    }
                    var c = opt.get();
                    if ("twitch".equals(row.getPlatform())) {
                        updateStatus("Opening Twitch OAuth (if required)...", true);
                    } else {
                        updateStatus("Connecting to " + row.getPlatform() + "...", true);
                    }
                    c.connect();
                });
                disconnectBtn.setOnAction(e -> {
                    ConnectionRow row = getTableView().getItems().get(getIndex());
                    platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> c.disconnect());
                });
                reconnectBtn.setOnAction(e -> {
                    ConnectionRow row = getTableView().getItems().get(getIndex());
                    platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> {
                        c.disconnect();
                        updateStatus("Reconnecting to " + row.getPlatform() + "...", true);
                        c.connect();
                    });
                });
                healthBtn.setOnAction(e -> {
                    ConnectionRow row = getTableView().getItems().get(getIndex());
                    platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> {
                        c.healthCheck().whenComplete((ok, ex) -> Platform.runLater(() -> {
                            String msg = ok != null && ok ? "healthy" : "unhealthy";
                            updateStatus(row.getPlatform() + " health: " + msg, false);
                        }));
                    });
                });
                box.setFillHeight(true);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ConnectionRow row = getTableView().getItems().get(getIndex());
                    boolean connected = row.getStatus().equals("CONNECTED");
                    boolean connecting = row.getStatus().equals("CONNECTING");
                    connectBtn.setDisable(connected || connecting);
                    disconnectBtn.setDisable(!connected);
                    reconnectBtn.setDisable(connecting);
                    setGraphic(box);
                }
            }
        });
    }

    private void refreshConnections() {
        connectionRows.clear();
        if (platformConnectionRegistry != null) {
            for (String id : platformConnectionRegistry.platformIds()) {
                var st = platformConnectionManager.status(id);
                var metrics = platformConnectionManager.metrics(id);
                String user = id.equals("twitch") && configurationService.get(CoreConfigKeys.TWITCH_CLIENT_ID) != null
                        ? configurationService.get(CoreConfigKeys.TWITCH_CLIENT_ID)
                        : "";
                boolean enabled = configurationService.getDynamicBoolean("platform." + id + ".enabled", false);
                connectionRows.add(new ConnectionRow(id, user, st, metrics, enabled));
            }
        } else { // fallback to existing twitch-only logic
            var st = platformConnectionManager.status("twitch");
            var metrics = platformConnectionManager.metrics("twitch");
            String user = configurationService.get(CoreConfigKeys.TWITCH_CLIENT_ID) != null
                    ? configurationService.get(CoreConfigKeys.TWITCH_CLIENT_ID)
                    : "";
            boolean enabled = configurationService.getDynamicBoolean("platform.twitch.enabled", false);
            connectionRows.add(new ConnectionRow("twitch", user, st, metrics, enabled));
        }
        // Apply simple status coloring via row factory (unchanged logic)
        if (connectionsTable != null && connectionsTable.getRowFactory() == null) {
            connectionsTable.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
                @Override
                protected void updateItem(ConnectionRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                        setTooltip(null);
                    } else {
                        if (isSelected()) { // preserve default selection styling for readability
                            setStyle("");
                        } else {
                            String state = item.getStatus();
                            String color;
                            switch (state) {
                                case "CONNECTED" -> color = "#c8f7c5";
                                case "CONNECTING" -> color = "#fff4c2";
                                case "FAILED" -> color = "#f7c5c5";
                                case "RECONNECT_SCHEDULED" -> color = "#e0d7ff";
                                default -> color = "white";
                            }
                            setStyle("-fx-background-color: " + color + ";");
                        }
                        // Build tooltip text
                        String baseTip = item.statusDetail;
                        String capsTip = "";
                        if (platformConnectionRegistry != null) {
                            capsTip = platformConnectionRegistry.metadata(item.getPlatform())
                                    .map(md -> "\nCapabilities: " + md.capabilities())
                                    .orElse("");
                        }
                        setTooltip(new Tooltip(baseTip + capsTip));
                    }
                }
            });
        }
    }

    private void startPlatformStatusSubscription() {
        statusSubHandle = eventBus
                .subscribe(new EventSubscription(e -> e.getType() == EventType.SYSTEM, this::handleSystemEvent, 0));
    }

    private void handleSystemEvent(Event e) {
        Platform.runLater(() -> {
            refreshConnections();
        });
    }

    private void initSystemLogTable() {
        if (systemLogTable == null)
            return;
        if (logTimeCol != null)
            logTimeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        if (logPlatformCol != null)
            logPlatformCol.setCellValueFactory(new PropertyValueFactory<>("platform"));
        if (logStateCol != null)
            logStateCol.setCellValueFactory(new PropertyValueFactory<>("state"));
        if (logDetailCol != null)
            logDetailCol.setCellValueFactory(new PropertyValueFactory<>("detail"));
        systemLogTable.setItems(systemLogRows);
        systemLogTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(SystemLogRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    String state = item.getState();
                    String color = switch (state) {
                        case "CONNECTED" -> "#c8f7c5";
                        case "CONNECTING" -> "#fff4c2";
                        case "FAILED" -> "#f7c5c5";
                        case "RECONNECT_SCHEDULED" -> "#e0d7ff";
                        default -> "white";
                    };
                    setStyle("-fx-background-color: " + color + ";");
                }
            }
        });
        if (logFilterCombo != null) {
            logFilterCombo.getItems().setAll("All", "twitch", "dummy", "dummy2");
            logFilterCombo.getSelectionModel().selectFirst();
            logFilterCombo.valueProperty().addListener((o, a, b) -> applyLogFilter());
        }
    }

    private void applyLogFilter() {
        if (logFilterCombo == null || systemLogTable == null)
            return;
        String sel = logFilterCombo.getValue();
        if (sel == null || sel.equals("All")) {
            systemLogTable.setItems(systemLogRows);
        } else {
            var filtered = systemLogRows.filtered(r -> r.getPlatform().equals(sel));
            systemLogTable.setItems(filtered);
        }
    }

    private void startSystemLogSubscription() {
        systemLogSubHandle = eventBus
                .subscribe(new EventSubscription(e -> e.getType() == EventType.SYSTEM, this::appendSystemLog, -10));
    }

    private void appendSystemLog(Event e) {
        Platform.runLater(() -> {
            try {
                String platform = String.valueOf(e.getPayload().getOrDefault("platform", e.getSourcePlatform()));
                String state = String.valueOf(e.getPayload().getOrDefault("state", "?"));
                String detail = String.valueOf(e.getPayload().getOrDefault("detail", ""));
                systemLogRows.add(0, new SystemLogRow(System.currentTimeMillis(), platform, state, detail));
                if (systemLogRows.size() > 200)
                    systemLogRows.remove(systemLogRows.size() - 1);
                // New: drive global status + throbber based on connection lifecycle
                switch (state) {
                    case "CONNECTING" -> updateStatus(platform + " connecting...", true);
                    case "CONNECTED" -> updateStatus(platform + " connected", false);
                    case "FAILED" -> updateStatus(platform + " failed: " + (detail == null ? "" : detail), false);
                    case "DISCONNECTED" -> updateStatus(platform + " disconnected", false);
                    case "RECONNECT_SCHEDULED" -> updateStatus(platform + " scheduled to reconnect", true);
                    default -> {
                        /* ignore */ }
                }
            } catch (Exception ex) {
                logger.debug("Failed to append system log: {}", ex.getMessage());
            }
        });
    }

    @FXML
    private void handleClearSystemLog() {
        systemLogRows.clear();
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

    // --- Profile handlers ---
    @FXML
    private void handleSaveProfileAs() {
        String name = newProfileNameField.getText();
        if (name == null || name.isBlank()) {
            updateStatus("Enter profile name", false);
            return;
        }
        try {
            profileService.saveProfile(name.trim());
            refreshProfiles();
            selectProfile(name.trim());
            updateStatus("Profile saved", false);
        } catch (Exception ex) {
            logger.warn("Save profile failed: {}", ex.getMessage());
            updateStatus("Save failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleApplyProfile() {
        String sel = profileListView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            updateStatus("Select profile", false);
            return;
        }
        try {
            profileService.applyProfile(sel);
            initSettingsControls();
            refreshExportHashCache();
            updateStatus("Profile applied", false);
        } catch (Exception ex) {
            logger.warn("Apply profile failed: {}", ex.getMessage());
            updateStatus("Apply failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleDeleteProfile() {
        String sel = profileListView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            updateStatus("Select profile", false);
            return;
        }
        try {
            profileService.deleteProfile(sel);
            refreshProfiles();
            updateStatus("Profile deleted", false);
        } catch (Exception ex) {
            logger.warn("Delete profile failed: {}", ex.getMessage());
            updateStatus("Delete failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleSetDefaultProfile() {
        String sel = profileListView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            updateStatus("Select profile", false);
            return;
        }
        try {
            profileService.setDefaultProfile(sel);
            highlightDefaultProfile();
            updateStatus("Default set", false);
        } catch (Exception ex) {
            updateStatus("Set default failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleClearDefaultProfile() {
        try {
            profileService.setDefaultProfile("");
            highlightDefaultProfile();
            updateStatus("Default cleared", false);
        } catch (Exception ex) {
            updateStatus("Clear default failed: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleRefreshProfiles() {
        refreshProfiles();
        highlightDefaultProfile();
        updateStatus("Profiles refreshed", false);
    }

    private void refreshProfiles() {
        if (profileListView != null) {
            profileListView.getItems().setAll(profileService.listProfiles());
        }
    }

    private void selectProfile(String name) {
        if (profileListView != null) {
            profileListView.getSelectionModel().select(name);
        }
    }

    private void highlightDefaultProfile() {
        String def = profileService.getDefaultProfile();
        if (profileListView != null) {
            profileListView.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item + (item.equals(def) ? " (default)" : ""));
                    }
                }
            });
        }
    }

    // --- ConnectionRow model ---
    public static class ConnectionRow {
        private final SimpleStringProperty platform;
        private final SimpleStringProperty user;
        private final SimpleStringProperty status;
        private final SimpleStringProperty connectedSince;
        private final SimpleIntegerProperty retries;
        private final SimpleIntegerProperty backoffMs;
        private final String statusDetail;
        private final javafx.beans.property.SimpleBooleanProperty enabled;

        public ConnectionRow(String platform, String user, PlatformStatus st,
                com.voxstream.core.platform.PlatformConnectionManager.Metrics m, boolean enabled) {
            this.platform = new SimpleStringProperty(platform);
            this.user = new SimpleStringProperty(user);
            this.status = new SimpleStringProperty(st.state().name());
            this.connectedSince = new SimpleStringProperty(st.connectedSinceEpochMs() > 0
                    ? java.time.Instant.ofEpochMilli(st.connectedSinceEpochMs()).toString()
                    : "-");
            this.retries = new SimpleIntegerProperty((int) m.failedAttempts);
            this.backoffMs = new SimpleIntegerProperty(m.currentBackoffMs);
            this.statusDetail = st.detail();
            this.enabled = new javafx.beans.property.SimpleBooleanProperty(enabled);
            this.enabled.addListener((obs, oldV, newV) -> {
                try {
                    // FIX: previously used property object toString; now use value
                    configurationServiceStatic.setDynamicBoolean("platform." + this.getPlatform() + ".enabled", newV);
                } catch (Exception ex) {
                    LoggerFactory.getLogger(MainController.class)
                            .warn("Failed to persist enable flag for {}: {}", this.getPlatform(), ex.getMessage());
                }
            });
        }

        // Convenience constructor (default enabled=true) for legacy call sites
        public ConnectionRow(String platform, String user, PlatformStatus st,
                com.voxstream.core.platform.PlatformConnectionManager.Metrics m) {
            this(platform, user, st, m, true);
        }

        public String getPlatform() {
            return platform.get();
        }

        public String getUser() {
            return user.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getConnectedSince() {
            return connectedSince.get();
        }

        public int getRetries() {
            return retries.get();
        }

        public int getBackoffMs() {
            return backoffMs.get();
        }

        public javafx.beans.property.SimpleBooleanProperty enabledProperty() {
            return enabled;
        }
    }

    public static class SystemLogRow {
        private final SimpleLongProperty epoch = new SimpleLongProperty();
        private final SimpleStringProperty time = new SimpleStringProperty();
        private final SimpleStringProperty platform = new SimpleStringProperty();
        private final SimpleStringProperty state = new SimpleStringProperty();
        private final SimpleStringProperty detail = new SimpleStringProperty();

        public SystemLogRow(long epochMs, String platform, String state, String detail) {
            this.epoch.set(epochMs);
            this.time.set(java.time.Instant.ofEpochMilli(epochMs).toString());
            this.platform.set(platform);
            this.state.set(state);
            this.detail.set(detail);
        }

        public String getTime() {
            return time.get();
        }

        public String getPlatform() {
            return platform.get();
        }

        public String getState() {
            return state.get();
        }

        public String getDetail() {
            return detail.get();
        }
    }

    // Static bridge for inner class config persistence
    private static ConfigurationService configurationServiceStatic;

    @Autowired
    private void initStaticConfig(ConfigurationService svc) {
        configurationServiceStatic = svc;
    }

    @FXML
    private void handleRefreshConnections() {
        refreshConnections();
    }

    @FXML
    private void handleConnectSelected() {
        ConnectionRow row = connectionsTable.getSelectionModel().getSelectedItem();
        if (row == null)
            return;
        try {
            platformConnectionManager.start();
        } catch (Exception ignored) {
        }
        // Ensure platform initialized when using toolbar button
        platformConnectionManager.ensurePlatformInitialized(row.getPlatform());
        platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> {
            if ("twitch".equals(row.getPlatform()))
                updateStatus("Opening Twitch OAuth (if required)...", true);
            c.connect();
        });
    }

    @FXML
    private void handleDisconnectSelected() {
        ConnectionRow row = connectionsTable.getSelectionModel().getSelectedItem();
        if (row == null)
            return;
        platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> c.disconnect());
    }

    @FXML
    private void handleReconnectSelected() {
        ConnectionRow row = connectionsTable.getSelectionModel().getSelectedItem();
        if (row == null)
            return;
        platformConnectionManager.connection(row.getPlatform()).ifPresent(c -> {
            c.disconnect();
            c.connect();
        });
    }

    private void loadTwitchConfigFields() {
        if (twitchClientIdField != null)
            twitchClientIdField.setText(configurationService.get(CoreConfigKeys.TWITCH_CLIENT_ID));
        if (twitchClientSecretField != null) {
            boolean pkce = Boolean.TRUE.equals(configurationService.get(CoreConfigKeys.TWITCH_OAUTH_PKCE_ENABLED)); // null-safe
            if (pkce) {
                twitchClientSecretField.setPromptText("(PKCE enabled - secret optional)");
            }
            twitchClientSecretField.setText(configurationService.get(CoreConfigKeys.TWITCH_CLIENT_SECRET));
        }
        if (twitchScopesField != null)
            twitchScopesField.setText(configurationService.get(CoreConfigKeys.TWITCH_SCOPES));
        if (twitchRedirectPortField != null)
            twitchRedirectPortField
                    .setText(String.valueOf(configurationService.get(CoreConfigKeys.TWITCH_REDIRECT_PORT)));
    }

    @FXML
    private void handleSaveTwitchConfig() {
        try {
            configurationService.set(CoreConfigKeys.TWITCH_CLIENT_ID, twitchClientIdField.getText().trim());
            boolean pkce = Boolean.TRUE.equals(configurationService.get(CoreConfigKeys.TWITCH_OAUTH_PKCE_ENABLED)); // null-safe
            String secret = twitchClientSecretField.getText().trim();
            if (!pkce || !secret.isBlank()) { // only persist secret if PKCE disabled or user supplied a value
                configurationService.set(CoreConfigKeys.TWITCH_CLIENT_SECRET, secret);
            }
            configurationService.set(CoreConfigKeys.TWITCH_SCOPES, twitchScopesField.getText().trim());
            configurationService.set(CoreConfigKeys.TWITCH_REDIRECT_PORT,
                    Integer.parseInt(twitchRedirectPortField.getText().trim()));
            twitchConfigStatusLabel.setText("Saved");
        } catch (Exception ex) {
            twitchConfigStatusLabel.setText("Error: " + ex.getMessage());
        }
    }

    @FXML
    void handleTwitchSignOut() { // visibility relaxed for test access
        try {
            twitchOAuthService.revokeAndDeleteTokens();
            configurationService.set(CoreConfigKeys.TWITCH_CLIENT_SECRET, "");
            twitchClientSecretField.setText("");
            twitchConfigStatusLabel.setText("Signed out");
            updateStatus("Twitch credentials revoked", false);
            refreshConnections();
        } catch (Exception ex) {
            twitchConfigStatusLabel.setText("Error: " + ex.getMessage());
        }
    }

    private void initGlobalPlatformsToggle() {
        if (globalPlatformsEnabledCheckbox == null)
            return;
        try {
            boolean enabled = configurationService.get(CoreConfigKeys.PLATFORM_ENABLED);
            globalPlatformsEnabledCheckbox.setSelected(enabled);
            globalPlatformsEnabledCheckbox.selectedProperty().addListener((obs, oldV, newV) -> {
                try {
                    configurationService.set(CoreConfigKeys.PLATFORM_ENABLED, newV);
                    updateStatus("Platform connections " + (newV ? "enabled" : "disabled"), false);
                    if (newV) {
                        try {
                            platformConnectionManager.start();
                        } catch (Exception ex) {
                            logger.warn("Failed to start PlatformConnectionManager: {}", ex.toString());
                        }
                    } else {
                        logger.info("Global platform disable set (new connections will not start until re-enabled)");
                    }
                    refreshConnections();
                } catch (Exception ex) {
                    logger.warn("Failed updating platform enabled flag: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            logger.debug("Unable to init global platforms toggle: {}", e.getMessage());
        }
    }

    private String statusEmoji(String state) {
        return switch (state) {
            case "CONNECTED" -> "✅";
            case "CONNECTING" -> "⏳";
            case "FAILED" -> "❌";
            case "RECONNECT_SCHEDULED" -> "🔁";
            case "DISCONNECTED" -> "⛔";
            default -> "⬜";
        };
    }
}
