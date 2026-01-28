-- ============================================================================
-- GICS Reference Data Seed
-- Migration V3: Complete GICS hierarchy (as of March 2023 classification)
-- Source: S&P Global / MSCI GICS Structure
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SECTORS (11 total)
-- ----------------------------------------------------------------------------

INSERT INTO gics_sectors (code, name) VALUES
('10', 'Energy'),
('15', 'Materials'),
('20', 'Industrials'),
('25', 'Consumer Discretionary'),
('30', 'Consumer Staples'),
('35', 'Health Care'),
('40', 'Financials'),
('45', 'Information Technology'),
('50', 'Communication Services'),
('55', 'Utilities'),
('60', 'Real Estate');

-- ----------------------------------------------------------------------------
-- INDUSTRY GROUPS (25 total)
-- ----------------------------------------------------------------------------

-- Energy (10)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '1010', 'Energy', id FROM gics_sectors WHERE code = '10';

-- Materials (15)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '1510', 'Materials', id FROM gics_sectors WHERE code = '15';

-- Industrials (20)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2010', 'Capital Goods', id FROM gics_sectors WHERE code = '20';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2020', 'Commercial & Professional Services', id FROM gics_sectors WHERE code = '20';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2030', 'Transportation', id FROM gics_sectors WHERE code = '20';

-- Consumer Discretionary (25)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2510', 'Automobiles & Components', id FROM gics_sectors WHERE code = '25';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2520', 'Consumer Durables & Apparel', id FROM gics_sectors WHERE code = '25';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2530', 'Consumer Services', id FROM gics_sectors WHERE code = '25';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '2550', 'Consumer Discretionary Distribution & Retail', id FROM gics_sectors WHERE code = '25';

-- Consumer Staples (30)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '3010', 'Consumer Staples Distribution & Retail', id FROM gics_sectors WHERE code = '30';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '3020', 'Food, Beverage & Tobacco', id FROM gics_sectors WHERE code = '30';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '3030', 'Household & Personal Products', id FROM gics_sectors WHERE code = '30';

-- Health Care (35)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '3510', 'Health Care Equipment & Services', id FROM gics_sectors WHERE code = '35';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '3520', 'Pharmaceuticals, Biotechnology & Life Sciences', id FROM gics_sectors WHERE code = '35';

-- Financials (40)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4010', 'Banks', id FROM gics_sectors WHERE code = '40';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4020', 'Financial Services', id FROM gics_sectors WHERE code = '40';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4030', 'Insurance', id FROM gics_sectors WHERE code = '40';

-- Information Technology (45)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4510', 'Software & Services', id FROM gics_sectors WHERE code = '45';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4520', 'Technology Hardware & Equipment', id FROM gics_sectors WHERE code = '45';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '4530', 'Semiconductors & Semiconductor Equipment', id FROM gics_sectors WHERE code = '45';

-- Communication Services (50)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '5010', 'Telecommunication Services', id FROM gics_sectors WHERE code = '50';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '5020', 'Media & Entertainment', id FROM gics_sectors WHERE code = '50';

-- Utilities (55)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '5510', 'Utilities', id FROM gics_sectors WHERE code = '55';

-- Real Estate (60)
INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '6010', 'Equity Real Estate Investment Trusts (REITs)', id FROM gics_sectors WHERE code = '60';

INSERT INTO gics_industry_groups (code, name, sector_id)
SELECT '6020', 'Real Estate Management & Development', id FROM gics_sectors WHERE code = '60';

-- ----------------------------------------------------------------------------
-- INDUSTRIES (74 total)
-- ----------------------------------------------------------------------------

