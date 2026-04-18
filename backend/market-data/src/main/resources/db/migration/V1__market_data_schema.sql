CREATE SCHEMA IF NOT EXISTS market_data;

CREATE TABLE market_data.underlying_prices (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    price NUMERIC(12, 4) NOT NULL,
    volume BIGINT,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_underlying_prices_ticker_observed
    ON market_data.underlying_prices (ticker, observed_at DESC);

CREATE TABLE market_data.option_quotes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    expiry DATE NOT NULL,
    strike NUMERIC(12, 4) NOT NULL,
    option_type VARCHAR(4) NOT NULL,
    bid NUMERIC(12, 4),
    ask NUMERIC(12, 4),
    mid NUMERIC(12, 4),
    last_price NUMERIC(12, 4),
    volume BIGINT,
    open_interest BIGINT,
    delta NUMERIC(10, 6),
    gamma NUMERIC(10, 6),
    theta NUMERIC(10, 6),
    vega NUMERIC(10, 6),
    rho NUMERIC(10, 6),
    implied_volatility NUMERIC(10, 6),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_option_quotes_ticker_expiry
    ON market_data.option_quotes (ticker, expiry, strike);

CREATE TABLE market_data.iv_observations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    atm_iv NUMERIC(10, 6) NOT NULL,
    observed_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (ticker, observed_date)
);

CREATE INDEX idx_iv_observations_ticker_date
    ON market_data.iv_observations (ticker, observed_date DESC);

CREATE TABLE market_data.contract_cache (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    con_id INTEGER NOT NULL,
    sec_type VARCHAR(10) NOT NULL,
    expiry DATE,
    strike NUMERIC(12, 4),
    option_right VARCHAR(4),
    exchange VARCHAR(20),
    currency VARCHAR(5),
    cached_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (symbol, sec_type, expiry, strike, option_right)
);

CREATE INDEX idx_contract_cache_symbol
    ON market_data.contract_cache (symbol);
