# Phase 3b: Broker Adapter Options Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make broker adapters (IBKR and Questrade) correctly handle options orders by creating proper option contracts instead of stock orders, so that option orders submitted from the Wheel Strategy UI execute at the broker level.

**Architecture:** Add option-specific fields (`optionType`, `strike`, `expiry`) to the order DTOs at every layer (portfolio orderBody → gateway PlaceOrderRequest → OrderRequest → adapters). Extend `IbkrContract` with option fields and update `IbkrAdapter.placeOrder()` to create OPT contracts when optionType is present. For Questrade, the adapter already sends `symbolId` which the frontend/portfolio layer must provide — no adapter change needed beyond logging. Update fake adapters for testing.

**Tech Stack:** Kotlin, Spring Boot, broker-gateway microservice (port 8084)

**Constraints:**
- No JDK on host — build/test in Docker: `docker compose exec broker-gateway-service ./gradlew test`
- MockK for mocking

---

## File Map

| File | Service | Action | Responsibility |
|------|---------|--------|----------------|
| `backend/portfolio/.../service/OrderExecutionService.kt` | Portfolio | Modify | Add optionType/strike/expiry to orderBody map |
| `backend/broker-gateway/.../api/dto/ApiDtos.kt` | Gateway | Modify | Add option fields to PlaceOrderRequest |
| `backend/broker-gateway/.../adapter/dto/OrderRequest.kt` | Gateway | Modify | Add option fields to OrderRequest |
| `backend/broker-gateway/.../api/controller/OrderController.kt` | Gateway | Modify | Pass option fields through to OrderRequest |
| `backend/broker-gateway/.../adapter/ibkr/IbkrAccountClient.kt` | Gateway | Modify | Extend IbkrContract with option fields |
| `backend/broker-gateway/.../adapter/ibkr/IbkrAdapter.kt` | Gateway | Modify | Build OPT contract when optionType present |
| `backend/broker-gateway/.../adapter/ibkr/FakeIbkrAdapter.kt` | Gateway | Modify | Log option details in fake order |
| `backend/broker-gateway/.../adapter/questrade/FakeQuestradeAdapter.kt` | Gateway | Modify | Log option details in fake order |

---

### Task 1: Add option fields to gateway DTOs and pass them through

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/OrderRequest.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/OrderController.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt`

- [ ] **Step 1: Add option fields to PlaceOrderRequest**

In `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt`, add after `secondaryRoute` in `PlaceOrderRequest`:

```kotlin
val optionType: String? = null,      // "CALL" or "PUT"
val strike: java.math.BigDecimal? = null,
val expiry: String? = null,          // ISO date "2026-06-19"
```

- [ ] **Step 2: Add option fields to OrderRequest**

In `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/OrderRequest.kt`, add after `secondaryRoute` in `OrderRequest`:

```kotlin
val optionType: String? = null,
val strike: java.math.BigDecimal? = null,
val expiry: String? = null,
```

- [ ] **Step 3: Pass option fields through in OrderController**

In `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/OrderController.kt`, update the `placeOrder` method's `OrderRequest` construction (line 31-36) to include the new fields:

```kotlin
val orderRequest = OrderRequest(
    symbol = request.symbol, action = request.action, quantity = request.quantity,
    orderType = request.orderType, limitPrice = request.limitPrice, stopPrice = request.stopPrice,
    timeInForce = request.timeInForce, currency = request.currency,
    symbolId = request.symbolId, primaryRoute = request.primaryRoute, secondaryRoute = request.secondaryRoute,
    optionType = request.optionType, strike = request.strike, expiry = request.expiry
)
```

- [ ] **Step 4: Add option fields to portfolio service orderBody map**

In `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt`, update the `orderBody` map (around line 78-88) to include option fields from the saved order:

```kotlin
val orderBody = mapOf(
    "symbol" to savedOrder.symbol,
    "action" to savedOrder.action.name,
    "quantity" to savedOrder.requestedUnits,
    "orderType" to savedOrder.orderType.name,
    "limitPrice" to savedOrder.limitPrice,
    "stopPrice" to savedOrder.stopPrice,
    "timeInForce" to savedOrder.timeInForce.name,
    "currency" to savedOrder.currency,
    "symbolId" to savedOrder.symbolId,
    "optionType" to savedOrder.optionType,
    "strike" to savedOrder.strikePrice,
    "expiry" to savedOrder.expirationDate?.toString(),
)
```

- [ ] **Step 5: Rebuild both services and verify**

```bash
docker compose up --build -d backend broker-gateway-service
```

Wait for healthy, then verify no startup errors.

- [ ] **Step 6: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/OrderRequest.kt
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/OrderController.kt
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt
git commit -m "feat(gateway): add optionType/strike/expiry to order DTOs end-to-end"
```

---

