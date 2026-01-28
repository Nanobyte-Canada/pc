-- V12__remove_figi_and_holdings_ingestion.sql
-- Remove OpenFIGI and Holdings ingestion infrastructure
-- Simplifies ingestion to only EODHD Universe step

-- Drop instrument_identifiers table (used only by OpenFIGI enrichment)
DROP TABLE IF EXISTS instrument_identifiers;

-- Update ingestion_steps constraint to only allow EODHD_UNIVERSE
ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN ('EODHD_UNIVERSE'));
