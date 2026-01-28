-- Alpha Vantage Enrichment Schema
-- Replaces SeekingAlpha enrichment with Alpha Vantage API data

-- ============================================================================
-- STOCKS TABLE: Alpha Vantage OVERVIEW endpoint fields
-- ============================================================================

-- Enrichment tracking columns
ALTER TABLE stocks ADD COLUMN av_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE stocks ADD COLUMN av_last_attempt_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN av_last_success_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN av_error_code VARCHAR(20);
ALTER TABLE stocks ADD COLUMN av_error_message TEXT;
ALTER TABLE stocks ADD COLUMN av_retry_count INTEGER DEFAULT 0;
ALTER TABLE stocks ADD COLUMN av_raw_payload JSONB;

-- Basic company info from OVERVIEW endpoint
ALTER TABLE stocks ADD COLUMN av_asset_type VARCHAR(50);
ALTER TABLE stocks ADD COLUMN av_description TEXT;
ALTER TABLE stocks ADD COLUMN av_cik VARCHAR(20);
ALTER TABLE stocks ADD COLUMN av_sector VARCHAR(100);
ALTER TABLE stocks ADD COLUMN av_industry VARCHAR(150);
ALTER TABLE stocks ADD COLUMN av_address VARCHAR(255);
ALTER TABLE stocks ADD COLUMN av_official_site VARCHAR(255);
ALTER TABLE stocks ADD COLUMN av_fiscal_year_end VARCHAR(20);
ALTER TABLE stocks ADD COLUMN av_latest_quarter DATE;

-- Financial metrics
ALTER TABLE stocks ADD COLUMN av_market_cap DECIMAL(20,2);
ALTER TABLE stocks ADD COLUMN av_ebitda DECIMAL(20,2);
ALTER TABLE stocks ADD COLUMN av_pe_ratio DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_peg_ratio DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_book_value DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_dividend_per_share DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_dividend_yield DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_eps DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_revenue_per_share_ttm DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_profit_margin DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_operating_margin_ttm DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_return_on_assets_ttm DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_return_on_equity_ttm DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_revenue_ttm DECIMAL(20,2);
ALTER TABLE stocks ADD COLUMN av_gross_profit_ttm DECIMAL(20,2);
ALTER TABLE stocks ADD COLUMN av_quarterly_earnings_growth_yoy DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_quarterly_revenue_growth_yoy DECIMAL(10,6);

-- Analyst ratings
ALTER TABLE stocks ADD COLUMN av_analyst_target_price DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_analyst_rating_strong_buy INTEGER;
ALTER TABLE stocks ADD COLUMN av_analyst_rating_buy INTEGER;
ALTER TABLE stocks ADD COLUMN av_analyst_rating_hold INTEGER;
ALTER TABLE stocks ADD COLUMN av_analyst_rating_sell INTEGER;
ALTER TABLE stocks ADD COLUMN av_analyst_rating_strong_sell INTEGER;

-- Price metrics
ALTER TABLE stocks ADD COLUMN av_trailing_pe DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_forward_pe DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_52_week_high DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_52_week_low DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_50_day_ma DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_200_day_ma DECIMAL(15,4);
ALTER TABLE stocks ADD COLUMN av_shares_outstanding BIGINT;
ALTER TABLE stocks ADD COLUMN av_beta DECIMAL(10,4);

-- Dividend dates
ALTER TABLE stocks ADD COLUMN av_dividend_date DATE;
ALTER TABLE stocks ADD COLUMN av_ex_dividend_date DATE;

-- Indexes for efficient enrichment queries
CREATE INDEX idx_stocks_av_status_pending ON stocks(av_enrichment_status)
    WHERE av_enrichment_status IN ('PENDING', 'FAILED_RETRYABLE', 'STALE');
CREATE INDEX idx_stocks_av_retry ON stocks(av_retry_count, av_last_attempt_at)
    WHERE av_enrichment_status = 'FAILED_RETRYABLE';
CREATE INDEX idx_stocks_av_sector ON stocks(av_sector) WHERE av_sector IS NOT NULL;
CREATE INDEX idx_stocks_av_industry ON stocks(av_industry) WHERE av_industry IS NOT NULL;

-- ============================================================================
-- ETFS TABLE: Alpha Vantage ETF_PROFILE endpoint fields
-- ============================================================================

-- Enrichment tracking columns
ALTER TABLE etfs ADD COLUMN av_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE etfs ADD COLUMN av_last_attempt_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN av_last_success_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN av_error_code VARCHAR(20);
ALTER TABLE etfs ADD COLUMN av_error_message TEXT;
ALTER TABLE etfs ADD COLUMN av_retry_count INTEGER DEFAULT 0;
ALTER TABLE etfs ADD COLUMN av_raw_payload JSONB;

