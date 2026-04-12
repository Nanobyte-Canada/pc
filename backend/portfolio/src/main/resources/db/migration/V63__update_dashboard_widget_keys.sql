-- Migrate dashboard widget preferences for the accounts page redesign
-- New merged widgets replace individual ones:
--   PORTFOLIO_VALUE + AVAILABLE_CASH + BUYING_POWER -> ACCOUNT_SUMMARY
--   OPEN_ORDERS + PENDING_ORDERS -> ORDERS
--   FEES_COMMISSION + DIVIDEND_CALENDAR -> FEES_AND_DIVIDENDS

-- Insert ACCOUNT_SUMMARY for any user that had PORTFOLIO_VALUE
INSERT INTO dashboard_preferences (user_id, context_type, context_id, widget_key, is_visible, sort_order, column_span)
SELECT dp.user_id, dp.context_type, dp.context_id, 'ACCOUNT_SUMMARY', dp.is_visible, 0, 4
FROM dashboard_preferences dp
WHERE dp.widget_key = 'PORTFOLIO_VALUE'
  AND NOT EXISTS (
    SELECT 1 FROM dashboard_preferences dp2
    WHERE dp2.user_id = dp.user_id
      AND dp2.context_type = dp.context_type
      AND COALESCE(dp2.context_id, 0) = COALESCE(dp.context_id, 0)
      AND dp2.widget_key = 'ACCOUNT_SUMMARY'
  );

-- Insert ORDERS for any user that had OPEN_ORDERS
INSERT INTO dashboard_preferences (user_id, context_type, context_id, widget_key, is_visible, sort_order, column_span)
SELECT dp.user_id, dp.context_type, dp.context_id, 'ORDERS', dp.is_visible, 7, 2
FROM dashboard_preferences dp
WHERE dp.widget_key = 'OPEN_ORDERS'
  AND NOT EXISTS (
    SELECT 1 FROM dashboard_preferences dp2
    WHERE dp2.user_id = dp.user_id
      AND dp2.context_type = dp.context_type
      AND COALESCE(dp2.context_id, 0) = COALESCE(dp.context_id, 0)
      AND dp2.widget_key = 'ORDERS'
  );

-- Insert FEES_AND_DIVIDENDS for any user that had FEES_COMMISSION
INSERT INTO dashboard_preferences (user_id, context_type, context_id, widget_key, is_visible, sort_order, column_span)
SELECT dp.user_id, dp.context_type, dp.context_id, 'FEES_AND_DIVIDENDS', dp.is_visible, 5, 1
FROM dashboard_preferences dp
WHERE dp.widget_key = 'FEES_COMMISSION'
  AND NOT EXISTS (
    SELECT 1 FROM dashboard_preferences dp2
    WHERE dp2.user_id = dp.user_id
      AND dp2.context_type = dp.context_type
      AND COALESCE(dp2.context_id, 0) = COALESCE(dp.context_id, 0)
      AND dp2.widget_key = 'FEES_AND_DIVIDENDS'
  );
