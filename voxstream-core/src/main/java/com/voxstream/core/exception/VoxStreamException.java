package com.voxstream.core.exception;

/**
 * Base exception class for VoxStream application.
 * Provides structured error handling with error codes and user-friendly
 * messages.
 */
public class VoxStreamException extends Exception {

    private final ErrorCode errorCode;
    private final String userMessage;

    public VoxStreamException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = errorCode.getDefaultUserMessage();
    }

    public VoxStreamException(ErrorCode errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public VoxStreamException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = errorCode.getDefaultUserMessage();
    }

    public VoxStreamException(ErrorCode errorCode, String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public int getErrorCodeValue() {
        return errorCode.getCode();
    }
}
