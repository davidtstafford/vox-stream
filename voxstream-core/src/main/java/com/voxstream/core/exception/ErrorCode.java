package com.voxstream.core.exception;

/**
 * Error codes for VoxStream application.
 * Provides structured error handling with numeric codes and user-friendly
 * messages.
 */
public enum ErrorCode {

    // System/Platform errors (1000-1999)
    SYSTEM_INCOMPATIBLE(1001, "System is not compatible with VoxStream"),
    SYSTEM_REQUIREMENTS_NOT_MET(1002, "System requirements not met"),
    MACOS_VERSION_TOO_OLD(1003, "macOS version must be Catalina (10.15) or newer"),
    ARCHITECTURE_NOT_SUPPORTED(1004, "32-bit architecture not supported on macOS"),
    JAVA_VERSION_INCOMPATIBLE(1005, "Java version is not compatible"),
    JAVAFX_INITIALIZATION_FAILED(1006, "JavaFX initialization failed"),

    // Application startup errors (2000-2999)
    APPLICATION_STARTUP_FAILED(2001, "Failed to start application"),
    SPRING_CONTEXT_FAILED(2002, "Failed to initialize Spring application context"),
    UI_INITIALIZATION_FAILED(2003, "Failed to initialize user interface"),
    FXML_LOAD_FAILED(2004, "Failed to load user interface layout"),
    CSS_LOAD_FAILED(2005, "Failed to load application styles"),
    CONFIGURATION_LOAD_FAILED(2006, "Failed to load application configuration"),

    // Platform API errors (3000-3999)
    PLATFORM_CONNECTION_FAILED(3001, "Failed to connect to streaming platform"),
    PLATFORM_AUTHENTICATION_FAILED(3002, "Failed to authenticate with streaming platform"),
    PLATFORM_API_ERROR(3003, "Streaming platform API error"),
    PLATFORM_RATE_LIMITED(3004, "Platform API rate limit exceeded"),
    PLATFORM_UNAVAILABLE(3005, "Streaming platform is currently unavailable"),

    // TTS errors (4000-4999)
    TTS_ENGINE_FAILED(4001, "Text-to-speech engine failed"),
    TTS_VOICE_NOT_FOUND(4002, "Requested voice not found"),
    TTS_SYNTHESIS_FAILED(4003, "Failed to synthesize speech"),
    TTS_AUDIO_OUTPUT_FAILED(4004, "Failed to output audio"),

    // Web output errors (5000-5999)
    WEB_SERVER_START_FAILED(5001, "Failed to start web server"),
    WEB_SERVER_PORT_IN_USE(5002, "Web server port is already in use"),
    WEB_CONTENT_GENERATION_FAILED(5003, "Failed to generate web content"),

    // Database/Storage errors (6000-6999)
    DATABASE_CONNECTION_FAILED(6001, "Failed to connect to database"),
    DATABASE_MIGRATION_FAILED(6002, "Database migration failed"),
    CONFIG_SAVE_FAILED(6003, "Failed to save configuration"),
    CONFIG_LOAD_FAILED(6004, "Failed to load configuration"),

    // Network/Communication errors (7000-7999)
    NETWORK_CONNECTION_FAILED(7001, "Network connection failed"),
    WEBSOCKET_CONNECTION_FAILED(7002, "WebSocket connection failed"),
    HTTP_REQUEST_FAILED(7003, "HTTP request failed"),

    // Security/Authentication errors (8000-8999)
    AUTHENTICATION_FAILED(8001, "Authentication failed"),
    AUTHORIZATION_FAILED(8002, "Authorization failed"),
    TOKEN_EXPIRED(8003, "Access token has expired"),
    TOKEN_INVALID(8004, "Access token is invalid"),

    // Unknown/Generic errors (9000-9999)
    UNKNOWN_ERROR(9001, "An unknown error occurred"),
    INTERNAL_ERROR(9002, "Internal application error"),
    OPERATION_CANCELLED(9003, "Operation was cancelled"),
    OPERATION_TIMEOUT(9004, "Operation timed out");

    private final int code;
    private final String defaultUserMessage;

    ErrorCode(int code, String defaultUserMessage) {
        this.code = code;
        this.defaultUserMessage = defaultUserMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultUserMessage() {
        return defaultUserMessage;
    }

    /**
     * Get error code by numeric value
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}
