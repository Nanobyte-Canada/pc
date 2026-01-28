-- Remove restrictive weight CHECK constraints
--
-- These constraints were too restrictive for real-world data:
-- 1. Weights can exceed 100% due to rounding/timing in external data sources
-- 2. Inverse/leveraged ETFs can have negative weight holdings (short positions)
--
-- Data validation should happen at the application layer if needed.

ALTER TABLE etf_holdings DROP CONSTRAINT IF EXISTS chk_etf_holdings_weight;
ALTER TABLE mutual_fund_holdings DROP CONSTRAINT IF EXISTS chk_mf_holdings_weight;
