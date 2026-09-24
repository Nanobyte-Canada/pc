# Strategy Service Production-Ready Design

## Overview

Make the strategy service production-ready with one-click Questrade trading, updated multi-leg options strategies, improved UI with strategy education, real-time options quote streaming, and a comprehensive E2E test suite.

## Current State Assessment

### Backend (strategy service)
- 7 strategies defined (Bull Call Spread, Bear Put Spread, Bull Put Spread, Bear Call Spread, Iron Condor, Covered Call, Protective Put)
- Zero unit tests (no `src/test/` directory)
- `StrategyCalculator.kt` computes P&L curves and break-evens but Greeks are stubs (gamma/theta/vega always zero at line 115)
- `LegValidator.kt` validates: non-empty, no duplicates, same expiration
- No integration with broker-gateway for order placement
- Education engine generates contextual warnings

### Broker Gateway
- `BrokerAdapter` interface supports single-leg orders via `placeOrder()`
- `QuestradeAdapter` resolves option symbolIds via search API
- `OrderRequest` supports `optionType`, `strike`, `expiry` fields but only for single-leg
- No multi-leg/combo order support

### Frontend
- `OptionsPage` at `/options` with chain table, strategy selector, leg builder, P&L chart
- `OptionsPage` hidden in "More" overflow menu on mobile
- `useMarketDataWebSocket` hook exists (ref-counting, reconnection, requestAnimationFrame batching) but only supports stock quotes
- `strategyStore` (Zustand) holds strategies, selected strategy, legs, calculation result
- `optionsStrategyService.ts` calls strategy service + broker order endpoints

### E2E Tests
- `options-chain.spec.ts`: 4 tests, 3 permanently skipped (empty state until symbol loaded)
- No strategy calculation or trading tests exist

## Requirements

| Decision | Choice |
|----------|--------|
| Phase order | All in one phase (backend + frontend + trading + streaming + tests) |
| Multi-leg orders | Atomic combo order via Questrade API |
| UI scope | Full overhaul |
| Strategies | Remove Covered Call + Protective Put, add Butterfly Spread |
| Education | Per-strategy: bullish/bearish/neutral labels, when-to-use, max P&L with dollar values |
| Streaming | Real-time options quotes via WebSocket |
| Trading | One-click Questrade only |
| E2E tests | Comprehensive strategy + trading test suite |

---

## Section 1: Backend Strategy Changes

### 1.1 StrategyType Enum Update

**File:** `backend/strategy/src/main/kotlin/com/portfolio/strategy/model/StrategyType.kt`

Remove:
- `COVERED_CALL`
- `PROTECTIVE_PUT`

Add:
- `BUTTERFLY_SPREAD`

Resulting enum (6 values):
```
BULL_CALL_SPREAD, BEAR_PUT_SPREAD, BULL_PUT_SPREAD, BEAR_CALL_SPREAD, IRON_CONDOR, BUTTERFLY_SPREAD
```

### 1.2 Butterfly Spread Definition

**Legs (3):**
1. BUY 1 ATM call
2. SELL 2 slightly OTM calls (OTM_1)
3. BUY 1 further OTM call (OTM_2)

**Properties:**
- Outlook: Neutral (range-bound)
- Risk profile: Limited risk, limited profit
- Max profit: Occurs at middle strike (difference between middle and lower strike minus net debit, times 100)
- Max loss: Net debit paid (times 100)
- Break-even: Two points (lower strike + net debit, upper strike - net debit)

**Leg templates:**
```
LegTemplate(BUY, CALL, ATM)
LegTemplate(SELL, CALL, OTM_1)  -- quantity 2
LegTemplate(BUY, CALL, OTM_2)
```

### 1.3 StrategyRegistry Update

**File:** `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyRegistry.kt`

- Remove Covered Call and Protective Put entries from `strategies` map
- Remove their education content from `educationContent` map
- Add Butterfly Spread entry:

