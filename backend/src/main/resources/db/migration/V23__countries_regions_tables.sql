-- Create regions and countries reference tables

-- Regions table
CREATE TABLE regions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE regions IS 'Geographic regions for portfolio analysis grouping';
COMMENT ON COLUMN regions.code IS 'Short code for the region (e.g., NA, EU, APAC)';
COMMENT ON COLUMN regions.name IS 'Full name of the region';

-- Countries table with FK to regions
CREATE TABLE countries (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(3) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    region_id BIGINT NOT NULL REFERENCES regions(id),
    alpha2_code VARCHAR(2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_countries_region_id ON countries(region_id);
CREATE INDEX idx_countries_code ON countries(code);

COMMENT ON TABLE countries IS 'Countries reference table for geographic exposure analysis';
COMMENT ON COLUMN countries.code IS 'ISO 3166-1 alpha-3 country code';
COMMENT ON COLUMN countries.alpha2_code IS 'ISO 3166-1 alpha-2 country code';
COMMENT ON COLUMN countries.region_id IS 'Reference to parent region';

-- Seed regions (5 main regions)
INSERT INTO regions (code, name) VALUES
    ('NA', 'North America'),
    ('EU', 'Europe'),
    ('APAC', 'Asia Pacific'),
    ('LATAM', 'Latin America'),
    ('MEA', 'Middle East & Africa'),
    ('OTHER', 'Other');

-- Seed countries
-- North America
INSERT INTO countries (code, name, alpha2_code, region_id) VALUES
    ('USA', 'United States', 'US', (SELECT id FROM regions WHERE code = 'NA')),
    ('CAN', 'Canada', 'CA', (SELECT id FROM regions WHERE code = 'NA'));

-- Europe
INSERT INTO countries (code, name, alpha2_code, region_id) VALUES
    ('GBR', 'United Kingdom', 'GB', (SELECT id FROM regions WHERE code = 'EU')),
    ('DEU', 'Germany', 'DE', (SELECT id FROM regions WHERE code = 'EU')),
    ('FRA', 'France', 'FR', (SELECT id FROM regions WHERE code = 'EU')),
    ('CHE', 'Switzerland', 'CH', (SELECT id FROM regions WHERE code = 'EU')),
    ('NLD', 'Netherlands', 'NL', (SELECT id FROM regions WHERE code = 'EU')),
    ('ITA', 'Italy', 'IT', (SELECT id FROM regions WHERE code = 'EU')),
    ('ESP', 'Spain', 'ES', (SELECT id FROM regions WHERE code = 'EU')),
    ('SWE', 'Sweden', 'SE', (SELECT id FROM regions WHERE code = 'EU')),
    ('NOR', 'Norway', 'NO', (SELECT id FROM regions WHERE code = 'EU')),
    ('DNK', 'Denmark', 'DK', (SELECT id FROM regions WHERE code = 'EU')),
    ('FIN', 'Finland', 'FI', (SELECT id FROM regions WHERE code = 'EU')),
    ('BEL', 'Belgium', 'BE', (SELECT id FROM regions WHERE code = 'EU')),
    ('AUT', 'Austria', 'AT', (SELECT id FROM regions WHERE code = 'EU')),
    ('IRL', 'Ireland', 'IE', (SELECT id FROM regions WHERE code = 'EU')),
    ('PRT', 'Portugal', 'PT', (SELECT id FROM regions WHERE code = 'EU')),
    ('POL', 'Poland', 'PL', (SELECT id FROM regions WHERE code = 'EU')),
    ('GRC', 'Greece', 'GR', (SELECT id FROM regions WHERE code = 'EU')),
    ('CZE', 'Czech Republic', 'CZ', (SELECT id FROM regions WHERE code = 'EU')),
    ('HUN', 'Hungary', 'HU', (SELECT id FROM regions WHERE code = 'EU')),
    ('RUS', 'Russia', 'RU', (SELECT id FROM regions WHERE code = 'EU')),
    ('LUX', 'Luxembourg', 'LU', (SELECT id FROM regions WHERE code = 'EU'));

-- Asia Pacific
INSERT INTO countries (code, name, alpha2_code, region_id) VALUES
    ('CHN', 'China', 'CN', (SELECT id FROM regions WHERE code = 'APAC')),
    ('JPN', 'Japan', 'JP', (SELECT id FROM regions WHERE code = 'APAC')),
    ('KOR', 'South Korea', 'KR', (SELECT id FROM regions WHERE code = 'APAC')),
    ('TWN', 'Taiwan', 'TW', (SELECT id FROM regions WHERE code = 'APAC')),
    ('IND', 'India', 'IN', (SELECT id FROM regions WHERE code = 'APAC')),
    ('AUS', 'Australia', 'AU', (SELECT id FROM regions WHERE code = 'APAC')),
    ('HKG', 'Hong Kong', 'HK', (SELECT id FROM regions WHERE code = 'APAC')),
    ('SGP', 'Singapore', 'SG', (SELECT id FROM regions WHERE code = 'APAC')),
    ('NZL', 'New Zealand', 'NZ', (SELECT id FROM regions WHERE code = 'APAC')),
    ('THA', 'Thailand', 'TH', (SELECT id FROM regions WHERE code = 'APAC')),
    ('MYS', 'Malaysia', 'MY', (SELECT id FROM regions WHERE code = 'APAC')),
    ('IDN', 'Indonesia', 'ID', (SELECT id FROM regions WHERE code = 'APAC')),
    ('PHL', 'Philippines', 'PH', (SELECT id FROM regions WHERE code = 'APAC')),
    ('VNM', 'Vietnam', 'VN', (SELECT id FROM regions WHERE code = 'APAC')),
    ('PAK', 'Pakistan', 'PK', (SELECT id FROM regions WHERE code = 'APAC'));

-- Latin America
INSERT INTO countries (code, name, alpha2_code, region_id) VALUES
    ('BRA', 'Brazil', 'BR', (SELECT id FROM regions WHERE code = 'LATAM')),
    ('MEX', 'Mexico', 'MX', (SELECT id FROM regions WHERE code = 'LATAM')),
    ('ARG', 'Argentina', 'AR', (SELECT id FROM regions WHERE code = 'LATAM')),
    ('CHL', 'Chile', 'CL', (SELECT id FROM regions WHERE code = 'LATAM')),
    ('COL', 'Colombia', 'CO', (SELECT id FROM regions WHERE code = 'LATAM')),
    ('PER', 'Peru', 'PE', (SELECT id FROM regions WHERE code = 'LATAM'));

-- Middle East & Africa
INSERT INTO countries (code, name, alpha2_code, region_id) VALUES
    ('ZAF', 'South Africa', 'ZA', (SELECT id FROM regions WHERE code = 'MEA')),
    ('ISR', 'Israel', 'IL', (SELECT id FROM regions WHERE code = 'MEA')),
    ('SAU', 'Saudi Arabia', 'SA', (SELECT id FROM regions WHERE code = 'MEA')),
    ('ARE', 'United Arab Emirates', 'AE', (SELECT id FROM regions WHERE code = 'MEA')),
    ('QAT', 'Qatar', 'QA', (SELECT id FROM regions WHERE code = 'MEA')),
    ('KWT', 'Kuwait', 'KW', (SELECT id FROM regions WHERE code = 'MEA')),
    ('EGY', 'Egypt', 'EG', (SELECT id FROM regions WHERE code = 'MEA')),
    ('NGA', 'Nigeria', 'NG', (SELECT id FROM regions WHERE code = 'MEA')),
    ('TUR', 'Turkey', 'TR', (SELECT id FROM regions WHERE code = 'MEA'));
