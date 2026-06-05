-- V74: Add option-specific fields to trade_orders for wheel strategy
ALTER TABLE trade_orders ADD COLUMN option_type VARCHAR(4);
ALTER TABLE trade_orders ADD COLUMN strike_price DECIMAL(12, 4);
ALTER TABLE trade_orders ADD COLUMN expiration_date DATE;
ALTER TABLE trade_orders ADD COLUMN symbol_id BIGINT;
ALTER TABLE trade_orders ADD COLUMN stop_price DECIMAL(12, 4);
