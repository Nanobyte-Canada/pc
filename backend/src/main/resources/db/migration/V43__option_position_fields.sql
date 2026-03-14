ALTER TABLE broker_positions ADD COLUMN strike_price DECIMAL(18,6);
ALTER TABLE broker_positions ADD COLUMN expiration_date DATE;
ALTER TABLE broker_positions ADD COLUMN option_type VARCHAR(10);
ALTER TABLE broker_positions ADD COLUMN underlying_symbol VARCHAR(20);

ALTER TABLE broker_positions ADD CONSTRAINT chk_option_type CHECK (option_type IS NULL OR option_type IN ('CALL', 'PUT'));
