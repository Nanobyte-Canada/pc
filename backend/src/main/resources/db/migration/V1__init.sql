-- Portfolio Construction Application Schema
-- Initial migration to establish Flyway connectivity

-- Application metadata table for storing configuration
CREATE TABLE IF NOT EXISTS app_metadata (
    id SERIAL PRIMARY KEY,
    key VARCHAR(255) NOT NULL UNIQUE,
    value TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial version record
INSERT INTO app_metadata (key, value) VALUES ('schema_version', '1.0.0');

-- Add comment for documentation
COMMENT ON TABLE app_metadata IS 'Application metadata and configuration storage';
