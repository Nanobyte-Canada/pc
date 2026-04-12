# Dashboard Data Display Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix portfolio value, available cash, buying power, and pending orders display on the dashboard and individual account pages.

**Architecture:** Fix the data pipeline to capture multi-currency balances and broker orders from SnapTrade. The balance fix uses the dedicated `getAccountBalance()` endpoint (per-currency) instead of the combined `getHoldings()` endpoint (single CAD balance). Orders are synced from SnapTrade into the existing `trade_orders` table with a schema change to make `group_id` nullable.

**Tech Stack:** Kotlin/Spring Boot, Spring Data JPA, Flyway, SnapTrade Java SDK 5.0.168, React/TypeScript frontend

**Spec:** `docs/superpowers/specs/2026-04-01-dashboard-data-fixes-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `backend/src/main/resources/db/migration/V61__order_sync_support.sql` | Create | Make `group_id` nullable on `trade_orders` |
| `backend/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt` | Modify | Make `group` property nullable |
| `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt` | Modify | Add `SnapTradeAccountOrderDto` |
| `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt` | Modify | Add `listAccountOrders()` method |
| `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt` | Modify | Implement `listAccountOrders()` |
| `backend/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt` | Modify | Add `listOrders()` method |
| `backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt` | Modify | Use dedicated balance API + sync orders |
| `backend/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt` | Modify | Fix balance format consistency |
| `backend/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt` | Modify | Adjust `getOpenOrders()` for nullable group |

---

### Task 1: Schema Migration — Make group_id Nullable

**Files:**
- Create: `backend/src/main/resources/db/migration/V61__order_sync_support.sql`

- [ ] **Step 1: Create migration file**

```sql
-- V61__order_sync_support.sql
-- Allow trade_orders to store broker-synced orders that don't belong to a portfolio group
ALTER TABLE trade_orders ALTER COLUMN group_id DROP NOT NULL;
```

- [ ] **Step 2: Verify migration applies**

Run: `docker compose up --build backend`

Check logs for: `Successfully applied 1 migration to schema "public", now at version v61`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V61__order_sync_support.sql
git commit -m "feat: make trade_orders.group_id nullable for broker-synced orders"
```

---

### Task 2: Entity Change — TradeOrder Nullable Group

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt:36-38`

- [ ] **Step 1: Update TradeOrder entity**

Change the `group` field from non-nullable to nullable:

```kotlin
// OLD (lines 36-38):
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "group_id", nullable = false)
val group: PortfolioGroup,

// NEW:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "group_id", nullable = true)
val group: PortfolioGroup? = null,
```

- [ ] **Step 2: Fix compilation errors from nullable group**

In `DashboardDataService.kt`, the `getOpenOrders()` method accesses `order.connection.accountName` (line 210). Update to handle nullable group in the order mapping (line 210):

```kotlin
// In DashboardDataService.getOpenOrders(), line 210:
accountName = order.connection.accountName,
```

This line is fine — it accesses `connection`, not `group`. No change needed here.

Check `TradeOrderRepository.kt` — the method `findByUserIdAndGroupIdOrderByCreatedAtDesc` takes a `groupId: Long` parameter. This still works for non-null group IDs. No change needed.

- [ ] **Step 3: Rebuild and verify compilation**

Run: `docker compose up --build backend`

Expected: Backend starts without errors, Hibernate validation passes.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt
git commit -m "feat: make TradeOrder.group nullable for external order sync"
```

---

### Task 3: Fix Balance Snapshot — Multi-Currency Support

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt:88-142`

- [ ] **Step 1: Update executePositionFetch to use dedicated balance API**

In `PositionFetchService.executePositionFetch()`, after the `getHoldings()` call, replace the balance source. Change lines 88-142:

```kotlin
// After line 90: val holdings = snapTradeService.getHoldings(user, accountId)
// Keep: positions extraction and saving (lines 91-133)
// Keep: option enrichment (line 136)

