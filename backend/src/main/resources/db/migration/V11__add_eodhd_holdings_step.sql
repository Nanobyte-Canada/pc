-- V11__add_eodhd_holdings_step.sql
-- Add EODHD_HOLDINGS to the step_name constraint
-- This replaces the deprecated FMP_HOLDINGS step with EODHD-based holdings fetching

-- Drop the existing constraint
ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;

-- Re-add with the new step name included
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN ('EODHD_UNIVERSE', 'OPENFIGI_ENRICH', 'FMP_HOLDINGS', 'EODHD_HOLDINGS'));
