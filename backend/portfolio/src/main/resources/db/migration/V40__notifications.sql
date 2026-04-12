-- Notification preferences per user
CREATE TABLE notification_preferences (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    drift_alerts        BOOLEAN NOT NULL DEFAULT TRUE,
    drift_threshold     DECIMAL(5, 2) NOT NULL DEFAULT 90.00,
    order_alerts        BOOLEAN NOT NULL DEFAULT TRUE,
    sync_failure_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    new_asset_alerts    BOOLEAN NOT NULL DEFAULT TRUE,
    rebalance_reminder  BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_frequency  VARCHAR(20) DEFAULT 'WEEKLY',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_prefs_user UNIQUE (user_id)
);

-- In-app notifications
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     TEXT NOT NULL,
    link        VARCHAR(500),
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- Trigger for notification_preferences updated_at
CREATE OR REPLACE FUNCTION update_notification_preferences_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_notification_preferences_updated_at();