```kotlin
StrategyType.BUTTERFLY_SPREAD to StrategyDefinition(
    type = StrategyType.BUTTERFLY_SPREAD,
    displayName = "Butterfly Spread",
    description = "Buy 1 ATM call, sell 2 OTM calls, buy 1 further OTM call — profits from low volatility at the middle strike",
    outlook = "Neutral (Range-Bound)",
    riskProfile = "Limited risk, limited profit",
    legCount = 3,
    legTemplates = listOf(
        LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.ATM),
        LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.OTM_1),  // quantity = 2
        LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.OTM_2)
    )
)
```

Add education content:
```kotlin
StrategyType.BUTTERFLY_SPREAD to EducationContent(
    whenToUse = "Use when you expect the underlying to stay near a specific price (the middle strike) with low volatility.",
    riskExplanation = "Maximum loss is the net debit paid to enter the spread. Maximum profit is the difference between the middle and outer strikes minus the net debit.",
    keyCharacteristics = listOf(
        "Low-cost way to profit from low volatility",
        "Three strike prices create a profit 'tent' at the middle strike",
        "Max profit at exactly the middle strike price",
        "Two break-even points define the profit zone"
    ),
    warnings = listOf(
        "Profit potential is capped at the middle strike",
        "Time decay works against you if stock doesn't move",
        "Narrow profit zone means precision matters"
    )
)
```

### 1.4 StrategyCalculator Enhancement

**File:** `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyCalculator.kt`

Changes:
1. **Fix Greeks computation** — compute gamma, theta, vega using Black-Scholes from `common/math/BlackScholes.kt` (already exists but unused). Use `GreeksCalculator` pattern from market-data service.
2. **Add dollar-value P&L** — add `maxProfitDollars` and `maxLossDollars` fields to `CalculationResult` and `CalculateResponse` DTO. Multiply max profit/loss by 100 (per contract).

**New DTO fields in `CalculateResponse`:**
```kotlin
val maxProfitDollars: BigDecimal   // maxProfit * 100; maxProfit is already position-level
val maxLossDollars: BigDecimal     // maxLoss * 100 (positive value = max loss amount)
val netDebitCreditDollars: BigDecimal  // netDebitCredit * 100
```

Quantity is applied per leg when the payoff curve is built (`leg.mid * leg.quantity`,
and the same for intrinsic value), not as a single top-level multiplier. This is
required for asymmetric structures such as the Butterfly, whose middle leg carries
quantity 2. Scaling an entire position (e.g. five butterflies) must be expressed by
editing each leg.

### 1.5 LegValidator Enhancement

**File:** `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt`

Add validation:
- For BUTTERFLY_SPREAD: exactly 3 legs required; all legs must share one option type
  (all calls or all puts — a put butterfly is a valid bearish structure); the SELL leg
  must carry quantity 2 and the two BUY legs quantity 1 each; the SELL strike must sit
  strictly between the two BUY strikes
- For other spreads: leg count must match strategy definition's `legCount`

### 1.6 New Trade Endpoint

**File:** `backend/strategy/src/main/kotlin/com/portfolio/strategy/api/controller/StrategyController.kt`

New endpoint:
```kotlin
@PostMapping("/trade")
fun tradeStrategy(@RequestBody request: TradeRequest): TradeResponse
```

**New DTOs:**
```kotlin
data class TradeRequest(
    val strategyType: StrategyType,
    val legs: List<LegRequest>,
    val spotPrice: BigDecimal,
    val connectionId: Long,
    val accountId: String,
    val orderType: String = "LIMIT",   // LIMIT for combo orders
    val limitPrice: BigDecimal,
    val timeInForce: String = "DAY"
)

data class TradeResponse(
    val orderId: String?,
    val status: String,     // SUBMITTED, REJECTED, ERROR
    val message: String,
    val legs: List<LegResult>
)

data class LegResult(
    val action: String,
    val symbol: String,
    val quantity: Int,
    val status: String
)
```

`strategyType` is accepted as a `String` and parsed server-side, so an unknown value
yields a 400 with a clear message rather than a Jackson deserialization failure.
Each leg must also carry `symbol` — the combo-order path needs it to resolve Questrade
symbolIds, and a leg without one is rejected before any broker call.

