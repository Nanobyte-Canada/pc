-- V61__order_sync_support.sql
-- Allow trade_orders to store broker-synced orders that don't belong to a portfolio group
ALTER TABLE trade_orders ALTER COLUMN group_id DROP NOT NULL;
