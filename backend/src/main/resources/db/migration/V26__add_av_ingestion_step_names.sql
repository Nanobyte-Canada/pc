-- Add AV_STOCK_INGESTION and AV_ETF_INGESTION to the step_name constraint
-- These are the new ingestion steps that fetch raw data from Alpha Vantage API

-- Drop the existing constraint
DO $$
BEGIN
    ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
EXCEPTION
    WHEN undefined_object THEN NULL;
END $$;

-- Add updated constraint with new ingestion step names
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        -- Alpha Vantage ingestion steps (fetch raw data from API)
        'AV_STOCK_INGESTION',
        'AV_ETF_INGESTION',
        -- Alpha Vantage enrichment steps (parse raw payload to entity fields)
        'AV_STOCK_ENRICHMENT',
        'AV_ETF_ENRICHMENT'
    ));

-- Add comments for documentation
COMMENT ON CONSTRAINT chk_step_name ON ingestion_steps IS
    'Valid step names: EODHD_UNIVERSE, AV_STOCK_INGESTION, AV_ETF_INGESTION, AV_STOCK_ENRICHMENT, AV_ETF_ENRICHMENT';
