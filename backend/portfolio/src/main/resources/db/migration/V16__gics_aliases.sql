-- V16__gics_aliases.sql
-- Create alias tables for mapping external data source codes to GICS hierarchy

CREATE TABLE gics_sector_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias_value VARCHAR(100) NOT NULL,   -- SA sector ID like "45" or name like "Technology"
    gics_sector_id BIGINT NOT NULL REFERENCES gics_sectors(id) ON DELETE CASCADE,
    source VARCHAR(30) NOT NULL DEFAULT 'SEEKING_ALPHA',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_sector_alias UNIQUE (alias_value, source)
);

CREATE TABLE gics_sub_industry_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias_code VARCHAR(20) NOT NULL,     -- SA sub-industry code like "45301020"
    alias_name VARCHAR(150),             -- SA sub-industry name like "Semiconductors"
    gics_sub_industry_id BIGINT NOT NULL REFERENCES gics_sub_industries(id) ON DELETE CASCADE,
    source VARCHAR(30) NOT NULL DEFAULT 'SEEKING_ALPHA',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_sub_industry_alias UNIQUE (alias_code, source)
);

CREATE INDEX idx_sector_alias_lower ON gics_sector_aliases(LOWER(alias_value));
CREATE INDEX idx_sector_alias_source ON gics_sector_aliases(source);
CREATE INDEX idx_sub_industry_alias_code ON gics_sub_industry_aliases(alias_code);
CREATE INDEX idx_sub_industry_alias_source ON gics_sub_industry_aliases(source);

COMMENT ON TABLE gics_sector_aliases IS 'Aliases for mapping external sector codes/names to GICS sectors';
COMMENT ON TABLE gics_sub_industry_aliases IS 'Aliases for mapping external sub-industry codes to GICS sub-industries';

-- Seed SA sector code aliases (SA uses 2-digit GICS sector codes)
INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT code, id, 'SEEKING_ALPHA' FROM gics_sectors;

-- Seed SA sector name aliases for common variations
INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Technology', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '45'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Information Technology', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '45'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Healthcare', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '35'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Health Care', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '35'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Financials', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '40'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Financial Services', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '40'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Discretionary', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '25'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Cyclical', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '25'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Staples', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '30'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Defensive', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '30'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Communication Services', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '50'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Communication', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '50'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Industrials', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '20'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Materials', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '15'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Basic Materials', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '15'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Energy', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '10'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Utilities', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '55'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Real Estate', id, 'SEEKING_ALPHA' FROM gics_sectors WHERE code = '60'
ON CONFLICT (alias_value, source) DO NOTHING;

-- Seed SA sub-industry code aliases (SA uses 8-digit GICS sub-industry codes)
INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT code, name, id, 'SEEKING_ALPHA' FROM gics_sub_industries;