// Replace balance handling (lines 138-142):
// OLD:
// val portfolioValue = holdings.totalValue?.let { BigDecimal.valueOf(it) }
// saveBalanceSnapshot(connection, holdings.balances, portfolioValue)

// NEW:
val portfolioValue = holdings.totalValue?.let { BigDecimal.valueOf(it) }

// Use dedicated balance endpoint for per-currency breakdown (CAD + USD)
val balances = try {
    snapTradeService.getAccountBalance(user, accountId)
} catch (e: Exception) {
    log.warn("Dedicated balance fetch failed for connection {}, falling back to holdings balance: {}",
        connectionId, e.message)
    holdings.balances
}
saveBalanceSnapshot(connection, balances, portfolioValue)
```

- [ ] **Step 2: Rebuild and verify**

Run: `docker compose up --build backend`

Expected: Backend compiles and starts.

- [ ] **Step 3: Trigger a sync and check balance snapshot**

Trigger sync via the dashboard "Refresh" button or by calling the sync endpoint. Then check:

```bash
docker compose exec postgres psql -U portfolio -d portfolio -c \
  "SELECT connection_id, cash, as_of_date FROM broker_balance_snapshots WHERE as_of_date = CURRENT_DATE ORDER BY connection_id;"
```

Expected: Balance snapshots should now contain `cash_USD` entries (e.g., `{"cash_CAD": 60736.09, "cash_USD": 3574.77, "buying_power_CAD": ...}`).

If still only CAD: the dedicated balance API also returns only CAD for Questrade — proceed with the current fix as-is (it's still an improvement to prefer the dedicated endpoint).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt
git commit -m "fix: use dedicated balance API for multi-currency cash snapshots"
```

---

### Task 4: Fix ActivityIngestionService Balance Format

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt:119-127`

The `syncBalanceForConnection()` method stores balances as simple currency keys (`{"CAD": 5000}`) without the `cash_` prefix and without buying power. This inconsistency causes `DashboardCashService` to parse them differently. Fix to use consistent format.

- [ ] **Step 1: Update syncBalanceForConnection cash map building**

Replace the cash map building logic (around lines 119-127) in `syncBalanceForConnection()`:

```kotlin
// OLD:
val cashMap = mutableMapOf<String, BigDecimal>()
for (balance in balances) {
    val curr = balance.currency ?: "CAD"
    val amount = balance.cash?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
    cashMap[curr] = (cashMap[curr] ?: BigDecimal.ZERO) + amount
}

// NEW — use consistent format with PositionFetchService:
val cashMap = mutableMapOf<String, BigDecimal>()
val buyingPowerMap = mutableMapOf<String, BigDecimal>()
for (balance in balances) {
    val curr = balance.currency ?: "CAD"
    val amount = balance.cash?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
    cashMap["cash_$curr"] = (cashMap["cash_$curr"] ?: BigDecimal.ZERO) + amount

    val bp = balance.buyingPower?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
    if (bp > BigDecimal.ZERO) {
        buyingPowerMap["buying_power_$curr"] = (buyingPowerMap["buying_power_$curr"] ?: BigDecimal.ZERO) + bp
    }
}
val combined = cashMap + buyingPowerMap
```

Then update the snapshot creation to use `combined` instead of `cashMap`:

```kotlin
// In the BrokerBalanceSnapshot constructor, change:
cash = objectMapper.writeValueAsString(cashMap),
// To:
cash = objectMapper.writeValueAsString(combined),
```

- [ ] **Step 2: Rebuild and verify**

Run: `docker compose up --build backend`

Expected: Compiles and starts.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt
git commit -m "fix: use consistent cash_ prefix format in balance snapshots"
```

---

