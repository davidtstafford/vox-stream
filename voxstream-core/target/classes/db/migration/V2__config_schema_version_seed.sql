-- Seed baseline configuration schema version if absent
-- This is idempotent: it only inserts if the key does not already exist.
INSERT INTO app_config (cfg_key, cfg_value, updated_at)
    SELECT 'config.schema.version', '1', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM app_config WHERE cfg_key='config.schema.version');
