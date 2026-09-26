ALTER TABLE broker_connections
    ADD COLUMN last_activities_sync_status VARCHAR(20),
    ADD COLUMN last_balance_sync_status VARCHAR(20);
