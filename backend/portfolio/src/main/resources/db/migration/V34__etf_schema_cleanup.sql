-- V34: Clean slate for ETF data + remove EODHD/AV columns
-- etf.com has replaced EODHD for ETFs; AV enrichment is no longer used for ETFs.

-- 1. Delete all ETF-related data (holdings, sectors, then ETFs)
DELETE FROM etf_holdings;
DELETE FROM etf_sector_allocations_factset;
DELETE FROM etfs;

-- 2. Drop old composite unique constraint, add symbol-only unique
ALTER TABLE etfs DROP CONSTRAINT IF EXISTS uq_etfs_symbol_exchange;
ALTER TABLE etfs ADD CONSTRAINT uq_etfs_symbol UNIQUE (symbol);

-- 3. Drop EODHD-specific columns from etfs
ALTER TABLE etfs DROP COLUMN IF EXISTS exchange;
ALTER TABLE etfs DROP COLUMN IF EXISTS exchange_code;
ALTER TABLE etfs DROP COLUMN IF EXISTS raw_eodhd_payload;

-- 4. Drop all Alpha Vantage columns from etfs
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_status;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_last_attempt_at;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_last_success_at;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_retry_count;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_error_code;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_ingestion_error_message;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_enrichment_status;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_last_attempt_at;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_last_success_at;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_error_code;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_error_message;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_retry_count;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_raw_payload;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_asset_type;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_description;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_net_assets;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_net_expense_ratio;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_portfolio_turnover;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_dividend_yield;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_inception_date;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_is_leveraged;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_holdings_count;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_holdings_as_of_date;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_info_tech;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_comm_services;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_consumer_disc;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_consumer_staples;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_healthcare;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_industrials;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_utilities;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_materials;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_energy;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_financials;
ALTER TABLE etfs DROP COLUMN IF EXISTS av_sector_real_estate;
