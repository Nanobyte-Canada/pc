-- Merge POSITIONS_TABLE + HOLDINGS_TABLE into single POSITIONS_HOLDINGS widget
-- Follows same pattern as V63 (OPEN_ORDERS + PENDING_ORDERS -> ORDERS)

INSERT INTO dashboard_preferences (user_id, context_type, context_id, widget_key, is_visible, sort_order, column_span)
SELECT dp.user_id, dp.context_type, dp.context_id, 'POSITIONS_HOLDINGS', dp.is_visible, 10, 4
FROM dashboard_preferences dp
WHERE dp.widget_key = 'POSITIONS_TABLE'
  AND NOT EXISTS (
    SELECT 1 FROM dashboard_preferences dp2
    WHERE dp2.user_id = dp.user_id
      AND dp2.context_type = dp.context_type
      AND COALESCE(dp2.context_id, 0) = COALESCE(dp.context_id, 0)
      AND dp2.widget_key = 'POSITIONS_HOLDINGS'
  );
