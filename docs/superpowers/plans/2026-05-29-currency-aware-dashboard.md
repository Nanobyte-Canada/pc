# Currency-Aware Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update dashboard and account pages so total value is C$-only, investment/cash show per-currency breakdown, and buying power shows both C$ and US$.

**Architecture:** Backend adds two new fields (`investmentByCurrency` on PortfolioValueDto, `totalBuyingPowerUSD` on DashboardCashResponse). Frontend consumes them in DashboardPage and AccountDetailPage KPI cards. No schema changes, no new endpoints.

**Tech Stack:** Kotlin/Spring Boot (backend DTOs + services), React/TypeScript (frontend types + pages + components)

---

### Task 1: Backend — Add `investmentByCurrency` to DTO and compute in service

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/DashboardDataDtos.kt:26-33`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt:247-313`

- [ ] **Step 1: Add `investmentByCurrency` field to `PortfolioValueDto`**

In `DashboardDataDtos.kt`, change the `PortfolioValueDto` data class:

```kotlin
data class PortfolioValueDto(
    val totalValue: BigDecimal,
    val investmentValue: BigDecimal,
    val investmentByCurrency: List<CurrencyAmountDto> = emptyList(),
    val cashValue: BigDecimal,
    val totalChange: BigDecimal?,
    val totalChangePercent: BigDecimal?,
    val currency: String = "CAD"
)
```

- [ ] **Step 2: Compute `investmentByCurrency` in `DashboardDataService.getSummary()`**

In `DashboardDataService.kt`, inside `getSummary()`, add the investment-by-currency computation after the line `val investmentValue = portfolioValue - cashValue` (line 267) and before the Day P&L section (line 269):

```kotlin
        // Investment breakdown by currency (from position data)
        val investmentByCurrency = positions
            .filter { it.instrumentType != InstrumentType.CASH }
            .groupBy { it.currency }
            .map { (currency, posns) ->
                CurrencyAmountDto(currency, posns.sumOf { it.currentValue ?: BigDecimal.ZERO }
                    .setScale(2, RoundingMode.HALF_UP))
            }
            .sortedByDescending { it.amount }
```

Then update the `PortfolioValueDto` construction in the return statement (around line 301-308) to include the new field:

