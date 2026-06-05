# Order Status Sync + Questrade Symbol Resolution — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add near-real-time order status updates via a 60-second scheduled sync that reuses existing order sync logic, plus auto-resolve Questrade option symbol IDs before placing orders.

**Architecture:** Feature 1: Extract `syncOrdersForConnection()` from `PositionFetchService` as a public method, create a lightweight `OrderStatusSyncScheduler` that runs every 60s for active orders, and wire notifications on status changes. Feature 2: Add `resolveOptionSymbolId()` to `QuestradeAdapter` that searches Questrade's symbol API and caches results in a `ConcurrentHashMap`.

**Tech Stack:** Kotlin/Spring Boot, `@Scheduled`, NotificationService, Questrade REST API, ConcurrentHashMap cache

**Constraints:** No JDK on host — all backend in Docker. MockK for testing. broker-gateway and portfolio are separate services.

---

## File Map

| File | Service | Action | Responsibility |
|------|---------|--------|----------------|
| `backend/portfolio/.../service/PositionFetchService.kt` | Portfolio | Modify | Extract `syncOrdersForConnection()` as public, add notification on status change |
| `backend/portfolio/.../scheduler/OrderStatusSyncScheduler.kt` | Portfolio | Create | 60s scheduler for active order sync |
| `backend/portfolio/.../repository/TradeOrderRepository.kt` | Portfolio | Modify | Add query for recent active orders |
| `backend/broker-gateway/.../questrade/QuestradeAdapter.kt` | Gateway | Modify | Add `resolveOptionSymbolId()`, fix `placeOrder()` |
| `frontend/src/components/wheel/OrderPanel.tsx` | Frontend | Modify | Show toast after submission |

---

### Task 1: Extract syncOrdersForConnection and add notification wiring

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/TradeOrderRepository.kt`

- [ ] **Step 1: Add repository query for recent active orders**

Add to `TradeOrderRepository.kt`:

```kotlin
fun findByStatusInAndCreatedAtAfter(
    statuses: List<OrderStatus>,
    after: java.time.OffsetDateTime
): List<TradeOrder>
```

- [ ] **Step 2: Inject NotificationService into PositionFetchService**

Add `NotificationService` to the constructor of `PositionFetchService`. It's in `com.portfolio.broker.service.NotificationService`. Add the import and constructor parameter.

- [ ] **Step 3: Extract syncOrdersForConnection as public method**

The existing `private fun syncOrders(connection, user, gwConnId, accountId)` method (around line 239) needs to become public so the new scheduler can call it. Rename to `syncOrdersForConnection`:

```kotlin
fun syncOrdersForConnection(
    connection: BrokerConnection,
    user: com.portfolio.auth.entity.User,
    gwConnId: String,
    accountId: String
) {
    // Same existing body, but add notification on status change
}
```

Keep the existing `syncOrders` private method and have it delegate to the new public one (or just rename — the existing caller is `executePositionFetch` in the same class).

- [ ] **Step 4: Add notification on status change**

Inside the existing block where an order status is updated (around line 272), add notification creation when the status transitions to a terminal state. After `tradeOrderRepository.save(existing)`:

```kotlin
if (existing.status != status) {
    val oldStatus = existing.status
    existing.status = status
    // ... rest of update logic ...
    tradeOrderRepository.save(existing)

    // Notify on terminal status changes
    if (status == OrderStatus.FILLED && oldStatus != OrderStatus.FILLED) {
        try {
            notificationService.createNotification(
                user = user,
                type = NotificationType.ORDER_FILLED,
                title = "Order Filled",
                message = "${existing.action} ${existing.filledUnits ?: existing.requestedUnits} ${existing.symbol} filled at ${existing.filledPrice ?: "market"}",
                link = null
            )
        } catch (e: Exception) {
            log.warn("Failed to create fill notification: {}", e.message)
        }
    } else if (status == OrderStatus.REJECTED) {
        try {
            notificationService.createNotification(
                user = user,
                type = NotificationType.ORDER_REJECTED,
                title = "Order Rejected",
                message = "${existing.action} ${existing.requestedUnits} ${existing.symbol} was rejected",
                link = null
            )
        } catch (e: Exception) {
            log.warn("Failed to create reject notification: {}", e.message)
        }
    }
}
```

Note: Need to capture old status before updating to detect the transition. Restructure the update logic to:
1. Read old status
2. Check if status changed
3. Apply update
4. Save
5. Create notification if terminal change

- [ ] **Step 5: Rebuild portfolio service**

Run: `docker compose up --build -d backend`
Wait for healthy.

- [ ] **Step 6: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/TradeOrderRepository.kt
git commit -m "feat(orders): extract syncOrdersForConnection, add fill/reject notifications"
```