**Trade flow:**
1. Validate legs via `LegValidator`
2. Call portfolio backend's `BrokerGatewayClient` (or direct broker-gateway call) to place combo order
3. Return order result to frontend

---

## Section 2: Broker-Gateway Multi-Leg Orders

### 2.1 New DTO

**File:** `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/OrderRequest.kt`

Add:
```kotlin
data class MultiLegOrderRequest(
    val legs: List<LegOrderDetail>,
    val orderType: OrderType,
    val limitPrice: BigDecimal,
    val timeInForce: TimeInForce = TimeInForce.DAY
)

data class LegOrderDetail(
    val symbol: String,
    val action: OrderAction,
    val quantity: BigDecimal,
    val optionType: String?,
    val strike: BigDecimal?,
    val expiry: String?,
    val symbolId: Long? = null
)
```

### 2.2 BrokerAdapter Extension

**File:** `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/BrokerAdapter.kt`

Add to interface:
```kotlin
fun placeMultiLegOrder(
    credentials: BrokerCredentials,
    accountId: String,
    request: MultiLegOrderRequest
): OrderResult {
    return OrderResult(null, OrderStatus.REJECTED,
        "Atomic multi-leg orders are not supported by ${brokerType.name}")
}
```

Rationale: a multi-leg strategy executed as independent single-leg orders has leg risk —
if any leg fails or fills at a different price, the account is left with an unbalanced
position that no longer matches the calculated payoff. Rejecting is safer than degrading.
Only adapters that can submit an atomic combo order (Questrade) support this path.

### 2.3 QuestradeAdapter Override

**File:** `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt`

Override `placeMultiLegOrder()`:
- Resolve symbolIds for all legs via existing search API
- Build Questrade combo order payload: each leg becomes an `orderLeg` with `symbolId`, `action`, `quantity`, `quantityType`
- Submit as single API call to `POST /v1/accounts/{accountId}/orders`
- Questrade handles atomic execution

### 2.4 New Controller Endpoint

**File:** `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/ComboOrderController.kt`

Route: `POST /api/v1/gateway/connections/{connectionId}/accounts/{accountId}/combo-orders`

`connectionId` and `accountId` are both path variables because credential resolution and
account scoping are per-connection; the originally specified bare `/combo` route on
`OrderController` could not carry them.

---

## Section 3: Frontend UI Overhaul

### 3.1 Navigation Update

Move `/options` from "More" overflow menu to main navigation:
- **Desktop (`IconRail`):** Add strategies icon (chart/strategy icon) as a top-level rail item
- **Mobile (`BottomTabBar`):** Add as a primary tab with "Strategies" label

### 3.2 OptionsPage Layout Redesign

**Three-panel layout:**

**Left sidebar — Strategy Selector + Education:**
- Card-based strategy selector (replaces pill-button `StrategySelector`)
- Each card: strategy name, outlook badge (Bullish/Bearish/Neutral with color), risk profile, leg count
- Clicking a card loads education panel below:
  - "When to Use" section
  - "Risk Explanation" section
  - "Key Characteristics" bullet list
  - "Warnings" callout

**Center panel — Leg Builder + Calculation:**
- Enhanced `LegBuilder` with:
  - Quantity adjusters per leg
  - Real-time mid prices from streaming quotes
  - Live net debit/credit calculation
  - Dollar-value P&L metrics (max profit/loss in both % and $ per contract)
- Strategy education content displayed above leg builder

**Right panel — P&L Chart + Trade:**
- Enhanced `PnlChart` with:
  - Dollar value axis labels
  - Break-even price markers
  - Max profit/loss annotations
- **OneClickTradeButton:** Prominent "Trade on Questrade" button
  - Confirms legs + limit price on click
  - Submits atomic combo order
  - Shows success/failure toast notification
- **ConnectionStatus indicator:** Shows Questrade connection state; prompts to connect if not connected

**Bottom panel — Options Chain (full width):**
- Full bidirectional chain (calls left, puts right)
- Clicking bid/ask adds/removes legs
- Real-time streaming price updates (flash green/red on change)

### 3.3 New Components

