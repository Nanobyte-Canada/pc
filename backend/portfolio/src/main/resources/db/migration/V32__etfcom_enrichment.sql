-- V32: Add etf.com enrichment columns and tables
-- Replaces Alpha Vantage ETF pipeline with etf.com API

-- ========================================
-- Universe fields (from /v2/fund/tickers)
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_fund_id INTEGER;
ALTER TABLE etfs ADD COLUMN etfcom_asset_class VARCHAR(50);

-- ========================================
-- Enrichment tracking
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE etfs ADD COLUMN etfcom_last_attempt_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN etfcom_last_success_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN etfcom_retry_count INTEGER DEFAULT 0;
ALTER TABLE etfs ADD COLUMN etfcom_error_code VARCHAR(50);
ALTER TABLE etfs ADD COLUMN etfcom_error_message TEXT;
ALTER TABLE etfs ADD COLUMN etfcom_raw_payload JSONB;

-- ========================================
-- Enrichment data: Summary
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_issuer VARCHAR(100);
ALTER TABLE etfs ADD COLUMN etfcom_inception_date DATE;
ALTER TABLE etfs ADD COLUMN etfcom_expense_ratio DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_aum DECIMAL(20,2);
ALTER TABLE etfs ADD COLUMN etfcom_index_tracked VARCHAR(255);
ALTER TABLE etfs ADD COLUMN etfcom_segment VARCHAR(255);
ALTER TABLE etfs ADD COLUMN etfcom_description TEXT;

-- ========================================
-- Enrichment data: Portfolio
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_weighted_avg_market_cap DECIMAL(20,2);
ALTER TABLE etfs ADD COLUMN etfcom_pe_ratio DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_pb_ratio DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_dividend_yield DECIMAL(18,6);

-- ========================================
-- Enrichment data: Performance (NAV)
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_return_1m_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_3m_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_ytd_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_1y_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_3y_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_5y_nav DECIMAL(18,6);

-- ========================================
-- Enrichment data: Performance (Price)
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_return_1m_price DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_3m_price DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_ytd_price DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_1y_price DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_3y_price DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN etfcom_return_5y_price DECIMAL(18,6);

-- ========================================
-- Enrichment data: Performance date
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_performance_as_of_date DATE;

-- ========================================
-- Enrichment data: Holdings metadata
-- ========================================
ALTER TABLE etfs ADD COLUMN etfcom_holdings_count INTEGER;
ALTER TABLE etfs ADD COLUMN etfcom_holdings_as_of_date DATE;

-- ========================================
-- FactSet sector allocations table
-- ========================================
CREATE TABLE etf_sector_allocations_factset (
    id BIGSERIAL PRIMARY KEY,
    etf_id BIGINT NOT NULL REFERENCES etfs(id),
    sector_name VARCHAR(100) NOT NULL,
    weight DECIMAL(18,6),
    as_of_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(etf_id, sector_name, as_of_date)
);

CREATE INDEX idx_etf_sector_factset_etf_id ON etf_sector_allocations_factset(etf_id);

-- ========================================
-- Add etf.com fields to etf_holdings
-- ========================================
ALTER TABLE etf_holdings ADD COLUMN etfcom_weight DECIMAL(18,6);
ALTER TABLE etf_holdings ADD COLUMN etfcom_last_updated_at TIMESTAMPTZ;

-- Add etf.com fields to mutual_fund_holdings (for consistency)
ALTER TABLE mutual_fund_holdings ADD COLUMN etfcom_weight DECIMAL(18,6);
ALTER TABLE mutual_fund_holdings ADD COLUMN etfcom_last_updated_at TIMESTAMPTZ;

-- ========================================
-- Update data_source constraint to include ETF_COM
-- ========================================
-- Drop existing constraint if it exists (safe approach)
DO $$
BEGIN
    ALTER TABLE etf_holdings DROP CONSTRAINT IF EXISTS etf_holdings_data_source_check;
    ALTER TABLE mutual_fund_holdings DROP CONSTRAINT IF EXISTS mutual_fund_holdings_data_source_check;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

ALTER TABLE etf_holdings ADD CONSTRAINT etf_holdings_data_source_check
    CHECK (data_source IN ('EODHD', 'ALPHA_VANTAGE', 'MANUAL', 'ETF_COM'));

ALTER TABLE mutual_fund_holdings ADD CONSTRAINT mutual_fund_holdings_data_source_check
    CHECK (data_source IN ('EODHD', 'ALPHA_VANTAGE', 'MANUAL', 'ETF_COM'));

-- ========================================
-- Update ingestion_steps step_name constraint
-- ========================================
DO $$
BEGIN
    ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS ingestion_steps_step_name_check;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

ALTER TABLE ingestion_steps ADD CONSTRAINT ingestion_steps_step_name_check
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        'AV_STOCK_INGESTION', 'AV_ETF_INGESTION',
        'AV_STOCK_ENRICHMENT', 'AV_ETF_ENRICHMENT',
        'ETFCOM_ETF_UNIVERSE', 'ETFCOM_ETF_ENRICHMENT'
    ));

-- ========================================
-- Indexes on enrichment status
-- ========================================
CREATE INDEX idx_etfs_etfcom_enrichment_status ON etfs(etfcom_enrichment_status);
CREATE INDEX idx_etfs_etfcom_fund_id ON etfs(etfcom_fund_id);

-- ========================================
-- Mark existing ETFs as PENDING for etf.com enrichment
-- ========================================
UPDATE etfs SET etfcom_enrichment_status = 'PENDING' WHERE etfcom_enrichment_status IS NULL;

-- NOTE: AV ETF columns are deprecated but NOT dropped. Clean up in a future migration.
