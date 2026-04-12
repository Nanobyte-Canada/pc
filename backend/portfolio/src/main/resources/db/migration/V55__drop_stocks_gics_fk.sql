-- V55: Drop gics_sub_industry_id FK from stocks (GICS resolution removed from enrichment).
ALTER TABLE stocks DROP COLUMN IF EXISTS gics_sub_industry_id;
