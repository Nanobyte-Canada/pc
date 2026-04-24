# Portfolio Service Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the portfolio service from SnapTrade to the broker-gateway service. Create a `BrokerGatewayClient` HTTP client, refactor all services to use it, then remove SnapTrade dependencies.

**Architecture:** The portfolio service will call the broker-gateway's REST API via a `BrokerGatewayClient` (Spring WebClient). All existing entities (BrokerPosition, BrokerActivity, BrokerBalanceSnapshot, TradeOrder) and their persistence logic stay unchanged — only the data source changes. The `BrokerConnection` entity gets a new `gatewayConnectionId` field linking to the gateway's connection. SnapTrade-specific code (adapter, DTOs, service, scheduler, config) is removed after migration.

**Tech Stack:** Spring WebFlux WebClient, existing Spring Data JPA entities, Flyway migration for schema changes.

**Spec:** `docs/superpowers/specs/2026-04-23-broker-gateway-design.md` — Migration Plan section

---

## Scope

This plan covers:
1. `BrokerGatewayClient` — HTTP client for the gateway REST API
2. `BrokerService` refactor — connection management via gateway
3. `PositionFetchService` refactor — position sync via gateway
4. `ActivityIngestionService` refactor — activity sync via gateway
5. `OrderExecutionService` refactor — orders via gateway
6. `BrokerController` updates — new connection flow
7. Schema migration — add `gatewayConnectionId`, remove SnapTrade columns
8. SnapTrade removal — delete all SnapTrade-specific code
9. Build, test, verify

**NOT in scope:** The gateway service itself (already built), frontend changes (deferred), the nightly scheduler (keeps working since it calls the same service methods).

---

## File Structure

### New files
```
backend/portfolio/src/main/kotlin/com/portfolio/broker/client/
  BrokerGatewayClient.kt               — HTTP client calling gateway REST API
  BrokerGatewayConfig.kt               — Gateway URL config
```

### Files to modify
```
backend/portfolio/src/main/kotlin/com/portfolio/broker/service/
  BrokerService.kt                      — Replace SnapTrade calls with gateway client
  PositionFetchService.kt               — Replace holdings/balance/order fetch
  ActivityIngestionService.kt           — Replace activity fetch
  OrderExecutionService.kt              — Replace placeOrder/cancelOrder

backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/
  BrokerController.kt                   — Update connection endpoints

backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/
  BrokerConnection.kt                   — Add gatewayConnectionId field

backend/portfolio/src/main/resources/
  db/migration/V57__add_gateway_connection_id.sql

backend/portfolio/src/main/resources/application.yml  — Add gateway URL config
```

### Files to delete (Task 8)
```
backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/
  SnapTradeAdapter.kt
  SnapTradeAdapterImpl.kt
  SnapTradeDtos.kt

backend/portfolio/src/main/kotlin/com/portfolio/broker/service/
  SnapTradeService.kt
  SnapTradeStatusService.kt

backend/portfolio/src/main/kotlin/com/portfolio/broker/scheduler/
  SnapTradeHealthScheduler.kt

backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/
  SnapTradeStatusCheck.kt

backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/
  SnapTradeStatusRepository.kt

backend/portfolio/src/main/kotlin/com/portfolio/broker/config/
  BrokerConfig.kt  (SnapTradeConfig, SnapTradeHealthConfig classes only)
```

---

## Task 1: BrokerGatewayClient

The HTTP client that calls the broker-gateway REST API. This replaces all SnapTradeService calls.

**Files:**
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/client/BrokerGatewayConfig.kt`
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/client/BrokerGatewayClient.kt`
- Modify: `backend/portfolio/src/main/resources/application.yml`

- [ ] **Step 1: Create BrokerGatewayConfig**

```kotlin
package com.portfolio.broker.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway")
data class BrokerGatewayConfig(
    val url: String = "http://localhost:8084",
    val apiKey: String = "dev-gateway-key",
    val timeoutMs: Long = 30000
)
```

