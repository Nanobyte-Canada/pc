-- V68: Drop legacy screener tables and old reference data schema
--
-- This migration removes all tables from the old screener implementation that relied on
-- the main backend schema. The portfolio app now uses data from the ingestion-service
-- (ingestion schema) via cross-schema queries.
--
-- Tables being dropped:
-- - stocks, etfs, etf_holdings, etf_sector_allocations_factset (old instrument data)
-- - GICS reference hierarchy (gics_sectors through gics_sub_industries + aliases)
-- - Geographic reference (regions, countries)
-- - Old ingestion tracking tables in public schema (ingestion_runs/steps/errors/batches/data_sources)
--
-- NOTE: This does NOT affect the ingestion schema managed by ingestion-service.
-- The ingestion.instruments, ingestion.exchanges, etc. tables remain intact.

-- Drop FK-dependent tables first
DROP TABLE IF EXISTS etf_holdings CASCADE;
DROP TABLE IF EXISTS etf_sector_allocations_factset CASCADE;

-- Drop main instrument tables
DROP TABLE IF EXISTS stocks CASCADE;
DROP TABLE IF EXISTS etfs CASCADE;

-- Drop GICS reference tables (leaf to root order)
DROP TABLE IF EXISTS gics_sub_industry_aliases CASCADE;
DROP TABLE IF EXISTS gics_sector_aliases CASCADE;
DROP TABLE IF EXISTS gics_sub_industries CASCADE;
DROP TABLE IF EXISTS gics_industries CASCADE;
DROP TABLE IF EXISTS gics_industry_groups CASCADE;
DROP TABLE IF EXISTS gics_sectors CASCADE;

-- Drop geographic reference tables
DROP TABLE IF EXISTS countries CASCADE;
DROP TABLE IF EXISTS regions CASCADE;

-- Drop legacy ingestion tracking tables (main backend copies in public schema)
-- The ingestion-service has its own copies in the ingestion schema which are NOT affected
DROP TABLE IF EXISTS ingestion_errors CASCADE;
DROP TABLE IF EXISTS ingestion_steps CASCADE;
DROP TABLE IF EXISTS ingestion_runs CASCADE;
DROP TABLE IF EXISTS ingestion_batches CASCADE;
DROP TABLE IF EXISTS data_sources CASCADE;
