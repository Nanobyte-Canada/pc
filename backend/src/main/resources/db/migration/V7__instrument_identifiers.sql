-- V7__instrument_identifiers.sql
-- Create instrument identifiers table for FIGI and other standard identifiers

CREATE TABLE instrument_identifiers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_type VARCHAR(20) NOT NULL,
    instrument_id BIGINT NOT NULL,
    identifier_type VARCHAR(30) NOT NULL,
    identifier_value VARCHAR(50) NOT NULL,
    source VARCHAR(30) NOT NULL,
    match_confidence VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_instrument_identifier UNIQUE (instrument_type, instrument_id, identifier_type),
    CONSTRAINT chk_identifier_type CHECK (identifier_type IN (
        'FIGI', 'COMPOSITE_FIGI', 'SHARE_CLASS_FIGI', 'ISIN', 'CUSIP', 'SEDOL'
    )),
    CONSTRAINT chk_instrument_type CHECK (instrument_type IN ('STOCK', 'ETF', 'MUTUAL_FUND')),
    CONSTRAINT chk_source CHECK (source IN ('EODHD', 'OPENFIGI', 'FMP', 'MANUAL')),
    CONSTRAINT chk_match_confidence CHECK (match_confidence IS NULL OR match_confidence IN ('EXACT', 'AMBIGUOUS', 'UNVERIFIED'))
);

-- Index for looking up identifiers by instrument
CREATE INDEX idx_identifiers_instrument ON instrument_identifiers(instrument_type, instrument_id);

-- Index for looking up instruments by identifier value
CREATE INDEX idx_identifiers_type_value ON instrument_identifiers(identifier_type, identifier_value);

-- Partial index for fast FIGI lookups
CREATE INDEX idx_identifiers_figi ON instrument_identifiers(identifier_value)
    WHERE identifier_type = 'FIGI';

-- Trigger to update updated_at timestamp
CREATE TRIGGER trg_instrument_identifiers_updated_at
    BEFORE UPDATE ON instrument_identifiers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