- [ ] **Step 2: Add gateway config to portfolio application.yml**

Add after the existing `broker:` section:

```yaml
broker-gateway:
  url: ${BROKER_GATEWAY_URL:http://localhost:8084}
  api-key: ${GATEWAY_API_KEY:dev-gateway-key}
  timeout-ms: ${BROKER_GATEWAY_TIMEOUT:30000}
```

- [ ] **Step 3: Create BrokerGatewayClient**

This client maps directly to the gateway REST API. Each method corresponds to a gateway endpoint and returns parsed JSON as domain-friendly types.

```kotlin
package com.portfolio.broker.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class BrokerGatewayClient(
    private val config: BrokerGatewayConfig,
    private val objectMapper: ObjectMapper,
    webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val timeout = Duration.ofMillis(config.timeoutMs)

    private val webClient = webClientBuilder
        .baseUrl(config.url)
        .defaultHeader("X-Gateway-Api-Key", config.apiKey)
        .build()

    // === Connection Management ===

    fun createConnection(userId: Long, brokerType: String, credentials: Map<String, Any>): JsonNode {
        val body = mapOf("userId" to userId, "brokerType" to brokerType, "credentials" to credentials)
        return post("/api/v1/gateway/connections", body)
    }

    fun listConnections(userId: Long): JsonNode {
        return get("/api/v1/gateway/connections?userId=$userId")
    }

    fun getConnection(connectionId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId")
    }

    fun deleteConnection(connectionId: String) {
        delete("/api/v1/gateway/connections/$connectionId")
    }

    fun validateConnection(connectionId: String): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/validate", emptyMap<String, Any>())
    }

    fun refreshConnection(connectionId: String): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/refresh", emptyMap<String, Any>())
    }

    // === Account Data ===

    fun listAccounts(connectionId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts")
    }

    fun getBalances(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/balances")
    }

    fun getPositions(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/positions")
    }

    fun getActivities(connectionId: String, accountId: String, startDate: LocalDate?, endDate: LocalDate?): JsonNode {
        val params = mutableListOf<String>()
        startDate?.let { params.add("startDate=$it") }
        endDate?.let { params.add("endDate=$it") }
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/activities$query")
    }

    fun getOrders(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders")
    }

    // === Order Execution ===

    fun placeOrder(connectionId: String, accountId: String, order: Map<String, Any?>): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders", order)
    }

    fun cancelOrder(connectionId: String, accountId: String, brokerOrderId: String) {
        delete("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders/$brokerOrderId")
    }

    // === Health ===

    fun getHealth(): JsonNode {
        return get("/api/v1/gateway/health")
    }

    // === HTTP helpers ===

    private fun get(path: String): JsonNode {
        return try {
            webClient.get().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(timeout) ?: objectMapper.createObjectNode()
        } catch (e: WebClientResponseException) {
            log.error("Gateway GET {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw e
        }
    }

    private fun post(path: String, body: Any): JsonNode {
        return try {
            webClient.post().uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(timeout) ?: objectMapper.createObjectNode()
        } catch (e: WebClientResponseException) {
            log.error("Gateway POST {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw e
        }
    }

    private fun delete(path: String) {
        try {
            webClient.delete().uri(path)
                .retrieve()
                .toBodilessEntity()
                .block(timeout)
        } catch (e: WebClientResponseException) {
            log.error("Gateway DELETE {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw e
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/client/ backend/portfolio/src/main/resources/application.yml
git commit -m "feat(portfolio): add BrokerGatewayClient for gateway REST API calls"
```

---

## Task 2: Schema Migration

Add `gateway_connection_id` to `broker_connections` table, remove SnapTrade-specific columns and tables.