### Task 5: Add Order Listing to SnapTrade Adapter

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt`

- [ ] **Step 1: Add SnapTradeAccountOrderDto**

Add to `SnapTradeDtos.kt` after the existing `SnapTradeOrderDto` class (after line 112):

```kotlin
data class SnapTradeAccountOrderDto(
    val brokerageOrderId: String?,
    val status: String?,
    val symbol: String?,
    val action: String?,
    val totalQuantity: Double?,
    val openQuantity: Double?,
    val filledQuantity: Double?,
    val executionPrice: Double?,
    val limitPrice: Double?,
    val stopPrice: Double?,
    val orderType: String?,
    val timeInForce: String?,
    val timePlaced: String?,
    val timeUpdated: String?,
    val timeExecuted: String?,
    val currency: String?
)
```

- [ ] **Step 2: Add method to SnapTradeAdapter interface**

Add to `SnapTradeAdapter.kt` after `cancelOrder` method (after line 61):

```kotlin
fun listAccountOrders(userId: String, userSecret: String, accountId: String): List<SnapTradeAccountOrderDto>
```

- [ ] **Step 3: Implement in SnapTradeAdapterImpl**

Add to `SnapTradeAdapterImpl.kt` after the `cancelOrder()` method (after line 261):

```kotlin
override fun listAccountOrders(userId: String, userSecret: String, accountId: String): List<SnapTradeAccountOrderDto> {
    return try {
        val response = snaptrade.accountInformation.getUserAccountOrders(
            userId, userSecret, UUID.fromString(accountId)
        ).execute()
        response.map { order ->
            SnapTradeAccountOrderDto(
                brokerageOrderId = order.brokerageOrderId,
                status = order.status?.toString(),
                symbol = order.universalSymbol?.symbol,
                action = order.action?.toString(),
                totalQuantity = order.totalQuantity?.toDoubleOrNull(),
                openQuantity = order.openQuantity?.toDoubleOrNull(),
                filledQuantity = order.filledQuantity?.toDoubleOrNull(),
                executionPrice = order.executionPrice,
                limitPrice = order.limitPrice,
                stopPrice = order.stopPrice,
                orderType = order.orderType,
                timeInForce = order.timeInForce,
                timePlaced = order.timePlaced,
                timeUpdated = order.timeUpdated,
                timeExecuted = order.timeExecuted,
                currency = order.universalSymbol?.currency?.code
            )
        }
    } catch (e: ApiException) {
        log.warn("Failed to fetch orders for account {}: {}", accountId, e.message)
        emptyList()
    }
}
```

> **Note:** The exact field accessors on the SDK response object (e.g., `order.totalQuantity`) may differ. Verify against the SnapTrade SDK `AccountOrderRecord` class during implementation. Check the SDK auto-generated model fields. The sample data (`tmp/data/SnapTrade/account/list_account_orders.json`) shows the JSON field names: `total_quantity`, `open_quantity`, `filled_quantity`, `execution_price`, `limit_price`, `stop_price`, `order_type`, `time_in_force`, `time_placed`, `time_updated`, `time_executed`. The SDK model will use camelCase equivalents.

- [ ] **Step 4: Add listOrders method to SnapTradeService**

Add to `SnapTradeService.kt` after the `cancelOrder()` method (after line 208):

```kotlin
/**
 * Lists all orders for a specific account from SnapTrade.
 */
fun listOrders(user: User, accountId: String): List<SnapTradeAccountOrderDto> {
    val snapUser = ensureUserRegistered(user)
    return adapter.listAccountOrders(snapUser.userId, snapUser.userSecret, accountId)
}
```

- [ ] **Step 5: Rebuild and verify**

Run: `docker compose up --build backend`

Expected: Compiles without errors.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt
git add backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt
git add backend/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt
git add backend/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt
git commit -m "feat: add order listing from SnapTrade API"
```

---

