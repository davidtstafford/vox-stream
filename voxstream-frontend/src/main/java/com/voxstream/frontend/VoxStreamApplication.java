package com.voxstream.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.voxstream.core.exception.ErrorCode;
import com.voxstream.frontend.service.ApplicationLauncher;
import com.voxstream.frontend.service.ErrorHandler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX Application class for VoxStream.
 * This class integrates Spring Boot with JavaFX for macOS Catalina+
 * compatibility.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.voxstream")
public class VoxStreamApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(VoxStreamApplication.class);
    private static final String APPLICATION_TITLE = "VoxStream";
    private static final String VERSION = "1.0.0-SNAPSHOT";

    private ConfigurableApplicationContext springContext;
    private ApplicationLauncher applicationLauncher;
    private ErrorHandler errorHandler;

    public static void main(String[] args) {
        // This main method should not be called directly for JavaFX
        // Use JavaFXLauncher instead to avoid conflicts
        System.err.println("Please use JavaFXLauncher.main() instead of VoxStreamApplication.main()");
        System.exit(1);
    }

    @Override
    public void init() throws Exception {
        logger.info("Starting VoxStream Application - Version {}", VERSION);
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("JavaFX Version: {}", System.getProperty("javafx.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));

        // Verify 64-bit architecture for macOS Catalina compatibility
        String osArch = System.getProperty("os.arch");
        logger.info("Architecture: {}", osArch);

        if (isMacOS() && !is64Bit()) {
            logger.error("macOS Catalina requires 64-bit applications. Current architecture: {}", osArch);
            System.exit(ErrorCode.ARCHITECTURE_NOT_SUPPORTED.getCode());
        }

        logger.info("Initializing Spring Application Context...");

        try {
            springContext = SpringApplication.run(VoxStreamApplication.class);
            applicationLauncher = springContext.getBean(ApplicationLauncher.class);
            errorHandler = springContext.getBean(ErrorHandler.class);

            logger.info("Spring Application Context initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Spring context", e);
            if (errorHandler != null) {
                errorHandler.handleFatalError(
                        ErrorCode.SPRING_CONTEXT_FAILED,
                        "Spring context initialization failed: " + e.getMessage(),
                        "Failed to initialize application services",
                        e);
            }
            throw e;
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Starting JavaFX Application...");

        try {
            // Run application launch sequence
            if (applicationLauncher != null) {
                boolean launchSuccess = applicationLauncher.launchApplication().get();
                if (!launchSuccess) {
                    logger.error("Application launch sequence failed");
                    shutdown();
                    return;
                }
            }

            // Load the main FXML layout
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            fxmlLoader.setControllerFactory(springContext::getBean);

            Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

            // Add CSS styling
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            primaryStage.setTitle(APPLICATION_TITLE + " - " + VERSION);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Handle close request properly
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application close requested");
                shutdown();
            });

            primaryStage.show();
            logger.info("JavaFX Application started successfully");

        } catch (Exception e) {
            logger.error("Failed to start JavaFX Application", e);
            if (errorHandler != null) {
                errorHandler.handleFatalError(
                        ErrorCode.UI_INITIALIZATION_FAILED,
                        "JavaFX application start failed: " + e.getMessage(),
                        "Failed to start the user interface",
                        e);
            }
            shutdown();
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping VoxStream Application...");
        shutdown();
    }

    private void shutdown() {
        try {
            if (springContext != null) {
                springContext.close();
                logger.info("Spring Application Context closed");
            }
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        } finally {
            Platform.exit();
        }
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private static boolean is64Bit() {
        String osArch = System.getProperty("os.arch");
        return osArch.contains("64") || osArch.equals("aarch64");
    }
}
