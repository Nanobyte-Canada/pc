-- V60__account_model_linking.sql
-- Link model portfolios directly to broker connections (replacing portfolio groups)

-- Add model portfolio reference to broker connections
ALTER TABLE broker_connections ADD COLUMN model_portfolio_id BIGINT
  REFERENCES model_portfolios(id) ON DELETE SET NULL;

-- Add accuracy tracking to broker connections
ALTER TABLE broker_connections ADD COLUMN model_accuracy DECIMAL(5,2);
ALTER TABLE broker_connections ADD COLUMN last_rebalanced_at TIMESTAMP;

-- Index for quick lookup
CREATE INDEX idx_broker_connections_model ON broker_connections(model_portfolio_id);

-- Migrate rebalance_events from group_id to connection_id
ALTER TABLE rebalance_events ADD COLUMN connection_id BIGINT
  REFERENCES broker_connections(id) ON DELETE CASCADE;
CREATE INDEX idx_rebalance_events_connection ON rebalance_events(connection_id);
