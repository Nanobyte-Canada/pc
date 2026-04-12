-- Dashboard widget preferences per user
CREATE TABLE dashboard_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context_type VARCHAR(20) NOT NULL DEFAULT 'DASHBOARD',
    context_id BIGINT,
    widget_key VARCHAR(50) NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 0,
    column_span INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_dashboard_prefs_unique
  ON dashboard_preferences(user_id, context_type, COALESCE(context_id, 0), widget_key);
CREATE INDEX idx_dashboard_prefs_user ON dashboard_preferences(user_id);
CREATE INDEX idx_dashboard_prefs_context ON dashboard_preferences(user_id, context_type, context_id);