-- Basic ETF profile fields
ALTER TABLE etfs ADD COLUMN av_asset_type VARCHAR(50);
ALTER TABLE etfs ADD COLUMN av_description TEXT;
ALTER TABLE etfs ADD COLUMN av_net_assets DECIMAL(20,2);
ALTER TABLE etfs ADD COLUMN av_net_expense_ratio DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_portfolio_turnover DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_dividend_yield DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_inception_date DATE;
ALTER TABLE etfs ADD COLUMN av_is_leveraged BOOLEAN DEFAULT FALSE;
ALTER TABLE etfs ADD COLUMN av_holdings_count INTEGER;
ALTER TABLE etfs ADD COLUMN av_holdings_as_of_date DATE;

-- Sector allocation (11 GICS sectors stored as percentages 0.0 to 1.0)
ALTER TABLE etfs ADD COLUMN av_sector_info_tech DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_comm_services DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_consumer_disc DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_consumer_staples DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_healthcare DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_industrials DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_utilities DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_materials DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_energy DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_financials DECIMAL(10,6);
ALTER TABLE etfs ADD COLUMN av_sector_real_estate DECIMAL(10,6);

-- Indexes for ETF enrichment queries
CREATE INDEX idx_etfs_av_status_pending ON etfs(av_enrichment_status)
    WHERE av_enrichment_status IN ('PENDING', 'FAILED_RETRYABLE', 'STALE');
CREATE INDEX idx_etfs_av_retry ON etfs(av_retry_count, av_last_attempt_at)
    WHERE av_enrichment_status = 'FAILED_RETRYABLE';

-- ============================================================================
-- ETF_HOLDINGS TABLE: Add source tracking for Alpha Vantage holdings
-- ============================================================================

-- Add data source column to track where holdings came from
ALTER TABLE etf_holdings ADD COLUMN data_source VARCHAR(20) DEFAULT 'EODHD';
-- Possible values: 'EODHD', 'ALPHA_VANTAGE', 'MANUAL'

-- Add Alpha Vantage specific weight (may differ from EODHD)
ALTER TABLE etf_holdings ADD COLUMN av_weight DECIMAL(10,6);

-- Track when AV data was last updated
ALTER TABLE etf_holdings ADD COLUMN av_last_updated_at TIMESTAMPTZ;

-- Index for source-based queries
CREATE INDEX idx_etf_holdings_data_source ON etf_holdings(etf_id, data_source, as_of_date);

-- ============================================================================
-- INGESTION_STEPS TABLE: Add new Alpha Vantage step names
-- ============================================================================

-- Update the check constraint to include new step names
-- First drop the existing constraint if it exists
DO $$
BEGIN
    ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
EXCEPTION
    WHEN undefined_object THEN NULL;
END $$;

-- Add updated constraint with both SA (deprecated) and AV (new) step names
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        -- SeekingAlpha steps (deprecated, kept for historical data)
        'SA_STOCK_ENRICHMENT',
        'SA_ETF_ENRICHMENT',
        'SA_MUTUAL_FUND_ENRICHMENT',
        -- Alpha Vantage steps (new)
        'AV_STOCK_ENRICHMENT',
        'AV_ETF_ENRICHMENT'
    ));

-- ============================================================================
-- COMMENTS for documentation
-- ============================================================================

COMMENT ON COLUMN stocks.av_enrichment_status IS 'Alpha Vantage enrichment status: PENDING, SUCCESS, FAILED_RETRYABLE, FAILED_PERMANENT, STALE';
COMMENT ON COLUMN stocks.av_sector IS 'GICS sector from Alpha Vantage OVERVIEW endpoint';
COMMENT ON COLUMN stocks.av_industry IS 'Industry classification from Alpha Vantage OVERVIEW endpoint';
COMMENT ON COLUMN stocks.av_market_cap IS 'Market capitalization in USD';
COMMENT ON COLUMN stocks.av_pe_ratio IS 'Price-to-Earnings ratio';
COMMENT ON COLUMN stocks.av_dividend_yield IS 'Annual dividend yield as decimal (0.05 = 5%)';

COMMENT ON COLUMN etfs.av_enrichment_status IS 'Alpha Vantage enrichment status: PENDING, SUCCESS, FAILED_RETRYABLE, FAILED_PERMANENT, STALE';
COMMENT ON COLUMN etfs.av_net_assets IS 'Total net assets under management in USD';
COMMENT ON COLUMN etfs.av_net_expense_ratio IS 'Net expense ratio as decimal (0.002 = 0.2%)';
COMMENT ON COLUMN etfs.av_sector_info_tech IS 'Information Technology sector allocation (0.0 to 1.0)';

COMMENT ON COLUMN etf_holdings.data_source IS 'Source of holdings data: EODHD, ALPHA_VANTAGE, or MANUAL';
