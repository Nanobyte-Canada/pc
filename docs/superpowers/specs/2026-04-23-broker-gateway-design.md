# Broker Gateway Service — Design & Research

## Context

Replace the SnapTrade aggregator with a dedicated `broker-gateway` microservice that connects directly to IBKR, Questrade, and Wealthsimple. The market-data service remains independent (streaming quotes only). The gateway handles user account data, positions, balances, transaction history, and order execution. The portfolio service calls the gateway via internal REST API.

**Initial scope**: Read + basic trading (holdings, balances, history, market/limit orders). Options trading as fast follow-up.

**Out of scope**: Market data streaming, options chains, Greeks, real-time quotes — these are the responsibility of `market-data-service`, not the broker-gateway. The research table below documents each broker's full API capabilities for future reference, but only account data and order execution capabilities are implemented in this service.

---

## Architecture

```
market-data-service  ──── IBKR TWS (streaming quotes, client ID 1)
broker-gateway (new) ──── IBKR TWS (account/orders, client ID 2)
                     ──── Questrade REST API
                     ──── Wealthsimple GraphQL API
portfolio-service    ──── broker-gateway (internal REST API on port 8084)
```

Port: 8084 (follows existing sequence: 8080/8081/8082/8083)

---

## Broker API Research Summary

### Interactive Brokers (TWS API)

| Feature | Details |
|---|---|
| **API Type** | TCP Socket (official Java SDK `ibapi`) |
| **Auth** | Local login to TWS/IB Gateway, no API keys. Use IBC for headless. |
| **Accounts** | `reqAccountSummary()` — 30+ tags (NetLiquidation, BuyingPower, etc.) |
| **Positions** | `reqPositions()` — streaming positions with cost basis and P&L |
| **Orders** | 43 order types. Initial scope: Market, Limit via `placeOrder()` / `cancelOrder()` |
| **Transactions** | Same-day: `reqExecutions()`. Historical: Flex Reports (XML web service) |
| **Historical Orders** | `reqCompletedOrders()` + `reqOpenOrders()` |
| **Options** | Full chain data, server-side Greeks, multi-leg via BAG contracts (future phase) |
| **Streaming** | All data streamed via callbacks. No WebSocket — persistent TCP socket |
| **Rate Limits** | 50 msg/sec, 60 hist req/10min, 100 market data lines |
| **Sandbox** | Paper trading account |
| **JVM SDK** | Official Java `ibapi` (vendored JAR — not in Maven Central) |
| **Existing integration** | `market-data-service` already has `IbkrConnectionManager`, `ContractResolver`, `SubscriptionManager` |

### Questrade (REST API)

| Feature | Details |
|---|---|
| **API Type** | REST (no official JVM SDK — custom `WebClient`) |
| **Auth** | OAuth 2.0 with **single-use** refresh tokens. Dynamic `api_server` URL per token exchange. |
| **Accounts** | `GET /v1/accounts` — Cash, Margin, TFSA, RRSP, RESP, LIRA, LIF, FHSA, etc. |
| **Balances** | `GET /v1/accounts/{id}/balances` — per-currency cash, equity, buying power |
| **Positions** | `GET /v1/accounts/{id}/positions` — qty, avg entry price, current value, P&L |
| **Orders** | Market, Limit, Stop, StopLimit, TrailingStop (6 variants). **Partner app registration required for placement.** |
| **Transactions** | `GET /v1/accounts/{id}/activities` — trades, dividends, deposits, fees |
| **Executions** | `GET /v1/accounts/{id}/executions` — fill records with fee breakdown |
| **Options** | Chains, Greeks via quote endpoint, multi-leg strategy quotes (future phase) |
| **Streaming** | WebSocket for L1 quotes + order notifications |
| **Rate Limits** | Per-token via `X-RateLimit-Remaining` header. ~1 req/sec safe. |
| **Sandbox** | Yes (practice login at `practicelogin.questrade.com`) |
| **Key Risk** | Refresh tokens are single-use — must persist new token after every refresh |

### Wealthsimple (Unofficial GraphQL API)

