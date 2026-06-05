-- Add gateway connection reference to broker_connections
ALTER TABLE broker_connections ADD COLUMN IF NOT EXISTS gateway_connection_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_broker_conn_gateway ON broker_connections(gateway_connection_id);

-- Drop SnapTrade health check table
DROP TABLE IF EXISTS snaptrade_status_checks;

-- Remove SnapTrade user columns
ALTER TABLE users DROP COLUMN IF EXISTS snaptrade_user_id;
ALTER TABLE users DROP COLUMN IF EXISTS snaptrade_user_secret_encrypted;
