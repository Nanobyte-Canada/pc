# Dashboard Data Display Fixes — Design Spec

## Problem

Portfolio value, available cash, buying power, and pending orders are displayed incorrectly on the dashboard and individual account pages.

**Expected correct values (per account):**

| Account | Portfolio Value | CAD Cash | USD Cash |
|---------|----------------|----------|----------|
| 9683 (LIRA) | 41,960 | 28,563.81 | 565.27 |
| 8214 (Locked-In RRSP) | 32,072.83 | 15,403.76 | 590.03 |
| 4150 (Margin) | 84.97 | 49.31 | 25.63 |
| 2736 (RRSP) | 24,165.45 | 3,343.12 | 1,482.29 |
| 0190 (TFSA) | 106,595.38 | 60,736.09 | 3,574.77 |

Total portfolio value: ~205,000 CAD. Additionally, 4 PENDING orders exist at the broker but are not displayed.

## Root Causes

### 1. Missing USD Cash in Balance Snapshots
The `PositionFetchService.saveBalanceSnapshot()` builds its cash map from the `balances[]` array returned by SnapTrade's `getHoldings()` endpoint. For Questrade accounts, this combined endpoint only returns a single aggregated CAD balance — no USD breakdown. The dedicated `getBalances()` endpoint (`getUserAccountBalance`) is more likely to return per-currency entries but is never called during position sync.

**Evidence:** All `broker_balance_snapshots.cash` JSONB entries contain only `cash_CAD` — no `cash_USD` keys exist in the database.

### 2. Portfolio Value Staleness
`connection.totalValue` is set from SnapTrade's `holdings.totalValue` (FX-converted to CAD). The stored values differ from expected values because data is stale from the last sync time. The computation pipeline itself is correct.

### 3. Orders Not Synced from Broker
The `trade_orders` table is empty. Orders are only created when the app places orders via `TradingController`. There is no ingestion/sync of existing broker orders from SnapTrade. The `getHoldings()` response includes an `orders` field that the adapter **discards**.

The `TradeOrder` entity requires a non-null `group_id` (portfolio group), but broker-originated orders don't belong to any portfolio group — this blocks storing external orders.

## Approach: Fix Data Layer

### Fix 1: Separate Balance Fetch for Multi-Currency Support

**Change:** In `PositionFetchService.executePositionFetch()`, after calling `getHoldings()`, also call `snapTradeService.getAccountBalance()` to get per-currency balance entries.

**Logic:**
```
try:
    balances = snapTradeService.getAccountBalance(user, accountId)
    // Always prefer dedicated balance endpoint (more granular)
    saveBalanceSnapshot(connection, balances, portfolioValue)
catch:
    // Fall back to holdings.balances if dedicated call fails
    saveBalanceSnapshot(connection, holdings.balances, portfolioValue)
```

**Files:**
- `PositionFetchService.kt` — modify `executePositionFetch()` to call `getAccountBalance()` and pass result to `saveBalanceSnapshot()`

### Fix 2: Order Sync from SnapTrade

**New adapter method:** `listAccountOrders(userId, userSecret, accountId)` → `List<SnapTradeAccountOrderDto>`

**New DTO:** `SnapTradeAccountOrderDto` with fields:
- `brokerageOrderId: String?`
- `status: String?` (PENDING, EXECUTED, CANCELED, NONE, etc.)
- `symbol: String?`
- `action: String?` (BUY, SELL)
- `totalQuantity: BigDecimal?`
- `openQuantity: BigDecimal?`
- `filledQuantity: BigDecimal?`
- `executionPrice: BigDecimal?`
- `limitPrice: BigDecimal?`
- `stopPrice: BigDecimal?`
- `orderType: String?` (Market, Limit)
- `timeInForce: String?`
- `timePlaced: OffsetDateTime?`
- `timeUpdated: OffsetDateTime?`
- `timeExecuted: OffsetDateTime?`

**SDK mapping:** Uses `snaptrade.accountInformation.getUserAccountOrders(userId, userSecret, accountId).execute()` and maps the SDK `AccountOrderRecord` to the DTO.

**Service method:** `SnapTradeService.listOrders(user, accountId)` — decrypts credentials and delegates to adapter.

**Sync scope:** Sync ALL orders from SnapTrade (not just pending). The dashboard's `getOpenOrders()` already filters by status (`PENDING`, `SUBMITTED`, `PARTIALLY_FILLED`). Storing all orders provides order history.

