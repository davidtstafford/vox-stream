package com.voxstream.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Main configuration class for VoxStream application.
 * Handles application-wide settings and preferences.
 */
@Component
@ConfigurationProperties(prefix = "voxstream")
public class VoxStreamConfiguration {

    private String version = "1.0.0-SNAPSHOT";
    private Database database = new Database();
    private EventBus eventBus = new EventBus();
    private TTS tts = new TTS();
    private WebOutput webOutput = new WebOutput();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public TTS getTts() {
        return tts;
    }

    public void setTts(TTS tts) {
        this.tts = tts;
    }

    public WebOutput getWebOutput() {
        return webOutput;
    }

    public void setWebOutput(WebOutput webOutput) {
        this.webOutput = webOutput;
    }

    public static class Database {
        private String type = "H2";
        private String path = "./data/voxstream.db";
        private boolean autoMigrate = true;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isAutoMigrate() {
            return autoMigrate;
        }

        public void setAutoMigrate(boolean autoMigrate) {
            this.autoMigrate = autoMigrate;
        }
    }

    public static class EventBus {
        private int maxEvents = 10000;
        private int purgeIntervalMinutes = 10;
        private boolean enableMetrics = true;

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }

        public int getPurgeIntervalMinutes() {
            return purgeIntervalMinutes;
        }

        public void setPurgeIntervalMinutes(int purgeIntervalMinutes) {
            this.purgeIntervalMinutes = purgeIntervalMinutes;
        }

        public boolean isEnableMetrics() {
            return enableMetrics;
        }

        public void setEnableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
        }
    }

    public static class TTS {
        private boolean enabled = false;
        private String provider = "AWS_POLLY";
        private String voice = "Joanna";
        private int maxCharacters = 500;
        private int maxRepeatedChars = 3;
        private int maxRepeatedWords = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public int getMaxCharacters() {
            return maxCharacters;
        }

        public void setMaxCharacters(int maxCharacters) {
            this.maxCharacters = maxCharacters;
        }

        public int getMaxRepeatedChars() {
            return maxRepeatedChars;
        }

        public void setMaxRepeatedChars(int maxRepeatedChars) {
            this.maxRepeatedChars = maxRepeatedChars;
        }

        public int getMaxRepeatedWords() {
            return maxRepeatedWords;
        }

        public void setMaxRepeatedWords(int maxRepeatedWords) {
            this.maxRepeatedWords = maxRepeatedWords;
        }
    }

    public static class WebOutput {
        private int port = 8080;
        private String host = "localhost";
        private boolean enableCors = true;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public boolean isEnableCors() {
            return enableCors;
        }

        public void setEnableCors(boolean enableCors) {
            this.enableCors = enableCors;
        }
    }
}