| Feature | Details |
|---|---|
| **API Type** | GraphQL at `my.wealthsimple.com/graphql` (unofficial, reverse-engineered) |
| **Auth** | OAuth 2.0 password grant with hardcoded `client_id`. 2FA via `x-wealthsimple-otp` header. **Requires storing user's actual password.** |
| **Accounts** | `FetchAllAccounts` — TFSA, RRSP, FHSA, Cash, Margin, Crypto |
| **Balances** | `FetchAccountFinancials` — CAD+USD, buying power, net liquidation |
| **Positions** | `FetchIdentityPositions` — qty, book value, market value, unrealized P&L |
| **Orders** | `SoOrdersOrderCreate` / `SoOrdersOrderCancel` — Market, Limit, Stop-Limit |
| **Transactions** | `FetchActivityFeedItems` — cursor pagination, max 99/page |
| **Options** | Single-leg supported. **No multi-leg.** |
| **Streaming** | GraphQL subscriptions (WebSocket) for quotes, activity, balances |
| **Rate Limits** | **7 trades per hour** (hard server limit). Read limits undocumented. |
| **Sandbox** | **None** — all testing against production |
| **Key Risks** | Unofficial API (TOS violation risk), can break without notice, direct credential sharing, REST→GraphQL migration already happened once (2021-2022) |

---

## Package Structure

```
backend/broker-gateway/
  settings.gradle.kts
  build.gradle.kts
  Dockerfile
  src/main/kotlin/com/portfolio/brokergateway/
    BrokerGatewayApplication.kt
    adapter/
      BrokerAdapter.kt                    # Core interface
      BrokerCapabilities.kt
      dto/                                # Unified DTOs
        UnifiedAccount.kt
        UnifiedBalance.kt
        UnifiedPosition.kt
        UnifiedActivity.kt
        UnifiedOrder.kt
        OrderRequest.kt
      ibkr/
        IbkrAdapter.kt                    # BrokerAdapter impl
        IbkrAccountClient.kt              # EClientSocket/EWrapper wrapper
        IbkrConnectionManager.kt          # Socket lifecycle, reconnect
        IbkrContractResolver.kt           # Symbol → Contract mapping
        IbkrFlexReportClient.kt           # Historical transactions
        IbkrConfig.kt
        IbkrDtoMappers.kt
      questrade/
        QuestradeAdapter.kt               # BrokerAdapter impl
        QuestradeRestClient.kt            # WebClient-based HTTP
        QuestradeTokenManager.kt          # Single-use refresh token rotation
        QuestradeConfig.kt
        QuestradeDtoMappers.kt
      wealthsimple/
        WealthsimpleAdapter.kt            # BrokerAdapter impl
        WealthsimpleGraphQlClient.kt      # Custom GraphQL HTTP client
        WealthsimpleTokenManager.kt       # Password-grant OAuth + 2FA
        WealthsimpleConfig.kt
        WealthsimpleDtoMappers.kt
        WealthsimpleRateLimiter.kt        # 7 trades/hr enforcer
    api/controller/
      ConnectionController.kt             # CRUD for broker connections
      DataController.kt                   # Positions, balances, activities, orders
      OrderController.kt                  # Place/cancel orders
      HealthController.kt                 # Per-broker health status
    api/dto/
      ApiRequestDtos.kt
      ApiResponseDtos.kt
    config/
      AppConfig.kt
      SecurityConfig.kt
      AdapterRegistry.kt                  # BrokerType → BrokerAdapter routing
    credential/
      CredentialService.kt
      CredentialEntity.kt                 # JPA entity
      CredentialRepository.kt
      TokenEncryptionService.kt           # AES-256-GCM (reuse pattern)
    exception/
      BrokerGatewayExceptions.kt
      GlobalExceptionHandler.kt
    health/
      BrokerHealthIndicator.kt
  src/main/resources/
    application.yml
    db/migration/
      V1__broker_gateway_schema.sql
```

---

## BrokerAdapter Interface

```kotlin
interface BrokerAdapter {
    val brokerType: BrokerType

    fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult
    fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials
    fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount>
    fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance
    fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition>
    fun getActivities(credentials: BrokerCredentials, accountId: String, startDate: LocalDate?, endDate: LocalDate?): List<UnifiedActivity>
    fun getOrders(credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?): List<UnifiedOrder>
    fun placeOrder(credentials: BrokerCredentials, accountId: String, request: OrderRequest): OrderResult
    fun cancelOrder(credentials: BrokerCredentials, accountId: String, brokerOrderId: String): CancelResult
    fun capabilities(): BrokerCapabilities
}
```

