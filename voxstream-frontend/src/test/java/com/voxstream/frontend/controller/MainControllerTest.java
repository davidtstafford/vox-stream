package com.voxstream.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.voxstream.core.bus.EventBus;
import com.voxstream.core.bus.EventSubscription;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.config.profile.ProfileService;
import com.voxstream.core.event.BaseEvent;
import com.voxstream.core.event.EventType;
import com.voxstream.core.platform.PlatformConnectionManager;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;
import com.voxstream.platform.api.PlatformStatus;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/** Minimal tests for MainController sign-out & system log append */
class MainControllerTest {

    private MainController controller;
    private TwitchOAuthService twitchOAuthService;
    private ConfigurationService configurationService;
    private EventBus eventBus;

    @BeforeEach
    void setup() throws Exception {
        // initialize JavaFX toolkit in headless mode
        Platform.startup(() -> {
        });
        controller = new MainController();
        twitchOAuthService = mock(TwitchOAuthService.class);
        configurationService = mock(ConfigurationService.class);
        eventBus = mock(EventBus.class);
        PlatformConnectionManager pcm = mock(PlatformConnectionManager.class);
        when(pcm.status("twitch")).thenReturn(PlatformStatus.disconnected());
        when(pcm.metrics("twitch")).thenReturn(new PlatformConnectionManager.Metrics());
        ProfileService profileService = mock(ProfileService.class);
        VoxStreamConfiguration cfg = mock(VoxStreamConfiguration.class);
        when(cfg.getVersion()).thenReturn("1.0.0-SNAPSHOT");

        inject(controller, "twitchOAuthService", twitchOAuthService);
        inject(controller, "configurationService", configurationService);
        inject(controller, "eventBus", eventBus);
        inject(controller, "platformConnectionManager", pcm);
        inject(controller, "profileService", profileService);
        inject(controller, "configuration", cfg);

        // minimal required FXML fields
        inject(controller, "twitchClientSecretField", new TextField());
        inject(controller, "twitchConfigStatusLabel", new Label());
        inject(controller, "connectionsTable", new TableView<>());
        inject(controller, "platformCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "userCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "statusCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "sinceCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "retriesCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "backoffCol", new TableColumn<MainController.ConnectionRow, String>());
        inject(controller, "actionsCol", new TableColumn<MainController.ConnectionRow, Void>());
        inject(controller, "systemLogTable", new TableView<MainController.SystemLogRow>());
        inject(controller, "logTimeCol", new TableColumn<MainController.SystemLogRow, String>());
        inject(controller, "logPlatformCol", new TableColumn<MainController.SystemLogRow, String>());
        inject(controller, "logStateCol", new TableColumn<MainController.SystemLogRow, String>());
        inject(controller, "logDetailCol", new TableColumn<MainController.SystemLogRow, String>());
        inject(controller, "statusLabel", new Label());
        inject(controller, "statusIndicator", new ProgressIndicator());
        inject(controller, "menuBar", new MenuBar());
        inject(controller, "mainTabPane", new TabPane());
        inject(controller, "connectionsTab", new Tab());
        inject(controller, "eventsTab", new Tab());
        inject(controller, "ttsTab", new Tab());
        inject(controller, "viewersTab", new Tab());
        inject(controller, "settingsTab", new Tab());

        controller.initialize(null, null);
    }

    @Test
    void testTwitchSignOutClearsSecret() {
        controller.handleTwitchSignOut();
        verify(twitchOAuthService, times(1)).revokeAndDeleteTokens();
    }

    @Test
    void testSystemLogAppendOnEvent() {
        ArgumentCaptor<EventSubscription> subCap = ArgumentCaptor.forClass(EventSubscription.class);
        verify(eventBus, atLeastOnce()).subscribe(subCap.capture());
        EventSubscription sysSub = subCap.getAllValues().stream()
                .filter(s -> s.getFilter().test(new BaseEvent(EventType.SYSTEM, "twitch", Map.of(), null))).findFirst()
                .orElse(null);
        assertNotNull(sysSub);
        BaseEvent evt = new BaseEvent(EventType.SYSTEM, "twitch",
                Map.of("platform", "twitch", "state", "CONNECTED", "detail", "ok"), null);
        sysSub.getConsumer().accept(evt);
        try {
            TableView<MainController.SystemLogRow> table = get(controller, "systemLogTable");
            assertEquals(1, table.getItems().size());
            assertEquals("CONNECTED", table.getItems().get(0).getState());
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
