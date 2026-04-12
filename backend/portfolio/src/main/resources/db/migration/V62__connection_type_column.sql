ALTER TABLE broker_connections ADD COLUMN connection_type VARCHAR(20);
COMMENT ON COLUMN broker_connections.connection_type IS 'SnapTrade connection type: read or trade';
