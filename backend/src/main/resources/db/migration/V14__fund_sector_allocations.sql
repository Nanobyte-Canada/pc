-- V14__fund_sector_allocations.sql
-- Create table for fund sector allocation breakdowns from SeekingAlpha

CREATE TABLE fund_sector_allocations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Polymorphic parent reference
    parent_type VARCHAR(20) NOT NULL,  -- 'ETF' or 'MUTUAL_FUND'
    parent_id BIGINT NOT NULL,
    as_of_date DATE NOT NULL,
    source VARCHAR(20) NOT NULL,       -- 'STOCK' or 'BOND'

    -- Stock sector breakdown (percentages, can exceed 100 for leveraged funds)
    basic_materials DECIMAL(8,5) DEFAULT 0,
    consumer_cyclical DECIMAL(8,5) DEFAULT 0,
    financials DECIMAL(8,5) DEFAULT 0,
    real_estate DECIMAL(8,5) DEFAULT 0,
    consumer_defensive DECIMAL(8,5) DEFAULT 0,
    healthcare DECIMAL(8,5) DEFAULT 0,
    utilities DECIMAL(8,5) DEFAULT 0,
    communication DECIMAL(8,5) DEFAULT 0,
    energy DECIMAL(8,5) DEFAULT 0,
    industrials DECIMAL(8,5) DEFAULT 0,
    technology DECIMAL(8,5) DEFAULT 0,

    -- Bond type breakdown
    government DECIMAL(8,5) DEFAULT 0,
    municipal DECIMAL(8,5) DEFAULT 0,
    corporate DECIMAL(8,5) DEFAULT 0,
    securitized DECIMAL(8,5) DEFAULT 0,
    derivative DECIMAL(8,5) DEFAULT 0,
    cash_and_equiv DECIMAL(8,5) DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_sector_alloc UNIQUE (parent_type, parent_id, as_of_date, source),
    CONSTRAINT chk_parent_type CHECK (parent_type IN ('ETF', 'MUTUAL_FUND')),
    CONSTRAINT chk_source CHECK (source IN ('STOCK', 'BOND'))
);

CREATE INDEX idx_sector_alloc_parent ON fund_sector_allocations(parent_type, parent_id);
CREATE INDEX idx_sector_alloc_date ON fund_sector_allocations(as_of_date DESC);

COMMENT ON TABLE fund_sector_allocations IS 'Fund sector allocation breakdowns from SeekingAlpha';
