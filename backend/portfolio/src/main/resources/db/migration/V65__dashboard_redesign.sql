-- Dashboard redesign: add PORTFOLIO_SUMMARY widget preference for existing users
-- Replaces PORTFOLIO_VALUE + AVAILABLE_CASH + BUYING_POWER in dashboard context

INSERT INTO dashboard_preferences (user_id, context_type, context_id, widget_key, is_visible, sort_order, column_span)
SELECT dp.user_id, dp.context_type, dp.context_id, 'PORTFOLIO_SUMMARY', dp.is_visible, 0, 4
FROM dashboard_preferences dp
WHERE dp.widget_key = 'PORTFOLIO_VALUE'
  AND NOT EXISTS (
    SELECT 1 FROM dashboard_preferences dp2
    WHERE dp2.user_id = dp.user_id
      AND dp2.context_type = dp.context_type
      AND COALESCE(dp2.context_id, 0) = COALESCE(dp.context_id, 0)
      AND dp2.widget_key = 'PORTFOLIO_SUMMARY'
  );
