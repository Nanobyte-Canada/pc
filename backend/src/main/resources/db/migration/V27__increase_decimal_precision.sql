-- V27: Increase DECIMAL precision from (10,6) to (18,6) to prevent numeric overflow
-- This fixes: "ERROR: numeric field overflow - A field with precision 10, scale 6 must round to an absolute value less than 10^4"
-- Some Alpha Vantage values (e.g., YoY growth percentages) can exceed 9999.999999

-- ========================================
-- Stocks table columns
-- ========================================
ALTER TABLE stocks ALTER COLUMN av_dividend_yield TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_profit_margin TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_operating_margin_ttm TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_return_on_assets_ttm TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_return_on_equity_ttm TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_quarterly_earnings_growth_yoy TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_quarterly_revenue_growth_yoy TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_percent_insiders TYPE DECIMAL(18,6);
ALTER TABLE stocks ALTER COLUMN av_percent_institutions TYPE DECIMAL(18,6);

-- ========================================
-- ETFs table columns
-- ========================================
ALTER TABLE etfs ALTER COLUMN av_net_expense_ratio TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_portfolio_turnover TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_dividend_yield TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_info_tech TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_comm_services TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_consumer_disc TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_consumer_staples TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_healthcare TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_industrials TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_utilities TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_materials TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_energy TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_financials TYPE DECIMAL(18,6);
ALTER TABLE etfs ALTER COLUMN av_sector_real_estate TYPE DECIMAL(18,6);

-- ========================================
-- Holdings tables columns
-- ========================================
ALTER TABLE etf_holdings ALTER COLUMN av_weight TYPE DECIMAL(18,6);
ALTER TABLE mutual_fund_holdings ALTER COLUMN av_weight TYPE DECIMAL(18,6);
