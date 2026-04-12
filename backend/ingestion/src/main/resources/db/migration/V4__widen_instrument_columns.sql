-- Widen instruments columns for non-standard values from data providers
-- country: EODHD returns "Unknown" for some instruments (mutual funds)
-- ticker: some EODHD tickers exceed 20 chars
ALTER TABLE instruments ALTER COLUMN country TYPE VARCHAR(50);
ALTER TABLE instruments ALTER COLUMN ticker TYPE VARCHAR(50);
