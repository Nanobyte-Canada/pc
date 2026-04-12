-- ============================================================================
-- Portfolio Groups Schema
-- Migration V38: Portfolio groups, targets, linked accounts, settings, exclusions
-- ============================================================================

-- ----------------------------------------------------------------------------
-- PORTFOLIO GROUPS
-- ----------------------------------------------------------------------------
CREATE TABLE portfolio_groups (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_group_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_portfolio_groups_user ON portfolio_groups(user_id);

COMMENT ON TABLE portfolio_groups IS 'Model portfolios with target allocations';

-- ----------------------------------------------------------------------------
-- PORTFOLIO TARGETS (target allocations per group)
-- ----------------------------------------------------------------------------
CREATE TABLE portfolio_targets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    symbol          VARCHAR(20) NOT NULL,
    target_percent  DECIMAL(7, 4) NOT NULL CHECK (target_percent >= 0 AND target_percent <= 100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_target_group_symbol UNIQUE (group_id, symbol)
);

CREATE INDEX idx_portfolio_targets_group ON portfolio_targets(group_id);

COMMENT ON TABLE portfolio_targets IS 'Target allocation percentages per symbol within a portfolio group';

-- ----------------------------------------------------------------------------
-- PORTFOLIO GROUP ACCOUNTS (linked broker connections)
-- ----------------------------------------------------------------------------
CREATE TABLE portfolio_group_accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    connection_id   BIGINT NOT NULL REFERENCES broker_connections(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_account UNIQUE (group_id, connection_id)
);

CREATE INDEX idx_group_accounts_group ON portfolio_group_accounts(group_id);
CREATE INDEX idx_group_accounts_conn ON portfolio_group_accounts(connection_id);

COMMENT ON TABLE portfolio_group_accounts IS 'Links broker connections to portfolio groups';

-- ----------------------------------------------------------------------------
-- PORTFOLIO GROUP SETTINGS
-- ----------------------------------------------------------------------------
CREATE TABLE portfolio_group_settings (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id                    BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    sell_to_rebalance           BOOLEAN NOT NULL DEFAULT FALSE,
    keep_currencies_separate    BOOLEAN NOT NULL DEFAULT FALSE,
    prevent_non_tradable_trades BOOLEAN NOT NULL DEFAULT FALSE,
    notify_new_assets           BOOLEAN NOT NULL DEFAULT TRUE,
    retain_cash_for_exchange    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settings_group UNIQUE (group_id)
);

COMMENT ON TABLE portfolio_group_settings IS 'Rebalancing settings per portfolio group';

-- ----------------------------------------------------------------------------
-- PORTFOLIO EXCLUDED ASSETS
-- ----------------------------------------------------------------------------
CREATE TABLE portfolio_excluded_assets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    symbol          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_excluded_group_symbol UNIQUE (group_id, symbol)
);

CREATE INDEX idx_excluded_assets_group ON portfolio_excluded_assets(group_id);

COMMENT ON TABLE portfolio_excluded_assets IS 'Assets excluded from drift calculation and rebalancing';

-- ----------------------------------------------------------------------------
-- TRIGGERS for updated_at
-- ----------------------------------------------------------------------------
CREATE TRIGGER trg_portfolio_groups_updated_at
    BEFORE UPDATE ON portfolio_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_portfolio_targets_updated_at
    BEFORE UPDATE ON portfolio_targets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_portfolio_group_settings_updated_at
    BEFORE UPDATE ON portfolio_group_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
