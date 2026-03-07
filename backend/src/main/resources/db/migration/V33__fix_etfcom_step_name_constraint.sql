-- Fix: V32 dropped wrong constraint name, leaving old chk_step_name in place
-- This migration consolidates to a single constraint with all valid step names

ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS ingestion_steps_step_name_check;

ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        'AV_STOCK_INGESTION', 'AV_ETF_INGESTION',
        'AV_STOCK_ENRICHMENT', 'AV_ETF_ENRICHMENT',
        'ETFCOM_ETF_UNIVERSE', 'ETFCOM_ETF_ENRICHMENT'
    ));
