-- Add GICS sector and industry group columns to stocks for direct sector mapping.
-- These replace the dropped FK relationship from V55.
ALTER TABLE stocks ADD COLUMN gics_sector_code VARCHAR(10);
ALTER TABLE stocks ADD COLUMN gics_industry_group_code VARCHAR(10);

-- Backfill from Alpha Vantage raw payload where available.
-- The av_raw_payload JSONB contains "Sector" and "Industry" fields.
-- Mapping is done at the application level via LookThroughService.FACTSET_SECTOR_TO_GICS.
