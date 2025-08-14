package com.voxstream.frontend;

import javafx.application.Application;

/**
 * Simple JavaFX Application Launcher.
 * This class serves as the entry point for JavaFX without Spring Boot
 * interference.
 */
public class JavaFXLauncher {

    public static void main(String[] args) {
        // Set system properties before JavaFX initialization
        System.setProperty("prism.lcdtext", "false"); // Better text rendering

        // Don't use preloader for now to avoid complications
        // System.setProperty("javafx.preloader",
        // "com.voxstream.frontend.VoxStreamPreloader");

        // Launch JavaFX Application
        Application.launch(VoxStreamApplication.class, args);
    }
}