### Task 2: Extend IbkrContract and update IbkrAdapter for options

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAccountClient.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt`

- [ ] **Step 1: Extend IbkrContract with option fields**

In `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAccountClient.kt`, update `IbkrContract` (line 68-73):

```kotlin
data class IbkrContract(
    val symbol: String,
    val secType: String = "STK",
    val exchange: String = "SMART",
    val currency: String = "USD",
    val strike: Double? = null,
    val lastTradeDateOrContractMonth: String? = null,
    val right: String? = null,
    val conId: Int? = null
)
```

- [ ] **Step 2: Update IbkrAdapter.placeOrder to handle options**

In `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt`, replace the `placeOrder` method (lines 121-147):

```kotlin
override fun placeOrder(
    credentials: BrokerCredentials,
    accountId: String,
    request: OrderRequest
): OrderResult {
    requireConnected()
    val isOption = request.optionType != null
    val contract = if (isOption) {
        IbkrContract(
            symbol = request.symbol,
            secType = "OPT",
            exchange = "SMART",
            currency = request.currency ?: "USD",
            strike = request.strike?.toDouble(),
            lastTradeDateOrContractMonth = request.expiry?.replace("-", ""),
            right = if (request.optionType == "CALL") "C" else "P",
            conId = request.symbolId?.toInt()
        )
    } else {
        IbkrContract(
            symbol = request.symbol,
            secType = "STK",
            exchange = "SMART",
            currency = request.currency ?: "USD"
        )
    }
    val orderSpec = IbkrOrderSpec(
        action = request.action.name,
        orderType = mapOrderTypeToIbkr(request.orderType),
        totalQuantity = request.quantity,
        limitPrice = request.limitPrice,
        auxPrice = request.stopPrice,
        timeInForce = request.timeInForce.name
    )
    val orderId = client.placeOrder(accountId, contract, orderSpec)
    return OrderResult(
        brokerOrderId = orderId.toString(),
        status = OrderStatus.SUBMITTED,
        message = "Order $orderId submitted"
    )
}
```

Key changes:
- Checks `request.optionType != null` to decide stock vs option
- For options: `secType = "OPT"`, maps `strike`, `expiry` (converted from "2026-06-19" to "20260619"), `right` ("C" or "P")
- Uses `symbolId` as IBKR `conId` if available
- Stock path unchanged

- [ ] **Step 3: Rebuild and verify**

```bash
docker compose up --build -d broker-gateway-service
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAccountClient.kt
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt
git commit -m "feat(ibkr): create OPT contracts for option orders with strike/expiry/right"
```

---

### Task 3: Update fake adapters for option order testing

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapter.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapter.kt`

- [ ] **Step 1: Update FakeIbkrAdapter.placeOrder**

In `FakeIbkrAdapter.kt`, replace the `placeOrder` method (around line 206-217):

```kotlin
override fun placeOrder(
    credentials: BrokerCredentials,
    accountId: String,
    request: OrderRequest
): OrderResult {
    val orderId = orderIdSequence.getAndIncrement()
    val optionDesc = if (request.optionType != null) {
        " ${request.optionType} ${request.strike} exp:${request.expiry}"
    } else ""
    log.info("FakeIbkrAdapter: placed {} {} order for {} {}{} @ {}",
        request.orderType, request.action, request.quantity, request.symbol, optionDesc,
        request.limitPrice ?: "MKT")
    return OrderResult(
        brokerOrderId = orderId.toString(),
        status = OrderStatus.SUBMITTED,
        message = "Order submitted for ${request.action} ${request.quantity} ${request.symbol}$optionDesc"
    )
}
```

- [ ] **Step 2: Update FakeQuestradeAdapter.placeOrder**

In `FakeQuestradeAdapter.kt`, replace the `placeOrder` method (around line 132-139):

```kotlin
override fun placeOrder(
    credentials: BrokerCredentials, accountId: String, request: OrderRequest
): OrderResult {
    val orderId = orderIdCounter.incrementAndGet()
    val optionDesc = if (request.optionType != null) {
        " ${request.optionType} ${request.strike} exp:${request.expiry}"
    } else ""
    log.info("FakeQuestradeAdapter: placed {} {} order for {} {}{} @ {}",
        request.orderType, request.action, request.quantity, request.symbol, optionDesc,
        request.limitPrice ?: "MKT")
    return OrderResult(brokerOrderId = orderId.toString(), status = OrderStatus.SUBMITTED,
        message = "Fake order submitted$optionDesc")
}
```

- [ ] **Step 3: Rebuild and verify**

```bash
docker compose up --build -d broker-gateway-service
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapter.kt
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapter.kt
git commit -m "feat(adapters): update fake adapters to log option order details"
```

---

### Task 4: End-to-end test and documentation

- [ ] **Step 1: Rebuild all services**

```bash
docker compose up --build -d
```

Wait for all services healthy.

- [ ] **Step 2: Test option order submission via Playwright**

Login → /wheel → TFSA → click position → Buy → check:
- Backend logs show option fields being passed
- `trade_orders` row has `option_type`, `strike_price` populated
- Order `status` is `SUBMITTED` (fake adapter returns success)
- Broker gateway logs show "FakeQuestradeAdapter: placed ... PUT 70 exp:..."

- [ ] **Step 3: Verify in database**

```bash
docker compose exec postgres psql -U portfolio -d portfolio -c \
  "SELECT id, symbol, option_type, strike_price, expiration_date, status FROM trade_orders ORDER BY id DESC LIMIT 3;"
```

Expected: Order with `status = SUBMITTED`, option fields populated.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/reference/
git commit -m "docs: update references for Phase 3b broker adapter options support"
```
