-- Extend portfolio group settings with rebalance automation fields
ALTER TABLE portfolio_group_settings ADD COLUMN rebalance_frequency VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE portfolio_group_settings ADD COLUMN accuracy_threshold DECIMAL(5,2) NOT NULL DEFAULT 90.00;
ALTER TABLE portfolio_group_settings ADD COLUMN auto_execute BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE portfolio_group_settings ADD COLUMN last_rebalanced_at TIMESTAMPTZ;
ALTER TABLE portfolio_group_settings ADD COLUMN next_rebalance_date DATE;

ALTER TABLE portfolio_group_settings ADD CONSTRAINT chk_rebalance_frequency
    CHECK (rebalance_frequency IN ('MANUAL', 'MONTHLY', 'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY'));
ALTER TABLE portfolio_group_settings ADD CONSTRAINT chk_accuracy_threshold
    CHECK (accuracy_threshold >= 0 AND accuracy_threshold <= 100);

-- Rebalance event history
CREATE TABLE rebalance_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    trigger_type VARCHAR(20) NOT NULL,
    accuracy_before DECIMAL(5,2),
    accuracy_after DECIMAL(5,2),
    trades_count INT NOT NULL DEFAULT 0,
    batch_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rebalance_trigger CHECK (trigger_type IN ('SCHEDULED', 'ACCURACY_DROP', 'MANUAL')),
    CONSTRAINT chk_rebalance_status CHECK (status IN ('COMPLETED', 'FAILED', 'SKIPPED', 'PENDING_APPROVAL'))
);

CREATE INDEX idx_rebalance_events_group_id ON rebalance_events(group_id);