```kotlin
            portfolioValue = PortfolioValueDto(
                totalValue = portfolioValue.setScale(2, RoundingMode.HALF_UP),
                investmentValue = investmentValue.setScale(2, RoundingMode.HALF_UP),
                investmentByCurrency = investmentByCurrency,
                cashValue = cashValue.setScale(2, RoundingMode.HALF_UP),
                totalChange = totalDayPnl?.setScale(2, RoundingMode.HALF_UP),
                totalChangePercent = totalDayPnlPercent,
                currency = "CAD"
            ),
```

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/DashboardDataDtos.kt backend/portfolio/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt
git commit -m "feat(api): add investmentByCurrency to dashboard summary response"
```

---

### Task 2: Backend — Add `totalBuyingPowerUSD` to cash response

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/DashboardDataDtos.kt:69-74`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/DashboardCashService.kt:26-77`

- [ ] **Step 1: Add `totalBuyingPowerUSD` field to `DashboardCashResponse`**

In `DashboardDataDtos.kt`, change the `DashboardCashResponse` data class:

```kotlin
data class DashboardCashResponse(
    val availableCash: List<CurrencyAmountDto>,
    val buyingPower: List<CurrencyAmountDto>,
    val totalCashCAD: BigDecimal,
    val totalBuyingPowerCAD: BigDecimal = BigDecimal.ZERO,
    val totalBuyingPowerUSD: BigDecimal = BigDecimal.ZERO
)
```

- [ ] **Step 2: Populate `totalBuyingPowerUSD` in `DashboardCashService.getCash()`**

In `DashboardCashService.kt`, inside the `getCash()` method, after the `totalBuyingPowerCAD` computation (line 64-66) and before the return statement (line 69), add:

```kotlin
        val totalBuyingPowerUSD = (buyingPowerByCurrency["USD"] ?: BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
```

Then update the return statement to include it:

```kotlin
        return DashboardCashResponse(
            availableCash = cashByCurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            buyingPower = buyingPowerByCurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            totalCashCAD = totalCashCAD.setScale(2, RoundingMode.HALF_UP),
            totalBuyingPowerCAD = totalBuyingPowerCAD.setScale(2, RoundingMode.HALF_UP),
            totalBuyingPowerUSD = totalBuyingPowerUSD
        )
```

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/DashboardDataDtos.kt backend/portfolio/src/main/kotlin/com/portfolio/broker/service/DashboardCashService.kt
git commit -m "feat(api): add totalBuyingPowerUSD to dashboard cash response"
```

---

### Task 3: Frontend — Update TypeScript types

**Files:**
- Modify: `frontend/src/types/dashboard.ts:3-10` and `frontend/src/types/dashboard.ts:45-50`

- [ ] **Step 1: Add `investmentByCurrency` to `PortfolioValueData`**

In `frontend/src/types/dashboard.ts`, update `PortfolioValueData`:

```typescript
export interface PortfolioValueData {
  totalValue: number
  investmentValue: number
  investmentByCurrency?: CurrencyAmount[]
  cashValue: number
  totalChange: number | null
  totalChangePercent: number | null
  currency: string
}
```

- [ ] **Step 2: Add `totalBuyingPowerUSD` to `DashboardCashResponse`**

In the same file, update `DashboardCashResponse`:

```typescript
export interface DashboardCashResponse {
  availableCash: CurrencyAmount[]
  buyingPower: CurrencyAmount[]
  totalCashCAD: number
  totalBuyingPowerCAD?: number
  totalBuyingPowerUSD?: number
}
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/types/dashboard.ts && git commit -m "feat(types): add investmentByCurrency and totalBuyingPowerUSD to dashboard types"
```

---

### Task 4: Frontend — Update DashboardPage currency display

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx:92-128` and `frontend/src/pages/DashboardPage.tsx:200-229`

- [ ] **Step 1: Update data extraction section (lines 92-128)**

Replace the entire data extraction block (from `const pv = summaryData?.portfolioValue` through `const combinedCardData = { ... }`) with:

```typescript
  const pv = summaryData?.portfolioValue
  const totalValue = pv?.totalValue ?? 0
  const investmentValue = pv?.investmentValue ?? 0
  const totalGain = pv?.totalChange ?? (totalValue - investmentValue)
  const totalGainPct = pv?.totalChangePercent ?? (investmentValue > 0 ? (totalGain / investmentValue) * 100 : 0)
  const isPositive = totalGain >= 0

  /* Investment amounts by currency */
  const investmentEntries = pv?.investmentByCurrency ?? []
  const investCAD = investmentEntries.find(c => c.currency === 'CAD')?.amount
  const investUSD = investmentEntries.find(c => c.currency === 'USD')?.amount

  /* Cash amounts by currency */
  const cashEntries = cashData?.availableCash ?? []
  const cashCAD = cashEntries.find(c => c.currency === 'CAD')?.amount
  const cashUSD = cashEntries.find(c => c.currency === 'USD')?.amount

  /* Buying power */
  const bpCAD = cashData?.totalBuyingPowerCAD
  const bpUSD = cashData?.totalBuyingPowerUSD

  /* Combined card data: Total Value / Investment / Cash */
  const combinedCardData = {
    totalValue: {
      cad: fmtCad(totalValue),
    },
    investment: {
      cad: `C$ ${fmtBreakdownAmount(investCAD)}`,
      usd: investUSD != null ? `US$ ${fmtBreakdownAmount(investUSD)}` : undefined,
    },
    cash: {
      cad: `C$ ${fmtBreakdownAmount(cashCAD)}`,
      usd: cashUSD != null ? `US$ ${fmtBreakdownAmount(cashUSD)}` : undefined,
    },
  }
```

- [ ] **Step 2: Update the Buying Power KPI card (lines 210-215)**

Replace the Buying Power `KpiCard` with:

```tsx
          <KpiCard
            label="Buying Power"
            icon={<ShoppingCart size={14} />}
            value={fmtCad(bpCAD)}
            breakdown={[
              { label: 'C$', value: fmtBreakdownAmount(bpCAD) },
              ...(bpUSD != null ? [{ label: 'US$', value: fmtBreakdownAmount(bpUSD) }] : []),
            ]}
          />
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/pages/DashboardPage.tsx && git commit -m "feat(ui): currency-aware KPI cards on dashboard page"
```

---

### Task 5: Frontend — Update AccountDetailPage currency display

**Files:**
- Modify: `frontend/src/pages/AccountDetailPage.tsx:107-131` and `frontend/src/pages/AccountDetailPage.tsx:165-192`

- [ ] **Step 1: Update data extraction section (lines 107-131)**

Replace the KPI data section (from `const pv = summaryData?.portfolioValue` through `const returnsBreakdown`) with:

```typescript
  /* KPI data */
  const pv = summaryData?.portfolioValue
  const totalValue = pv?.totalValue ?? 0
  const investmentValue = pv?.investmentValue ?? 0
  const totalGain = pv?.totalChange ?? (totalValue - investmentValue)

  /* Investment by currency */
  const investmentEntries = pv?.investmentByCurrency ?? []
  const investmentBreakdown = investmentEntries.map(c => ({
    label: c.currency === 'CAD' ? 'C$' : c.currency === 'USD' ? 'US$' : c.currency,
    value: fmtBreakdownAmount(c.amount),
  }))
  if (investmentBreakdown.length === 0) {
    investmentBreakdown.push({ label: 'C$', value: fmtBreakdownAmount(investmentValue) })
  }

  /* Cash breakdown by currency */
  const cashBreakdown = (cashData?.availableCash ?? []).map(c => ({
    label: c.currency === 'CAD' ? 'C$' : c.currency === 'USD' ? 'US$' : c.currency,
    value: fmtBreakdownAmount(c.amount),
  }))

  /* Buying power */
  const bpCAD = cashData?.totalBuyingPowerCAD
  const bpUSD = cashData?.totalBuyingPowerUSD

  /* Returns breakdown */
  const roi = irrData?.portfolioTotalReturnPct
  const irr = irrData?.portfolioIrr
  const divYield = irrData?.portfolioDividendYield
  const returnsBreakdown: Array<{ label: string; value: string; variant: 'positive' | 'negative' | 'neutral' }> = [
    { label: 'ROI', value: fmtPct(roi), variant: (roi ?? 0) >= 0 ? 'positive' : 'negative' },
    { label: 'IRR', value: fmtPct(irr), variant: (irr ?? 0) >= 0 ? 'positive' : 'negative' },
    { label: 'Div Yield', value: fmtPct(divYield), variant: (divYield ?? 0) >= 0 ? 'positive' : 'negative' },
  ]
```

- [ ] **Step 2: Update the 4-column KPI row (lines 165-192)**

Replace the entire KPI row with:

```tsx
        <div className="account-detail-kpi-row">
          <KpiCard
            label="Total Value"
            icon={<TrendingUp size={14} />}
            value={fmtCad(totalValue)}
            variant="emerald"
            breakdown={[{ label: 'Gain', value: fmtCadSigned(totalGain) }]}
          />
          <KpiCard
            label="Investment"
            icon={<TrendingUp size={14} />}
            value={fmtCad(investmentValue)}
            breakdown={investmentBreakdown}
          />
          <KpiCard
            label="Cash"
            icon={<Wallet size={14} />}
            value={fmtCad(cashData?.totalCashCAD)}
            breakdown={cashBreakdown}
          />
          <KpiCard
            label="Buying Power"
            icon={<DollarSign size={14} />}
            value={fmtCad(bpCAD)}
            breakdown={[
              { label: 'C$', value: fmtBreakdownAmount(bpCAD) },
              ...(bpUSD != null ? [{ label: 'US$', value: fmtBreakdownAmount(bpUSD) }] : []),
            ]}
          />
        </div>
```

Note: The 4th card changes from "Returns" to "Buying Power" (with C$ + US$). If the user wants to keep Returns as a 4th card instead, a 5th card would be needed — but the spec says to add buying power display on this page.

**Important:** This replaces the Returns card with Buying Power. If both are needed, add a 5th KPI card. The existing `returnsBreakdown` data is computed but would need another card to display it. Add an import for `ShoppingCart` from lucide-react if using that icon instead:

```typescript
import { ChevronRight, TrendingUp, Wallet, DollarSign, ShoppingCart } from 'lucide-react'
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/pages/AccountDetailPage.tsx && git commit -m "feat(ui): currency-aware KPI cards on account detail page"
```

---

### Task 6: Validate

- [ ] **Step 1: Build backend in Docker**

```bash
docker compose exec backend ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Build frontend**

```bash
cd frontend && npm run build
```

Expected: No TypeScript errors, build succeeds.

- [ ] **Step 3: Run frontend lint**

```bash
cd frontend && npm run lint
```

Expected: No lint errors.

- [ ] **Step 4: Run frontend tests**

```bash
cd frontend && npm run test:run
```

Expected: All tests pass.

- [ ] **Step 5: Visual verification**

Start the dev server (`npm run dev` from `frontend/`) and verify in browser:
1. Dashboard page → Total Value card shows C$ only (no US$ line in header section)
2. Dashboard page → Investment column shows per-currency breakdown (C$, US$ if applicable)
3. Dashboard page → Cash column shows per-currency breakdown
4. Dashboard page → Buying Power card shows C$ main value with C$/US$ breakdown
5. Account detail page → Same currency display pattern

---

### Task 7: Update documentation

**Files:**
- Modify: `docs/reference/api-endpoints.md`
- Modify: `docs/reference/frontend-map.md`
- Modify: `docs/reference/entity-relationships.md`

- [ ] **Step 1: Update api-endpoints.md**

Add a note under the `GET /api/v1/dashboard/summary` section documenting that `portfolioValue` now includes `investmentByCurrency: List<CurrencyAmountDto>`.

Add a note under the `GET /api/v1/dashboard/cash` section documenting the new `totalBuyingPowerUSD` field.

- [ ] **Step 2: Update entity-relationships.md**

Update the `PortfolioValueDto` and `DashboardCashResponse` DTO descriptions to include the new fields.

- [ ] **Step 3: Update frontend-map.md**

Update the `types/dashboard.ts` section to reflect the new fields in `PortfolioValueData` and `DashboardCashResponse`.

- [ ] **Step 4: Move spec to archive**

```bash
git mv docs/superpowers/specs/2026-05-29-currency-aware-dashboard-design.md .archive/
```

- [ ] **Step 5: Commit**

```bash
git add docs/reference/ .archive/ && git commit -m "docs: update references for currency-aware dashboard changes"
```
