-- V51: Seed GICS sector and sub-industry aliases for Alpha Vantage sector/industry strings.
-- Allows enrichment processor to resolve AV strings to GICS FK on the stocks table.

-- ============================================================
-- Sector aliases (source = 'ALPHA_VANTAGE')
-- Unique constraint: (alias_value, source)
-- ============================================================
INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Technology', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '45'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Information Technology', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '45'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Communication Services', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '50'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Cyclical', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '25'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Discretionary', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '25'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Defensive', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '30'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Consumer Staples', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '30'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Healthcare', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '35'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Health Care', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '35'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Industrials', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '20'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Basic Materials', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '15'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Materials', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '15'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Energy', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '10'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Financials', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '40'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Financial Services', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '40'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Real Estate', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '60'
ON CONFLICT (alias_value, source) DO NOTHING;

INSERT INTO gics_sector_aliases (alias_value, gics_sector_id, source)
SELECT 'Utilities', id, 'ALPHA_VANTAGE' FROM gics_sectors WHERE code = '55'
ON CONFLICT (alias_value, source) DO NOTHING;

-- ============================================================
-- Sub-industry aliases (source = 'ALPHA_VANTAGE')
-- alias_code = short internal code, alias_name = AV industry string
-- Unique constraint: (alias_code, source)
-- ============================================================

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_SOFTWARE_APP', 'Software—Application', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45103020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_SOFTWARE_INFRA', 'Software—Infrastructure', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45103020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_SEMIS', 'Semiconductors', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45301020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_SEMI_EQUIP', 'Semiconductor Equipment & Materials', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45301010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_TECH_HW', 'Computer Hardware', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45202030'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_ELECTRONICS', 'Electronic Components', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '45203010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_INTERNET_CONTENT', 'Internet Content & Information', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '50203010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_TELECOM', 'Integrated Telecommunication Services', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '50101010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_WIRELESS', 'Wireless Telecommunication Services', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '50101020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_ENTERTAINMENT', 'Movies & Entertainment', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '50202020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_INTERNET_RETAIL', 'Internet & Direct Marketing Retail', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '25502020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_AUTO_MFG', 'Automobile Manufacturers', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '25102010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_RESTAURANTS', 'Restaurants', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '25301040'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_BEVERAGES', 'Soft Drinks & Non-alcoholic Beverages', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '30201030'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_FOOD_PROC', 'Packaged Foods & Meats', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '30202030'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_TOBACCO', 'Tobacco', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '30203010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_PHARMA', 'Pharmaceuticals', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '35202010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_BIOTECH', 'Biotechnology', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '35201010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_MED_DEVICES', 'Health Care Equipment', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '35101010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_HEALTH_PLANS', 'Managed Health Care', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '35102020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_BANKS', 'Diversified Banks', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '40101010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_REGIONAL_BANKS', 'Regional Banks', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '40101015'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_INS_PROP', 'Property & Casualty Insurance', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '40301020'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_ASSET_MGMT', 'Asset Management & Custody Banks', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '40203010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_OIL_EP', 'Oil & Gas Exploration & Production', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '10102010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_OIL_INT', 'Integrated Oil & Gas', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '10101010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_AEROSPACE', 'Aerospace & Defense', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '20101010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_IND_CONGL', 'Industrial Conglomerates', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '20105010'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_REIT_RETAIL', 'Retail REITs', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '60101040'
ON CONFLICT (alias_code, source) DO NOTHING;

INSERT INTO gics_sub_industry_aliases (alias_code, alias_name, gics_sub_industry_id, source)
SELECT 'AV_REIT_OFFICE', 'Office REITs', id, 'ALPHA_VANTAGE'
FROM gics_sub_industries WHERE code = '60101030'
ON CONFLICT (alias_code, source) DO NOTHING;