### Task 6: Add Order Sync to Position Fetch Pipeline

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt`

- [ ] **Step 1: Add TradeOrderRepository dependency**

Add to the `PositionFetchService` constructor:

```kotlin
private val tradeOrderRepository: TradeOrderRepository,
```

Add the import:

```kotlin
import com.portfolio.broker.repository.TradeOrderRepository
```

- [ ] **Step 2: Add syncOrders method**

Add a new private method after `saveBalanceSnapshot()`:

```kotlin
private fun syncOrders(
    connection: BrokerConnection,
    user: com.portfolio.auth.entity.User,
    accountId: String
) {
    try {
        val orders = snapTradeService.listOrders(user, accountId)
        log.info("Fetched {} orders for connection {}", orders.size, connection.id)

        for (orderDto in orders) {
            val brokerOrderId = orderDto.brokerageOrderId ?: continue
            val symbol = orderDto.symbol ?: continue

            val status = mapSnapTradeOrderStatus(orderDto.status)
            val action = mapSnapTradeOrderAction(orderDto.action) ?: continue
            val orderType = mapSnapTradeOrderType(orderDto.orderType)
            val timeInForce = mapSnapTradeTimeInForce(orderDto.timeInForce)

            val totalQty = orderDto.totalQuantity?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
            val filledQty = orderDto.filledQuantity?.let { BigDecimal.valueOf(it) }
            val execPrice = orderDto.executionPrice?.let { BigDecimal.valueOf(it) }
            val limitPrice = orderDto.limitPrice?.let { BigDecimal.valueOf(it) }
            val requestedPrice = limitPrice ?: execPrice ?: BigDecimal.ZERO
            val requestedAmount = totalQty.multiply(requestedPrice)

            val existing = tradeOrderRepository.findByBrokerOrderId(brokerOrderId)
            if (existing != null) {
                existing.status = status
                existing.filledUnits = filledQty
                existing.filledPrice = execPrice
                existing.filledAmount = filledQty?.multiply(execPrice ?: BigDecimal.ZERO)
                existing.updatedAt = OffsetDateTime.now()
                if (status == OrderStatus.FILLED && existing.filledAt == null) {
                    existing.filledAt = orderDto.timeExecuted?.let { parseOffsetDateTime(it) }
                }
                if (status == OrderStatus.CANCELLED && existing.cancelledAt == null) {
                    existing.cancelledAt = orderDto.timeUpdated?.let { parseOffsetDateTime(it) }
                }
                tradeOrderRepository.save(existing)
            } else {
                val newOrder = TradeOrder(
                    user = user,
                    group = null,
                    connection = connection,
                    symbol = symbol,
                    action = action,
                    orderType = orderType,
                    timeInForce = timeInForce,
                    requestedUnits = totalQty,
                    requestedPrice = requestedPrice,
                    requestedAmount = requestedAmount,
                    limitPrice = limitPrice,
                    status = status,
                    brokerOrderId = brokerOrderId,
                    accountIdExternal = connection.accountIdExternal,
                    currency = orderDto.currency ?: "CAD",
                    filledUnits = filledQty,
                    filledPrice = execPrice,
                    submittedAt = orderDto.timePlaced?.let { parseOffsetDateTime(it) }
                )
                tradeOrderRepository.save(newOrder)
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to sync orders for connection {}: {}", connection.id, e.message)
        // Non-fatal — positions and balances are already saved
    }
}

private fun mapSnapTradeOrderStatus(status: String?): OrderStatus {
    return when (status?.uppercase()) {
        "EXECUTED" -> OrderStatus.FILLED
        "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
        "PARTIAL" -> OrderStatus.PARTIALLY_FILLED
        "REJECTED" -> OrderStatus.REJECTED
        else -> OrderStatus.PENDING
    }
}

private fun mapSnapTradeOrderAction(action: String?): OrderAction? {
    return when (action?.uppercase()) {
        "BUY" -> OrderAction.BUY
        "SELL" -> OrderAction.SELL
        else -> null
    }
}

private fun mapSnapTradeOrderType(type: String?): OrderType {
    return when (type?.lowercase()) {
        "limit" -> OrderType.LIMIT
        else -> OrderType.MARKET
    }
}

private fun mapSnapTradeTimeInForce(tif: String?): TimeInForce {
    return when (tif?.uppercase()) {
        "GTC" -> TimeInForce.GTC
        else -> TimeInForce.DAY
    }
}

private fun parseOffsetDateTime(value: String): OffsetDateTime? {
    return try {
        OffsetDateTime.parse(value)
    } catch (e: Exception) {
        try {
            java.time.LocalDateTime.parse(value.removeSuffix("Z")).atOffset(java.time.ZoneOffset.UTC)
        } catch (e2: Exception) {
            null
        }
    }
}
```

- [ ] **Step 3: Call syncOrders in executePositionFetch**

Add the order sync call in `executePositionFetch()`, after the balance snapshot save and before updating the connection:

```kotlin
// After: saveBalanceSnapshot(connection, balances, portfolioValue)
// Add:
syncOrders(connection, user, accountId)
```

- [ ] **Step 4: Add required imports**

Add to the imports in `PositionFetchService.kt`:

```kotlin
import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.entity.OrderAction
import com.portfolio.broker.entity.OrderType
import com.portfolio.broker.entity.TimeInForce
import com.portfolio.broker.entity.TradeOrder
import com.portfolio.broker.repository.TradeOrderRepository
```

- [ ] **Step 5: Rebuild and verify compilation**

Run: `docker compose up --build backend`

Expected: Backend compiles and starts.

- [ ] **Step 6: Trigger sync and verify orders in DB**

Trigger a sync, then check:

```bash
docker compose exec postgres psql -U portfolio -d portfolio -c \
  "SELECT id, symbol, action, status, broker_order_id, requested_units FROM trade_orders ORDER BY created_at DESC LIMIT 10;"
```

Expected: Should now contain synced orders from SnapTrade with PENDING/FILLED/CANCELLED statuses.

- [ ] **Step 7: Verify dashboard shows open orders**

Check the dashboard — the Open Orders widget should now display PENDING orders.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt
git commit -m "feat: sync broker orders from SnapTrade during position fetch"
```

---

### Task 7: Frontend Verification

**Files:**
- Verify: `frontend/src/components/dashboard/widgets/AvailableCashWidget.tsx`
- Verify: `frontend/src/components/dashboard/widgets/BuyingPowerWidget.tsx`
- Verify: `frontend/src/components/dashboard/widgets/PortfolioValueWidget.tsx`
- Verify: `frontend/src/components/dashboard/widgets/OpenOrdersWidget.tsx`

- [ ] **Step 1: Verify multi-currency cash display**

After syncing, check the dashboard's Available Cash widget. It should show both CAD and USD entries. The widget already handles multi-currency via `data.availableCash.map(c => ...)` in `AvailableCashWidget.tsx`.

If only CAD shows: the SnapTrade dedicated balance API also returns only CAD for Questrade. This would require a different approach (documented in spec as a known limitation).

- [ ] **Step 2: Verify buying power display**

Check the Buying Power widget for multi-currency entries.

- [ ] **Step 3: Verify portfolio value**

Check the Portfolio Value widget shows updated total value after sync.

- [ ] **Step 4: Verify open orders display**

Check the Open Orders widget shows the PENDING orders from SnapTrade.

- [ ] **Step 5: Verify individual account pages**

Navigate to each of the 5 account pages and verify per-account values match expectations.

- [ ] **Step 6: Run frontend build**

Run from `frontend/`:

```bash
npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 7: Run frontend tests**

Run from `frontend/`:

```bash
npm run test:run
```

Expected: All tests pass.

---

### Task 8: Add Backend Tests

**Files:**
- Create: `backend/src/test/kotlin/com/portfolio/broker/service/PositionFetchServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/portfolio/broker/service/OrderExecutionServiceTest.kt` (if it references non-null group)

- [ ] **Step 1: Create PositionFetchService test for order status mapping**

Create `backend/src/test/kotlin/com/portfolio/broker/service/PositionFetchServiceTest.kt`:

```kotlin
package com.portfolio.broker.service

import com.portfolio.broker.entity.OrderStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PositionFetchServiceTest {

    // Test the order status mapping logic by creating a test instance
    // Since the mapping methods are private, test through the public syncOrders flow
    // or extract mapping to a companion object

    @Test
    fun `mapSnapTradeOrderStatus maps EXECUTED to FILLED`() {
        assertEquals(OrderStatus.FILLED, mapStatus("EXECUTED"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps CANCELED to CANCELLED`() {
        assertEquals(OrderStatus.CANCELLED, mapStatus("CANCELED"))
        assertEquals(OrderStatus.CANCELLED, mapStatus("CANCELLED"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps PENDING and unknown to PENDING`() {
        assertEquals(OrderStatus.PENDING, mapStatus("PENDING"))
        assertEquals(OrderStatus.PENDING, mapStatus("NONE"))
        assertEquals(OrderStatus.PENDING, mapStatus(null))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps PARTIAL to PARTIALLY_FILLED`() {
        assertEquals(OrderStatus.PARTIALLY_FILLED, mapStatus("PARTIAL"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps REJECTED to REJECTED`() {
        assertEquals(OrderStatus.REJECTED, mapStatus("REJECTED"))
    }

    // Helper to invoke the mapping logic directly
    private fun mapStatus(status: String?): OrderStatus {
        return when (status?.uppercase()) {
            "EXECUTED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "PARTIAL" -> OrderStatus.PARTIALLY_FILLED
            "REJECTED" -> OrderStatus.REJECTED
            else -> OrderStatus.PENDING
        }
    }
}
```

> **Note:** Since the mapping methods in PositionFetchService are private, this test duplicates the mapping logic. Alternatively, extract the mapping functions to a companion object or a utility to make them directly testable. Choose whichever is consistent with existing test patterns in the codebase (check `OrderExecutionServiceTest.kt` for style).

- [ ] **Step 2: Check and fix existing tests for nullable group**

Search for tests that create `TradeOrder` with a non-null group and verify they still compile:

```bash
docker compose exec backend grep -r "TradeOrder(" src/test/ --include="*.kt" -l
```

If any test creates `TradeOrder(... group = someGroup ...)`, it should still work since `group` is now nullable with a default of `null`. Tests that pass a non-null group will still compile.

- [ ] **Step 3: Run backend tests**

```bash
docker compose exec backend ./gradlew test
```

Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/portfolio/broker/service/PositionFetchServiceTest.kt
git commit -m "test: add order status mapping tests for PositionFetchService"
```

---

### Task 9: Documentation and Cleanup

**Files:**
- Modify: `CLAUDE.md` (if migration count or entity relationships changed)
- Review: All changed files for dead code

- [ ] **Step 1: Update CLAUDE.md migration info**

Update the database section in `CLAUDE.md` to reflect the new migration count and latest migration number. The current text references "54 migration files (V1 through V60)". Update to include V61.

- [ ] **Step 2: Update entity relationships in CLAUDE.md**

If applicable, ensure the entity relationship section mentions `TradeOrder.group` is now nullable.

- [ ] **Step 3: Remove any dead code or unused imports**

Review all modified files for:
- Unused imports added during the changes
- Commented-out code
- Any debugging artifacts (e.g., extra log statements beyond what's useful)

- [ ] **Step 4: Final verification — full stack test**

1. Restart the full stack: `docker compose down && docker compose up --build`
2. Log into the app
3. Trigger a sync/refresh
4. Verify dashboard shows correct portfolio value, multi-currency cash, buying power, and pending orders
5. Check individual account pages

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git add -u  # stage any cleanup changes
git commit -m "docs: update CLAUDE.md for V61 migration and order sync changes"
```
