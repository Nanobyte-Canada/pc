-- V24__mutual_fund_holdings_av_fields.sql
-- Add Alpha Vantage fields to mutual_fund_holdings for consistency with etf_holdings

ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS data_source VARCHAR(20) DEFAULT 'EODHD';
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS av_weight DECIMAL(10,6);
ALTER TABLE mutual_fund_holdings ADD COLUMN IF NOT EXISTS av_last_updated_at TIMESTAMPTZ;

-- Add constraint for data_source
ALTER TABLE mutual_fund_holdings ADD CONSTRAINT chk_mf_data_source
    CHECK (data_source IN ('EODHD', 'ALPHA_VANTAGE', 'MANUAL'));

-- Comments
COMMENT ON COLUMN mutual_fund_holdings.data_source IS 'Source of holdings data: EODHD, ALPHA_VANTAGE, or MANUAL';
COMMENT ON COLUMN mutual_fund_holdings.av_weight IS 'Weight from Alpha Vantage (preferred when data_source is ALPHA_VANTAGE)';
COMMENT ON COLUMN mutual_fund_holdings.av_last_updated_at IS 'When Alpha Vantage data was last updated';
