# Wheel Screen 3: Order Submission (End-to-End) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the OrderPanel Buy/Sell buttons to actually submit orders through the backend, extending all DTOs and the database to carry option-specific fields (optionType, strike, expiry, symbolId, stopPrice) end-to-end from frontend to broker gateway.

**Architecture:** Extend the existing order pipeline at every layer: frontend types → trading service → TradingController → OrderExecutionService → BrokerGatewayClient → Gateway OrderController → broker adapters. Add a Flyway migration for option columns on `trade_orders`. Expose broker `supportedOrderTypes` in the connections response so the frontend can populate the Order Type dropdown. Broker adapters receive the new fields but continue to handle them as stock orders for now (adapter-level option contract support is Phase 3b).

**Tech Stack:** Kotlin/Spring Boot (backend), Flyway (migrations), React/TypeScript (frontend), MockK (testing)

**Design spec:** `docs/superpowers/specs/2026-05-29-wheel-screen3-order-design.md`

**Important constraints:**
- No JDK on host — all backend build/test runs inside Docker: `docker compose exec backend ./gradlew test`
- Flyway next number: V74
- MockK for mocking, not Mockito
- Controllers return DTOs, never entities

---

## File Map

| File | Action | Layer | Responsibility |
|------|--------|-------|----------------|
| `backend/portfolio/src/main/resources/db/migration/V74__add_option_fields_to_trade_orders.sql` | Create | DB | Add option columns to trade_orders |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt` | Modify | Entity | Add option fields to JPA entity |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/TradingDtos.kt` | Modify | DTO | Add option fields + stopPrice to TradeExecutionInput |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt` | Modify | Service | Pass option fields + stopPrice in orderBody map |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/BrokerDtos.kt` | Modify | DTO | Add supportedOrderTypes to BrokerConnectionDto |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerConnectionService.kt` | Modify | Service | Populate supportedOrderTypes from gateway capabilities |
| `frontend/src/types/trading.ts` | Modify | Types | Add option fields + stopPrice to TradeExecutionInput |
| `frontend/src/types/broker.ts` | Modify | Types | Add supportedOrderTypes to BrokerConnection |
| `frontend/src/services/tradingService.ts` | Modify | Service | Add submitOptionsOrder function |
| `frontend/src/components/wheel/OrderPanel.tsx` | Modify | UI | Wire Buy/Sell to API, add stop price field, broker order types |
| `frontend/src/components/wheel/OrderPanel.css` | Modify | CSS | Stop price input styling |

---

### Task 1: Flyway migration — add option fields to trade_orders

**Files:**
- Create: `backend/portfolio/src/main/resources/db/migration/V74__add_option_fields_to_trade_orders.sql`

- [ ] **Step 1: Create migration file**

```sql
-- V74: Add option-specific fields to trade_orders for wheel strategy
ALTER TABLE trade_orders ADD COLUMN option_type VARCHAR(4);
ALTER TABLE trade_orders ADD COLUMN strike_price DECIMAL(12, 4);
ALTER TABLE trade_orders ADD COLUMN expiration_date DATE;
ALTER TABLE trade_orders ADD COLUMN symbol_id BIGINT;
ALTER TABLE trade_orders ADD COLUMN stop_price DECIMAL(12, 4);
```

- [ ] **Step 2: Verify migration applies**

Run: `docker compose up --build -d backend && sleep 45 && docker compose logs backend --tail 20 | grep -i "migrat"`
Expected: Log shows "Successfully applied 1 migration to schema" or "V74" applied.

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/resources/db/migration/V74__add_option_fields_to_trade_orders.sql
git commit -m "feat(db): add option fields to trade_orders — V74 migration"
```

---

### Task 2: Extend TradeOrder entity with option fields

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt`

- [ ] **Step 1: Add option fields to the entity**

Add these fields after the existing `limitPrice` field (around line 67):

```kotlin
@Column(name = "option_type")
var optionType: String? = null,

@Column(name = "strike_price")
var strikePrice: BigDecimal? = null,

@Column(name = "expiration_date")
var expirationDate: java.time.LocalDate? = null,

