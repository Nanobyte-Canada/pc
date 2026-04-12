-- Trade orders for tracking executed rebalance trades
CREATE TABLE trade_orders (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id            BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    connection_id       BIGINT NOT NULL REFERENCES broker_connections(id) ON DELETE CASCADE,
    batch_id            UUID,
    symbol              VARCHAR(20) NOT NULL,
    action              VARCHAR(4) NOT NULL CHECK (action IN ('BUY', 'SELL')),
    order_type          VARCHAR(10) NOT NULL DEFAULT 'MARKET',
    time_in_force       VARCHAR(3) NOT NULL DEFAULT 'DAY',
    requested_units     DECIMAL(18, 6) NOT NULL,
    requested_price     DECIMAL(18, 6) NOT NULL,
    requested_amount    DECIMAL(18, 2) NOT NULL,
    limit_price         DECIMAL(18, 6),
    filled_units        DECIMAL(18, 6),
    filled_price        DECIMAL(18, 6),
    filled_amount       DECIMAL(18, 2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'CAD',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SUBMITTED','FILLED','PARTIALLY_FILLED','REJECTED','CANCELLED','FAILED')),
    broker_order_id     VARCHAR(255),
    account_id_external VARCHAR(100),
    error_message       TEXT,
    error_code          VARCHAR(50),
    submitted_at        TIMESTAMPTZ,
    filled_at           TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_orders_user_id ON trade_orders(user_id);
CREATE INDEX idx_trade_orders_group_id ON trade_orders(group_id);
CREATE INDEX idx_trade_orders_batch_id ON trade_orders(batch_id);
CREATE INDEX idx_trade_orders_status ON trade_orders(status);
CREATE INDEX idx_trade_orders_connection_id ON trade_orders(connection_id);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_trade_orders_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trade_orders_updated_at
    BEFORE UPDATE ON trade_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_trade_orders_updated_at();
