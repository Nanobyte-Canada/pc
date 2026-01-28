-- Add missing Alpha Vantage OVERVIEW fields to stocks table

-- Valuation ratios
ALTER TABLE stocks ADD COLUMN av_price_to_sales_ratio_ttm DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_price_to_book_ratio DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_ev_to_revenue DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_ev_to_ebitda DECIMAL(12,4);
ALTER TABLE stocks ADD COLUMN av_diluted_eps_ttm DECIMAL(12,4);

-- Shares & Ownership
ALTER TABLE stocks ADD COLUMN av_shares_float BIGINT;
ALTER TABLE stocks ADD COLUMN av_percent_insiders DECIMAL(10,6);
ALTER TABLE stocks ADD COLUMN av_percent_institutions DECIMAL(10,6);

-- Comments for documentation
COMMENT ON COLUMN stocks.av_price_to_sales_ratio_ttm IS 'Price to Sales ratio (trailing twelve months)';
COMMENT ON COLUMN stocks.av_price_to_book_ratio IS 'Price to Book ratio';
COMMENT ON COLUMN stocks.av_ev_to_revenue IS 'Enterprise Value to Revenue ratio';
COMMENT ON COLUMN stocks.av_ev_to_ebitda IS 'Enterprise Value to EBITDA ratio';
COMMENT ON COLUMN stocks.av_diluted_eps_ttm IS 'Diluted Earnings Per Share (trailing twelve months)';
COMMENT ON COLUMN stocks.av_shares_float IS 'Number of shares available for public trading';
COMMENT ON COLUMN stocks.av_percent_insiders IS 'Percentage of shares held by insiders (0.12 = 12%)';
COMMENT ON COLUMN stocks.av_percent_institutions IS 'Percentage of shares held by institutions (0.64 = 64%)';
