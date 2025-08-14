-- Initial schema for VoxStream (Phase 2.2 start)

CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    source_platform VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    importance INT NOT NULL DEFAULT 0,
    correlation_id VARCHAR(64) NOT NULL,
    payload CLOB NULL
);

CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_type ON events(type);
CREATE INDEX IF NOT EXISTS idx_events_corr ON events(correlation_id);

-- Viewer schema placeholder
CREATE TABLE IF NOT EXISTS viewers (
    id VARCHAR(64) PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    handle VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_viewers_platform_handle ON viewers(platform, handle);

-- Configuration key/value placeholder
CREATE TABLE IF NOT EXISTS app_config (
    cfg_key VARCHAR(128) PRIMARY KEY,
    cfg_value CLOB NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
