ALTER TABLE account_analytics RENAME COLUMN irr TO xirr;
ALTER TABLE account_analytics ADD COLUMN total_return DECIMAL(18, 2);
ALTER TABLE account_analytics ADD COLUMN total_return_pct DECIMAL(10, 4);
ALTER TABLE account_analytics ADD COLUMN dividend_yield DECIMAL(10, 4);
