CREATE SCHEMA IF NOT EXISTS strategy;

-- Orders
CREATE TABLE strategy.orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    underlying VARCHAR(20) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'LIMIT',
    net_price NUMERIC(12, 4),
    quantity INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    ibkr_order_id BIGINT,
    snaptrade_order_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_status ON strategy.orders (user_id, status);

-- Order legs
CREATE TABLE strategy.order_legs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES strategy.orders(id) ON DELETE CASCADE,
    leg_index INTEGER NOT NULL,
    action VARCHAR(4) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    con_id BIGINT,
    option_type VARCHAR(4),
    strike NUMERIC(12, 4),
    expiry DATE,
    quantity INTEGER NOT NULL,
    fill_price NUMERIC(12, 4),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_legs_order_id ON strategy.order_legs (order_id);

-- Executions
CREATE TABLE strategy.executions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES strategy.orders(id) ON DELETE CASCADE,
    ibkr_exec_id VARCHAR(100),
    price NUMERIC(12, 4) NOT NULL,
    quantity INTEGER NOT NULL,
    commission NUMERIC(10, 4),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_order_id ON strategy.executions (order_id);

-- Positions
CREATE TABLE strategy.positions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    underlying VARCHAR(20) NOT NULL,
    entry_order_id BIGINT REFERENCES strategy.orders(id),
    exit_order_id BIGINT REFERENCES strategy.orders(id),
    net_entry_price NUMERIC(12, 4),
    net_exit_price NUMERIC(12, 4),
    quantity INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    realized_pnl NUMERIC(12, 4),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_positions_user_status ON strategy.positions (user_id, status);

-- Position legs
CREATE TABLE strategy.position_legs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id BIGINT NOT NULL REFERENCES strategy.positions(id) ON DELETE CASCADE,
    action VARCHAR(4) NOT NULL,
    option_type VARCHAR(4) NOT NULL,
    strike NUMERIC(12, 4) NOT NULL,
    expiry DATE NOT NULL,
    quantity INTEGER NOT NULL,
    entry_price NUMERIC(12, 4),
    current_price NUMERIC(12, 4),
    delta NUMERIC(10, 6),
    gamma NUMERIC(10, 6),
    theta NUMERIC(10, 6),
    vega NUMERIC(10, 6),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_position_legs_position_id ON strategy.position_legs (position_id);

-- Wheel accounts
CREATE TABLE strategy.wheel_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    account_id BIGINT,
    tickers TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Wheel configs
CREATE TABLE strategy.wheel_configs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wheel_account_id BIGINT NOT NULL REFERENCES strategy.wheel_accounts(id) ON DELETE CASCADE,
    dte_min INTEGER NOT NULL DEFAULT 25,
    dte_max INTEGER NOT NULL DEFAULT 45,
    csp_delta_target NUMERIC(6, 4) NOT NULL DEFAULT 0.30,
    csp_delta_tolerance NUMERIC(6, 4) NOT NULL DEFAULT 0.10,
    cc_delta_target NUMERIC(6, 4) NOT NULL DEFAULT 0.30,
    cc_delta_tolerance NUMERIC(6, 4) NOT NULL DEFAULT 0.10,
    iv_rank_threshold NUMERIC(6, 4) NOT NULL DEFAULT 0.30,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (wheel_account_id)
);

-- Wheel recommendations
CREATE TABLE strategy.wheel_recommendations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wheel_account_id BIGINT NOT NULL REFERENCES strategy.wheel_accounts(id) ON DELETE CASCADE,
    ticker VARCHAR(20) NOT NULL,
    option_type VARCHAR(4) NOT NULL,
    strike NUMERIC(12, 4) NOT NULL,
    expiry DATE NOT NULL,
    dte INTEGER NOT NULL,
    delta NUMERIC(10, 6),
    bid NUMERIC(12, 4),
    ask NUMERIC(12, 4),
    mid NUMERIC(12, 4),
    annualized_yield NUMERIC(10, 6),
    score NUMERIC(10, 6),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wheel_recommendations_account
    ON strategy.wheel_recommendations (wheel_account_id, status);

-- Wheel holdings
CREATE TABLE strategy.wheel_holdings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wheel_account_id BIGINT NOT NULL REFERENCES strategy.wheel_accounts(id) ON DELETE CASCADE,
    ticker VARCHAR(20) NOT NULL,
    shares INTEGER NOT NULL,
    cost_basis NUMERIC(12, 4) NOT NULL,
    assigned_from BIGINT REFERENCES strategy.wheel_recommendations(id),
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Admin actions
CREATE TABLE strategy.admin_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_actions_admin ON strategy.admin_actions (admin_user_id, created_at DESC);
