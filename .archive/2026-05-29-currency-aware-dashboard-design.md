# Currency-Aware Dashboard & Account Page

## Summary

Update the dashboard and account page to:
1. Display total portfolio value in C$ only (remove the misleading US$ line)
2. Break down investment and cash by their native currency (C$, US$, etc.)
3. Display buying power in both C$ and US$ (add US$ to the API)

## Current State

### Total Value (Combined KPI Card)
- Top: C$ total + a US$ line that actually shows USD cash (not total value in USD)
- Bottom: "Investment" (C$ only) | "Cash" (C$ and US$)
- Source: `/api/v1/dashboard/summary` → `PortfolioValueDto` (CAD only) + `/api/v1/dashboard/cash` → per-currency cash

### Cash
- `/api/v1/dashboard/cash` returns `availableCash: CurrencyAmountDto[]` with per-currency amounts
- Frontend already extracts CAD/USD and displays separately

### Investment
- Computed as `portfolioValue - cashValue` (single CAD number)
- No per-currency breakdown exists
- Each `BrokerPosition` entity has a `currency` field (e.g., "CAD", "USD")

### Buying Power
- `/api/v1/dashboard/cash` returns `buyingPower: CurrencyAmountDto[]` + `totalBuyingPowerCAD`
- No `totalBuyingPowerUSD` field exists
- Frontend shows `totalBuyingPowerCAD` as main value with per-currency breakdown rows

## Design

### 1. Total Value — C$ Only

**Frontend only.** Remove the US$ line from the Total Value combined card.

**DashboardPage.tsx** — change `combinedCardData.totalValue`:
```typescript
// BEFORE
totalValue: {
  cad: fmtCad(totalValue),
  usd: cashUSD || bpUSD ? `US$ ${fmtBreakdownAmount(...)}` : undefined,
}

// AFTER
totalValue: {
  cad: fmtCad(totalValue),
  // usd removed entirely
}
```

No backend changes — `PortfolioValueDto` already returns CAD only.

### 2. Investment Breakdown by Currency

**Backend — compute investment per currency from positions.**

`DashboardDataService.getSummary()`:
- After fetching positions, group by `position.currency` and sum `currentValue`:
  ```kotlin
  val investmentByCurrency = positions
      .filter { it.instrumentType != InstrumentType.CASH }
      .groupBy { it.currency }
      .map { (currency, posns) ->
          CurrencyAmountDto(currency, posns.sumOf { it.currentValue ?: BigDecimal.ZERO }
              .setScale(2, RoundingMode.HALF_UP))
      }
      .sortedByDescending { it.amount }
  ```

`PortfolioValueDto` — add new field:
```kotlin
data class PortfolioValueDto(
    val totalValue: BigDecimal,
    val investmentValue: BigDecimal,
    val investmentByCurrency: List<CurrencyAmountDto> = emptyList(),  // NEW
    val cashValue: BigDecimal,
    val totalChange: BigDecimal?,
    val totalChangePercent: BigDecimal?,
    val currency: String = "CAD"
)
```

**Frontend — display each currency in the Investment column:**

`types/dashboard.ts`:
```typescript
interface PortfolioValueData {
  // ... existing fields ...
  investmentByCurrency?: CurrencyAmount[]  // NEW
}
```

`DashboardPage.tsx` — the combined card's Investment column:
```typescript
investment: {
  cad: /* from investmentByCurrency CAD entry */,
  usd: /* from investmentByCurrency USD entry, if present */,
}
```

Display null/0/negative values as-is (e.g., "C$ 0.00", "US$ -150.00").

### 3. Cash Display — Minor Cleanup

Cash is already per-currency. Ensure:
- If a currency has 0 or negative, show it (don't hide it)
- Currently `cashUSD` defaults to `?? 0` — show the actual value

### 4. Buying Power — Add US$ to API and Display

**Backend — add `totalBuyingPowerUSD` from raw snapshot data.**

`DashboardCashResponse` — add new field:
```kotlin
data class DashboardCashResponse(
    val availableCash: List<CurrencyAmountDto>,
    val buyingPower: List<CurrencyAmountDto>,
    val totalCashCAD: BigDecimal,
    val totalBuyingPowerCAD: BigDecimal = BigDecimal.ZERO,
    val totalBuyingPowerUSD: BigDecimal = BigDecimal.ZERO  // NEW
)
```

`DashboardCashService.getCash()` — extract raw USD buying power:
```kotlin
val totalBuyingPowerUSD = buyingPowerByCurrency["USD"] ?: BigDecimal.ZERO
```
This is the raw broker-reported USD amount, not an FX conversion.

**Frontend — show both C$ and US$ for buying power.**

`types/dashboard.ts`:
```typescript
interface DashboardCashResponse {
  // ... existing fields ...
  totalBuyingPowerUSD?: number  // NEW
}
```

`DashboardPage.tsx` — Buying Power KPI card shows C$ main value, US$ secondary:
```typescript
<KpiCard
  label="Buying Power"
  value={fmtCad(cashData?.totalBuyingPowerCAD)}
  // Show US$ as part of breakdown or as secondary value
/>
```

### 5. Account Detail Page — Same Pattern

Apply identical changes to `AccountDetailPage.tsx`:
- Total Value: C$ only (remove US$ if shown)
- Investment KPI: show per-currency from `investmentByCurrency`
- Cash: already per-currency
- Buying Power: show C$ and US$

For individual accounts, the summary endpoint is called with `connectionId`, so investment by currency will reflect that specific account's positions only.

## Files Affected

| Layer | File | Change |
|-------|------|--------|
| Backend DTO | `DashboardDataDtos.kt` | Add `investmentByCurrency` to `PortfolioValueDto`, add `totalBuyingPowerUSD` to `DashboardCashResponse` |
| Backend Service | `DashboardDataService.kt` | Compute `investmentByCurrency` from positions grouped by currency |
| Backend Service | `DashboardCashService.kt` | Extract `totalBuyingPowerUSD` from raw snapshot USD values |
| Frontend Type | `types/dashboard.ts` | Add `investmentByCurrency` and `totalBuyingPowerUSD` to interfaces |
| Frontend Page | `DashboardPage.tsx` | Remove US$ from total value, show investment by currency, update buying power |
| Frontend Page | `AccountDetailPage.tsx` | Same display changes as DashboardPage |
| Frontend Component | `KpiCard.tsx` | Adjust combined variant: investment/cash columns use `CurrencyValue { cad, usd? }` which already supports optional USD — feed from `investmentByCurrency` array |

## Edge Cases

- **No USD positions/cash/buying power:** USD lines simply don't render (or show 0 per user preference)
- **Negative cash:** Display as-is (e.g., "C$ -500.00" for margin debit)
- **Zero values:** Display "C$ 0.00" — do not hide
- **New currencies (EUR, etc.):** The design handles any currency — `investmentByCurrency` and `availableCash` are lists, not hardcoded to CAD/USD
- **Single-account view:** Same logic applies — `getSummary(connectionId)` filters positions to that account

## Out of Scope

- Changing how `connection.totalValue` is computed (stays as FX-converted CAD total)
- Adding FX conversion display for total portfolio value (user explicitly wants C$ only)
- Changing the `/accounts` endpoint DTO (it already has all needed fields; display changes are frontend-only)