-- Energy (1010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '101010', 'Energy Equipment & Services', id FROM gics_industry_groups WHERE code = '1010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '101020', 'Oil, Gas & Consumable Fuels', id FROM gics_industry_groups WHERE code = '1010';

-- Materials (1510)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '151010', 'Chemicals', id FROM gics_industry_groups WHERE code = '1510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '151020', 'Construction Materials', id FROM gics_industry_groups WHERE code = '1510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '151030', 'Containers & Packaging', id FROM gics_industry_groups WHERE code = '1510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '151040', 'Metals & Mining', id FROM gics_industry_groups WHERE code = '1510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '151050', 'Paper & Forest Products', id FROM gics_industry_groups WHERE code = '1510';

-- Capital Goods (2010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201010', 'Aerospace & Defense', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201020', 'Building Products', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201030', 'Construction & Engineering', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201040', 'Electrical Equipment', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201050', 'Industrial Conglomerates', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201060', 'Machinery', id FROM gics_industry_groups WHERE code = '2010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '201070', 'Trading Companies & Distributors', id FROM gics_industry_groups WHERE code = '2010';

-- Commercial & Professional Services (2020)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '202010', 'Commercial Services & Supplies', id FROM gics_industry_groups WHERE code = '2020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '202020', 'Professional Services', id FROM gics_industry_groups WHERE code = '2020';

-- Transportation (2030)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '203010', 'Air Freight & Logistics', id FROM gics_industry_groups WHERE code = '2030';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '203020', 'Passenger Airlines', id FROM gics_industry_groups WHERE code = '2030';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '203030', 'Marine Transportation', id FROM gics_industry_groups WHERE code = '2030';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '203040', 'Ground Transportation', id FROM gics_industry_groups WHERE code = '2030';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '203050', 'Transportation Infrastructure', id FROM gics_industry_groups WHERE code = '2030';

-- Automobiles & Components (2510)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '251010', 'Automobile Components', id FROM gics_industry_groups WHERE code = '2510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '251020', 'Automobiles', id FROM gics_industry_groups WHERE code = '2510';

-- Consumer Durables & Apparel (2520)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '252010', 'Household Durables', id FROM gics_industry_groups WHERE code = '2520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '252020', 'Leisure Products', id FROM gics_industry_groups WHERE code = '2520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '252030', 'Textiles, Apparel & Luxury Goods', id FROM gics_industry_groups WHERE code = '2520';

-- Consumer Services (2530)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '253010', 'Hotels, Restaurants & Leisure', id FROM gics_industry_groups WHERE code = '2530';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '253020', 'Diversified Consumer Services', id FROM gics_industry_groups WHERE code = '2530';

-- Consumer Discretionary Distribution & Retail (2550)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '255010', 'Distributors', id FROM gics_industry_groups WHERE code = '2550';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '255020', 'Internet & Direct Marketing Retail', id FROM gics_industry_groups WHERE code = '2550';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '255030', 'Broadline Retail', id FROM gics_industry_groups WHERE code = '2550';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '255040', 'Specialty Retail', id FROM gics_industry_groups WHERE code = '2550';

-- Consumer Staples Distribution & Retail (3010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '301010', 'Consumer Staples Distribution & Retail', id FROM gics_industry_groups WHERE code = '3010';

-- Food, Beverage & Tobacco (3020)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '302010', 'Beverages', id FROM gics_industry_groups WHERE code = '3020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '302020', 'Food Products', id FROM gics_industry_groups WHERE code = '3020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '302030', 'Tobacco', id FROM gics_industry_groups WHERE code = '3020';

-- Household & Personal Products (3030)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '303010', 'Household Products', id FROM gics_industry_groups WHERE code = '3030';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '303020', 'Personal Care Products', id FROM gics_industry_groups WHERE code = '3030';

-- Health Care Equipment & Services (3510)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '351010', 'Health Care Equipment & Supplies', id FROM gics_industry_groups WHERE code = '3510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '351020', 'Health Care Providers & Services', id FROM gics_industry_groups WHERE code = '3510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '351030', 'Health Care Technology', id FROM gics_industry_groups WHERE code = '3510';

-- Pharmaceuticals, Biotechnology & Life Sciences (3520)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '352010', 'Biotechnology', id FROM gics_industry_groups WHERE code = '3520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '352020', 'Pharmaceuticals', id FROM gics_industry_groups WHERE code = '3520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '352030', 'Life Sciences Tools & Services', id FROM gics_industry_groups WHERE code = '3520';

-- Banks (4010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '401010', 'Banks', id FROM gics_industry_groups WHERE code = '4010';

-- Financial Services (4020)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '402010', 'Financial Services', id FROM gics_industry_groups WHERE code = '4020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '402020', 'Consumer Finance', id FROM gics_industry_groups WHERE code = '4020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '402030', 'Capital Markets', id FROM gics_industry_groups WHERE code = '4020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '402040', 'Mortgage Real Estate Investment Trusts (REITs)', id FROM gics_industry_groups WHERE code = '4020';

-- Insurance (4030)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '403010', 'Insurance', id FROM gics_industry_groups WHERE code = '4030';

-- Software & Services (4510)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '451010', 'IT Services', id FROM gics_industry_groups WHERE code = '4510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '451020', 'Software', id FROM gics_industry_groups WHERE code = '4510';

-- Technology Hardware & Equipment (4520)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '452010', 'Communications Equipment', id FROM gics_industry_groups WHERE code = '4520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '452020', 'Technology Hardware, Storage & Peripherals', id FROM gics_industry_groups WHERE code = '4520';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '452030', 'Electronic Equipment, Instruments & Components', id FROM gics_industry_groups WHERE code = '4520';

-- Semiconductors & Semiconductor Equipment (4530)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '453010', 'Semiconductors & Semiconductor Equipment', id FROM gics_industry_groups WHERE code = '4530';

-- Telecommunication Services (5010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '501010', 'Diversified Telecommunication Services', id FROM gics_industry_groups WHERE code = '5010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '501020', 'Wireless Telecommunication Services', id FROM gics_industry_groups WHERE code = '5010';

-- Media & Entertainment (5020)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '502010', 'Media', id FROM gics_industry_groups WHERE code = '5020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '502020', 'Entertainment', id FROM gics_industry_groups WHERE code = '5020';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '502030', 'Interactive Media & Services', id FROM gics_industry_groups WHERE code = '5020';

-- Utilities (5510)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '551010', 'Electric Utilities', id FROM gics_industry_groups WHERE code = '5510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '551020', 'Gas Utilities', id FROM gics_industry_groups WHERE code = '5510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '551030', 'Multi-Utilities', id FROM gics_industry_groups WHERE code = '5510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '551040', 'Water Utilities', id FROM gics_industry_groups WHERE code = '5510';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '551050', 'Independent Power and Renewable Electricity Producers', id FROM gics_industry_groups WHERE code = '5510';

-- Equity REITs (6010)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601010', 'Diversified REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601025', 'Industrial REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601030', 'Hotel & Resort REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601040', 'Office REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601050', 'Health Care REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601060', 'Residential REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601070', 'Retail REITs', id FROM gics_industry_groups WHERE code = '6010';

INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '601080', 'Specialized REITs', id FROM gics_industry_groups WHERE code = '6010';

-- Real Estate Management & Development (6020)
INSERT INTO gics_industries (code, name, industry_group_id)
SELECT '602010', 'Real Estate Management & Development', id FROM gics_industry_groups WHERE code = '6020';

-- ----------------------------------------------------------------------------
-- SUB-INDUSTRIES (163 total)
-- ----------------------------------------------------------------------------

-- Energy Equipment & Services (101010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10101010', 'Oil & Gas Drilling', id FROM gics_industries WHERE code = '101010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10101020', 'Oil & Gas Equipment & Services', id FROM gics_industries WHERE code = '101010';

-- Oil, Gas & Consumable Fuels (101020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10102010', 'Integrated Oil & Gas', id FROM gics_industries WHERE code = '101020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10102020', 'Oil & Gas Exploration & Production', id FROM gics_industries WHERE code = '101020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10102030', 'Oil & Gas Refining & Marketing', id FROM gics_industries WHERE code = '101020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10102040', 'Oil & Gas Storage & Transportation', id FROM gics_industries WHERE code = '101020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '10102050', 'Coal & Consumable Fuels', id FROM gics_industries WHERE code = '101020';

-- Chemicals (151010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15101010', 'Commodity Chemicals', id FROM gics_industries WHERE code = '151010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15101020', 'Diversified Chemicals', id FROM gics_industries WHERE code = '151010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15101030', 'Fertilizers & Agricultural Chemicals', id FROM gics_industries WHERE code = '151010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15101040', 'Industrial Gases', id FROM gics_industries WHERE code = '151010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15101050', 'Specialty Chemicals', id FROM gics_industries WHERE code = '151010';

-- Construction Materials (151020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15102010', 'Construction Materials', id FROM gics_industries WHERE code = '151020';

-- Containers & Packaging (151030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15103010', 'Metal, Glass & Plastic Containers', id FROM gics_industries WHERE code = '151030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15103020', 'Paper & Plastic Packaging Products & Materials', id FROM gics_industries WHERE code = '151030';

-- Metals & Mining (151040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104010', 'Aluminum', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104020', 'Diversified Metals & Mining', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104025', 'Copper', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104030', 'Gold', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104040', 'Precious Metals & Minerals', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104045', 'Silver', id FROM gics_industries WHERE code = '151040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15104050', 'Steel', id FROM gics_industries WHERE code = '151040';

-- Paper & Forest Products (151050)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15105010', 'Forest Products', id FROM gics_industries WHERE code = '151050';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '15105020', 'Paper Products', id FROM gics_industries WHERE code = '151050';

-- Aerospace & Defense (201010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20101010', 'Aerospace & Defense', id FROM gics_industries WHERE code = '201010';

-- Building Products (201020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20102010', 'Building Products', id FROM gics_industries WHERE code = '201020';

-- Construction & Engineering (201030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20103010', 'Construction & Engineering', id FROM gics_industries WHERE code = '201030';

-- Electrical Equipment (201040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20104010', 'Electrical Components & Equipment', id FROM gics_industries WHERE code = '201040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20104020', 'Heavy Electrical Equipment', id FROM gics_industries WHERE code = '201040';

-- Industrial Conglomerates (201050)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20105010', 'Industrial Conglomerates', id FROM gics_industries WHERE code = '201050';

-- Machinery (201060)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20106010', 'Construction Machinery & Heavy Transportation Equipment', id FROM gics_industries WHERE code = '201060';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20106015', 'Agricultural & Farm Machinery', id FROM gics_industries WHERE code = '201060';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20106020', 'Industrial Machinery & Supplies & Components', id FROM gics_industries WHERE code = '201060';

-- Trading Companies & Distributors (201070)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20107010', 'Trading Companies & Distributors', id FROM gics_industries WHERE code = '201070';

-- Commercial Services & Supplies (202010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20201010', 'Commercial Printing', id FROM gics_industries WHERE code = '202010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20201050', 'Environmental & Facilities Services', id FROM gics_industries WHERE code = '202010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20201060', 'Office Services & Supplies', id FROM gics_industries WHERE code = '202010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20201070', 'Diversified Support Services', id FROM gics_industries WHERE code = '202010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20201080', 'Security & Alarm Services', id FROM gics_industries WHERE code = '202010';

-- Professional Services (202020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20202010', 'Human Resource & Employment Services', id FROM gics_industries WHERE code = '202020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20202020', 'Research & Consulting Services', id FROM gics_industries WHERE code = '202020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20202030', 'Data Processing & Outsourced Services', id FROM gics_industries WHERE code = '202020';

-- Air Freight & Logistics (203010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20301010', 'Air Freight & Logistics', id FROM gics_industries WHERE code = '203010';

-- Passenger Airlines (203020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20302010', 'Passenger Airlines', id FROM gics_industries WHERE code = '203020';

-- Marine Transportation (203030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20303010', 'Marine Transportation', id FROM gics_industries WHERE code = '203030';

-- Ground Transportation (203040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20304010', 'Rail Transportation', id FROM gics_industries WHERE code = '203040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20304020', 'Trucking', id FROM gics_industries WHERE code = '203040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20304030', 'Cargo Ground Transportation', id FROM gics_industries WHERE code = '203040';

-- Transportation Infrastructure (203050)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20305010', 'Airport Services', id FROM gics_industries WHERE code = '203050';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20305020', 'Highways & Railtracks', id FROM gics_industries WHERE code = '203050';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '20305030', 'Marine Ports & Services', id FROM gics_industries WHERE code = '203050';

-- Automobile Components (251010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25101010', 'Automotive Parts & Equipment', id FROM gics_industries WHERE code = '251010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25101020', 'Tires & Rubber', id FROM gics_industries WHERE code = '251010';

-- Automobiles (251020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25102010', 'Automobile Manufacturers', id FROM gics_industries WHERE code = '251020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25102020', 'Motorcycle Manufacturers', id FROM gics_industries WHERE code = '251020';

-- Household Durables (252010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25201010', 'Consumer Electronics', id FROM gics_industries WHERE code = '252010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25201020', 'Home Furnishings', id FROM gics_industries WHERE code = '252010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25201030', 'Homebuilding', id FROM gics_industries WHERE code = '252010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25201040', 'Household Appliances', id FROM gics_industries WHERE code = '252010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25201050', 'Housewares & Specialties', id FROM gics_industries WHERE code = '252010';

-- Leisure Products (252020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25202010', 'Leisure Products', id FROM gics_industries WHERE code = '252020';

-- Textiles, Apparel & Luxury Goods (252030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25203010', 'Apparel, Accessories & Luxury Goods', id FROM gics_industries WHERE code = '252030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25203020', 'Footwear', id FROM gics_industries WHERE code = '252030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25203030', 'Textiles', id FROM gics_industries WHERE code = '252030';

-- Hotels, Restaurants & Leisure (253010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25301010', 'Casinos & Gaming', id FROM gics_industries WHERE code = '253010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25301020', 'Hotels, Resorts & Cruise Lines', id FROM gics_industries WHERE code = '253010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25301030', 'Leisure Facilities', id FROM gics_industries WHERE code = '253010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25301040', 'Restaurants', id FROM gics_industries WHERE code = '253010';

-- Diversified Consumer Services (253020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25302010', 'Education Services', id FROM gics_industries WHERE code = '253020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25302020', 'Specialized Consumer Services', id FROM gics_industries WHERE code = '253020';

-- Distributors (255010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25501010', 'Distributors', id FROM gics_industries WHERE code = '255010';

-- Internet & Direct Marketing Retail (255020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25502020', 'Internet & Direct Marketing Retail', id FROM gics_industries WHERE code = '255020';

-- Broadline Retail (255030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25503010', 'Broadline Retail', id FROM gics_industries WHERE code = '255030';

-- Specialty Retail (255040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504010', 'Apparel Retail', id FROM gics_industries WHERE code = '255040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504020', 'Computer & Electronics Retail', id FROM gics_industries WHERE code = '255040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504030', 'Home Improvement Retail', id FROM gics_industries WHERE code = '255040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504040', 'Other Specialty Retail', id FROM gics_industries WHERE code = '255040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504050', 'Automotive Retail', id FROM gics_industries WHERE code = '255040';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '25504060', 'Homefurnishing Retail', id FROM gics_industries WHERE code = '255040';

-- Consumer Staples Distribution & Retail (301010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30101010', 'Drug Retail', id FROM gics_industries WHERE code = '301010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30101020', 'Food Distributors', id FROM gics_industries WHERE code = '301010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30101030', 'Food Retail', id FROM gics_industries WHERE code = '301010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30101040', 'Consumer Staples Merchandise Retail', id FROM gics_industries WHERE code = '301010';

-- Beverages (302010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30201010', 'Brewers', id FROM gics_industries WHERE code = '302010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30201020', 'Distillers & Vintners', id FROM gics_industries WHERE code = '302010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30201030', 'Soft Drinks & Non-alcoholic Beverages', id FROM gics_industries WHERE code = '302010';

-- Food Products (302020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30202010', 'Agricultural Products & Services', id FROM gics_industries WHERE code = '302020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30202030', 'Packaged Foods & Meats', id FROM gics_industries WHERE code = '302020';

-- Tobacco (302030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30203010', 'Tobacco', id FROM gics_industries WHERE code = '302030';

-- Household Products (303010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30301010', 'Household Products', id FROM gics_industries WHERE code = '303010';

-- Personal Care Products (303020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '30302010', 'Personal Care Products', id FROM gics_industries WHERE code = '303020';

-- Health Care Equipment & Supplies (351010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35101010', 'Health Care Equipment', id FROM gics_industries WHERE code = '351010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35101020', 'Health Care Supplies', id FROM gics_industries WHERE code = '351010';

-- Health Care Providers & Services (351020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35102010', 'Health Care Distributors', id FROM gics_industries WHERE code = '351020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35102015', 'Health Care Services', id FROM gics_industries WHERE code = '351020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35102020', 'Health Care Facilities', id FROM gics_industries WHERE code = '351020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35102030', 'Managed Health Care', id FROM gics_industries WHERE code = '351020';

-- Health Care Technology (351030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35103010', 'Health Care Technology', id FROM gics_industries WHERE code = '351030';

-- Biotechnology (352010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35201010', 'Biotechnology', id FROM gics_industries WHERE code = '352010';

-- Pharmaceuticals (352020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35202010', 'Pharmaceuticals', id FROM gics_industries WHERE code = '352020';

-- Life Sciences Tools & Services (352030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '35203010', 'Life Sciences Tools & Services', id FROM gics_industries WHERE code = '352030';

-- Banks (401010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40101010', 'Diversified Banks', id FROM gics_industries WHERE code = '401010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40101015', 'Regional Banks', id FROM gics_industries WHERE code = '401010';

-- Financial Services (402010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40201020', 'Diversified Financial Services', id FROM gics_industries WHERE code = '402010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40201030', 'Multi-Sector Holdings', id FROM gics_industries WHERE code = '402010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40201040', 'Specialized Finance', id FROM gics_industries WHERE code = '402010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40201050', 'Commercial & Residential Mortgage Finance', id FROM gics_industries WHERE code = '402010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40201060', 'Transaction & Payment Processing Services', id FROM gics_industries WHERE code = '402010';

-- Consumer Finance (402020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40202010', 'Consumer Finance', id FROM gics_industries WHERE code = '402020';

-- Capital Markets (402030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40203010', 'Asset Management & Custody Banks', id FROM gics_industries WHERE code = '402030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40203020', 'Investment Banking & Brokerage', id FROM gics_industries WHERE code = '402030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40203030', 'Diversified Capital Markets', id FROM gics_industries WHERE code = '402030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40203040', 'Financial Exchanges & Data', id FROM gics_industries WHERE code = '402030';

-- Mortgage REITs (402040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40204010', 'Mortgage REITs', id FROM gics_industries WHERE code = '402040';

-- Insurance (403010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40301010', 'Insurance Brokers', id FROM gics_industries WHERE code = '403010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40301020', 'Life & Health Insurance', id FROM gics_industries WHERE code = '403010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40301030', 'Multi-line Insurance', id FROM gics_industries WHERE code = '403010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40301040', 'Property & Casualty Insurance', id FROM gics_industries WHERE code = '403010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '40301050', 'Reinsurance', id FROM gics_industries WHERE code = '403010';

-- IT Services (451010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45101010', 'IT Consulting & Other Services', id FROM gics_industries WHERE code = '451010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45101020', 'Internet Services & Infrastructure', id FROM gics_industries WHERE code = '451010';

-- Software (451020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45102010', 'Application Software', id FROM gics_industries WHERE code = '451020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45102020', 'Systems Software', id FROM gics_industries WHERE code = '451020';

-- Communications Equipment (452010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45201020', 'Communications Equipment', id FROM gics_industries WHERE code = '452010';

-- Technology Hardware, Storage & Peripherals (452020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45202030', 'Technology Hardware, Storage & Peripherals', id FROM gics_industries WHERE code = '452020';

-- Electronic Equipment, Instruments & Components (452030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45203010', 'Electronic Equipment & Instruments', id FROM gics_industries WHERE code = '452030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45203015', 'Electronic Components', id FROM gics_industries WHERE code = '452030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45203020', 'Electronic Manufacturing Services', id FROM gics_industries WHERE code = '452030';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45203030', 'Technology Distributors', id FROM gics_industries WHERE code = '452030';

-- Semiconductors & Semiconductor Equipment (453010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45301010', 'Semiconductor Materials & Equipment', id FROM gics_industries WHERE code = '453010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '45301020', 'Semiconductors', id FROM gics_industries WHERE code = '453010';

-- Diversified Telecommunication Services (501010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50101010', 'Alternative Carriers', id FROM gics_industries WHERE code = '501010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50101020', 'Integrated Telecommunication Services', id FROM gics_industries WHERE code = '501010';

-- Wireless Telecommunication Services (501020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50102010', 'Wireless Telecommunication Services', id FROM gics_industries WHERE code = '501020';

-- Media (502010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50201010', 'Advertising', id FROM gics_industries WHERE code = '502010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50201020', 'Broadcasting', id FROM gics_industries WHERE code = '502010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50201030', 'Cable & Satellite', id FROM gics_industries WHERE code = '502010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50201040', 'Publishing', id FROM gics_industries WHERE code = '502010';

-- Entertainment (502020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50202010', 'Movies & Entertainment', id FROM gics_industries WHERE code = '502020';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50202020', 'Interactive Home Entertainment', id FROM gics_industries WHERE code = '502020';

-- Interactive Media & Services (502030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '50203010', 'Interactive Media & Services', id FROM gics_industries WHERE code = '502030';

-- Electric Utilities (551010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55101010', 'Electric Utilities', id FROM gics_industries WHERE code = '551010';

-- Gas Utilities (551020)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55102010', 'Gas Utilities', id FROM gics_industries WHERE code = '551020';

-- Multi-Utilities (551030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55103010', 'Multi-Utilities', id FROM gics_industries WHERE code = '551030';

-- Water Utilities (551040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55104010', 'Water Utilities', id FROM gics_industries WHERE code = '551040';

-- Independent Power and Renewable Electricity Producers (551050)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55105010', 'Independent Power Producers & Energy Traders', id FROM gics_industries WHERE code = '551050';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '55105020', 'Renewable Electricity', id FROM gics_industries WHERE code = '551050';

-- Diversified REITs (601010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60101010', 'Diversified REITs', id FROM gics_industries WHERE code = '601010';

-- Industrial REITs (601025)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60102510', 'Industrial REITs', id FROM gics_industries WHERE code = '601025';

-- Hotel & Resort REITs (601030)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60103010', 'Hotel & Resort REITs', id FROM gics_industries WHERE code = '601030';

-- Office REITs (601040)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60104010', 'Office REITs', id FROM gics_industries WHERE code = '601040';

-- Health Care REITs (601050)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60105010', 'Health Care REITs', id FROM gics_industries WHERE code = '601050';

-- Residential REITs (601060)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60106010', 'Multi-Family Residential REITs', id FROM gics_industries WHERE code = '601060';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60106020', 'Single-Family Residential REITs', id FROM gics_industries WHERE code = '601060';

-- Retail REITs (601070)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60107010', 'Retail REITs', id FROM gics_industries WHERE code = '601070';

-- Specialized REITs (601080)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60108010', 'Other Specialized REITs', id FROM gics_industries WHERE code = '601080';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60108020', 'Self-Storage REITs', id FROM gics_industries WHERE code = '601080';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60108030', 'Telecom Tower REITs', id FROM gics_industries WHERE code = '601080';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60108040', 'Timber REITs', id FROM gics_industries WHERE code = '601080';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60108050', 'Data Center REITs', id FROM gics_industries WHERE code = '601080';

-- Real Estate Management & Development (602010)
INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60201010', 'Diversified Real Estate Activities', id FROM gics_industries WHERE code = '602010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60201020', 'Real Estate Operating Companies', id FROM gics_industries WHERE code = '602010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60201030', 'Real Estate Development', id FROM gics_industries WHERE code = '602010';

INSERT INTO gics_sub_industries (code, name, industry_id)
SELECT '60201040', 'Real Estate Services', id FROM gics_industries WHERE code = '602010';