Credentials are a sealed class hierarchy (`IbkrCredentials`, `QuestradeCredentials`, `WealthsimpleCredentials`), each containing broker-specific auth data (socket host/port for IBKR, OAuth tokens for Questrade/WS).

---

## Unified DTOs — Field-by-Field Broker Mapping

The unified DTOs standardize data from all three brokers. Below is the complete mapping showing each unified field, what each broker calls it, and how it maps to the existing portfolio service entities.

### Accounts

| Unified Field | Type | IBKR (TWS API) | Questrade (REST) | Wealthsimple (GraphQL) | Existing Entity (`BrokerConnection`) |
|---|---|---|---|---|---|
| `accountId` | String | `managedAccounts()` callback string | `accounts[].number` | `node.id` | `accountIdExternal` |
| `accountNumber` | String? | Same as accountId | `accounts[].number` | `node.unifiedAccountNumber` | `accountNumber` |
| `accountName` | String? | N/A (no name field) | `accounts[].type` + number | `node.nickname` | `accountName` |
| `accountType` | AccountType (enum) | Raw string ("Individual", "RRSP") | `accounts[].type` ("Cash", "TFSA", "RRSP", "Margin") | `node.unifiedAccountType` ("ca_tfsa", "ca_rrsp", "ca_non_registered") | `accountType` (String) |
| `currency` | String? | `reqAccountSummary` → "Currency" tag | `accounts[].currency` ("CAD"/"USD") | Derived from `financials.currency` | `currency` (not in entity — inferred) |
| `brokerType` | BrokerType (enum) | IBKR | QUESTRADE | WEALTHSIMPLE | `broker.code` |
| `status` | String? | Connection state (connected/disconnected) | `accounts[].status` ("Active"/"Inactive") | `node.status` | `status` (ConnectionStatus enum) |

**Account type normalization:**

| Unified Enum | IBKR raw | Questrade raw | Wealthsimple raw |
|---|---|---|---|
| CASH | "Individual" | "Cash" | "ca_non_registered" |
| MARGIN | "Margin" | "Margin" | "ca_non_registered_margin" |
| TFSA | "TFSA" | "TFSA" | "ca_tfsa" |
| RRSP | "RRSP" | "RRSP" | "ca_rrsp" |
| FHSA | "FHSA" | N/A | "ca_fhsa" |
| RESP | N/A | "RESP" | N/A |
| LIRA | "LIRA" | "LIRA" | "ca_lira" |
| LIF | "LIF" | "LIF" | N/A |
| RIF | "RIF" | "RIF" / "RRIF" | N/A |
| CRYPTO | N/A | N/A | "ca_crypto" |
| OTHER | Anything else | Anything else | Anything else |

---

### Positions

| Unified Field | Type | IBKR (TWS) | Questrade (REST) | Wealthsimple (GQL) | Existing Entity (`BrokerPosition`) |
|---|---|---|---|---|---|
| `symbol` | String | `contract.symbol()` | `positions[].symbol` | `node.stock.symbol` | `symbol` |
| `symbolId` | String? | `contract.conId()` (int) | `positions[].symbolId` (int) | `node.id` (UUID) | `symbolIdExternal` |
| `securityName` | String? | `contract.description()` | N/A (separate symbol lookup) | `node.stock.name` | `securityName` |
| `instrumentType` | InstrumentType (enum) | `contract.secType()` ("STK", "OPT", "ETF", "BOND") | `positions[].symbolTypeCode` ("Stock", "Option", "ETF") | `node.securityType` ("equity", "etf") | `instrumentType` (enum) |
| `quantity` | BigDecimal | `position()` callback `pos` param (Decimal) | `positions[].openQuantity` (Double) | `node.quantity` | `quantity` (18,6) |
| `averageCost` | BigDecimal? | `position()` callback `avgCost` param | `positions[].averageEntryPrice` | `node.book_value / quantity` | `averageCost` (18,6) |
| `currentPrice` | BigDecimal? | `reqMktData` → tick 4 (last) or tick 9 (close) | `positions[].currentPrice` | `node.quote.amount` | `currentPrice` (18,6) |
| `currentValue` | BigDecimal? | `position()` → `marketValue` or computed | `positions[].currentMarketValue` | `node.market_value.amount` | `currentValue` (18,2) |
| `totalPnl` | BigDecimal? | `reqPnLSingle()` → `unrealizedPnL` | `positions[].openPnL` | `node.market_value - node.book_value` | `totalPnl` (18,2) |
| `totalPnlPercent` | BigDecimal? | Computed: pnl / totalCost × 100 | Computed: openPnL / totalCost × 100 | Computed | `totalPnlPercent` (10,4) |
| `currency` | String | `contract.currency()` | `positions[].currencyCode` | `node.currency` | `currency` (3) |
| `strikePrice` | BigDecimal? | `contract.strike()` | Via option chain lookup | N/A (future) | `strikePrice` (18,6) |
| `expirationDate` | LocalDate? | `contract.lastTradeDateOrContractMonth()` | Via option chain lookup | N/A (future) | `expirationDate` |
| `optionType` | String? | `contract.right()` ("C"/"P") → "CALL"/"PUT" | Via option chain lookup | N/A (future) | `optionType` (10) |
| `underlyingSymbol` | String? | `contract.symbol()` (for OPT, parent symbol) | Via option chain lookup | N/A (future) | `underlyingSymbol` (20) |