| Component | Purpose |
|-----------|---------|
| `StrategyEducationCard` | Displays when-to-use, risk explanation, characteristics, warnings |
| `DollarValuePnlMetrics` | Shows max profit/loss in $ and % per contract |
| `OneClickTradeButton` | Single button to execute strategy on Questrade |
| `ConnectionStatusBadge` | Shows broker connection status with connect prompt |

### 3.4 Existing Component Updates

| Component | Changes |
|-----------|---------|
| `StrategySelector` | Replace pills with card layout |
| `LegBuilder` | Add quantity adjusters, live mid prices, dollar-value metrics |
| `PnlChart` | Add dollar axis, break-even labels, max annotations |
| `QuoteBar` | Wire to streaming for real-time updates |
| `OptionsChainTable` | Wire streaming for real-time bid/ask updates |
| `UnderlyingSearch` | Add autocomplete suggestions |

### 3.5 Store Updates

**File:** `frontend/src/stores/strategyStore.ts`

Add:
- `connectionStatus: { connected: boolean, brokerType: string }` — broker connection state
- `tradeInProgress: boolean` — disables trade button during submission

**File:** `frontend/src/types/options.ts`

Add types:
```typescript
interface StrategyEducation {
  whenToUse: string;
  riskExplanation: string;
  keyCharacteristics: string[];
  warnings: string[];
}

interface TradeRequest {
  strategyType: string;
  legs: Leg[];
  spotPrice: number;
  connectionId: number;
  accountId: string;
  limitPrice: number;
}

interface TradeResponse {
  orderId: string | null;
  status: 'SUBMITTED' | 'REJECTED' | 'ERROR';
  message: string;
}
```

---

## Section 4: Streaming Integration

### 4.1 Backend (market-data service)

Options streaming already exists in the market-data service and was **not** part of this
work: `OptionStreamingService.startStreaming(symbol, expiry, strike, optionType)` /
`stopStreaming(...)` are reference-counted, and `QuoteWebSocketHandler` broadcasts
normalized `option_quote` messages that the frontend's `useMarketDataWebSocket` hook
consumes via `batchUpdateChainQuotes`.

Consequently there is no `option_tick` message type and no conId-keyed subscription API.
The remaining frontend work is consuming this existing stream in the leg builder
(live mid prices) and flashing price changes in the chain table.

### 4.2 Frontend Streaming Wiring

Live leg-builder prices are derived directly from the chain store — a pure helper,
`derivePriceFor(chains, underlying)` in `frontend/src/hooks/liveMids.ts`, resolves each
leg's quote using the same strike-key normalization as `batchUpdateChainQuotes` and
prefers the stored `mid`, falling back to the bid/ask midpoint. The unused
`useOptionStreaming` hook was deleted: its per-contract subscription logic duplicated
the page-level chain subscription. Contract-level batch subscription by conId is not
required by the shipped design.

**Chain table integration:**
- `OptionsChainTable` cells receive live updates from streaming hook
- Price flash animation: green for up, red for down (CSS transition on color)
- Debounce updates via `requestAnimationFrame` (existing pattern)

---

## Section 5: E2E Test Suite

### 5.1 New Spec: `e2e/tests/regression/strategies.spec.ts`

Following existing conventions: scenario IDs, `@regression` tag, authenticated beforeEach.

| Scenario ID | Test | Assertions |
|-------------|------|------------|
| `STRAT-001` | Strategy list loads | 6 strategies render with correct names |
| `STRAT-002` | Strategy education display | Click each card, education panel shows when-to-use, risk, characteristics |
| `STRAT-003` | Outlook labels | Each strategy shows correct bullish/bearish/neutral badge |
| `STRAT-004` | Butterfly Spread present | Butterfly Spread in list with 3-leg description |
| `STRAT-005` | Covered Call removed | Covered Call NOT in strategy list |
| `STRAT-006` | Protective Put removed | Protective Put NOT in strategy list |
| `STRAT-007` | Strategy selection updates UI | Select strategy, education + leg template update |
| `STRAT-008` | All strategies have education | Each strategy has non-empty when-to-use and risk explanation |

### 5.2 New Spec: `e2e/tests/regression/options-trading.spec.ts`

