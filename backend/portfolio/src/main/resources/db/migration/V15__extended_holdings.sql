-- V15__extended_holdings.sql
-- Extend holdings tables to support ETF-to-ETF and MF holdings from SeekingAlpha

-- Extend etf_holdings with new columns
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS holding_type VARCHAR(20) DEFAULT 'STOCK';
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS held_etf_id BIGINT REFERENCES etfs(id) ON DELETE SET NULL;
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS held_mutual_fund_id BIGINT REFERENCES mutual_funds(id) ON DELETE SET NULL;
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS rank INTEGER;
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS source_section VARCHAR(20) DEFAULT 'EODHD';
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS raw_country VARCHAR(50);
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS is_valid_symbol BOOLEAN;

-- Extend mutual_fund_holdings with same columns
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS holding_type VARCHAR(20) DEFAULT 'STOCK';
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS held_etf_id BIGINT REFERENCES etfs(id) ON DELETE SET NULL;
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS held_mutual_fund_id BIGINT REFERENCES mutual_funds(id) ON DELETE SET NULL;
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS rank INTEGER;
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS source_section VARCHAR(20) DEFAULT 'EODHD';
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS raw_country VARCHAR(50);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS is_valid_symbol BOOLEAN;

-- Constraints for holding type
ALTER TABLE etf_holdings ADD CONSTRAINT chk_etf_holding_type
    CHECK (holding_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'UNKNOWN'));
ALTER TABLE mutual_fund_holdings ADD CONSTRAINT chk_mf_holding_type
    CHECK (holding_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'UNKNOWN'));

-- Constraints for source section
ALTER TABLE etf_holdings ADD CONSTRAINT chk_etf_source_section
    CHECK (source_section IN ('TOP_TEN', 'EODHD'));
ALTER TABLE mutual_fund_holdings ADD CONSTRAINT chk_mf_source_section
    CHECK (source_section IN ('TOP_TEN', 'EODHD'));

-- Indexes for resolution queries
CREATE INDEX IF NOT EXISTS idx_etf_holdings_source ON etf_holdings(source_section);
CREATE INDEX IF NOT EXISTS idx_mf_holdings_source ON mutual_fund_holdings(source_section);

-- Drop old unresolved index if exists (will be recreated with better filter)
DROP INDEX IF EXISTS idx_etf_holdings_unresolved;
DROP INDEX IF EXISTS idx_mf_holdings_unresolved;

-- New indexes for unresolved holdings
CREATE INDEX idx_etf_holdings_unresolved_new ON etf_holdings(etf_id, as_of_date)
    WHERE resolution_status = 'UNRESOLVED';
CREATE INDEX idx_mf_holdings_unresolved_new ON mutual_fund_holdings(mutual_fund_id, as_of_date)
    WHERE resolution_status = 'UNRESOLVED';

-- Index for held instruments (for lookthrough analysis)
CREATE INDEX IF NOT EXISTS idx_etf_holdings_held_etf ON etf_holdings(held_etf_id) WHERE held_etf_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_etf_holdings_held_mf ON etf_holdings(held_mutual_fund_id) WHERE held_mutual_fund_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mf_holdings_held_etf ON mutual_fund_holdings(held_etf_id) WHERE held_etf_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mf_holdings_held_mf ON mutual_fund_holdings(held_mutual_fund_id) WHERE held_mutual_fund_id IS NOT NULL;
