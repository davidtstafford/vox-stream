package com.voxstream.core.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.voxstream.core.exception.ErrorCode;
import com.voxstream.core.exception.VoxStreamException;

/**
 * Core error handler service without UI dependencies.
 * Provides logging and system validation capabilities.
 */
@Service
public class CoreErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(CoreErrorHandler.class);

    /**
     * Handle a VoxStream exception with proper logging
     */
    public void handleError(VoxStreamException e) {
        handleError(e.getErrorCode(), e.getMessage(), e.getUserMessage(), e);
    }

    /**
     * Handle a generic exception
     */
    public void handleError(String message, Throwable e) {
        handleError(ErrorCode.INTERNAL_ERROR, message, "An unexpected error occurred", e);
    }

    /**
     * Handle an error with specific error code
     */
    public void handleError(ErrorCode errorCode, String technicalMessage, String userMessage, Throwable cause) {
        logger.error("Error {}: {} - {}", errorCode.getCode(), technicalMessage, userMessage, cause);
    }

    /**
     * Handle fatal errors that require application shutdown
     */
    public void handleFatalError(ErrorCode errorCode, String technicalMessage, String userMessage, Throwable cause) {
        logger.error("FATAL ERROR {}: {} - {}", errorCode.getCode(), technicalMessage, userMessage, cause);
        // UI handling should be done by frontend error handler
    }

    /**
     * Validate system requirements
     */
    public CompletableFuture<Boolean> validateSystemRequirements() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check macOS version
                if (isMacOS() && !isMacOSVersionSupported()) {
                    handleFatalError(
                            ErrorCode.MACOS_VERSION_TOO_OLD,
                            "macOS version: " + System.getProperty("os.version"),
                            "VoxStream requires macOS Catalina (10.15) or newer. Please update your system.",
                            null);
                    return false;
                }

                // Check architecture on macOS
                if (isMacOS() && !is64Bit()) {
                    handleFatalError(
                            ErrorCode.ARCHITECTURE_NOT_SUPPORTED,
                            "Architecture: " + System.getProperty("os.arch"),
                            "VoxStream requires 64-bit architecture on macOS.",
                            null);
                    return false;
                }

                // Check Java version
                if (!isJavaVersionSupported()) {
                    handleFatalError(
                            ErrorCode.JAVA_VERSION_INCOMPATIBLE,
                            "Java version: " + System.getProperty("java.version"),
                            "VoxStream requires Java 17 or newer. Please update your Java installation.",
                            null);
                    return false;
                }

                logger.info("System requirements validation passed");
                return true;

            } catch (Exception e) {
                handleFatalError(
                        ErrorCode.SYSTEM_REQUIREMENTS_NOT_MET,
                        "System validation failed",
                        "Failed to validate system requirements",
                        e);
                return false;
            }
        });
    }

    private boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private boolean is64Bit() {
        String osArch = System.getProperty("os.arch");
        return osArch.contains("64") || osArch.equals("aarch64");
    }

    private boolean isMacOSVersionSupported() {
        String osVersion = System.getProperty("os.version");
        try {
            String[] versionParts = osVersion.split("\\.");
            int majorVersion = Integer.parseInt(versionParts[0]);
            int minorVersion = Integer.parseInt(versionParts[1]);

            // macOS Catalina is 10.15, Big Sur is 11.0, etc.
            return majorVersion > 10 || (majorVersion == 10 && minorVersion >= 15);
        } catch (Exception e) {
            logger.warn("Could not parse macOS version: {}", osVersion);
            return true; // Assume supported if we can't parse
        }
    }

    private boolean isJavaVersionSupported() {
        String javaVersion = System.getProperty("java.version");
        try {
            String versionNumber = javaVersion.split("\\.")[0];
            if (versionNumber.equals("1")) {
                // Old format like "1.8.0_xxx"
                versionNumber = javaVersion.split("\\.")[1];
            }
            int version = Integer.parseInt(versionNumber);
            return version >= 17;
        } catch (Exception e) {
            logger.warn("Could not parse Java version: {}", javaVersion);
            return true; // Assume supported if we can't parse
        }
    }
}
