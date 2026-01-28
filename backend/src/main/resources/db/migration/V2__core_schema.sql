-- ============================================================================
-- Portfolio Construction Application - Core Schema
-- Migration V2: GICS hierarchy, securities, and holdings
-- ============================================================================

-- ----------------------------------------------------------------------------
-- GICS HIERARCHY TABLES
-- ----------------------------------------------------------------------------

CREATE TABLE gics_sectors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(2) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gics_sectors_code UNIQUE (code)
);

CREATE INDEX idx_gics_sectors_code ON gics_sectors(code);

COMMENT ON TABLE gics_sectors IS 'GICS Level 1: Sectors (11 total)';

-- ----------------------------------------------------------------------------

CREATE TABLE gics_industry_groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(4) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sector_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gics_industry_groups_code UNIQUE (code),
    CONSTRAINT fk_gics_industry_groups_sector
        FOREIGN KEY (sector_id) REFERENCES gics_sectors(id) ON DELETE RESTRICT
);

CREATE INDEX idx_gics_industry_groups_code ON gics_industry_groups(code);
CREATE INDEX idx_gics_industry_groups_sector ON gics_industry_groups(sector_id);

COMMENT ON TABLE gics_industry_groups IS 'GICS Level 2: Industry Groups (25 total)';

-- ----------------------------------------------------------------------------

CREATE TABLE gics_industries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    industry_group_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gics_industries_code UNIQUE (code),
    CONSTRAINT fk_gics_industries_group
        FOREIGN KEY (industry_group_id) REFERENCES gics_industry_groups(id) ON DELETE RESTRICT
);

CREATE INDEX idx_gics_industries_code ON gics_industries(code);
CREATE INDEX idx_gics_industries_group ON gics_industries(industry_group_id);

COMMENT ON TABLE gics_industries IS 'GICS Level 3: Industries (74 total)';

-- ----------------------------------------------------------------------------

CREATE TABLE gics_sub_industries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(8) NOT NULL,
    name VARCHAR(150) NOT NULL,
    industry_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gics_sub_industries_code UNIQUE (code),
    CONSTRAINT fk_gics_sub_industries_industry
        FOREIGN KEY (industry_id) REFERENCES gics_industries(id) ON DELETE RESTRICT
);

CREATE INDEX idx_gics_sub_industries_code ON gics_sub_industries(code);
CREATE INDEX idx_gics_sub_industries_industry ON gics_sub_industries(industry_id);

COMMENT ON TABLE gics_sub_industries IS 'GICS Level 4: Sub-Industries (163 total)';

-- ----------------------------------------------------------------------------
-- SECURITIES TABLES
-- ----------------------------------------------------------------------------

CREATE TABLE stocks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    sedol VARCHAR(7),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    country VARCHAR(3) NOT NULL DEFAULT 'USA',
    gics_sub_industry_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stocks_ticker_exchange UNIQUE (ticker, exchange),
    CONSTRAINT uq_stocks_isin UNIQUE (isin),
    CONSTRAINT uq_stocks_cusip UNIQUE (cusip),
    CONSTRAINT fk_stocks_gics
        FOREIGN KEY (gics_sub_industry_id) REFERENCES gics_sub_industries(id) ON DELETE SET NULL,
    CONSTRAINT chk_stocks_status
        CHECK (status IN ('ACTIVE', 'DELISTED', 'SUSPENDED', 'PENDING'))
);

CREATE INDEX idx_stocks_ticker ON stocks(ticker);
CREATE INDEX idx_stocks_exchange ON stocks(exchange);
CREATE INDEX idx_stocks_ticker_exchange ON stocks(ticker, exchange);
CREATE INDEX idx_stocks_isin ON stocks(isin) WHERE isin IS NOT NULL;
CREATE INDEX idx_stocks_cusip ON stocks(cusip) WHERE cusip IS NOT NULL;
CREATE INDEX idx_stocks_gics ON stocks(gics_sub_industry_id) WHERE gics_sub_industry_id IS NOT NULL;
CREATE INDEX idx_stocks_status ON stocks(status);

COMMENT ON TABLE stocks IS 'Individual equity securities';

-- ----------------------------------------------------------------------------