**Instrument type normalization:**

| Unified Enum | IBKR `secType` | Questrade `symbolTypeCode` | Wealthsimple `securityType` |
|---|---|---|---|
| STOCK | "STK" | "Stock" | "equity" |
| ETF | "STK" (exchange-inferred) | "ETF" | "etf" |
| MUTUAL_FUND | "FUND" | "MutualFund" | "mutual_fund" |
| OPTION | "OPT" | "Option" | N/A |
| BOND | "BOND" | "Bond" | N/A |
| CASH | "CASH" | N/A | N/A |
| CRYPTO | "CRYPTO" | N/A | "crypto" |
| OTHER | Anything else | Anything else | Anything else |

---

### Balances

| Unified Field | Type | IBKR (TWS) | Questrade (REST) | Wealthsimple (GQL) | Existing Entity (`BrokerBalanceSnapshot`) |
|---|---|---|---|---|---|
| `totalEquity` | BigDecimal? | `reqAccountSummary` → "NetLiquidation" tag | `balances.combinedBalances[].totalEquity` | `financials.net_liquidation_value.amount` | N/A (derived from totalValue) |
| `totalValue` | BigDecimal? | `reqAccountSummary` → "GrossPositionValue" tag | `balances.combinedBalances[].marketValue` | `financials.market_value.amount` | `totalValue` (18,2) |
| `cashBalances` | List<CashBalance> | `reqAccountSummary` → "TotalCashValue-C" / "TotalCashValue-S" per currency | `balances.perCurrencyBalances[]` → `{currency, cash}` | `financials.available_to_trade` per currency | `cash` (JSONB map) |
| `buyingPower` | BigDecimal? | `reqAccountSummary` → "BuyingPower" tag | `balances.combinedBalances[].buyingPower` | `financials.buying_power.amount` | N/A (not stored) |
| `currency` | String | Account default currency | `balances.combinedBalances[].currency` | `financials.currency` | `currency` (3) |

---

### Activities / Transactions

| Unified Field | Type | IBKR (TWS/Flex) | Questrade (REST) | Wealthsimple (GQL) | Existing Entity (`BrokerActivity`) |
|---|---|---|---|---|---|
| `externalId` | String? | Flex: `transactionID` / TWS: `execId` | `activities[].transactionDate` + index (no unique ID) | `node.canonicalId` | `externalId` (100) |
| `type` | ActivityType (enum) | Flex: `code` ("BUY","SELL","DIV","DEP","WITH") / TWS: `side` | `activities[].type` ("Trades","Dividends","Deposits","Fees") | `node.type` ("buy","sell","dividend","deposit","withdrawal") | `type` (String, 50) |
| `symbol` | String? | Flex: `symbol` / TWS: `contract.symbol()` | `activities[].symbol` | `node.securitySymbol` | `symbol` (20) |
| `description` | String? | Flex: `description` | `activities[].description` | `node.description` | `description` (TEXT) |
| `quantity` | BigDecimal? | Flex: `quantity` / TWS: `execution.shares()` | `activities[].quantity` | `node.quantity` | `quantity` (18,6) |
| `price` | BigDecimal? | Flex: `tradePrice` / TWS: `execution.price()` | `activities[].price` | `node.price.amount` | `price` (18,6) |
| `amount` | BigDecimal | Flex: `amount` / TWS: computed | `activities[].netAmount` | `node.amount.amount` | `amount` (18,2) |
| `fee` | BigDecimal? | Flex: `ibCommission` / TWS: `execution.commission()` | `activities[].commission` | `node.fee.amount` | `fee` (18,4) |
| `currency` | String | Flex: `currency` / TWS: `contract.currency()` | `activities[].currency` | `node.amount.currency` | `currency` (3) |
| `tradeDate` | LocalDate | Flex: `tradeDate` / TWS: `execution.time()` | `activities[].tradeDate` | `node.occurredAt` (parse date) | `tradeDate` |
| `settlementDate` | LocalDate? | Flex: `settleDate` | `activities[].settlementDate` | N/A | `settlementDate` |
| `optionType` | String? | Flex: `putCall` → "CALL"/"PUT" | N/A (via symbol lookup) | N/A | `optionType` (20) |

