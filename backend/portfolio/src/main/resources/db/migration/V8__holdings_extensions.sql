-- V8__holdings_extensions.sql
-- Extend holdings tables to support unresolved holdings

-- Add raw identifier columns for unresolved ETF holdings
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS raw_ticker VARCHAR(50);
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS raw_name VARCHAR(255);
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS raw_isin VARCHAR(20);
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS raw_cusip VARCHAR(20);
ALTER TABLE etf_holdings ADD COLUMN IF NOT EXISTS resolution_status VARCHAR(20) DEFAULT 'RESOLVED';

-- Add raw identifier columns for unresolved mutual fund holdings
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS raw_ticker VARCHAR(50);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS raw_name VARCHAR(255);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS raw_isin VARCHAR(20);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS raw_cusip VARCHAR(20);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS resolution_status VARCHAR(20) DEFAULT 'RESOLVED';

-- Make stock_id nullable for unresolved holdings
ALTER TABLE etf_holdings ALTER COLUMN stock_id DROP NOT NULL;
ALTER TABLE mutual_fund_holdings ALTER COLUMN stock_id DROP NOT NULL;

-- Add check constraint for resolution status
ALTER TABLE etf_holdings ADD CONSTRAINT chk_etf_holdings_resolution
    CHECK (resolution_status IN ('RESOLVED', 'UNRESOLVED', 'PARTIAL'));
ALTER TABLE mutual_fund_holdings ADD CONSTRAINT chk_mf_holdings_resolution
    CHECK (resolution_status IN ('RESOLVED', 'UNRESOLVED', 'PARTIAL'));

-- Indexes for unresolved holdings queries
CREATE INDEX idx_etf_holdings_unresolved ON etf_holdings(resolution_status)
    WHERE resolution_status != 'RESOLVED';
CREATE INDEX idx_mf_holdings_unresolved ON mutual_fund_holdings(resolution_status)
    WHERE resolution_status != 'RESOLVED';
