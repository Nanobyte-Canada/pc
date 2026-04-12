-- V54: Drop parsed Alpha Vantage enrichment columns from stocks table.
-- Raw data is kept in av_raw_payload (JSONB) and parsed on-demand.
-- Mirrors V53 approach used for ETFs.

ALTER TABLE stocks
    -- Enrichment tracking (no longer needed)
    DROP COLUMN IF EXISTS av_enrichment_status,
    DROP COLUMN IF EXISTS av_last_attempt_at,
    DROP COLUMN IF EXISTS av_last_success_at,
    DROP COLUMN IF EXISTS av_error_code,
    DROP COLUMN IF EXISTS av_error_message,
    DROP COLUMN IF EXISTS av_retry_count,
    -- Company info
    DROP COLUMN IF EXISTS av_asset_type,
    DROP COLUMN IF EXISTS av_description,
    DROP COLUMN IF EXISTS av_cik,
    DROP COLUMN IF EXISTS av_sector,
    DROP COLUMN IF EXISTS av_industry,
    DROP COLUMN IF EXISTS av_address,
    DROP COLUMN IF EXISTS av_official_site,
    DROP COLUMN IF EXISTS av_fiscal_year_end,
    DROP COLUMN IF EXISTS av_latest_quarter,
    -- Financial metrics
    DROP COLUMN IF EXISTS av_market_cap,
    DROP COLUMN IF EXISTS av_ebitda,
    DROP COLUMN IF EXISTS av_pe_ratio,
    DROP COLUMN IF EXISTS av_peg_ratio,
    DROP COLUMN IF EXISTS av_book_value,
    DROP COLUMN IF EXISTS av_dividend_per_share,
    DROP COLUMN IF EXISTS av_dividend_yield,
    DROP COLUMN IF EXISTS av_eps,
    DROP COLUMN IF EXISTS av_revenue_per_share_ttm,
    DROP COLUMN IF EXISTS av_profit_margin,
    DROP COLUMN IF EXISTS av_operating_margin_ttm,
    DROP COLUMN IF EXISTS av_return_on_assets_ttm,
    DROP COLUMN IF EXISTS av_return_on_equity_ttm,
    DROP COLUMN IF EXISTS av_revenue_ttm,
    DROP COLUMN IF EXISTS av_gross_profit_ttm,
    DROP COLUMN IF EXISTS av_quarterly_earnings_growth_yoy,
    DROP COLUMN IF EXISTS av_quarterly_revenue_growth_yoy,
    -- Analyst ratings
    DROP COLUMN IF EXISTS av_analyst_target_price,
    DROP COLUMN IF EXISTS av_analyst_rating_strong_buy,
    DROP COLUMN IF EXISTS av_analyst_rating_buy,
    DROP COLUMN IF EXISTS av_analyst_rating_hold,
    DROP COLUMN IF EXISTS av_analyst_rating_sell,
    DROP COLUMN IF EXISTS av_analyst_rating_strong_sell,
    -- Price metrics
    DROP COLUMN IF EXISTS av_trailing_pe,
    DROP COLUMN IF EXISTS av_forward_pe,
    DROP COLUMN IF EXISTS av_52_week_high,
    DROP COLUMN IF EXISTS av_52_week_low,
    DROP COLUMN IF EXISTS av_50_day_ma,
    DROP COLUMN IF EXISTS av_200_day_ma,
    DROP COLUMN IF EXISTS av_shares_outstanding,
    DROP COLUMN IF EXISTS av_beta,
    -- Valuation ratios
    DROP COLUMN IF EXISTS av_price_to_sales_ratio_ttm,
    DROP COLUMN IF EXISTS av_price_to_book_ratio,
    DROP COLUMN IF EXISTS av_ev_to_revenue,
    DROP COLUMN IF EXISTS av_ev_to_ebitda,
    DROP COLUMN IF EXISTS av_diluted_eps_ttm,
    -- Shares & ownership
    DROP COLUMN IF EXISTS av_shares_float,
    DROP COLUMN IF EXISTS av_percent_insiders,
    DROP COLUMN IF EXISTS av_percent_institutions,
    -- Dividend dates
    DROP COLUMN IF EXISTS av_dividend_date,
    DROP COLUMN IF EXISTS av_ex_dividend_date;