**Activity type normalization:**

| Unified Enum | IBKR Flex `code` | Questrade `type` | Wealthsimple `type` |
|---|---|---|---|
| BUY | "BUY", "BOT" | "Trades" (action=Buy) | "buy" |
| SELL | "SELL", "SLD" | "Trades" (action=Sell) | "sell" |
| DIVIDEND | "DIV", "CDIV" | "Dividends" | "dividend" |
| TRANSFER_IN | "DEP" | "Deposits" | "deposit", "institutional_transfer" |
| TRANSFER_OUT | "WITH" | "Withdrawals" | "withdrawal" |
| FEE | "COMM", "OTHER_FEE" | "Fees", "FX conversion" | "fee" |
| COMMISSION | "COMM" | "Commissions" | N/A (included in fee) |
| INTEREST | "INT" | "Interest" | "interest" |
| OPTION_EXPIRATION | "EXP" | N/A | N/A |
| OPTION_ASSIGNMENT | "ASSIGN" | N/A | N/A |
| OPTION_EXERCISE | "EXER" | N/A | N/A |
| STOCK_SPLIT | "SPLIT" | N/A | "stock_split" |
| CORPORATE_ACTION | "CA" | "Corporate actions" | "reorganization" |
| OTHER | Anything else | Anything else | Anything else |

---

### Orders

| Unified Field | Type | IBKR (TWS) | Questrade (REST) | Wealthsimple (GQL) | Existing Entity (`TradeOrder`) |
|---|---|---|---|---|---|
| `brokerOrderId` | String | `order.orderId()` (int → String) | `orders[].id` (int → String) | `node.orderId` | `brokerOrderId` (255) |
| `symbol` | String | `contract.symbol()` | `orders[].symbol` | `node.securitySymbol` | `symbol` (20) |
| `action` | OrderAction (enum) | `order.action()` ("BUY"/"SELL") | `orders[].side` ("Buy"/"Sell") | `node.orderSubType` ("buy_quantity"/"sell_quantity") | `action` (enum BUY/SELL) |
| `orderType` | OrderType (enum) | `order.orderType()` ("MKT"/"LMT"/"STP"/"STP LMT") | `orders[].orderType` ("Market"/"Limit"/"Stop"/"StopLimit") | `node.orderType` ("buy_quantity"/"sell_quantity") + `limitPrice` presence | `orderType` (enum MARKET/LIMIT) |
| `timeInForce` | TimeInForce (enum) | `order.tif()` ("DAY"/"GTC"/"IOC"/"FOK") | `orders[].timeInForce` ("Day"/"GoodTillCanceled"/"IOC"/"FOK") | "day" / "until_cancel" | `timeInForce` (enum DAY/GTC) |
| `totalQuantity` | BigDecimal | `order.totalQuantity()` (Decimal) | `orders[].totalQuantity` | `node.quantity` | `requestedUnits` (18,6) |
| `filledQuantity` | BigDecimal? | `order.filledQuantity()` (Decimal) | `orders[].filledQuantity` | `node.fillQuantity` | `filledUnits` (18,6) |
| `executionPrice` | BigDecimal? | `execution.avgPrice()` or `orderState.avgFillPrice()` | `orders[].avgExecPrice` | `node.fillPrice.amount` | `filledPrice` (18,6) |
| `limitPrice` | BigDecimal? | `order.lmtPrice()` | `orders[].limitPrice` | `node.limitPrice.amount` | `limitPrice` (18,6) |
| `stopPrice` | BigDecimal? | `order.auxPrice()` | `orders[].stopPrice` | N/A (stop-limit uses `limitPrice`) | N/A (not in entity) |
| `status` | OrderStatus (enum) | `orderState.status()` | `orders[].state` | `node.status` | `status` (enum) |
| `currency` | String? | `contract.currency()` | `orders[].currency` | N/A | `currency` (3) |
| `submittedAt` | OffsetDateTime? | TWS internal (no direct field) | `orders[].creationTime` (ISO8601) | `node.createdAt` | `submittedAt` |
| `filledAt` | OffsetDateTime? | `execution.time()` | `orders[].updateTime` (when filled) | `node.updatedAt` (when filled) | `filledAt` |