@Column(name = "symbol_id")
var symbolId: Long? = null,

@Column(name = "stop_price")
var stopPrice: BigDecimal? = null,
```

- [ ] **Step 2: Update the toDto() method** (if one exists) to include the new fields in TradeOrderDto

Find where `TradeOrderDto` is built from `TradeOrder` and add the new fields. The DTO in TradingDtos.kt also needs these — done in Task 3.

- [ ] **Step 3: Verify build inside Docker**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/TradeOrder.kt
git commit -m "feat(entity): add option fields to TradeOrder entity"
```

---

### Task 3: Extend TradingDtos with option fields

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/TradingDtos.kt`

- [ ] **Step 1: Add option fields to TradeExecutionInput**

Current TradeExecutionInput has: `symbol, action, units, price, amount, currency, connectionId, limitPrice?`

Add after `limitPrice`:

```kotlin
val optionType: String? = null,    // "CALL" or "PUT"
val strikePrice: BigDecimal? = null,
val expirationDate: String? = null, // ISO date "2026-06-19"
val symbolId: Long? = null,
val stopPrice: BigDecimal? = null,
```

- [ ] **Step 2: Add option fields to TradeOrderDto**

Find the `TradeOrderDto` data class and add matching fields:

```kotlin
val optionType: String? = null,
val strikePrice: BigDecimal? = null,
val expirationDate: String? = null,
val symbolId: Long? = null,
val stopPrice: BigDecimal? = null,
```

- [ ] **Step 3: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/TradingDtos.kt
git commit -m "feat(dto): add option fields and stopPrice to trading DTOs"
```

---

### Task 4: Update OrderExecutionService to pass option fields to gateway

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt`

- [ ] **Step 1: Store option fields when creating TradeOrder**

In the section where `TradeOrder` is constructed (around line 55-72), add the new fields from `tradeInput`:

```kotlin
optionType = tradeInput.optionType,
strikePrice = tradeInput.strikePrice,
expirationDate = tradeInput.expirationDate?.let { java.time.LocalDate.parse(it) },
symbolId = tradeInput.symbolId,
stopPrice = tradeInput.stopPrice,
```

- [ ] **Step 2: Include option fields in orderBody map**

Update the `orderBody` map (lines 73-81) to include the new fields:

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
)
```

- [ ] **Step 3: Update the toDto mapping** to include new fields when building TradeOrderDto from TradeOrder