---

### Task 2: Create OrderStatusSyncScheduler

**Files:**
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/scheduler/OrderStatusSyncScheduler.kt`

- [ ] **Step 1: Create the scheduler**

```kotlin
package com.portfolio.broker.scheduler

import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.TradeOrderRepository
import com.portfolio.broker.service.PositionFetchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
@ConditionalOnProperty(
    prefix = "broker.sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class OrderStatusSyncScheduler(
    private val tradeOrderRepository: TradeOrderRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val positionFetchService: PositionFetchService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun syncActiveOrders() {
        val cutoff = OffsetDateTime.now().minusHours(24)
        val activeOrders = tradeOrderRepository.findByStatusInAndCreatedAtAfter(
            listOf(OrderStatus.SUBMITTED, OrderStatus.PARTIALLY_FILLED),
            cutoff
        )

        if (activeOrders.isEmpty()) return

        val ordersByConnection = activeOrders.groupBy { it.connection.id }
        log.info("Order status sync: {} active orders across {} connections",
            activeOrders.size, ordersByConnection.size)

        for ((connectionId, orders) in ordersByConnection) {
            try {
                val connection = orders.first().connection
                if (connection.gatewayConnectionId == null || connection.accountIdExternal == null) continue

                positionFetchService.syncOrdersForConnection(
                    connection = connection,
                    user = connection.user,
                    gwConnId = connection.gatewayConnectionId!!,
                    accountId = connection.accountIdExternal!!
                )
            } catch (e: Exception) {
                log.warn("Order sync failed for connection {}: {}", connectionId, e.message)
            }
        }
    }
}
```

- [ ] **Step 2: Rebuild and verify**

Run: `docker compose up --build -d backend`
Wait for healthy. Check logs for "Order status sync" messages (will only appear if there are active orders).

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/scheduler/OrderStatusSyncScheduler.kt
git commit -m "feat(scheduler): add 60s order status sync for active orders"
```

---

### Task 3: Questrade symbol resolution

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt`

- [ ] **Step 1: Add symbol cache and resolution method**

Add a `ConcurrentHashMap` cache as a class field and a `resolveOptionSymbolId` method to `QuestradeAdapter`:

```kotlin
private val symbolIdCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

