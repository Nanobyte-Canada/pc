-- Drop views that reference broker_id before dropping the column
DROP VIEW IF EXISTS v_portfolio_summary;
DROP VIEW IF EXISTS v_aggregated_positions;

-- Remove legacy SnapTrade columns from broker_connections
ALTER TABLE broker_connections DROP COLUMN IF EXISTS snaptrade_authorization_id;
DROP INDEX IF EXISTS idx_broker_connections_snaptrade_auth;

-- Remove broker_id FK (gateway now provides broker listing)
ALTER TABLE broker_connections DROP CONSTRAINT IF EXISTS fk_broker_connections_broker;
ALTER TABLE broker_connections DROP CONSTRAINT IF EXISTS uq_broker_connections;
DROP INDEX IF EXISTS idx_broker_connections_broker;
ALTER TABLE broker_connections DROP COLUMN IF EXISTS broker_id;