**Sync logic in `PositionFetchService`:**
```kotlin
fun syncOrders(connection, user, accountId) {
    val orders = snapTradeService.listOrders(user, accountId)
    for (order in orders) {
        val existing = tradeOrderRepository.findByBrokerOrderId(order.brokerageOrderId)
        if (existing != null) {
            // Update status, filled fields
            existing.status = mapOrderStatus(order.status)
            existing.filledUnits = order.filledQuantity
            // ...
        } else {
            // Create new TradeOrder
            TradeOrder(
                user = user,
                group = null,  // external orders have no group
                connection = connection,
                symbol = order.symbol,
                action = mapOrderAction(order.action),
                // ...
            )
        }
    }
}
```

**Status mapping:**
- SnapTrade `PENDING` → `OrderStatus.PENDING`
- SnapTrade `EXECUTED` → `OrderStatus.FILLED`
- SnapTrade `CANCELED` → `OrderStatus.CANCELLED`
- SnapTrade `NONE` / other → `OrderStatus.PENDING`

**Schema migration (V61):**
```sql
ALTER TABLE trade_orders ALTER COLUMN group_id DROP NOT NULL;
```

**Entity change:** Make `TradeOrder.group` nullable (`@ManyToOne` with `nullable = true` in `@JoinColumn`).

**Repository:** Add `findByBrokerOrderId(brokerOrderId: String): TradeOrder?`

### Fix 3: Dashboard Open Orders Query Adjustment

The existing `DashboardDataService.getOpenOrders()` queries `tradeOrderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc()`. This will automatically pick up synced orders once they're in the database. No logic change needed — just verify:
1. The repository query works with nullable `group_id`
2. `order.connection.accountName` doesn't cause lazy-loading issues (the existing query joins connection)

### Fix 4: Frontend Verification

After backend fixes, verify these widgets render correctly:
- `AvailableCashWidget.tsx` — should show CAD + USD entries from `DashboardCashResponse.availableCash`
- `BuyingPowerWidget.tsx` — should show per-currency buying power
- `PortfolioValueWidget.tsx` — should reflect updated `totalValue`
- `OpenOrdersWidget.tsx` — should list synced PENDING orders

No frontend code changes expected unless the widget doesn't handle multi-currency display.

## Files Changed

| File | Change |
|------|--------|
| `PositionFetchService.kt` | Call separate `getAccountBalance()`, sync orders |
| `SnapTradeAdapter.kt` | Add `listAccountOrders()` method |
| `SnapTradeAdapterImpl.kt` | Implement `listAccountOrders()` with SDK mapping |
| `SnapTradeDtos.kt` | Add `SnapTradeAccountOrderDto` |
| `SnapTradeService.kt` | Add `listOrders()` method |
| `TradeOrder.kt` | Make `group` nullable |
| `TradeOrderRepository.kt` | Add `findByBrokerOrderId()` |
| `V61__order_sync_support.sql` | `ALTER TABLE trade_orders ALTER COLUMN group_id DROP NOT NULL` |
| Frontend widgets | Verify display (no changes expected) |

## Testing

- Add/update backend unit tests for `PositionFetchService` (balance snapshot with multi-currency, order sync logic)
- Add unit tests for `SnapTradeAdapterImpl.listAccountOrders()` mapping
- Add unit test for order status mapping (SnapTrade status → `OrderStatus` enum)
- Update any existing tests that assume `TradeOrder.group` is non-null
- Verify existing frontend tests still pass after any widget changes

## Documentation

- Update `CLAUDE.md` if new API routes, entity relationships, or migration counts change
- Update any existing markdown documentation that references trade orders or balance storage

## Cleanup

- Remove obsolete code paths that are no longer needed after the fixes
- Delete any dead code or unused imports introduced by the changes
- Clean up any commented-out code or temporary debugging artifacts
- Ensure no orphaned files remain from the refactoring

## Verification

1. Trigger a position sync for all 5 active accounts
2. Query `broker_balance_snapshots.cash` — should now contain `cash_CAD`, `cash_USD`, `buying_power_CAD`, `buying_power_USD` entries
3. Query `trade_orders` — should contain synced orders with PENDING/FILLED/CANCELLED statuses
4. Check dashboard: portfolio value, cash breakdown, buying power, and open orders should display correctly
5. Check individual account pages: per-account values should match expected numbers
6. Run `npm run build` (frontend) — no compilation errors
7. Run `docker compose exec backend ./gradlew test` — all backend tests pass