| Scenario ID | Test | Assertions |
|-------------|------|------------|
| `TRADE-001` | Load options chain | Enter symbol, load chain, strikes + quotes render |
| `TRADE-002` | Add legs to builder | Click bid/ask, LegBuilder shows correct badges |
| `TRADE-003` | Calculate P&L | Add legs, click Calculate, P&L chart renders with metrics |
| `TRADE-004` | Dollar-value P&L | Max profit/loss shown in both % and $ per contract |
| `TRADE-005` | Trade button when disconnected | Trade button shows connection prompt |
| `TRADE-006` | Remove legs | Add legs, Clear All, builder empty |
| `TRADE-007` | Iron Condor with too few legs | Select Iron Condor, add 2 legs, validation error shown |
| `TRADE-008` | Strategy suggest by outlook | Use suggest endpoint with "bullish", only bullish strategies returned |
| `TRADE-009` | P&L break-even points | Verify break-even prices shown on chart |
| `TRADE-010` | Butterfly Spread leg count | Select Butterfly Spread, verify 3-leg template loads |

TRADE-007 (Iron Condor with too few legs) and TRADE-009 (break-even markers) were added
after the initial implementation. The suggest-endpoint scenario is TRADE-008, not
TRADE-010 as first drafted. See ADR-0034 for the deployed-UAT scope of this suite.

### 5.3 Existing Test Fixes

**`options-chain.spec.ts`:**
- Remove 3 permanent skips by implementing proper test data setup
- Use API call to seed known symbol (e.g., SPY) before asserting chain state
- Keep conditional skip for environments where market data provider is unavailable

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────┐
│                    Frontend (React)                    │
│  OptionsPage: StrategySelector + Education + LegBuilder│
│  + PnlChart + OneClickTradeButton + OptionsChainTable  │
└────────┬──────────────┬──────────────────┬────────────┘
         │ HTTP          │ HTTP             │ WebSocket
         ▼               ▼                  ▼
┌────────────┐  ┌────────────────┐  ┌─────────────────┐
│  Strategy  │  │   Portfolio    │  │   Market Data   │
│  Service   │  │   Backend      │  │   Service       │
│  (8083)    │  │   (8080)       │  │   (8082)        │
│            │  │                │  │                  │
│ /strategies│  │ /auth/*        │  │ /quotes/*        │
│ /trade     │  │ /brokers/*     │  │ /chains/*        │
└─────┬──────┘  └───────┬────────┘  │ /ws/options      │
      │                 │           └────────┬──────────┘
      │ HTTP            │ HTTP               │ WebSocket
      ▼                 ▼                    ▼
┌──────────────────────────────────────────────────────┐
│              Broker Gateway (8084)                    │
│  /api/v1/gateway/connections/*/accounts/*/combo-orders│
│  QuestradeAdapter.placeMultiLegOrder()               │
└──────────────────────┬───────────────────────────────┘
                       │ REST API
                       ▼
              ┌─────────────────┐
              │    Questrade     │
              │      API         │
              └─────────────────┘
```

---

## Database Changes

No schema changes required. Existing Flyway migrations cover all needed tables. The strategy service is stateless for calculations.

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Questrade combo order API format changes | Abstracted behind `BrokerAdapter`; unsupported or failing adapters reject the order rather than degrading to sequential single-leg execution |
| Streaming WebSocket connection drops | Existing reconnection logic in `useMarketDataWebSocket` handles reconnection |
| Strategy calculation errors | Add comprehensive unit tests (currently zero) |
| Multi-leg order partial fill | Questrade combo orders are atomic — all-or-nothing fill |
| Rate limiting on streaming | Reference-counted subscriptions; unsubscribe on component unmount |

---

## Success Criteria

1. 6 strategies display correctly (Butterfly Spread added, Covered Call + Protective Put removed)
2. Each strategy shows education content with outlook badge
3. Max profit/loss displayed in both percentage and dollar values
4. One-click trade submits atomic combo order to Questrade
5. Options chain shows real-time streaming quotes
6. All E2E tests pass
7. Strategy service has unit tests with >80% coverage on calculator and validator
