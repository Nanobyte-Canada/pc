# Portfolio Rebalancing Engine — Design Spec

## Problem

The rebalancing engine (`RebalanceService.calculateTradesForAccount()`) has multiple bugs:
1. Only generates BUY orders — never sells positions not in the model
2. Uses $1.00 placeholder price for all orders
3. Doesn't account for FX conversion (USD/CAD)
4. Doesn't detect existing positions that are already in the model (sells and re-buys instead of adjusting)
5. Rebalancing widgets are placed above the main dashboard widgets (should be below risk/sector/geography row)

## Expected Behavior

Given an account with a model portfolio applied:
- **SELL** all positions NOT in the model portfolio (full liquidation)
- **ADJUST** positions that ARE in the model (sell excess or buy remaining to match target %)
- **BUY** new positions required by the model that aren't currently held
- Orders use actual market prices from `broker_positions.currentPrice`
- All values computed in CAD using `ExchangeRateService` for FX conversion
- No fractional shares — whole units only (residual cash is acceptable)
- SELL orders listed before BUY orders
- If insufficient cash for all BUY orders after sells, flag a warning

## Algorithm

### Step 1: Gather Data
```
positions = broker_positions WHERE connection_id AND is_current
allocations = model_portfolio_allocations WHERE model_portfolio_id
cashBalances = broker_balance_snapshots (parsed JSONB)
fxRate = ExchangeRateService.getRate("USD", today)  → CAD per 1 USD
```

### Step 2: Calculate Total Portfolio Value (in CAD)
```
positionValueCAD = sum(
  for each position:
    if currency == "CAD" → currentValue
    else → currentValue × fxRate
)
cashValueCAD = sum(
  for each cash entry:
    if currency == "CAD" → amount
    else → amount × fxRate
)
totalPortfolioValueCAD = positionValueCAD + cashValueCAD
```

### Step 3: Classify Positions
```
modelSymbols = set of alloc.symbol for each allocation
for each position:
  if position.symbol IN modelSymbols → KEEP (needs adjustment)
  else → LIQUIDATE (sell entirely)
```

### Step 4: Generate SELL Orders (Liquidation)
```
for each LIQUIDATE position:
  create SELL order:
    units = position.quantity (whole units)
    price = position.currentPrice
    amount = units × price
    currency = position.currency
  add proceeds to availableCashCAD (FX-converted)
```

### Step 5: Calculate Target Values
```
for each allocation:
  targetValueCAD = totalPortfolioValueCAD × targetPercent / 100
```

### Step 6: Generate Adjustment + Buy Orders
```
for each allocation:
  existingPosition = positions.find(symbol == alloc.symbol)
  
  if existingPosition:
    currentValueCAD = existingPosition.currentValue (FX-converted)
    diffCAD = targetValueCAD - currentValueCAD
    
    if diffCAD > MIN_TRADE_AMOUNT:
      // Need to buy more
      units = floor(diffCAD / priceFxAdjusted)
      generate BUY order
      deduct from availableCashCAD
    
    elif diffCAD < -MIN_TRADE_AMOUNT:
      // Need to sell excess
      units = floor(abs(diffCAD) / price)
      generate SELL order
      add proceeds to availableCashCAD
  
  else (new position):
    // Need to buy entirely
    price = lookupPrice(alloc.symbol)  // from other user positions
    if price unavailable → skip with warning
    units = floor(targetValueCAD / priceFxAdjusted)
    generate BUY order
    deduct from availableCashCAD
```

### Step 7: Cash Sufficiency Check
```
if any BUY order would make availableCashCAD < 0:
  add cashWarning: "Execute SELL orders first to generate funds for BUY orders"
  mark affected BUY orders as cash-insufficient
```

### Step 8: Return Response
```
orders = [SELL orders sorted by amount DESC] + [BUY orders sorted by amount DESC]
return PendingOrdersResponse(orders, totalAmount, cashRemaining, cashWarning)
```

## Constants
- `MIN_TRADE_AMOUNT = BigDecimal(10)` — skip trades smaller than $10 CAD (existing constant, unchanged)

