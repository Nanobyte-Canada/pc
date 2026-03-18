-- V52: Truncate all ingestion data to start fresh under the new pipeline design.
-- Uses a DO block because TRUNCATE has no IF EXISTS clause in PostgreSQL.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'etf_holdings',
        'etf_sector_allocation_factset',
        'etfs',
        'stocks',
        'ingestion_errors',
        'ingestion_steps',
        'ingestion_runs'
    ]
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = t
        ) THEN
            EXECUTE 'TRUNCATE TABLE public.' || quote_ident(t) || ' CASCADE';
        END IF;
    END LOOP;
END $$;
