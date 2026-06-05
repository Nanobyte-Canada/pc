ALTER TABLE broker_gateway.connections ADD COLUMN IF NOT EXISTS refresh_failure_count INT NOT NULL DEFAULT 0;
