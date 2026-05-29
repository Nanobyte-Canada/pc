# Wheel Screen 1: Positions & Financials — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Wheel Strategy landing page with a ticker-rows × weekly-expiry-columns calendar grid, dynamic ticker discovery, covered call detection, and 5 KPI cards — matching the existing dashboard design language.

**Architecture:** Replace the current expiry×ticker grid (WheelGrid) with a transposed calendar grid where tickers are rows and weekly Friday expiry dates are columns. Remove hardcoded `WHEEL_TICKERS` — discover tickers dynamically from positions API. Detect CC-eligible tickers (100+ shares owned). Desktop shows 4 weekly columns, mobile shows 2. Navigation arrows shift the calendar window. All data from existing APIs — no backend changes.

**Tech Stack:** React 18, TypeScript, CSS custom properties (no Tailwind), existing KpiCard component pattern, existing hooks (useWheelPositions, useDashboardCash, useExchangeRate, useWheelActivities)

**Design spec:** `docs/superpowers/specs/2026-05-29-wheel-screen1-positions-design.md`
**Mockup:** `.superpowers/brainstorm/51960-1780073119/content/option-a-final.html`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/src/types/wheel.ts` | Modify | Add `CalendarWindow`, `TickerRowData`, `CCInfo` types; update `WheelGridData` for transposed grid |
| `frontend/src/hooks/useWheelPositions.ts` | Modify | Remove ticker filter param, add CC detection, add stock position fetching, restructure grid builder for ticker-rows |
| `frontend/src/hooks/__tests__/useWheelPositions.test.ts` | Modify | Update tests for dynamic tickers, CC detection, transposed grid |
| `frontend/src/components/wheel/WheelCalendarGrid.tsx` | Create | New transposed grid: ticker rows × expiry columns, timeline nav, CC slots |
| `frontend/src/components/wheel/WheelCalendarGrid.css` | Create | Grid styling: sticky ticker column, expiry headers, DTE badges, responsive 4→2 columns |
| `frontend/src/components/wheel/PositionCard.tsx` | Modify | Simplify: remove premium row, show strike+type and P&L only |
| `frontend/src/components/wheel/PositionCard.css` | Modify | Streamlined compact card |
| `frontend/src/components/wheel/WheelKpiCards.tsx` | Create | 5 KPI cards using existing KpiCard pattern |
| `frontend/src/components/wheel/WheelKpiCards.css` | Create | KPI row layout: 5-col desktop, 2×2+1 mobile |
| `frontend/src/pages/WheelPage.tsx` | Rewrite | New layout: AccountNavBar, KPIs, timeline nav, calendar grid, dynamic tickers |
| `frontend/src/pages/WheelPage.css` | Rewrite | Page layout, responsive breakpoints |
| `frontend/src/components/wheel/WheelGrid.tsx` | Delete | Replaced by WheelCalendarGrid |
| `frontend/src/components/wheel/WheelGrid.css` | Delete | Replaced by WheelCalendarGrid.css |
| `frontend/src/components/wheel/CapitalSummary.tsx` | Delete | Replaced by WheelKpiCards |
| `frontend/src/components/wheel/CapitalSummary.css` | Delete | Replaced by WheelKpiCards.css |

---

### Task 1: Update types for transposed calendar grid

**Files:**
- Modify: `frontend/src/types/wheel.ts`

- [ ] **Step 1: Add new types for calendar grid and CC detection**

Add the following types after the existing `WheelChainStrike` interface at the end of the file:

```typescript
export interface CCInfo {
  sharesOwned: number
  contractsAvailable: number
}

export interface TickerRowData {
  symbol: string
  currentPrice: number | null
  currency: string
  totalExposure: number
  ccInfo: CCInfo | null
  cells: Record<string, WheelCell>
}

export interface CalendarWindow {
  startDate: string
  endDate: string
  expiries: Array<{
    date: string
    dte: number
    dayOfWeek: string
    isMonthly: boolean
  }>
}

export interface CalendarGridData {
  tickerRows: TickerRowData[]
  calendarWindow: CalendarWindow
  manualTickers: string[]
}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/wheel.ts
git commit -m "feat(wheel): add calendar grid and CC detection types"
```

---

### Task 2: Refactor useWheelPositions for dynamic tickers and CC detection

**Files:**
- Modify: `frontend/src/hooks/useWheelPositions.ts`
- Modify: `frontend/src/hooks/__tests__/useWheelPositions.test.ts`

- [ ] **Step 1: Write tests for dynamic ticker discovery and CC detection**

Add new tests to `frontend/src/hooks/__tests__/useWheelPositions.test.ts`:

```typescript
import { discoverTickers, detectCCEligible } from '../useWheelPositions'
import type { BrokerPosition } from '@/types/broker'
import type { AggregatedPosition } from '@/types/broker'