CREATE TABLE etfs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    issuer VARCHAR(100),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    domicile VARCHAR(3) NOT NULL DEFAULT 'USA',
    inception_date DATE,
    expense_ratio DECIMAL(6,4),
    asset_class VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_etfs_symbol_exchange UNIQUE (symbol, exchange),
    CONSTRAINT uq_etfs_isin UNIQUE (isin),
    CONSTRAINT uq_etfs_cusip UNIQUE (cusip),
    CONSTRAINT chk_etfs_expense_ratio
        CHECK (expense_ratio IS NULL OR (expense_ratio >= 0 AND expense_ratio <= 1)),
    CONSTRAINT chk_etfs_status
        CHECK (status IN ('ACTIVE', 'DELISTED', 'SUSPENDED', 'PENDING'))
);

CREATE INDEX idx_etfs_symbol ON etfs(symbol);
CREATE INDEX idx_etfs_symbol_exchange ON etfs(symbol, exchange);
CREATE INDEX idx_etfs_issuer ON etfs(issuer) WHERE issuer IS NOT NULL;
CREATE INDEX idx_etfs_asset_class ON etfs(asset_class) WHERE asset_class IS NOT NULL;
CREATE INDEX idx_etfs_status ON etfs(status);

COMMENT ON TABLE etfs IS 'Exchange-traded funds';

-- ----------------------------------------------------------------------------

CREATE TABLE mutual_funds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    issuer VARCHAR(100),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    domicile VARCHAR(3) NOT NULL DEFAULT 'USA',
    inception_date DATE,
    expense_ratio DECIMAL(6,4),
    fund_type VARCHAR(50),
    asset_class VARCHAR(50),
    minimum_investment DECIMAL(15,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_mutual_funds_symbol UNIQUE (symbol),
    CONSTRAINT uq_mutual_funds_isin UNIQUE (isin),
    CONSTRAINT uq_mutual_funds_cusip UNIQUE (cusip),
    CONSTRAINT chk_mutual_funds_expense_ratio
        CHECK (expense_ratio IS NULL OR (expense_ratio >= 0 AND expense_ratio <= 1)),
    CONSTRAINT chk_mutual_funds_min_investment
        CHECK (minimum_investment IS NULL OR minimum_investment >= 0),
    CONSTRAINT chk_mutual_funds_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'SUSPENDED', 'PENDING'))
);

CREATE INDEX idx_mutual_funds_symbol ON mutual_funds(symbol);
CREATE INDEX idx_mutual_funds_issuer ON mutual_funds(issuer) WHERE issuer IS NOT NULL;
CREATE INDEX idx_mutual_funds_asset_class ON mutual_funds(asset_class) WHERE asset_class IS NOT NULL;
CREATE INDEX idx_mutual_funds_status ON mutual_funds(status);

COMMENT ON TABLE mutual_funds IS 'Mutual funds';

-- ----------------------------------------------------------------------------
-- DATA LINEAGE TABLES
-- ----------------------------------------------------------------------------

CREATE TABLE data_sources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_data_sources_code UNIQUE (code)
);

COMMENT ON TABLE data_sources IS 'Reference table for data vendors and sources';

-- Seed common data sources
INSERT INTO data_sources (code, name, description) VALUES
    ('MANUAL', 'Manual Entry', 'Manually entered data'),
    ('SEC_EDGAR', 'SEC EDGAR', 'SEC Electronic Data Gathering, Analysis, and Retrieval'),
    ('FUND_WEBSITE', 'Fund Website', 'Data from fund issuer website'),
    ('BLOOMBERG', 'Bloomberg', 'Bloomberg Terminal data'),
    ('REFINITIV', 'Refinitiv', 'Refinitiv/Reuters data'),
    ('MORNINGSTAR', 'Morningstar', 'Morningstar data feed');

-- ----------------------------------------------------------------------------

CREATE TABLE ingestion_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id BIGINT NOT NULL,
    batch_date DATE NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    record_count INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    metadata JSONB,

    CONSTRAINT fk_ingestion_batches_source
        FOREIGN KEY (source_id) REFERENCES data_sources(id) ON DELETE RESTRICT,
    CONSTRAINT chk_ingestion_batches_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_ingestion_batches_source ON ingestion_batches(source_id);
CREATE INDEX idx_ingestion_batches_date ON ingestion_batches(batch_date);
CREATE INDEX idx_ingestion_batches_status ON ingestion_batches(status);

COMMENT ON TABLE ingestion_batches IS 'Track data import batches for auditing';

-- ----------------------------------------------------------------------------
-- HOLDINGS TABLES (TIME-SERIES)
-- ----------------------------------------------------------------------------