## Price Lookup Strategy

For securities the account doesn't currently hold:
1. Search `broker_positions` across the user's other active connections for the same symbol
2. Use the most recent `currentPrice` found
3. If not found in any user account → flag as "Price unavailable", skip the order, add warning

## FX Conversion

- All target calculations done in CAD
- USD positions: `valueCAD = valueUSD × fxRate` where `fxRate = ExchangeRateService.getRate("USD", today)`
- Buy order for USD security: `units = floor(targetValueCAD / (priceUSD × fxRate))`
- Assume broker handles auto-conversion at execution time

## DTO Changes

### PendingOrderDto — add fields:
```kotlin
val currentValue: BigDecimal?      // current position value (for context)
val targetPercent: BigDecimal?     // target allocation % (for context)
val targetValue: BigDecimal?       // target value in CAD
val cashInsufficient: Boolean      // true if not enough cash for this BUY
```

### PendingOrdersResponse — add fields:
```kotlin
val cashRemaining: BigDecimal      // residual cash after all orders
val cashWarning: String?           // warning message if insufficient cash
val totalSellAmount: BigDecimal    // total SELL proceeds
val totalBuyAmount: BigDecimal     // total BUY cost
```

## DriftCalculationService.getRebalanceProgress() Fix

Current bug: only shows model allocation symbols, all at 0% actual. Must also:
- Show non-model positions (to indicate they'll be sold)
- Correctly match position symbols to allocation symbols
- FX-convert position values to CAD for accurate percentage calculation

### Updated entries:
```
For each model allocation:
  actualPercent = position.currentValueCAD / totalValueCAD × 100
  
For each non-model position:
  add entry with targetPercent=0%, actualPercent=X% (marked as "will be sold")
```

## Frontend Changes

### AccountDetailPage.tsx
Move rebalancing widgets below the risk/sector/geography row:
```
[Account Header]
[Portfolio Value | Available Cash | Buying Power | Open Orders]
[Risk Profile | Sector Exposure | Geographic Exposure]
[Rebalancing Progress | Pending Orders]          ← MOVED HERE
[Fees & Commission | Dividend Calendar]
```

### PendingOrdersWidget.tsx
- Already supports SELL and BUY order display (grid with sections)
- Show SELL section first, then BUY section
- Add cash warning banner if `cashWarning` is set
- Show cash remaining after all orders

### RebalancingProgressWidget.tsx
- Show non-model positions with target=0% and "SELL" indicator
- Show model positions with actual vs target bars

## Files Changed

| File | Change |
|------|--------|
| `RebalanceService.kt` | Rewrite `calculateTradesForAccount()` with full sell/adjust/buy logic |
| `DriftCalculationService.kt` | Fix `getRebalanceProgress()` to show non-model positions and correct actual % |
| `ModelPortfolioDtos.kt` | Add fields to `PendingOrderDto`, `PendingOrdersResponse`, `RebalanceProgressEntry` |
| `AccountDetailPage.tsx` | Move rebalancing widget row below risk/sector/geography |
| `PendingOrdersWidget.tsx` | Add cash warning banner, show SELL first |
| `RebalancingProgressWidget.tsx` | Show non-model positions with sell indicator |
| `frontend/src/types/modelPortfolio.ts` | Update TypeScript types to match new DTO fields |

## Verification

1. Rebuild backend: `docker compose up --build backend`
2. Navigate to LIRA account (connection 25):
   - Rebalancing Progress should show UPRO at actual %, TQQQ/SPXU/SOXL/TECL at 0% actual
   - Pending Orders should show: SELL UPRO, then BUY TQQQ/SPXU/SOXL/TECL
   - Prices should be actual market prices (not $1.00)
   - Total should approximate portfolio value
3. Navigate to Locked-In RRSP (connection 26):
   - Should show SELL QQU.TO, SELL SPXU.TO, then BUY VFV.TO/XIU.TO/QQC.TO/XEF.TO/XEC.TO
4. Widgets should appear below risk/sector/geography row
5. `npm run build` succeeds
6. `docker compose exec backend ./gradlew test` passes
