package com.voxstream.frontend.service;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.voxstream.core.exception.ErrorCode;
import com.voxstream.core.exception.VoxStreamException;
import com.voxstream.core.service.CoreErrorHandler;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

/**
 * Frontend error handler service with JavaFX UI capabilities.
 * Extends core error handling with user-friendly dialogs and notifications.
 */
@Service
public class ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    @Autowired
    private CoreErrorHandler coreErrorHandler;

    /**
     * Handle a VoxStream exception with proper logging and user notification
     */
    public void handleError(VoxStreamException e) {
        coreErrorHandler.handleError(e);
        Platform.runLater(() -> showErrorDialog(e.getErrorCode(), e.getUserMessage(), e.getMessage(), e));
    }

    /**
     * Handle a generic exception
     */
    public void handleError(String message, Throwable e) {
        coreErrorHandler.handleError(message, e);
        Platform.runLater(() -> showErrorDialog(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", message, e));
    }

    /**
     * Handle an error with specific error code
     */
    public void handleError(ErrorCode errorCode, String technicalMessage, String userMessage, Throwable cause) {
        coreErrorHandler.handleError(errorCode, technicalMessage, userMessage, cause);
        Platform.runLater(() -> showErrorDialog(errorCode, userMessage, technicalMessage, cause));
    }

    /**
     * Handle fatal errors that require application shutdown
     */
    public void handleFatalError(ErrorCode errorCode, String technicalMessage, String userMessage, Throwable cause) {
        coreErrorHandler.handleFatalError(errorCode, technicalMessage, userMessage, cause);

        Platform.runLater(() -> {
            showFatalErrorDialog(errorCode, userMessage, technicalMessage, cause);
            System.exit(errorCode.getCode());
        });
    }

    /**
     * Show a non-blocking error notification
     */
    public void showErrorNotification(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("VoxStream Warning");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Show an error dialog with technical details
     */
    private void showErrorDialog(ErrorCode errorCode, String userMessage, String technicalMessage, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("VoxStream Error");
        alert.setHeaderText("Error " + errorCode.getCode());
        alert.setContentText(userMessage);

        // Add technical details in expandable content
        if (technicalMessage != null || cause != null) {
            StringBuilder details = new StringBuilder();
            if (technicalMessage != null) {
                details.append("Technical Details:\n").append(technicalMessage).append("\n\n");
            }
            if (cause != null) {
                details.append("Stack Trace:\n").append(getStackTrace(cause));
            }

            TextArea textArea = new TextArea(details.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            alert.getDialogPane().setExpandableContent(textArea);
        }

        alert.showAndWait();
    }

    /**
     * Show a fatal error dialog that provides option to restart or exit
     */
    private void showFatalErrorDialog(ErrorCode errorCode, String userMessage, String technicalMessage,
            Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR, userMessage, ButtonType.OK);
        alert.setTitle("VoxStream Fatal Error");
        alert.setHeaderText("Fatal Error " + errorCode.getCode());

        // Add technical details
        if (technicalMessage != null || cause != null) {
            StringBuilder details = new StringBuilder();
            details.append("The application encountered a fatal error and must close.\n\n");
            if (technicalMessage != null) {
                details.append("Technical Details:\n").append(technicalMessage).append("\n\n");
            }
            if (cause != null) {
                details.append("Stack Trace:\n").append(getStackTrace(cause));
            }

            TextArea textArea = new TextArea(details.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            alert.getDialogPane().setExpandableContent(textArea);
        }

        alert.showAndWait();
    }

    /**
     * Convert throwable to string stack trace
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
