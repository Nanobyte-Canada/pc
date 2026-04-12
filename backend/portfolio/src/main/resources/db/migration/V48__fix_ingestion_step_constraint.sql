-- V48: Remove deprecated AV_ETF_INGESTION and AV_ETF_ENRICHMENT from step_name constraint
-- Delete deprecated step rows first (ingestion_errors cascade via ON DELETE CASCADE FK)
DELETE FROM ingestion_steps
WHERE step_name IN ('AV_ETF_INGESTION', 'AV_ETF_ENRICHMENT');

ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        'AV_STOCK_INGESTION',
        'AV_STOCK_ENRICHMENT',
        'ETFCOM_ETF_UNIVERSE',
        'ETFCOM_ETF_ENRICHMENT'
    ));