private fun resolveOptionSymbolId(
    creds: BrokerCredentials.QuestradeCredentials,
    symbol: String,
    strike: java.math.BigDecimal,
    expiry: String,
    optionType: String
): Long? {
    val cacheKey = "$symbol:$strike:$expiry:$optionType"
    symbolIdCache[cacheKey]?.let { return it }

    try {
        val response = restClient.get(creds.apiServerUrl, creds.accessToken,
            "/v1/symbols/search?prefix=$symbol")
        val symbols = response.path("symbols")

        for (sym in symbols) {
            val symType = sym.path("securityType").asText("")
            if (symType != "Option") continue

            val symUnderlying = sym.path("underlyingSymbol").asText("")
            if (symUnderlying != symbol && !symUnderlying.startsWith(symbol)) continue

            val symStrike = sym.path("strikePrice").decimalValue()
            val symExpiry = sym.path("expiryDate").asText("").take(10)
            val symRight = sym.path("optionRight").asText("")

            val rightMatch = when (optionType.uppercase()) {
                "CALL" -> symRight == "Call"
                "PUT" -> symRight == "Put"
                else -> false
            }

            if (symStrike.compareTo(strike) == 0 && symExpiry == expiry && rightMatch) {
                val resolvedId = sym.path("symbolId").asLong()
                if (resolvedId > 0) {
                    symbolIdCache[cacheKey] = resolvedId
                    log.info("Resolved Questrade symbolId for {} {} {} {}: {}", symbol, strike, expiry, optionType, resolvedId)
                    return resolvedId
                }
            }
        }

        log.warn("Could not resolve Questrade symbolId for {} {} {} {}", symbol, strike, expiry, optionType)
        return null
    } catch (e: Exception) {
        log.error("Questrade symbol search failed for {}: {}", symbol, e.message)
        return null
    }
}
```

- [ ] **Step 2: Update placeOrder to resolve symbolId for options**

In the `placeOrder` method, add resolution before building the order body. Replace the current `placeOrder` implementation:

```kotlin
override fun placeOrder(
    credentials: BrokerCredentials, accountId: String, request: OrderRequest
): OrderResult {
    val creds = credentials as BrokerCredentials.QuestradeCredentials

    // Resolve symbolId for options if not provided
    val resolvedSymbolId: Any = if (request.optionType != null && request.symbolId == null
        && request.strike != null && request.expiry != null) {
        resolveOptionSymbolId(creds, request.symbol, request.strike, request.expiry, request.optionType)
            ?: throw IllegalArgumentException("Could not resolve Questrade symbolId for ${request.symbol} ${request.optionType} ${request.strike} ${request.expiry}")
    } else {
        request.symbolId ?: request.symbol
    }

    val body = mutableMapOf<String, Any?>(
        "symbolId" to resolvedSymbolId,
        "quantity" to request.quantity,
        "orderType" to when (request.orderType) {
            OrderType.MARKET -> "Market"; OrderType.LIMIT -> "Limit"
            OrderType.STOP -> "Stop"; OrderType.STOP_LIMIT -> "StopLimit"
        },
        "timeInForce" to when (request.timeInForce) {
            TimeInForce.DAY -> "Day"; TimeInForce.GTC -> "GoodTillCanceled"
            TimeInForce.IOC -> "ImmediateOrCancel"; TimeInForce.FOK -> "FillOrKill"
        },
        "action" to if (request.action == OrderAction.BUY) "Buy" else "Sell",
        "limitPrice" to request.limitPrice,
        "stopPrice" to request.stopPrice
    )
    request.primaryRoute?.let { body["primaryRoute"] = it }
    request.secondaryRoute?.let { body["secondaryRoute"] = it }
    val filteredBody = body.filterValues { it != null }

    val response = restClient.post(creds.apiServerUrl, creds.accessToken,
        "/v1/accounts/$accountId/orders", filteredBody)
    val orderId = response.get("orderId")?.asText() ?: response.get("id")?.asText()
    return OrderResult(brokerOrderId = orderId, status = OrderStatus.SUBMITTED)
}
```

Key changes from original:
- Calls `resolveOptionSymbolId()` when `optionType` is set but `symbolId` is null
- Throws `IllegalArgumentException` if resolution fails (clearer than sending a ticker string)
- Uses `resolvedSymbolId` in the body map instead of `request.symbolId ?: request.symbol`

- [ ] **Step 3: Rebuild broker-gateway**

Run: `docker compose up --build -d broker-gateway-service`
Wait for healthy.

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt
git commit -m "feat(questrade): add option symbol resolution with in-memory cache"
```

---

### Task 4: Frontend toast after order submission

**Files:**
- Modify: `frontend/src/components/wheel/OrderPanel.tsx`

- [ ] **Step 1: Import useToast and show success/error toast**

Add import:
```typescript
import { useToast } from '@/stores/toastStore'
```

Inside the component, add:
```typescript
const { success, error: showError } = useToast()
```

Update `handleSubmitOrder` — after successful submission, show toast before closing:

```typescript
const handleSubmitOrder = useCallback(async (action: 'BUY' | 'SELL') => {
  setSubmitting(true)
  setSubmitError(null)
  try {
    // ... existing submission code ...
    await submitOptionsOrder({ ... })
    success(`${action} order submitted for ${ticker}`)
    onClose()
  } catch (err) {
    const msg = err instanceof Error ? err.message : 'Order failed'
    setSubmitError(msg)
    showError(msg)
  } finally {
    setSubmitting(false)
  }
}, [/* deps + success, showError */])
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wheel/OrderPanel.tsx
git commit -m "feat(ui): show toast notification on order submission success/failure"
```

---

### Task 5: Deploy and verify end-to-end

- [ ] **Step 1: Rebuild all services**

```bash
docker compose up --build -d
```

- [ ] **Step 2: Verify order status sync**

Submit an order via the wheel UI. Check:
- `trade_orders` shows the order with initial status
- Backend logs show "Order status sync" running every 60 seconds
- After sync runs, order status may update if broker processed it
- Notification created if order fills or is rejected

- [ ] **Step 3: Verify Questrade symbol resolution**

Submit an options order for a Questrade account. Check gateway logs:
- "Resolved Questrade symbolId for SOXL 70 2026-06-19 PUT: {id}"
- Order body sent to Questrade has numeric symbolId, not ticker string
- If Questrade token is expired, verify error message is clear

- [ ] **Step 4: Verify toast notification**

After clicking Buy/Sell:
- Success: green toast "BUY order submitted for SOXL"
- Failure: red toast with error message + error displayed in panel

- [ ] **Step 5: Commit docs**

```bash
git add docs/reference/
git commit -m "docs: update references for order status sync and Questrade symbol resolution"
```