**Files:**
- Create: `backend/portfolio/src/main/resources/db/migration/V57__gateway_migration.sql`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt`

- [ ] **Step 1: Check highest Flyway version**

Run: `ls backend/portfolio/src/main/resources/db/migration/ | sort -V | tail -5`
Use the next available number (verify V57 is free).

- [ ] **Step 2: Create migration**

```sql
-- V57__gateway_migration.sql

-- Add gateway connection reference to broker_connections
ALTER TABLE broker_connections ADD COLUMN IF NOT EXISTS gateway_connection_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_broker_conn_gateway ON broker_connections(gateway_connection_id);

-- Drop SnapTrade health check table
DROP TABLE IF EXISTS snaptrade_status_checks;

-- Remove SnapTrade user columns (nullable, so safe to drop)
ALTER TABLE users DROP COLUMN IF EXISTS snaptrade_user_id;
ALTER TABLE users DROP COLUMN IF EXISTS snaptrade_user_secret_encrypted;
```

- [ ] **Step 3: Add gatewayConnectionId to BrokerConnection entity**

Add this field to the BrokerConnection entity class:

```kotlin
@Column(name = "gateway_connection_id", length = 36)
var gatewayConnectionId: String? = null
```

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/resources/db/migration/V57__gateway_migration.sql backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt
git commit -m "feat(portfolio): add gateway_connection_id field and cleanup SnapTrade schema"
```

---

## Task 3: Refactor BrokerService