- [ ] **Step 4: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All existing tests pass (new fields are nullable, so existing tests shouldn't break).

- [ ] **Step 6: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/OrderExecutionService.kt
git commit -m "feat(service): pass option fields and stopPrice through to broker gateway"
```

---

### Task 5: Expose supportedOrderTypes in broker connections response

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/BrokerDtos.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerConnectionService.kt`

- [ ] **Step 1: Add field to BrokerConnectionDto**

Add after the existing `modelPortfolioName` field:

```kotlin
val supportedOrderTypes: List<String> = listOf("MARKET", "LIMIT"),
```

- [ ] **Step 2: Populate from gateway capabilities**

In `BrokerConnectionService.kt`, find where `BrokerConnectionDto` is built from the entity. Add logic to look up capabilities from the gateway client and include `supportedOrderTypes`.

If the gateway client doesn't have a capabilities endpoint exposed, default to `listOf("MARKET", "LIMIT")` for now and add a comment noting this should call the gateway in the future. The Questrade and IBKR adapters both support `MARKET, LIMIT, STOP, STOP_LIMIT` — we can hardcode by broker type:

```kotlin
val orderTypes = when (connection.broker?.slug) {
    "questrade", "ibkr" -> listOf("MARKET", "LIMIT", "STOP", "STOP_LIMIT")
    else -> listOf("MARKET", "LIMIT")
}
```

- [ ] **Step 3: Verify build and tests**

Run: `docker compose exec backend ./gradlew test`
Expected: Tests pass.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/BrokerDtos.kt backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerConnectionService.kt
git commit -m "feat(api): expose supportedOrderTypes in broker connections response"
```

---

### Task 6: Extend frontend types for options trading

**Files:**
- Modify: `frontend/src/types/trading.ts`
- Modify: `frontend/src/types/broker.ts`

- [ ] **Step 1: Add option fields to TradeExecutionInput**

In `frontend/src/types/trading.ts`, update `TradeExecutionInput`:

```typescript
export interface TradeExecutionInput {
  symbol: string
  action: 'BUY' | 'SELL'
  units: number
  price: number
  amount: number
  currency: string
  connectionId: number
  limitPrice?: number
  optionType?: string
  strikePrice?: number
  expirationDate?: string
  symbolId?: number
  stopPrice?: number
}
```

- [ ] **Step 2: Add supportedOrderTypes to BrokerConnection**

In `frontend/src/types/broker.ts`, add to `BrokerConnection` interface:

```typescript
supportedOrderTypes?: string[]
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/trading.ts frontend/src/types/broker.ts
git commit -m "feat(types): add option fields to trading types, supportedOrderTypes to broker"
```

---

### Task 7: Add options order submission to trading service

**Files:**
- Modify: `frontend/src/services/tradingService.ts`

- [ ] **Step 1: Add submitOptionsOrder function**

Add to `frontend/src/services/tradingService.ts`:

```typescript
export async function submitOptionsOrder(trade: TradeExecutionInput): Promise<TradeOrder> {
  const request: ExecuteTradesRequest = {
    groupId: 0,
    trades: [trade],
    orderType: trade.limitPrice != null ? 'LIMIT' : 'MARKET',
    timeInForce: 'DAY',
  }
  const response = await apiFetch(`${API_BASE}/execute`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  const result: ExecuteTradesResponse = await response.json()
  return result.orders[0]
}
```

Make sure `TradeExecutionInput` is imported from `@/types/trading`.

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/tradingService.ts
git commit -m "feat(service): add submitOptionsOrder to trading service"
```

---

### Task 8: Wire OrderPanel Buy/Sell buttons to API and add stop price field

**Files:**
- Modify: `frontend/src/components/wheel/OrderPanel.tsx`
- Modify: `frontend/src/components/wheel/OrderPanel.css`

- [ ] **Step 1: Add imports and state**

Add imports:
```typescript
import { submitOptionsOrder } from '@/services/tradingService'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
```

Add state for stop price and submission:
```typescript
const [stopPrice, setStopPrice] = useState('')
const [submitting, setSubmitting] = useState(false)
const [submitError, setSubmitError] = useState<string | null>(null)
```

- [ ] **Step 2: Get supported order types from broker connection**

Use the `accounts` prop to derive the selected account's broker, then look up order types. Since `accounts` has `brokerName`, determine supported types:

```typescript
const supportedOrderTypes = useMemo(() => {
  const account = accounts.find(a => a.connectionId === selectedAccountId)
  if (!account) return ['Market', 'Limit']
  const broker = account.brokerName.toLowerCase()
  if (broker.includes('questrade') || broker.includes('ibkr') || broker.includes('interactive'))
    return ['Market', 'Limit', 'Stop', 'Stop Limit']
  return ['Market', 'Limit']
}, [accounts, selectedAccountId])
```

Update the Order Type dropdown to use `supportedOrderTypes` instead of hardcoded options.

- [ ] **Step 3: Add stop price input field**

After the limit price section, add a stop price field that shows when orderType is 'Stop' or 'Stop Limit':

```tsx
{(orderType === 'Stop' || orderType === 'Stop Limit') && (
  <div className="order-panel__section">
    <label className="order-panel__label">Stop Price</label>
    <div className="order-panel__price-input-wrap">
      <span className="order-panel__price-currency">{currencyLabel}</span>
      <input
        type="text"
        inputMode="decimal"
        className="order-panel__price-input"
        value={stopPrice}
        onChange={e => {
          const v = e.target.value
          if (/^\d*\.?\d{0,2}$/.test(v) || v === '') setStopPrice(v)
        }}
        placeholder="0.00"
      />
    </div>
  </div>
)}
```

- [ ] **Step 4: Replace console.log handlers with actual API calls**

Replace `handleBuy` and `handleSell` with:

```typescript
const handleSubmitOrder = useCallback(async (action: 'BUY' | 'SELL') => {
  setSubmitting(true)
  setSubmitError(null)
  try {
    const lp = parseFloat(limitPrice)
    const sp = parseFloat(stopPrice)
    const strikeNum = parseFloat(strike)
    await submitOptionsOrder({
      symbol: ticker,
      action,
      units: quantity,
      price: lp || 0,
      amount: quantity * (lp || 0) * 100,
      currency: getCurrencyLabel(ticker) === 'C$' ? 'CAD' : 'USD',
      connectionId: selectedAccountId,
      limitPrice: orderType === 'Limit' || orderType === 'Stop Limit' ? lp : undefined,
      stopPrice: orderType === 'Stop' || orderType === 'Stop Limit' ? sp : undefined,
      optionType: optionType === 'Call' ? 'CALL' : 'PUT',
      strikePrice: strikeNum || undefined,
      expirationDate: expiration || undefined,
    })
    onClose()
  } catch (err) {
    setSubmitError(err instanceof Error ? err.message : 'Order failed')
  } finally {
    setSubmitting(false)
  }
}, [ticker, optionType, expiration, strike, orderType, quantity, limitPrice, stopPrice, selectedAccountId, onClose])
```

Update buttons to use `handleSubmitOrder`:
```tsx
<button
  className="order-panel__action-btn order-panel__action-btn--buy"
  onClick={() => handleSubmitOrder('BUY')}
  disabled={submitting}
>
  {submitting ? 'Submitting...' : 'Buy'}
</button>
<button
  className="order-panel__action-btn order-panel__action-btn--sell"
  onClick={() => handleSubmitOrder('SELL')}
  disabled={submitting}
>
  {submitting ? 'Submitting...' : 'Sell'}
</button>
```

- [ ] **Step 5: Add error display**

Add below the action buttons:
```tsx
{submitError && (
  <div className="order-panel__error">{submitError}</div>
)}
```

- [ ] **Step 6: Update Order Type dropdown to use supportedOrderTypes**

Replace the hardcoded `<option value="Limit">Limit</option><option value="Market">Market</option>` with:

```tsx
{supportedOrderTypes.map(ot => (
  <option key={ot} value={ot}>{ot}</option>
))}
```

Update the `orderType` state type to `string` instead of `'Market' | 'Limit'`.

- [ ] **Step 7: Add CSS for error state**

Add to `OrderPanel.css`:
```css
.order-panel__error {
  font-size: 11px;
  color: var(--danger-text);
  text-align: center;
  padding: 6px 0;
}
```

- [ ] **Step 8: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/wheel/OrderPanel.tsx frontend/src/components/wheel/OrderPanel.css
git commit -m "feat(wheel): wire OrderPanel to trading API, add stop price and broker order types"
```

---

### Task 9: Deploy, UAT test, and update documentation

**Files:**
- Modify: `docs/reference/frontend-map.md`
- Modify: `docs/reference/api-endpoints.md`

- [ ] **Step 1: Rebuild all services**

```bash
docker compose up --build -d
```

Wait for all services healthy.

- [ ] **Step 2: UAT test with Playwright**

Login → /wheel → TFSA account:
- Click a position card → OrderPanel opens
- Verify Order Type dropdown shows: Market, Limit, Stop, Stop Limit (Questrade)
- Select "Stop Limit" → verify both Limit Price and Stop Price fields appear
- Click Buy → verify API call is made (check console for network request or submission feedback)
- Verify error handling if order fails

- [ ] **Step 3: Update documentation**

Update `docs/reference/frontend-map.md` OrderPanel entry.
Update `docs/reference/api-endpoints.md` to note option fields in trading endpoints.
Update `docs/reference/database-schema.md` for V74 migration.

- [ ] **Step 4: Commit**

```bash
git add docs/reference/
git commit -m "docs: update references for Screen 3 order submission changes"
```
