-- V21: Drop SeekingAlpha enrichment columns
-- SeekingAlpha integration has been replaced by Alpha Vantage

-- Drop SeekingAlpha enrichment columns from stocks table
ALTER TABLE stocks
    DROP COLUMN IF EXISTS sa_enrichment_status,
    DROP COLUMN IF EXISTS sa_last_attempt_at,
    DROP COLUMN IF EXISTS sa_last_success_at,
    DROP COLUMN IF EXISTS sa_error_code,
    DROP COLUMN IF EXISTS sa_error_message,
    DROP COLUMN IF EXISTS sa_retry_count,
    DROP COLUMN IF EXISTS sa_raw_payload,
    DROP COLUMN IF EXISTS sa_slug,
    DROP COLUMN IF EXISTS sa_company_name,
    DROP COLUMN IF EXISTS sa_followers_count,
    DROP COLUMN IF EXISTS sa_equity_type,
    DROP COLUMN IF EXISTS sa_business_description,
    DROP COLUMN IF EXISTS sa_employees,
    DROP COLUMN IF EXISTS sa_year_founded,
    DROP COLUMN IF EXISTS sa_city,
    DROP COLUMN IF EXISTS sa_state,
    DROP COLUMN IF EXISTS sa_webpage;

-- Drop SeekingAlpha enrichment columns from etfs table
ALTER TABLE etfs
    DROP COLUMN IF EXISTS sa_enrichment_status,
    DROP COLUMN IF EXISTS sa_last_attempt_at,
    DROP COLUMN IF EXISTS sa_last_success_at,
    DROP COLUMN IF EXISTS sa_error_code,
    DROP COLUMN IF EXISTS sa_error_message,
    DROP COLUMN IF EXISTS sa_retry_count,
    DROP COLUMN IF EXISTS sa_raw_symbols_payload,
    DROP COLUMN IF EXISTS sa_raw_symbol_data_payload,
    DROP COLUMN IF EXISTS sa_slug,
    DROP COLUMN IF EXISTS sa_legal_name,
    DROP COLUMN IF EXISTS sa_long_desc,
    DROP COLUMN IF EXISTS sa_benchmark,
    DROP COLUMN IF EXISTS sa_morningstar_category,
    DROP COLUMN IF EXISTS sa_investment_strategy,
    DROP COLUMN IF EXISTS sa_fund_objective,
    DROP COLUMN IF EXISTS sa_is_leveraged,
    DROP COLUMN IF EXISTS sa_is_inverse,
    DROP COLUMN IF EXISTS sa_invest_style,
    DROP COLUMN IF EXISTS sa_net_assets,
    DROP COLUMN IF EXISTS sa_month_end_nav,
    DROP COLUMN IF EXISTS sa_holdings_as_of_date,
    DROP COLUMN IF EXISTS sa_num_holdings;

-- Drop SeekingAlpha enrichment columns from mutual_funds table
ALTER TABLE mutual_funds
    DROP COLUMN IF EXISTS sa_enrichment_status,
    DROP COLUMN IF EXISTS sa_last_attempt_at,
    DROP COLUMN IF EXISTS sa_last_success_at,
    DROP COLUMN IF EXISTS sa_error_code,
    DROP COLUMN IF EXISTS sa_error_message,
    DROP COLUMN IF EXISTS sa_retry_count,
    DROP COLUMN IF EXISTS sa_raw_symbols_payload,
    DROP COLUMN IF EXISTS sa_raw_symbol_data_payload,
    DROP COLUMN IF EXISTS sa_slug,
    DROP COLUMN IF EXISTS sa_legal_name,
    DROP COLUMN IF EXISTS sa_long_desc,
    DROP COLUMN IF EXISTS sa_benchmark,
    DROP COLUMN IF EXISTS sa_morningstar_category,
    DROP COLUMN IF EXISTS sa_investment_strategy,
    DROP COLUMN IF EXISTS sa_fund_objective,
    DROP COLUMN IF EXISTS sa_is_leveraged,
    DROP COLUMN IF EXISTS sa_invest_style,
    DROP COLUMN IF EXISTS sa_net_assets,
    DROP COLUMN IF EXISTS sa_month_end_nav,
    DROP COLUMN IF EXISTS sa_holdings_as_of_date,
    DROP COLUMN IF EXISTS sa_num_holdings;

-- Drop SA enrichment status enum type if it exists and is not used elsewhere
DROP TYPE IF EXISTS sa_enrichment_status;

-- Remove SEEKING_ALPHA from data_sources if it exists
DELETE FROM data_sources WHERE name = 'SEEKING_ALPHA';

-- Delete historical SA ingestion steps (optional - preserves data integrity)
DELETE FROM ingestion_errors WHERE step_id IN (
    SELECT id FROM ingestion_steps WHERE step_name IN ('SA_STOCK_ENRICHMENT', 'SA_ETF_ENRICHMENT', 'SA_MUTUAL_FUND_ENRICHMENT')
);
DELETE FROM ingestion_steps WHERE step_name IN ('SA_STOCK_ENRICHMENT', 'SA_ETF_ENRICHMENT', 'SA_MUTUAL_FUND_ENRICHMENT');