Replace SnapTrade calls in `BrokerService` with gateway client calls. The connection sync flow changes significantly — instead of syncing SnapTrade authorizations, we now manage gateway connections.

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerService.kt`

- [ ] **Step 1: Replace SnapTradeService dependency with BrokerGatewayClient**

In the constructor, replace `private val snapTradeService: SnapTradeService` with `private val gatewayClient: BrokerGatewayClient`. Keep all other dependencies unchanged.

- [ ] **Step 2: Refactor getAvailableBrokers()**

Replace the SnapTrade brokerage listing with a static list of supported brokers (IBKR, Questrade, Wealthsimple) sourced from the gateway health endpoint:

```kotlin
fun getAvailableBrokers(): List<BrokerDto> {
    val health = gatewayClient.getHealth()
    val brokers = health.get("brokers") ?: return emptyList()
    return brokers.filter { it.get("enabled")?.asBoolean() == true }.map { b ->
        val type = b.get("brokerType")?.asText() ?: ""
        BrokerDto(
            id = null, code = type, name = type,
            slug = type.lowercase(), status = b.get("status")?.asText(),
            logoUrl = null, description = null, url = null, openUrl = null,
            enabled = true, maintenanceMode = false, isDegraded = false,
            allowsTrading = true, allowsFractionalUnits = null,
            hasReporting = true, isRealTimeConnection = null,
            brokerageType = null, authTypes = null
        )
    }
}
```

- [ ] **Step 3: Refactor syncConnections()**

Replace the SnapTrade auth+account sync with gateway connection listing:

```kotlin
fun syncConnections(user: User): List<BrokerConnection> {
    val existing = connectionRepository.findByUser(user)
    val gatewayConnectionIds = existing.mapNotNull { it.gatewayConnectionId }

    // For each existing connection with a gatewayConnectionId, validate it
    for (conn in existing) {
        if (conn.gatewayConnectionId != null) {
            try {
                val validation = gatewayClient.validateConnection(conn.gatewayConnectionId!!)
                val connected = validation.get("connected")?.asBoolean() ?: false
                conn.status = if (connected) ConnectionStatus.ACTIVE else ConnectionStatus.ERROR
                conn.connectionErrorMessage = validation.get("message")?.asText()
                connectionRepository.save(conn)
            } catch (e: Exception) {
                log.error("Failed to validate gateway connection {}: {}", conn.gatewayConnectionId, e.message)
            }
        }
    }
    return connectionRepository.findByUser(user)
}
```

- [ ] **Step 4: Refactor getConnectionPortalUrl() → createGatewayConnection()**

Replace the SnapTrade OAuth redirect flow. The new flow creates a gateway connection directly with credentials provided by the frontend:

```kotlin
fun createGatewayConnection(user: User, brokerType: String, credentials: Map<String, Any>): BrokerConnection {
    val response = gatewayClient.createConnection(user.id!!, brokerType, credentials)
    val gatewayConnId = response.get("connectionId")?.asText()
        ?: throw IllegalStateException("No connectionId in gateway response")
    val status = response.get("status")?.asText() ?: "ACTIVE"

    // Fetch accounts from gateway
    val accountsResponse = gatewayClient.listAccounts(gatewayConnId)
    val accounts = accountsResponse.get("accounts") ?: objectMapper.createArrayNode()
    val firstAccount = accounts.firstOrNull()

    val broker = brokerRepository.findByCode(brokerType)

    val connection = BrokerConnection(
        user = user,
        broker = broker,
        gatewayConnectionId = gatewayConnId,
        accountIdExternal = firstAccount?.get("accountId")?.asText(),
        accountNumber = firstAccount?.get("accountNumber")?.asText(),
        accountType = firstAccount?.get("accountType")?.asText(),
        accountName = firstAccount?.get("accountName")?.asText(),
        brokerName = brokerType,
        status = if (status == "ACTIVE") ConnectionStatus.ACTIVE else ConnectionStatus.ERROR,
        connectionErrorMessage = response.get("errorMessage")?.asText()
    )
    connection.metadata = objectMapper.writeValueAsString(accounts)
    return connectionRepository.save(connection)
}
```

- [ ] **Step 5: Refactor disconnectBroker()**

Replace SnapTrade disconnect with gateway delete:

```kotlin
fun disconnectBroker(user: User, connectionId: Long) {
    val connection = connectionRepository.findById(connectionId)
        .orElseThrow { NotFoundException("Connection not found", "CONNECTION_NOT_FOUND") }
    require(connection.user.id == user.id) { "Connection does not belong to user" }

    if (connection.gatewayConnectionId != null) {
        try {
            gatewayClient.deleteConnection(connection.gatewayConnectionId!!)
        } catch (e: Exception) {
            log.warn("Failed to delete gateway connection: {}", e.message)
        }
    }
    connection.status = ConnectionStatus.DISCONNECTED
    connectionRepository.save(connection)
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerService.kt
git commit -m "refactor(portfolio): migrate BrokerService from SnapTrade to gateway client"
```

---

## Task 4: Refactor PositionFetchService

Replace `snapTradeService.getHoldings()`, `getAccountBalance()`, `fetchOptionPositions()`, and `listOrders()` with gateway client calls.

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt`

- [ ] **Step 1: Replace SnapTradeService dependency with BrokerGatewayClient**

In the constructor, replace `private val snapTradeService: SnapTradeService` with `private val gatewayClient: BrokerGatewayClient`.

- [ ] **Step 2: Refactor the position fetch pipeline**

The main `executePositionFetch()` method changes its data source from SnapTrade to gateway. The entity mapping logic stays the same — only the data retrieval changes.

Replace the SnapTrade holdings call:
```kotlin
// OLD: val holdings = snapTradeService.getHoldings(user, accountId)
// NEW:
val gwConnId = connection.gatewayConnectionId
    ?: throw IllegalStateException("No gateway connection for ${connection.id}")
val positionsResponse = gatewayClient.getPositions(gwConnId, connection.accountIdExternal!!)
val positions = positionsResponse.get("positions") ?: objectMapper.createArrayNode()
val balanceResponse = gatewayClient.getBalances(gwConnId, connection.accountIdExternal!!)
```

Map gateway positions to BrokerPosition entities using the same field mapping:
```kotlin
positions.forEach { pos ->
    val symbol = pos.get("symbol")?.asText() ?: return@forEach
    val quantity = pos.get("quantity")?.decimalValue() ?: return@forEach
    val currentPrice = pos.get("currentPrice")?.decimalValue()
    val averageCost = pos.get("averageCost")?.decimalValue()
    val currentValue = pos.get("currentValue")?.decimalValue()
    val totalPnl = pos.get("totalPnl")?.decimalValue()
    val totalPnlPercent = pos.get("totalPnlPercent")?.decimalValue()
    val instrumentType = pos.get("instrumentType")?.asText()
    val currency = pos.get("currency")?.asText() ?: "CAD"

    val entity = BrokerPosition(
        connection = connection,
        symbol = symbol,
        quantity = quantity,
        currentPrice = currentPrice,
        averageCost = averageCost,
        currentValue = currentValue,
        totalPnl = totalPnl,
        totalPnlPercent = totalPnlPercent,
        instrumentType = instrumentType?.let { InstrumentType.valueOf(it) },
        currency = currency,
        asOfDate = LocalDate.now(),
        asOfTimestamp = OffsetDateTime.now(),
        // Option fields from gateway
        strikePrice = pos.get("strikePrice")?.decimalValue(),
        expirationDate = pos.get("expirationDate")?.asText()?.let { LocalDate.parse(it) },
        optionType = pos.get("optionType")?.asText(),
        underlyingSymbol = pos.get("underlyingSymbol")?.asText()
    )
    positionRepository.save(entity)
}
```

Replace the balance snapshot with gateway balance response:
```kotlin
val totalEquity = balanceResponse.get("totalEquity")?.decimalValue()
val cashBalances = balanceResponse.get("cashBalances")
val cashMap = mutableMapOf<String, BigDecimal>()
cashBalances?.forEach { cb ->
    val cur = cb.get("currency")?.asText() ?: return@forEach
    val amt = cb.get("amount")?.decimalValue() ?: return@forEach
    cashMap["cash_$cur"] = amt
}
val buyingPower = balanceResponse.get("buyingPower")?.decimalValue()
if (buyingPower != null) {
    cashMap["buying_power_${balanceResponse.get("currency")?.asText() ?: "CAD"}"] = buyingPower
}
// Save BrokerBalanceSnapshot with cashMap as JSON
```

Replace the order sync:
```kotlin
// OLD: val orders = snapTradeService.listOrders(user, accountId)
// NEW:
val ordersResponse = gatewayClient.getOrders(gwConnId, connection.accountIdExternal!!)
val orders = ordersResponse.get("orders") ?: objectMapper.createArrayNode()
// Map each order JsonNode to TradeOrder entity using same logic
```

- [ ] **Step 3: Remove fetchOptionPositions() call**

The gateway's `getPositions()` already includes option fields (strikePrice, expirationDate, optionType, underlyingSymbol) in the unified response. No separate call needed.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt
git commit -m "refactor(portfolio): migrate PositionFetchService from SnapTrade to gateway client"
```

---

## Task 5: Refactor ActivityIngestionService

Replace `snapTradeService.getAllAccountActivities()` with gateway client calls.

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt`

- [ ] **Step 1: Replace SnapTradeService dependency with BrokerGatewayClient**

- [ ] **Step 2: Refactor activity sync**

Replace the paginated SnapTrade call with the gateway activities endpoint:
```kotlin
// OLD: val activities = snapTradeService.getAllAccountActivities(user, accountId, startDate, endDate)
// NEW:
val gwConnId = connection.gatewayConnectionId
    ?: throw IllegalStateException("No gateway connection for ${connection.id}")
val response = gatewayClient.getActivities(gwConnId, connection.accountIdExternal!!, startDate, endDate)
val activities = response.get("activities") ?: objectMapper.createArrayNode()
```

Map gateway activities to BrokerActivity entities:
```kotlin
activities.forEach { act ->
    val tradeDate = act.get("tradeDate")?.asText()?.let { LocalDate.parse(it) } ?: return@forEach
    val type = act.get("type")?.asText() ?: "OTHER"
    val amount = act.get("amount")?.decimalValue() ?: return@forEach
    val currency = act.get("currency")?.asText() ?: "CAD"

    val entity = BrokerActivity(
        connection = connection,
        externalId = act.get("externalId")?.asText(),
        type = type,
        symbol = act.get("symbol")?.asText(),
        description = act.get("description")?.asText(),
        quantity = act.get("quantity")?.decimalValue(),
        price = act.get("price")?.decimalValue(),
        amount = amount,
        fee = act.get("fee")?.decimalValue(),
        currency = currency,
        tradeDate = tradeDate,
        settlementDate = act.get("settlementDate")?.asText()?.let { LocalDate.parse(it) },
        optionType = act.get("optionType")?.asText()
    )
    // FX conversion stays the same
    if (currency != "CAD") {
        val rate = exchangeRateService.getRate(currency, tradeDate)
        entity.amountCad = amount.multiply(rate)
        entity.exchangeRate = rate
    } else {
        entity.amountCad = amount
    }
    activityRepository.save(entity)
}
```

- [ ] **Step 3: Refactor balance snapshot method**

Replace `snapTradeService.getAccountBalance()` with `gatewayClient.getBalances()`.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt
git commit -m "refactor(portfolio): migrate ActivityIngestionService from SnapTrade to gateway client"
```

---

## Task 6: Refactor OrderExecutionService

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt`

- [ ] **Step 1: Replace SnapTradeService dependency with BrokerGatewayClient**

- [ ] **Step 2: Refactor placeOrder**

```kotlin
// OLD: val result = snapTradeService.placeOrder(user, accountId, action, symbol, units, ...)
// NEW:
val gwConnId = connection.gatewayConnectionId
    ?: throw IllegalStateException("No gateway connection")
val orderBody = mapOf(
    "symbol" to tradeOrder.symbol,
    "action" to tradeOrder.action.name,
    "quantity" to tradeOrder.requestedUnits,
    "orderType" to tradeOrder.orderType.name,
    "limitPrice" to tradeOrder.limitPrice,
    "timeInForce" to tradeOrder.timeInForce.name,
    "currency" to tradeOrder.currency
)
val result = gatewayClient.placeOrder(gwConnId, connection.accountIdExternal!!, orderBody)
val brokerOrderId = result.get("brokerOrderId")?.asText()
```

- [ ] **Step 3: Refactor cancelOrder**

```kotlin
// OLD: snapTradeService.cancelOrder(user, accountId, brokerOrderId)
// NEW:
val gwConnId = connection.gatewayConnectionId ?: throw IllegalStateException("No gateway connection")
gatewayClient.cancelOrder(gwConnId, connection.accountIdExternal!!, brokerOrderId)
```

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt
git commit -m "refactor(portfolio): migrate OrderExecutionService from SnapTrade to gateway client"
```

---

## Task 7: Update BrokerController

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt`

- [ ] **Step 1: Update connection endpoints**

Replace the SnapTrade OAuth portal flow with direct gateway connection creation. The `POST /api/v1/brokers/connect` endpoint changes to accept broker credentials and create a gateway connection:

```kotlin
@PostMapping("/connect")
fun connectBroker(
    @RequestBody request: ConnectBrokerRequest,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<BrokerConnectionDto> {
    val user = userService.getUser(principal.userId)
    val connection = brokerService.createGatewayConnection(user, request.brokerType, request.credentials)
    return ResponseEntity.status(201).body(BrokerConnectionDto.from(connection))
}

data class ConnectBrokerRequest(
    val brokerType: String,
    val credentials: Map<String, Any>
)
```

- [ ] **Step 2: Remove SnapTrade-specific endpoints**

Remove or update:
- `GET /api/v1/brokers/snaptrade/status` — remove (replaced by gateway health)
- `GET /api/v1/brokers/config-status` — remove
- `GET /api/v1/brokers/authorization-types` — remove

Add:
- `GET /api/v1/brokers/health` — proxy to gateway health endpoint

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt
git commit -m "refactor(portfolio): update BrokerController for gateway connection flow"
```

---

## Task 8: Remove SnapTrade Dependencies

**Files to delete:**
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/SnapTradeStatusService.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/scheduler/SnapTradeHealthScheduler.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/SnapTradeStatusCheck.kt`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/SnapTradeStatusRepository.kt`

**Files to modify:**
- `backend/portfolio/build.gradle.kts` — remove `com.snaptrade:snaptrade-java-sdk:5.0.168`
- `backend/portfolio/src/main/kotlin/com/portfolio/Application.kt` — remove `@EnableConfigurationProperties(SnapTradeConfig::class, SnapTradeHealthConfig::class)`
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/config/BrokerConfig.kt` — remove `SnapTradeConfig` and `SnapTradeHealthConfig` data classes (keep `BrokerEncryptionConfig` and `BrokerSyncConfig`)
- `backend/portfolio/src/main/resources/application.yml` — remove `snaptrade:` config section

- [ ] **Step 1: Delete SnapTrade files**

- [ ] **Step 2: Remove SnapTrade SDK dependency from build.gradle.kts**

- [ ] **Step 3: Clean up Application.kt and BrokerConfig.kt**

- [ ] **Step 4: Remove snaptrade config from application.yml**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(portfolio): remove SnapTrade adapter, service, config, and SDK dependency"
```

---

## Task 9: Build, Test, and Verify

- [ ] **Step 1: Build portfolio service**

Run: `docker compose build backend`

- [ ] **Step 2: Build broker-gateway service**

Run: `docker compose build broker-gateway-service`

- [ ] **Step 3: Start full stack**

Run: `docker compose up -d`

- [ ] **Step 4: Verify both services are healthy**

```bash
curl http://localhost:8080/health
curl http://localhost:8084/api/v1/gateway/health
```

- [ ] **Step 5: Run portfolio tests**

Run: `docker compose exec backend ./gradlew test`

- [ ] **Step 6: Run gateway tests**

Run: `docker run --rm -v "C:\Users\SaurabhBilakhia\Documents\POC\pc\backend:/work" -w //work/broker-gateway gradle:8.10-jdk21-alpine gradle test --no-daemon`

- [ ] **Step 7: Run frontend build**

Run: `cd frontend && npm run build`

- [ ] **Step 8: Commit any fixes**

---

## Task 10: Update Documentation

- [ ] **Step 1: Update docs/reference/backend-services.md** — Remove SnapTrade service section, update BrokerService/PositionFetchService/ActivityIngestionService descriptions to reference gateway client

- [ ] **Step 2: Update docs/reference/api-endpoints.md** — Remove SnapTrade-specific endpoints, update connection flow

- [ ] **Step 3: Update docs/reference/database-schema.md** — Add gateway_connection_id column, note dropped snaptrade columns/tables

- [ ] **Step 4: Update docs/reference/configurations.md** — Remove SnapTrade env vars, add BROKER_GATEWAY_URL/GATEWAY_API_KEY

- [ ] **Step 5: Update docs/reference/entity-relationships.md** — Update BrokerConnection entity fields

- [ ] **Step 6: Commit**

```bash
git add docs/reference/
git commit -m "docs: update reference documentation for SnapTrade → gateway migration"
```

---

## Verification Checklist

1. Portfolio service builds without SnapTrade SDK
2. All portfolio tests pass
3. All gateway tests pass
4. `GET /api/v1/brokers` returns IBKR, QUESTRADE, WEALTHSIMPLE
5. `POST /api/v1/brokers/connect` creates a gateway connection
6. `POST /api/v1/brokers/connections/{id}/fetch` syncs positions via gateway
7. `POST /api/v1/brokers/connections/{id}/sync-activities` syncs activities via gateway
8. Nightly scheduler still runs (calls same service methods)
9. No SnapTrade imports remain in portfolio service
10. All documentation updated