describe('discoverTickers', () => {
  it('returns unique underlying symbols from option positions', () => {
    const positions: BrokerPosition[] = [
      makePosition({ underlyingSymbol: 'SOXL', optionType: 'PUT' }),
      makePosition({ id: 2, underlyingSymbol: 'SOXL', optionType: 'PUT' }),
      makePosition({ id: 3, underlyingSymbol: 'TQQQ', optionType: 'CALL' }),
    ]
    const tickers = discoverTickers(positions)
    expect(tickers).toEqual(['SOXL', 'TQQQ'])
  })

  it('ignores positions without optionType', () => {
    const positions: BrokerPosition[] = [
      makePosition({ underlyingSymbol: null, optionType: null, symbol: 'AAPL', instrumentType: 'STOCK' }),
    ]
    const tickers = discoverTickers(positions)
    expect(tickers).toEqual([])
  })
})

describe('detectCCEligible', () => {
  it('detects tickers with 100+ shares', () => {
    const positions: BrokerPosition[] = [
      { id: 10, symbol: 'TQQQ', instrumentType: 'STOCK', quantity: 100, currency: 'USD',
        securityName: null, averageCost: 40, currentPrice: 827, currentValue: 82700,
        totalPnl: 42700, totalPnlPercent: 107, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
      { id: 11, symbol: 'QQU.TO', instrumentType: 'STOCK', quantity: 300, currency: 'CAD',
        securityName: null, averageCost: 26, currentPrice: 41.38, currentValue: 12414,
        totalPnl: 4614, totalPnlPercent: 59, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
      { id: 12, symbol: 'TECL', instrumentType: 'STOCK', quantity: 38, currency: 'USD',
        securityName: null, averageCost: 86, currentPrice: 256, currentValue: 9728,
        totalPnl: 6460, totalPnlPercent: 197, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
    ]
    const ccMap = detectCCEligible(positions)
    expect(ccMap.get('TQQQ')).toEqual({ sharesOwned: 100, contractsAvailable: 1 })
    expect(ccMap.get('QQU.TO')).toEqual({ sharesOwned: 300, contractsAvailable: 3 })
    expect(ccMap.has('TECL')).toBe(false) // 38 < 100
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm run test:run -- --grep "discoverTickers|detectCCEligible"`
Expected: FAIL — functions not exported yet.

- [ ] **Step 3: Add `discoverTickers` and `detectCCEligible` functions**

Add these exported functions to `frontend/src/hooks/useWheelPositions.ts` after the existing `computeTickerTotals` function:

```typescript
export function discoverTickers(positions: BrokerPosition[]): string[] {
  const tickerSet = new Set<string>()
  for (const p of positions) {
    const fields = resolveOptionFields(p)
    if (fields) tickerSet.add(fields.underlyingSymbol)
  }
  return Array.from(tickerSet).sort()
}

export function detectCCEligible(positions: BrokerPosition[]): Map<string, { sharesOwned: number; contractsAvailable: number }> {
  const ccMap = new Map<string, { sharesOwned: number; contractsAvailable: number }>()
  for (const p of positions) {
    if (p.optionType != null || p.strikePrice != null) continue
    const qty = Math.abs(p.quantity ?? 0)
    if (qty >= 100) {
      ccMap.set(p.symbol, {
        sharesOwned: qty,
        contractsAvailable: Math.floor(qty / 100),
      })
    }
  }
  return ccMap
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npm run test:run -- --grep "discoverTickers|detectCCEligible"`
Expected: PASS

- [ ] **Step 5: Add `generateWeeklyExpiries` function**

Add to `frontend/src/hooks/useWheelPositions.ts`:

```typescript
export function generateWeeklyExpiries(startDate: Date, count: number): Array<{ date: string; dte: number; dayOfWeek: string; isMonthly: boolean }> {
  const today = new Date()
  const expiries: Array<{ date: string; dte: number; dayOfWeek: string; isMonthly: boolean }> = []
  const d = new Date(startDate)
  const dayOfWeek = d.getDay()
  if (dayOfWeek !== 5) {
    d.setDate(d.getDate() + ((5 - dayOfWeek + 7) % 7))
  }
  for (let i = 0; i < count; i++) {
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    expiries.push({
      date: iso,
      dte: Math.max(0, diffDays(today, d)),
      dayOfWeek: DAY_NAMES[d.getDay()],
      isMonthly: isMonthlyExpiry(iso),
    })
    d.setDate(d.getDate() + 7)
  }
  return expiries
}
```

- [ ] **Step 6: Verify full test suite passes**

Run: `cd frontend && npm run test:run`
Expected: All existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/hooks/useWheelPositions.ts frontend/src/hooks/__tests__/useWheelPositions.test.ts
git commit -m "feat(wheel): add dynamic ticker discovery, CC detection, and weekly expiry generation"
```

---

### Task 3: Create WheelKpiCards component

**Files:**
- Create: `frontend/src/components/wheel/WheelKpiCards.tsx`
- Create: `frontend/src/components/wheel/WheelKpiCards.css`

- [ ] **Step 1: Create WheelKpiCards.tsx**

```typescript
import type { CapitalMetrics, CCInfo } from '@/types/wheel'
import type { CurrencyAmount } from '@/types/dashboard'
import { formatCurrency } from '@/services/brokerService'
import { TrendingUp, Shield, TriangleAlert, DollarSign, Target } from 'lucide-react'
import './WheelKpiCards.css'

interface WheelKpiCardsProps {
  metrics: CapitalMetrics | null
  buyingPower: CurrencyAmount[]
  ccEligible: Map<string, { sharesOwned: number; contractsAvailable: number }>
  positionCounts: { csp: number; cc: number; expiring: number; total: number }
}

export function WheelKpiCards({ metrics, buyingPower, ccEligible, positionCounts }: WheelKpiCardsProps) {
  const bpCad = buyingPower.find(bp => bp.currency === 'CAD')?.amount ?? 0
  const bpUsd = buyingPower.find(bp => bp.currency === 'USD')?.amount ?? 0
  const totalCC = Array.from(ccEligible.values()).reduce((sum, cc) => sum + cc.contractsAvailable, 0)
  const ccEntries = Array.from(ccEligible.entries()).slice(0, 3)

  return (
    <div className="wheel-kpi-row">
      {/* 1. Capital Available */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Available</span>
          <TrendingUp size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          C$ {bpCad.toLocaleString('en-US', { maximumFractionDigits: 0 })}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">C$</span>
          <span className="wheel-kpi__breakdown-value">{bpCad.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">US$</span>
          <span className="wheel-kpi__breakdown-value">{bpUsd.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
        </div>
      </div>

      {/* 2. Capital Deployed */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Deployed</span>
          <Shield size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          {formatCurrency(metrics?.deployedCsp.usd ?? 0, 'USD')}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CSP</span>
          <span className="wheel-kpi__breakdown-value">{formatCurrency(metrics?.deployedCsp.usd ?? 0, 'USD')}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CC</span>
          <span className="wheel-kpi__breakdown-value">{formatCurrency(metrics?.ccsWritten.usd ?? 0, 'USD')}</span>
        </div>
      </div>

      {/* 3. CC Available */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">CC Available</span>
          <TriangleAlert size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value wheel-kpi__value--cc">
          {totalCC} contract{totalCC !== 1 ? 's' : ''}
        </div>
        <div className="wheel-kpi__divider" />
        {ccEntries.map(([ticker, info]) => (
          <div key={ticker} className="wheel-kpi__breakdown-row">
            <span className="wheel-kpi__breakdown-label">{ticker}</span>
            <span className="wheel-kpi__breakdown-value">{info.contractsAvailable}</span>
          </div>
        ))}
        {ccEntries.length === 0 && (
          <div className="wheel-kpi__breakdown-row">
            <span className="wheel-kpi__breakdown-label">None</span>
            <span className="wheel-kpi__breakdown-value">--</span>
          </div>
        )}
      </div>

      {/* 4. Premium & P&L */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Premium & P&L</span>
          <DollarSign size={14} className="wheel-kpi__icon" />
        </div>
        <div className={`wheel-kpi__value ${(metrics?.unrealizedPnl.usd ?? 0) >= 0 ? 'wheel-kpi__value--pos' : 'wheel-kpi__value--neg'}`}>
          {formatCurrency(metrics?.unrealizedPnl.usd ?? 0, 'USD')}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Premium</span>
          <span className="wheel-kpi__breakdown-value wheel-kpi__breakdown-value--pos">
            +{formatCurrency(metrics?.totalPremium.usd ?? 0, 'USD')}
          </span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Unrealized</span>
          <span className={`wheel-kpi__breakdown-value ${(metrics?.unrealizedPnl.usd ?? 0) >= 0 ? 'wheel-kpi__breakdown-value--pos' : 'wheel-kpi__breakdown-value--neg'}`}>
            {formatCurrency(metrics?.unrealizedPnl.usd ?? 0, 'USD')}
          </span>
        </div>
      </div>

      {/* 5. Positions */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Positions</span>
          <Target size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">{positionCounts.total}</div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CSP</span>
          <span className="wheel-kpi__breakdown-value">{positionCounts.csp}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CC</span>
          <span className="wheel-kpi__breakdown-value">{positionCounts.cc}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Expiring</span>
          <span className="wheel-kpi__breakdown-value wheel-kpi__breakdown-value--neg">{positionCounts.expiring}</span>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create WheelKpiCards.css**

```css
/* Matches KpiCard.css pattern exactly */
.wheel-kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.wheel-kpi-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.wheel-kpi__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wheel-kpi__label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-secondary);
  font-weight: 500;
}

.wheel-kpi__icon { color: var(--text-secondary); }

.wheel-kpi__value {
  font-size: 18px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--text-primary);
  margin: 8px 0 0;
}

.wheel-kpi__value--pos { color: var(--success-text); }
.wheel-kpi__value--neg { color: var(--danger-text); }
.wheel-kpi__value--cc { color: var(--orange-text); }

.wheel-kpi__divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0;
}

.wheel-kpi__breakdown-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.wheel-kpi__breakdown-label {
  font-size: 10px;
  color: var(--text-muted);
}

.wheel-kpi__breakdown-value {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.wheel-kpi__breakdown-value--pos { color: var(--success-text); }
.wheel-kpi__breakdown-value--neg { color: var(--danger-text); }

/* Mobile: 2x2 grid + CC row full width */
@media (max-width: 768px) {
  .wheel-kpi-row {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    padding: 0 16px;
  }

  .wheel-kpi-card:nth-child(3) {
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .wheel-kpi-card:nth-child(3) .wheel-kpi__value {
    margin: 0 0 0 8px;
    font-size: 14px;
  }

  .wheel-kpi-card:nth-child(3) .wheel-kpi__divider,
  .wheel-kpi-card:nth-child(3) .wheel-kpi__breakdown-row {
    display: none;
  }
}
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds (component not yet imported anywhere).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/WheelKpiCards.tsx frontend/src/components/wheel/WheelKpiCards.css
git commit -m "feat(wheel): create WheelKpiCards component with 5 KPI cards matching dashboard pattern"
```

---

### Task 4: Create WheelCalendarGrid component

**Files:**
- Create: `frontend/src/components/wheel/WheelCalendarGrid.tsx`
- Create: `frontend/src/components/wheel/WheelCalendarGrid.css`
- Modify: `frontend/src/components/wheel/PositionCard.tsx`
- Modify: `frontend/src/components/wheel/PositionCard.css`

- [ ] **Step 1: Simplify PositionCard — remove premium row, show strike+type and P&L only**

Replace the entire `PositionCard.tsx` content with:

```typescript
import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './PositionCard.css'

interface PositionCardProps {
  position: WheelPosition
  onClick: (position: WheelPosition) => void
}

export function PositionCard({ position, onClick }: PositionCardProps) {
  const typeClass = position.type === 'CSP' ? 'wpc--csp' : 'wpc--cc'
  const strikeClass = position.type === 'CSP' ? 'wpc__strike--csp' : 'wpc__strike--cc'
  const typeLabel = position.type === 'CSP' ? 'Put' : 'CC'
  const pnlClass = (position.pnl ?? 0) >= 0 ? 'wpc__pnl--pos' : 'wpc__pnl--neg'

  return (
    <div
      className={`wpc ${typeClass}`}
      onClick={() => onClick(position)}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onClick(position) }}
    >
      <div className="wpc__row">
        <span className={`wpc__strike ${strikeClass}`}>
          ${position.strike} {typeLabel}
        </span>
        <span className="wpc__otm">
          {position.otmPercent != null ? `${position.otmPercent.toFixed(1)}%` : ''}
        </span>
      </div>
      <div className="wpc__row">
        <span className={`wpc__pnl ${pnlClass}`}>
          {position.pnl != null
            ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}`
            : '--'}
        </span>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Rewrite PositionCard.css for compact card**

Replace entire `PositionCard.css` with:

```css
.wpc {
  border-radius: var(--radius-sm);
  padding: 7px 10px;
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast);
}

.wpc:hover { transform: translateY(-1px); }

.wpc--csp {
  background: var(--csp-bg);
  border: 1px solid var(--csp-border);
}
.wpc--csp:hover { border-color: rgba(99, 102, 241, 0.5); }

.wpc--cc {
  background: var(--cc-bg);
  border: 1px solid var(--cc-border);
}
.wpc--cc:hover { border-color: rgba(249, 115, 22, 0.5); }

.wpc__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.wpc__row + .wpc__row { margin-top: 3px; }

.wpc__strike {
  font-weight: 600;
  font-family: var(--font-mono);
  font-size: 11px;
}
.wpc__strike--csp { color: var(--indigo-text, #818cf8); }
.wpc__strike--cc { color: var(--orange-text, #fb923c); }

.wpc__otm {
  color: var(--text-muted);
  font-size: 9px;
}

.wpc__pnl {
  font-family: var(--font-mono);
  font-size: 10px;
}
.wpc__pnl--pos { color: var(--success-text); }
.wpc__pnl--neg { color: var(--danger-text); }

/* Mobile compact */
@media (max-width: 768px) {
  .wpc { padding: 5px 7px; }
  .wpc__strike { font-size: 10px; }
  .wpc__otm { font-size: 8px; }
  .wpc__pnl { font-size: 9px; }
}
```

- [ ] **Step 3: Create WheelCalendarGrid.tsx**

Create `frontend/src/components/wheel/WheelCalendarGrid.tsx`:

```typescript
import type { WheelPosition, CCInfo } from '@/types/wheel'
import { getDteUrgency } from '@/types/wheel'
import { PositionCard } from './PositionCard'
import { formatCurrency } from '@/services/brokerService'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import './WheelCalendarGrid.css'

interface ExpiryColumn {
  date: string
  dte: number
  dayOfWeek: string
  isMonthly: boolean
}

interface TickerRow {
  symbol: string
  currentPrice: number | null
  currency: string
  totalExposure: number
  ccInfo: CCInfo | null
  cells: Record<string, { positions: WheelPosition[] }>
}

interface WheelCalendarGridProps {
  tickerRows: TickerRow[]
  expiries: ExpiryColumn[]
  dateRange: string
  onPrev: () => void
  onNext: () => void
  onToday: () => void
  onPositionClick: (position: WheelPosition, ticker: string, expiryDate: string) => void
  onEmptySlotClick: (ticker: string, expiryDate: string) => void
  onCCSlotClick: (ticker: string, expiryDate: string) => void
  onAddTicker: () => void
}

export function WheelCalendarGrid({
  tickerRows, expiries, dateRange,
  onPrev, onNext, onToday,
  onPositionClick, onEmptySlotClick, onCCSlotClick,
  onAddTicker,
}: WheelCalendarGridProps) {
  const today = new Date().toISOString().split('T')[0]

  return (
    <>
      {/* Timeline navigation */}
      <div className="wcg-timeline">
        <button className="wcg-timeline__btn" onClick={onPrev} aria-label="Previous weeks">
          <ChevronLeft size={14} />
        </button>
        <span className="wcg-timeline__range">{dateRange}</span>
        <button className="wcg-timeline__btn" onClick={onNext} aria-label="Next weeks">
          <ChevronRight size={14} />
        </button>
        <button className="wcg-timeline__today" onClick={onToday}>Today</button>
      </div>

      {/* Grid */}
      <div className="wcg-wrapper">
        <div className="wcg-scroll">
          <table className="wcg-table">
            <thead>
              <tr>
                <th className="wcg-th-ticker">Ticker</th>
                {expiries.map(exp => {
                  const urgency = getDteUrgency(exp.dte)
                  const isToday = exp.date === today
                  return (
                    <th key={exp.date} className={`wcg-th-expiry ${isToday ? 'wcg-th-expiry--today' : ''}`}>
                      <div className="wcg-expiry-date">
                        {formatExpiryShort(exp.date)}
                        {exp.isMonthly && <span className="wcg-monthly-badge">Monthly</span>}
                      </div>
                      <div className="wcg-expiry-meta">
                        <span className={`wcg-dte wcg-dte--${urgency}`}>{exp.dte}d</span>
                        <span className="wcg-expiry-day">{exp.dayOfWeek.slice(0, 3)}</span>
                      </div>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {tickerRows.map(row => (
                <tr key={row.symbol}>
                  <td className="wcg-td-ticker">
                    <div className="wcg-ticker-name">{row.symbol}</div>
                    <div className="wcg-ticker-price">
                      {row.currentPrice != null ? `$${row.currentPrice.toLocaleString()}` : '--'}
                    </div>
                    <div className="wcg-ticker-exposure">
                      {getCurrencyLabel(row.currency)} {row.totalExposure.toLocaleString('en-US', { maximumFractionDigits: 0 })}
                    </div>
                    {row.ccInfo && (
                      <>
                        <div className="wcg-ticker-shares">{row.ccInfo.sharesOwned} shares</div>
                        <div className="wcg-ticker-cc-badge">&#x25B2; {row.ccInfo.contractsAvailable} CC</div>
                      </>
                    )}
                  </td>
                  {expiries.map(exp => {
                    const cell = row.cells[exp.date]
                    const positions = cell?.positions ?? []
                    const isToday = exp.date === today
                    const hasCC = row.ccInfo != null && positions.length === 0

                    return (
                      <td key={exp.date} className={`wcg-td-cell ${isToday ? 'wcg-td-cell--today' : ''}`}>
                        <div className="wcg-cell-content">
                          {positions.map(pos => (
                            <PositionCard
                              key={pos.id}
                              position={pos}
                              onClick={p => onPositionClick(p, row.symbol, exp.date)}
                            />
                          ))}
                          {positions.length > 0 && row.ccInfo && (
                            <button className="wcg-cc-slot" onClick={() => onCCSlotClick(row.symbol, exp.date)}>
                              + Sell CC
                            </button>
                          )}
                          {hasCC ? (
                            <button className="wcg-cc-slot" onClick={() => onCCSlotClick(row.symbol, exp.date)}>
                              + Sell CC
                            </button>
                          ) : positions.length === 0 ? (
                            <button className="wcg-empty-slot" onClick={() => onEmptySlotClick(row.symbol, exp.date)}>
                              +
                            </button>
                          ) : null}
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button className="wcg-add-ticker" onClick={onAddTicker}>+ Add Ticker</button>
      </div>

      {/* Legend */}
      <div className="wcg-legend">
        <span className="wcg-legend__item"><span className="wcg-legend__dot wcg-legend__dot--csp" /> CSP</span>
        <span className="wcg-legend__item"><span className="wcg-legend__dot wcg-legend__dot--cc" /> CC</span>
      </div>
    </>
  )
}

function formatExpiryShort(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function getCurrencyLabel(currency: string): string {
  return currency === 'CAD' ? 'C$' : 'US$'
}
```

- [ ] **Step 4: Create WheelCalendarGrid.css**

Create `frontend/src/components/wheel/WheelCalendarGrid.css`:

```css
/* Timeline navigation */
.wcg-timeline {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.wcg-timeline__btn {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  padding: 5px 12px;
  cursor: pointer;
  font-family: inherit;
  display: flex;
  align-items: center;
  transition: border-color var(--transition-fast);
}
.wcg-timeline__btn:hover { border-color: var(--border-hover); }

.wcg-timeline__range {
  flex: 1;
  text-align: center;
  font-size: 11px;
  color: var(--text-secondary);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.wcg-timeline__today {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
  border-radius: var(--radius-sm);
  color: var(--success-text);
  padding: 5px 12px;
  cursor: pointer;
  font-size: 11px;
  font-weight: 500;
  font-family: inherit;
}

/* Grid wrapper */
.wcg-wrapper {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.wcg-scroll { overflow: auto; flex: 1; }

.wcg-table { width: 100%; border-collapse: collapse; }

/* Header cells */
.wcg-th-ticker {
  padding: 8px 12px;
  text-align: left;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-secondary);
  font-weight: 500;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  left: 0;
  z-index: 3;
  background: var(--bg-secondary);
  width: 120px;
}

.wcg-th-expiry {
  padding: 8px 10px;
  text-align: center;
  min-width: 150px;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--bg-secondary);
}
.wcg-th-expiry--today { background: rgba(248, 113, 113, 0.03); }

.wcg-expiry-date {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.wcg-monthly-badge {
  background: rgba(99, 102, 241, 0.15);
  color: var(--indigo-text, #818cf8);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 8px;
  font-weight: 500;
}

.wcg-expiry-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 2px;
}

.wcg-dte {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 9px;
  font-weight: 600;
}
.wcg-dte--critical { background: rgba(248, 113, 113, 0.2); color: var(--danger-text); }
.wcg-dte--warning { background: rgba(251, 191, 36, 0.15); color: var(--warning-text); }
.wcg-dte--safe { background: rgba(16, 185, 129, 0.15); color: var(--success-text); }

.wcg-expiry-day { font-size: 9px; color: var(--text-muted); }

/* Body cells */
.wcg-td-ticker {
  padding: 8px 12px;
  position: sticky;
  left: 0;
  background: var(--bg-secondary);
  z-index: 1;
  border-bottom: 1px solid rgba(255, 255, 255, 0.025);
}

.wcg-ticker-name { font-size: 13px; font-weight: 700; color: var(--text-primary); }
.wcg-ticker-price { font-size: 10px; color: var(--text-secondary); font-family: var(--font-mono); margin-top: 1px; }
.wcg-ticker-exposure { font-size: 10px; font-family: var(--font-mono); color: var(--text-secondary); margin-top: 2px; }
.wcg-ticker-shares { font-size: 9px; color: var(--text-muted); margin-top: 2px; }

.wcg-ticker-cc-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: var(--cc-bg);
  border: 1px solid var(--cc-border);
  color: var(--orange-text, #fb923c);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 8px;
  font-weight: 500;
  margin-top: 3px;
}

.wcg-td-cell {
  padding: 4px 6px;
  vertical-align: top;
  border-bottom: 1px solid rgba(255, 255, 255, 0.025);
}
.wcg-td-cell--today { background: rgba(248, 113, 113, 0.03); }

.wcg-cell-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* Empty slot */
.wcg-empty-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-sm);
  padding: 12px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 16px;
  background: none;
  font-family: inherit;
  transition: border-color var(--transition-fast), color var(--transition-fast);
}
.wcg-empty-slot:hover {
  border-color: rgba(99, 102, 241, 0.3);
  color: var(--indigo-text, #818cf8);
}

/* CC slot */
.wcg-cc-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(249, 115, 22, 0.15);
  border-radius: var(--radius-sm);
  padding: 12px;
  cursor: pointer;
  color: rgba(249, 115, 22, 0.4);
  font-size: 10px;
  background: none;
  font-family: inherit;
  transition: all var(--transition-fast);
}
.wcg-cc-slot:hover {
  border-color: rgba(249, 115, 22, 0.4);
  color: var(--orange-text, #fb923c);
  background: rgba(249, 115, 22, 0.04);
}

/* Add ticker */
.wcg-add-ticker {
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--border);
  padding: 8px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 11px;
  background: none;
  border-left: none;
  border-right: none;
  border-bottom: none;
  font-family: inherit;
  width: 100%;
  transition: color var(--transition-fast);
}
.wcg-add-ticker:hover { color: var(--indigo-text, #818cf8); }

/* Legend */
.wcg-legend {
  display: flex;
  gap: 14px;
  padding: 6px 0;
  font-size: 9px;
  color: var(--text-muted);
}
.wcg-legend__item { display: flex; align-items: center; gap: 4px; }
.wcg-legend__dot { width: 7px; height: 7px; border-radius: 2px; }
.wcg-legend__dot--csp { background: rgba(99, 102, 241, 0.5); }
.wcg-legend__dot--cc { background: rgba(249, 115, 22, 0.5); }

/* Mobile: 2-column grid, compact */
@media (max-width: 768px) {
  .wcg-timeline { padding: 0 16px; }
  .wcg-wrapper { margin: 0 16px; }
  .wcg-th-ticker { width: 85px; padding: 6px 8px; font-size: 8px; }
  .wcg-th-expiry { min-width: 120px; padding: 6px 8px; }
  .wcg-expiry-date { font-size: 11px; }
  .wcg-dte { font-size: 8px; }
  .wcg-td-ticker { padding: 6px 8px; }
  .wcg-ticker-name { font-size: 12px; }
  .wcg-ticker-price { font-size: 9px; }
  .wcg-ticker-exposure { font-size: 9px; }
  .wcg-ticker-shares { font-size: 8px; }
  .wcg-ticker-cc-badge { font-size: 7px; }
  .wcg-td-cell { padding: 3px 5px; }
  .wcg-empty-slot { padding: 9px; font-size: 14px; }
  .wcg-cc-slot { padding: 9px; font-size: 9px; }
  .wcg-legend { padding: 4px 16px; font-size: 8px; }
}
```

- [ ] **Step 5: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/wheel/WheelCalendarGrid.tsx frontend/src/components/wheel/WheelCalendarGrid.css frontend/src/components/wheel/PositionCard.tsx frontend/src/components/wheel/PositionCard.css
git commit -m "feat(wheel): create WheelCalendarGrid with ticker rows, expiry columns, CC slots, and simplified PositionCard"
```

---

### Task 5: Rewrite WheelPage with new layout

**Files:**
- Rewrite: `frontend/src/pages/WheelPage.tsx`
- Rewrite: `frontend/src/pages/WheelPage.css`

- [ ] **Step 1: Rewrite WheelPage.tsx**

Replace the entire file with the new implementation that uses dynamic tickers, calendar grid, and KPI cards. The new WheelPage:
- Removes `WHEEL_TICKERS` constant
- Calls `useWheelPositions` without a ticker filter (pass `undefined` or all discovered tickers)
- Uses `discoverTickers()` and `detectCCEligible()` to find tickers and CC opportunities
- Manages calendar window state (`calendarOffset`) for timeline navigation
- Uses `generateWeeklyExpiries()` for column generation (4 columns desktop, managed via state)
- Renders `AccountNavBar`, `WheelKpiCards`, `WheelCalendarGrid`
- Keeps `OrderPanel` for Screen 3 navigation (position click → order panel)

Key state: `calendarOffset: number` (weeks from today, default 0), `selectedConnectionId`, `selectedPosition`.

The page should compute:
- `discoveredTickers` from option positions via `discoverTickers()`
- `ccEligible` from stock positions via `detectCCEligible()`
- `allTickers` = union of discovered + CC-eligible + manual tickers
- `expiries` from `generateWeeklyExpiries(startDate, 4)` (4 for desktop)
- `tickerRows` by building cells from grid data + CC info
- `positionCounts` for KPI card
- `capitalMetrics` (reuse existing computation)

Navigation handlers:
- `handlePositionClick` → open OrderPanel (Screen 3)
- `handleEmptySlotClick` → will later navigate to Screen 2 (for now, open OrderPanel like current behavior)
- `handleCCSlotClick` → will later navigate to Screen 2 with calls context (for now, open OrderPanel)

- [ ] **Step 2: Rewrite WheelPage.css**

Replace with new layout styles matching the spec:
- `padding: 0 24px`, flex column layout
- KPI cards section
- Grid area fills remaining space
- Mobile: `padding: 0`, KPI section with 16px padding

- [ ] **Step 3: Delete old components**

```bash
git rm frontend/src/components/wheel/WheelGrid.tsx
git rm frontend/src/components/wheel/WheelGrid.css
git rm frontend/src/components/wheel/CapitalSummary.tsx
git rm frontend/src/components/wheel/CapitalSummary.css
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors.

- [ ] **Step 5: Run all tests**

Run: `cd frontend && npm run test:run`
Expected: All tests pass (existing wheel position tests still work, KpiCard tests unaffected).

- [ ] **Step 6: Run linter on changed files**

Run: `cd frontend && npx eslint src/pages/WheelPage.tsx src/components/wheel/WheelCalendarGrid.tsx src/components/wheel/WheelKpiCards.tsx src/components/wheel/PositionCard.tsx --ext ts,tsx`
Expected: No errors.

- [ ] **Step 7: Commit**

```bash
git add -u
git add frontend/src/pages/WheelPage.tsx frontend/src/pages/WheelPage.css
git commit -m "feat(wheel): rewrite WheelPage with calendar grid, dynamic tickers, KPI cards, and CC detection"
```

---

### Task 6: Deploy, UAT test, and update documentation

**Files:**
- Modify: `docs/reference/frontend-map.md`

- [ ] **Step 1: Build and deploy to Docker**

```bash
docker compose up --build -d frontend
```

- [ ] **Step 2: UAT test with Playwright**

Login → navigate to /wheel → verify:
- 5 KPI cards render with real data
- Calendar grid shows dynamic tickers (not just SOXL/TECL/TQQQ/UPRO)
- Timeline nav ← → shifts weeks
- Position cards are clickable → opens OrderPanel
- CC-eligible tickers show orange badges and "Sell CC" slots
- Total exposure shows in base currency per ticker
- Mobile at 375px: 2 columns, compact grid

- [ ] **Step 3: Update frontend-map.md**

Update the Wheel Strategy Module section to reflect the new components:
- Replace WheelGrid entry with WheelCalendarGrid
- Replace CapitalSummary entry with WheelKpiCards
- Update PositionCard description
- Update WheelPage description

- [ ] **Step 4: Move spec and plan to archive**

```bash
git mv docs/superpowers/specs/2026-05-29-wheel-screen1-positions-design.md .archive/
git mv docs/superpowers/plans/2026-05-29-wheel-screen1-positions.md .archive/
```

- [ ] **Step 5: Commit**

```bash
git add docs/reference/frontend-map.md
git commit -m "docs: update frontend-map for wheel Screen 1 redesign, archive spec and plan"
```