**Order status normalization:**

| Unified Enum | IBKR `orderState.status()` | Questrade `state` | Wealthsimple `status` |
|---|---|---|---|
| PENDING | "PendingSubmit", "PendingCancel" | "Pending" | "submitted" |
| SUBMITTED | "Submitted", "PreSubmitted" | "Accepted", "Open" | "posted" |
| FILLED | "Filled" | "Executed" | "filled" |
| PARTIALLY_FILLED | "PartiallyFilled" (custom check) | "PartiallyExecuted" | "partial_fill" |
| CANCELLED | "Cancelled", "ApiCancelled" | "Canceled", "Expired" | "cancelled" |
| REJECTED | "Inactive" (with error) | "Rejected" | "rejected" |
| FAILED | "Error" | "Failed" | "failed" |

**Order type normalization:**

| Unified Enum | IBKR `orderType` | Questrade `orderType` | Wealthsimple |
|---|---|---|---|
| MARKET | "MKT" | "Market" | No `limitPrice` present |
| LIMIT | "LMT" | "Limit" | `limitPrice` present |
| STOP | "STP" | "Stop" | N/A |
| STOP_LIMIT | "STP LMT" | "StopLimit" | "stop_limit" |

---

## Internal REST API (called by portfolio service)

### Connection Management
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/gateway/connections` | Register broker connection (encrypts & stores credentials) |
| GET | `/api/v1/gateway/connections?userId={id}` | List user's connections |
| GET | `/api/v1/gateway/connections/{id}` | Get connection details + capabilities |
| DELETE | `/api/v1/gateway/connections/{id}` | Remove connection |
| POST | `/api/v1/gateway/connections/{id}/validate` | Test connectivity |
| POST | `/api/v1/gateway/connections/{id}/refresh` | Rotate OAuth tokens |

### Data Retrieval
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/gateway/connections/{id}/accounts` | List accounts |
| GET | `/api/v1/gateway/connections/{id}/accounts/{accId}/balances` | Get balances |
| GET | `/api/v1/gateway/connections/{id}/accounts/{accId}/positions` | Get positions |
| GET | `/api/v1/gateway/connections/{id}/accounts/{accId}/activities?startDate=&endDate=` | Get transactions |
| GET | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders?status=` | Get orders |

### Order Execution
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders` | Place order |
| DELETE | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders/{orderId}` | Cancel order |

### Health
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/gateway/health` | Overall health + per-broker status |
| GET | `/api/v1/gateway/health/{brokerType}` | Specific broker health |

---

## Credential Storage

**Database**: Own Flyway schema `broker_gateway` with `connections` table:
- `credentials_encrypted` (TEXT) — AES-256-GCM encrypted JSON of `BrokerCredentials`
- `accounts_json` (JSONB) — cached account list
- Indexed on `user_id` and `broker_type`

**IBKR special case**: Credentials are just `host/port/clientId` (no tokens). Connection is via persistent TCP socket.

**Wealthsimple password**: Stored encrypted alongside OAuth tokens. Needed for re-auth when tokens fully expire. Risk acknowledged — users warned before storing.

**Service-to-service auth**: API key in `X-Gateway-Api-Key` header. Portfolio service includes this on all requests.

---

## Error Handling

