-- Model portfolio templates (system-provided and user-created)
CREATE TABLE model_portfolios (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    risk_level VARCHAR(20) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT false,
    user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_model_portfolio_risk_level
        CHECK (risk_level IN ('LOW', 'MODERATE', 'HIGH', 'EXTRA_HIGH'))
);

CREATE TABLE model_portfolio_allocations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_portfolio_id BIGINT NOT NULL REFERENCES model_portfolios(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    target_percent DECIMAL(7,4) NOT NULL,
    asset_class VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_model_alloc_percent CHECK (target_percent >= 0 AND target_percent <= 100)
);

CREATE INDEX idx_model_portfolios_user_id ON model_portfolios(user_id);
CREATE INDEX idx_model_alloc_portfolio_id ON model_portfolio_allocations(model_portfolio_id);

-- Link portfolio groups to model portfolios
ALTER TABLE portfolio_groups ADD COLUMN model_portfolio_id BIGINT REFERENCES model_portfolios(id);
ALTER TABLE portfolio_groups ADD COLUMN benchmark_model_id BIGINT REFERENCES model_portfolios(id);

-- Seed system model portfolios
INSERT INTO model_portfolios (name, description, risk_level, is_system) VALUES
('Conservative Income', 'Low-risk portfolio emphasizing fixed income and cash preservation. Suitable for capital preservation and income generation.', 'LOW', true),
('Balanced Growth', 'Moderate-risk portfolio balancing equities and fixed income. Suitable for steady long-term growth with reduced volatility.', 'MODERATE', true),
('Growth', 'Higher-risk portfolio with majority equity allocation. Suitable for long-term investors comfortable with market fluctuations.', 'HIGH', true),
('Aggressive Equity', 'Maximum equity exposure across domestic and international markets. Suitable for investors with high risk tolerance and long time horizons.', 'EXTRA_HIGH', true);

-- Conservative Income allocations (40% equity, 50% fixed income, 10% cash)
INSERT INTO model_portfolio_allocations (model_portfolio_id, symbol, target_percent, asset_class)
SELECT id, unnest(ARRAY['XIU.TO', 'VFV.TO', 'ZAG.TO', 'XBB.TO']),
       unnest(ARRAY[15.0, 10.0, 40.0, 25.0]::DECIMAL[]),
       unnest(ARRAY['Equity', 'Equity', 'Fixed Income', 'Fixed Income'])
FROM model_portfolios WHERE name = 'Conservative Income' AND is_system = true;

-- Balanced Growth allocations (55% equity, 35% fixed income, 10% cash)
INSERT INTO model_portfolio_allocations (model_portfolio_id, symbol, target_percent, asset_class)
SELECT id, unnest(ARRAY['XIU.TO', 'VFV.TO', 'XEF.TO', 'ZAG.TO', 'XBB.TO']),
       unnest(ARRAY[20.0, 25.0, 10.0, 25.0, 10.0]::DECIMAL[]),
       unnest(ARRAY['Equity', 'Equity', 'Equity', 'Fixed Income', 'Fixed Income'])
FROM model_portfolios WHERE name = 'Balanced Growth' AND is_system = true;

-- Growth allocations (75% equity, 15% fixed income, 10% cash)
INSERT INTO model_portfolio_allocations (model_portfolio_id, symbol, target_percent, asset_class)
SELECT id, unnest(ARRAY['XIU.TO', 'VFV.TO', 'XEF.TO', 'XEC.TO', 'ZAG.TO']),
       unnest(ARRAY[25.0, 30.0, 15.0, 5.0, 15.0]::DECIMAL[]),
       unnest(ARRAY['Equity', 'Equity', 'Equity', 'Equity', 'Fixed Income'])
FROM model_portfolios WHERE name = 'Growth' AND is_system = true;

-- Aggressive Equity allocations (100% equity)
INSERT INTO model_portfolio_allocations (model_portfolio_id, symbol, target_percent, asset_class)
SELECT id, unnest(ARRAY['XIU.TO', 'VFV.TO', 'XEF.TO', 'XEC.TO', 'QQC.TO']),
       unnest(ARRAY[30.0, 30.0, 15.0, 10.0, 15.0]::DECIMAL[]),
       unnest(ARRAY['Equity', 'Equity', 'Equity', 'Equity', 'Equity'])
FROM model_portfolios WHERE name = 'Aggressive Equity' AND is_system = true;