CREATE TABLE etf_holdings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    etf_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    as_of_date DATE NOT NULL,
    weight DECIMAL(8,6),
    shares DECIMAL(18,4),
    market_value DECIMAL(18,2),
    ingestion_batch_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_etf_holdings_snapshot UNIQUE (etf_id, stock_id, as_of_date),
    CONSTRAINT fk_etf_holdings_etf
        FOREIGN KEY (etf_id) REFERENCES etfs(id) ON DELETE CASCADE,
    CONSTRAINT fk_etf_holdings_stock
        FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    CONSTRAINT fk_etf_holdings_batch
        FOREIGN KEY (ingestion_batch_id) REFERENCES ingestion_batches(id) ON DELETE SET NULL,
    CONSTRAINT chk_etf_holdings_weight
        CHECK (weight IS NULL OR (weight >= 0 AND weight <= 1)),
    CONSTRAINT chk_etf_holdings_shares
        CHECK (shares IS NULL OR shares >= 0),
    CONSTRAINT chk_etf_holdings_value
        CHECK (market_value IS NULL OR market_value >= 0)
);

CREATE INDEX idx_etf_holdings_etf ON etf_holdings(etf_id);
CREATE INDEX idx_etf_holdings_stock ON etf_holdings(stock_id);
CREATE INDEX idx_etf_holdings_date ON etf_holdings(as_of_date);
CREATE INDEX idx_etf_holdings_etf_date ON etf_holdings(etf_id, as_of_date);
CREATE INDEX idx_etf_holdings_batch ON etf_holdings(ingestion_batch_id) WHERE ingestion_batch_id IS NOT NULL;

COMMENT ON TABLE etf_holdings IS 'ETF holdings snapshots (many-to-many with time dimension)';

-- ----------------------------------------------------------------------------

CREATE TABLE mutual_fund_holdings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mutual_fund_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    as_of_date DATE NOT NULL,
    weight DECIMAL(8,6),
    shares DECIMAL(18,4),
    market_value DECIMAL(18,2),
    ingestion_batch_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_mf_holdings_snapshot UNIQUE (mutual_fund_id, stock_id, as_of_date),
    CONSTRAINT fk_mf_holdings_fund
        FOREIGN KEY (mutual_fund_id) REFERENCES mutual_funds(id) ON DELETE CASCADE,
    CONSTRAINT fk_mf_holdings_stock
        FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    CONSTRAINT fk_mf_holdings_batch
        FOREIGN KEY (ingestion_batch_id) REFERENCES ingestion_batches(id) ON DELETE SET NULL,
    CONSTRAINT chk_mf_holdings_weight
        CHECK (weight IS NULL OR (weight >= 0 AND weight <= 1)),
    CONSTRAINT chk_mf_holdings_shares
        CHECK (shares IS NULL OR shares >= 0),
    CONSTRAINT chk_mf_holdings_value
        CHECK (market_value IS NULL OR market_value >= 0)
);

CREATE INDEX idx_mf_holdings_fund ON mutual_fund_holdings(mutual_fund_id);
CREATE INDEX idx_mf_holdings_stock ON mutual_fund_holdings(stock_id);
CREATE INDEX idx_mf_holdings_date ON mutual_fund_holdings(as_of_date);
CREATE INDEX idx_mf_holdings_fund_date ON mutual_fund_holdings(mutual_fund_id, as_of_date);
CREATE INDEX idx_mf_holdings_batch ON mutual_fund_holdings(ingestion_batch_id) WHERE ingestion_batch_id IS NOT NULL;

COMMENT ON TABLE mutual_fund_holdings IS 'Mutual fund holdings snapshots (many-to-many with time dimension)';

-- ----------------------------------------------------------------------------
-- UTILITY FUNCTION: Updated timestamp trigger
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
CREATE TRIGGER trg_stocks_updated_at
    BEFORE UPDATE ON stocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_etfs_updated_at
    BEFORE UPDATE ON etfs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_mutual_funds_updated_at
    BEFORE UPDATE ON mutual_funds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_gics_sectors_updated_at
    BEFORE UPDATE ON gics_sectors
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_gics_industry_groups_updated_at
    BEFORE UPDATE ON gics_industry_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_gics_industries_updated_at
    BEFORE UPDATE ON gics_industries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_gics_sub_industries_updated_at
    BEFORE UPDATE ON gics_sub_industries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