Unified exception hierarchy mapping to HTTP statuses:
| Exception | HTTP | When |
|---|---|---|
| `BrokerAuthenticationException` | 401 | Token expired, invalid credentials, needs re-auth |
| `BrokerConnectionException` | 502 | Broker API unreachable, TWS disconnected |
| `BrokerRateLimitException` | 429 | WS 7/hr limit, Questrade rate limit |
| `BrokerOrderRejectedException` | 422 | Insufficient funds, invalid symbol, quantity issues |
| `BrokerUnsupportedOperationException` | 501 | Feature not available for this broker |
| `ConnectionNotFoundException` | 404 | Invalid connection ID |
| `BrokerDataException` | 502 | Unexpected response from broker |

---

## Per-Broker Implementation Notes

### IBKR
- Reuse connection management patterns from `market-data-service/IbkrConnectionManager`
- Wrap async `EWrapper` callbacks with `CompletableFuture` for synchronous REST responses
- Flex Reports (XML) for historical transactions — requires pre-configured Flex Query ID + token
- `@ConditionalOnProperty("broker-gateway.ibkr.enabled")` — adapter only instantiates when enabled
- Dependencies: vendored `TwsApi.jar`, Kotlin coroutines for async handling

### Questrade
- `WebClient`-based REST client with dynamic base URL from token exchange
- `QuestradeTokenManager` must atomically persist new refresh token after every rotation
- Order placement may require Questrade Partner App registration
- ~1 req/sec rate limiting via Resilience4j

### Wealthsimple
- Custom GraphQL client (raw HTTP POST with `operationName`, `variables`, `query`)
- Operation names reverse-engineered from web app: `FetchAllAccounts`, `FetchIdentityPositions`, etc.
- `WealthsimpleRateLimiter` enforces 7 trades/hr with sliding window
- 2FA handling: initial auth may require OTP code from user
- All Wealthsimple code isolated behind adapter interface for easy removal if API breaks

---

## Migration Plan (SnapTrade → Gateway)

### Phase 1: Build gateway skeleton
Project structure, build, Docker, Flyway, credential storage, `BrokerAdapter` interface, unified DTOs, REST controllers, error handling, health check.

### Phase 2: IBKR adapter
TWS socket connection, account/position/balance/order operations, Flex Reports for history.

### Phase 3: Questrade adapter
REST client, OAuth token rotation, all data operations.

### Phase 4: Wealthsimple adapter
GraphQL client, password-grant auth, 2FA, rate limiter.

### Phase 5: Portfolio service migration
Create `BrokerGatewayClient` in portfolio service. Refactor `BrokerService`, `PositionFetchService`, `ActivityIngestionService` to call gateway instead of SnapTrade.

Key files to modify:
- `broker/service/BrokerService.kt` — swap SnapTrade calls for gateway client
- `broker/service/PositionFetchService.kt` — use `gatewayClient.getPositions()` + `getBalances()`
- `broker/service/ActivityIngestionService.kt` — use `gatewayClient.getActivities()`
- `broker/controller/BrokerController.kt` — adjust endpoints for multi-broker support

### Phase 6: SnapTrade removal
Delete adapter, service, config, scheduler, DTOs. Flyway migration to drop SnapTrade-specific columns/tables. Remove `snaptrade-java-sdk` dependency.

---

## Configuration

Key environment variables:
| Variable | Purpose |
|---|---|
| `BROKER_ENCRYPTION_KEY` | AES-256-GCM key for credential encryption |
| `GATEWAY_API_KEY` | Service-to-service auth |
| `IBKR_GATEWAY_ENABLED` | Enable IBKR adapter |
| `IBKR_HOST` / `IBKR_PORT` | TWS/Gateway address (shared with market-data) |
| `IBKR_GATEWAY_CLIENT_ID` | Client ID 2 (market-data uses 1) |
| `IBKR_FLEX_TOKEN` / `IBKR_FLEX_QUERY_ID` | Flex Reports for historical data |
| `QUESTRADE_ENABLED` | Enable Questrade adapter |
| `WEALTHSIMPLE_ENABLED` | Enable Wealthsimple adapter |
| `BROKER_GATEWAY_URL` | Added to portfolio service (default: `http://broker-gateway-service:8084`) |

---

## Verification

1. `docker compose exec broker-gateway-service ./gradlew test` passes
2. Each adapter validates connection on startup (when enabled)
3. Gateway health endpoint returns per-broker status
4. Portfolio service successfully calls gateway for positions/balances/activities
5. Integration test: connect broker → fetch positions → place order → cancel order
6. Frontend broker connection flow works end-to-end
