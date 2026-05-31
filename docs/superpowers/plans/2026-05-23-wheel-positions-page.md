# Wheel Positions Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dedicated `/wheel` page that displays open CSP and CC positions in a timeline grid (expiry dates on Y-axis, tickers on X-axis) with account filtering, capital metrics, and position actions.

**Architecture:** Frontend-only feature using existing backend APIs. A new page with custom components (no AG Grid — position cards require custom rendering). Data flows through a new `useWheelPositions` hook that fetches positions from existing broker APIs, filters to options, and organises them into the grid structure. Account switching reuses the existing `useBrokerConnections` hook.

**Tech Stack:** React 18, TypeScript, React Query, Zustand (existing stores), React Router, plain CSS with CSS custom properties, Inter + JetBrains Mono fonts.

**Spec:** `docs/superpowers/specs/2026-05-23-wheel-positions-page-design.md`
**Mockup:** `.superpowers/brainstorm/13920-1779511337/content/timeline-layout-v4.html`

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `frontend/src/types/wheel.ts` | TypeScript interfaces for wheel grid data structures |
| `frontend/src/hooks/useWheelPositions.ts` | Hook: fetch positions, filter to options, group into grid structure |
| `frontend/src/hooks/__tests__/useWheelPositions.test.ts` | Tests for position filtering and grouping logic |
| `frontend/src/components/wheel/CapitalSummary.tsx` | Capital metrics bar (cash, deployed, shares, CCs, premium, P&L) |
| `frontend/src/components/wheel/CapitalSummary.css` | Styles for capital summary bar |
| `frontend/src/components/wheel/PositionCard.tsx` | Individual position card (strike, premium, P&L, OTM) |
| `frontend/src/components/wheel/PositionCard.css` | Styles for position card (CSP vs CC colour, labels) |
| `frontend/src/components/wheel/ClosePositionDialog.tsx` | Confirmation dialog for closing a position |
| `frontend/src/components/wheel/ClosePositionDialog.css` | Styles for close dialog |
| `frontend/src/components/wheel/WheelGrid.tsx` | Timeline grid table with sticky header, scrollable body, sticky totals |
| `frontend/src/components/wheel/WheelGrid.css` | Grid table styles, sticky positioning, empty slots |
| `frontend/src/pages/WheelPage.tsx` | Page component: account tabs, data orchestration, layout |
| `frontend/src/pages/WheelPage.css` | Page-level styles |

### Modified Files

| File | Change |
|------|--------|
| `frontend/src/App.tsx` | Add lazy import + route for WheelPage |
| `frontend/src/components/layout/AppSidebar.tsx` | Add "Wheel" nav item in Options section |
| `frontend/src/pages/OptionsPage.tsx` | Read `?ticker=` query param and auto-load chain on mount |

---

## Task 1: Wheel Types

**Files:**
- Create: `frontend/src/types/wheel.ts`

- [ ] **Step 1: Create type definitions**

```typescript
// frontend/src/types/wheel.ts

export interface WheelTicker {
  symbol: string
  currentPrice: number | null
}

export interface WheelPosition {
  id: number
  type: 'CSP' | 'CC'
  strike: number
  premium: number | null
  currentPrice: number | null
  pnl: number | null
  otmPercent: number | null
  quantity: number
  accountName: string | null
  accountNumber: string | null
  connectionId: number
}

export interface WheelCell {
  positions: WheelPosition[]
}

export interface WheelExpiryRow {
  expiryDate: string
  dte: number
  dayOfWeek: string
  isMonthly: boolean
  cells: Record<string, WheelCell>
}

export interface TickerTotals {
  positionCount: number
  cspExposure: number
  totalPnl: number
}

export interface WheelGridData {
  tickers: WheelTicker[]
  expiryRows: WheelExpiryRow[]
  totals: Record<string, TickerTotals>
}

export interface CapitalMetrics {
  availableCash: number
  deployedCsp: number
  sharesHeld: number
  ccsWritten: number
  totalPremium: number
  unrealizedPnl: number
}

export type DteUrgency = 'critical' | 'warning' | 'normal' | 'safe' | 'far'

export function getDteUrgency(dte: number): DteUrgency {
  if (dte <= 10) return 'critical'
  if (dte <= 21) return 'warning'
  if (dte <= 45) return 'normal'
  if (dte <= 70) return 'safe'
  return 'far'
}

export function isMonthlyExpiry(date: Date): boolean {
  const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  const thirdFriday = getThirdFriday(date.getFullYear(), date.getMonth())
  return date.getDate() === thirdFriday.getDate()
}

function getThirdFriday(year: number, month: number): Date {
  const first = new Date(year, month, 1)
  const dayOfWeek = first.getDay()
  const firstFriday = dayOfWeek <= 5 ? (5 - dayOfWeek + 1) : (5 + 7 - dayOfWeek + 1)
  return new Date(year, month, firstFriday + 14)
}
```

- [ ] **Step 2: Verify types compile**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: No errors related to wheel.ts

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/wheel.ts
git commit -m "feat(wheel): add TypeScript types for wheel positions grid"
```

---

## Task 2: Position Grouping Logic + Tests

**Files:**
- Create: `frontend/src/hooks/useWheelPositions.ts`
- Create: `frontend/src/hooks/__tests__/useWheelPositions.test.ts`

- [ ] **Step 1: Write tests for the pure grouping function**

The hook will call APIs via React Query, but the grouping/filtering logic is a pure function we can test directly.

```typescript
// frontend/src/hooks/__tests__/useWheelPositions.test.ts
import { describe, it, expect } from 'vitest'
import { buildWheelGrid, computeCapitalMetrics, computeTickerTotals } from '../useWheelPositions'
import type { BrokerPosition } from '@/types/broker'

function makePosition(overrides: Partial<BrokerPosition> = {}): BrokerPosition {
  return {
    id: 1,
    symbol: 'SOXL 250530P00020000',
    securityName: null,
    instrumentType: 'OPTION',
    quantity: -1,
    averageCost: 0.85,
    currentPrice: 0.23,
    currentValue: -23,
    totalPnl: 62,
    totalPnlPercent: 72.9,
    currency: 'USD',
    strikePrice: 20,
    expirationDate: '2026-05-30',
    optionType: 'PUT',
    underlyingSymbol: 'SOXL',
    ...overrides,
  }
}

describe('buildWheelGrid', () => {
  const tickers = ['SOXL', 'TECL']
  const today = new Date('2026-05-23')

  it('places a CSP position in the correct cell', () => {
    const positions = [makePosition()]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row).toBeDefined()
    expect(row!.cells['SOXL'].positions).toHaveLength(1)
    expect(row!.cells['SOXL'].positions[0].type).toBe('CSP')
    expect(row!.cells['SOXL'].positions[0].strike).toBe(20)
  })

  it('places a CC position in the correct cell', () => {
    const positions = [
      makePosition({
        id: 2,
        symbol: 'TECL 250530C00085000',
        optionType: 'CALL',
        strikePrice: 85,
        underlyingSymbol: 'TECL',
        quantity: -1,
        averageCost: 3.40,
        totalPnl: 210,
      }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['TECL'].positions).toHaveLength(1)
    expect(row!.cells['TECL'].positions[0].type).toBe('CC')
  })

  it('filters out non-option positions', () => {
    const positions = [
      makePosition({ instrumentType: 'STOCK', strikePrice: null, optionType: null }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const totalPositions = grid.expiryRows.reduce(
      (sum, row) => sum + Object.values(row.cells).reduce(
        (s, cell) => s + cell.positions.length, 0
      ), 0
    )
    expect(totalPositions).toBe(0)
  })

  it('filters out positions with expiry beyond 90 days', () => {
    const positions = [
      makePosition({ expirationDate: '2026-09-18' }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const totalPositions = grid.expiryRows.reduce(
      (sum, row) => sum + Object.values(row.cells).reduce(
        (s, cell) => s + cell.positions.length, 0
      ), 0
    )
    expect(totalPositions).toBe(0)
  })

  it('stacks multiple positions in the same cell', () => {
    const positions = [
      makePosition({ id: 1, strikePrice: 20 }),
      makePosition({ id: 2, strikePrice: 18, averageCost: 0.60, totalPnl: 40 }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['SOXL'].positions).toHaveLength(2)
  })

  it('computes DTE correctly', () => {
    const positions = [makePosition()]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.dte).toBe(7)
  })

  it('includes expiry dates from available expirations even without positions', () => {
    const availableExpiries = ['2026-06-06', '2026-06-20']
    const grid = buildWheelGrid([], tickers, availableExpiries, today)

    expect(grid.expiryRows.length).toBeGreaterThanOrEqual(2)
    const jun6 = grid.expiryRows.find(r => r.expiryDate === '2026-06-06')
    expect(jun6).toBeDefined()
    expect(jun6!.cells['SOXL'].positions).toHaveLength(0)
  })
})

describe('computeTickerTotals', () => {
  it('computes position count, CSP exposure, and total P&L', () => {
    const positions = [
      makePosition(),
      makePosition({
        id: 2,
        optionType: 'CALL',
        strikePrice: 26,
        totalPnl: 28,
        quantity: -1,
      }),
    ]
    const tickers = ['SOXL']
    const today = new Date('2026-05-23')
    const grid = buildWheelGrid(positions, tickers, [], today)
    const totals = computeTickerTotals(grid)

    expect(totals['SOXL'].positionCount).toBe(2)
    expect(totals['SOXL'].cspExposure).toBe(2000) // $20 strike x 100
    expect(totals['SOXL'].totalPnl).toBe(90) // 62 + 28
  })
})

describe('computeCapitalMetrics', () => {
  it('computes capital metrics from positions and cash', () => {
    const optionPositions = [
      makePosition({ optionType: 'PUT', strikePrice: 20, quantity: -1, averageCost: 0.85, totalPnl: 62 }),
      makePosition({
        id: 2,
        optionType: 'CALL',
        strikePrice: 26,
        underlyingSymbol: 'SOXL',
        quantity: -1,
        averageCost: 0.65,
        totalPnl: 28,
      }),
    ]
    const stockPositions = [
      makePosition({
        id: 3,
        instrumentType: 'STOCK',
        symbol: 'SOXL',
        underlyingSymbol: null,
        optionType: null,
        strikePrice: null,
        currentValue: 2245,
        quantity: 100,
      }),
    ]
    const tickers = ['SOXL']
    const metrics = computeCapitalMetrics(optionPositions, stockPositions, tickers, 5000)

    expect(metrics.availableCash).toBe(5000)
    expect(metrics.deployedCsp).toBe(2000) // $20 x 100
    expect(metrics.sharesHeld).toBe(2245) // stock value
    expect(metrics.ccsWritten).toBe(2600) // $26 x 100
    expect(metrics.totalPremium).toBe(150) // (0.85 + 0.65) x 100
    expect(metrics.unrealizedPnl).toBe(90) // 62 + 28
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `cd frontend && npx vitest run src/hooks/__tests__/useWheelPositions.test.ts 2>&1 | tail -10`
Expected: FAIL — module not found

- [ ] **Step 3: Implement the pure functions and hook**

```typescript
// frontend/src/hooks/useWheelPositions.ts
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getConnectionPositions, getAggregatedPositions } from '@/services/brokerService'
import { getOptionsChainWithGreeks } from '@/services/marketDataService'
import type { BrokerPosition, ConnectionPositionsResponse, AggregatedPositionsResponse } from '@/types/broker'
import type {
  WheelGridData, WheelExpiryRow, WheelCell, WheelPosition,
  WheelTicker, TickerTotals, CapitalMetrics,
} from '@/types/wheel'

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const MAX_DTE = 90

function diffDays(from: Date, to: Date): number {
  const msPerDay = 86400000
  const utcFrom = Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())
  const utcTo = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate())
  return Math.round((utcTo - utcFrom) / msPerDay)
}

function getThirdFriday(year: number, month: number): Date {
  const first = new Date(year, month, 1)
  const dayOfWeek = first.getDay()
  const firstFriday = dayOfWeek <= 5 ? (5 - dayOfWeek + 1) : (5 + 7 - dayOfWeek + 1)
  return new Date(year, month, firstFriday + 14)
}

function isMonthlyExpiry(dateStr: string): boolean {
  const d = new Date(dateStr + 'T00:00:00')
  const thirdFriday = getThirdFriday(d.getFullYear(), d.getMonth())
  return d.getDate() === thirdFriday.getDate()
}

function isOptionPosition(p: BrokerPosition): boolean {
  return (
    p.instrumentType === 'OPTION' &&
    p.optionType != null &&
    p.strikePrice != null &&
    p.expirationDate != null &&
    p.underlyingSymbol != null
  )
}

function toWheelPosition(
  p: BrokerPosition,
  accountName: string | null,
  accountNumber: string | null,
  connectionId: number,
): WheelPosition {
  return {
    id: p.id,
    type: p.optionType === 'CALL' ? 'CC' : 'CSP',
    strike: p.strikePrice!,
    premium: p.averageCost != null ? Math.abs(p.averageCost) * 100 : null,
    currentPrice: p.currentPrice,
    pnl: p.totalPnl,
    otmPercent: null,
    quantity: Math.abs(p.quantity),
    accountName,
    accountNumber,
    connectionId,
  }
}

export function buildWheelGrid(
  positions: BrokerPosition[],
  tickers: string[],
  availableExpiries: string[],
  today: Date,
  accountName: string | null = null,
  accountNumber: string | null = null,
  connectionId: number = 0,
  underlyingPrices: Record<string, number> = {},
): WheelGridData {
  const optionPositions = positions.filter(
    p => isOptionPosition(p) && tickers.includes(p.underlyingSymbol!)
  )

  const expirySet = new Set<string>()
  availableExpiries.forEach(e => {
    const dte = diffDays(today, new Date(e + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(e)
  })
  optionPositions.forEach(p => {
    const dte = diffDays(today, new Date(p.expirationDate! + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(p.expirationDate!)
  })

  const sortedExpiries = Array.from(expirySet).sort()

  const expiryRows: WheelExpiryRow[] = sortedExpiries.map(expiry => {
    const d = new Date(expiry + 'T00:00:00')
    const dte = diffDays(today, d)

    const cells: Record<string, WheelCell> = {}
    tickers.forEach(ticker => {
      const matching = optionPositions
        .filter(p => p.underlyingSymbol === ticker && p.expirationDate === expiry)
        .map(p => {
          const wp = toWheelPosition(p, accountName, accountNumber, connectionId)
          const underlyingPrice = underlyingPrices[ticker]
          if (underlyingPrice && underlyingPrice > 0) {
            wp.otmPercent = Math.abs(wp.strike - underlyingPrice) / underlyingPrice * 100
          }
          return wp
        })
      cells[ticker] = { positions: matching }
    })

    return {
      expiryDate: expiry,
      dte,
      dayOfWeek: DAY_NAMES[d.getDay()],
      isMonthly: isMonthlyExpiry(expiry),
      cells,
    }
  })

  const tickerList: WheelTicker[] = tickers.map(symbol => ({
    symbol,
    currentPrice: underlyingPrices[symbol] ?? null,
  }))

  return {
    tickers: tickerList,
    expiryRows,
    totals: computeTickerTotals({ tickers: tickerList, expiryRows, totals: {} }),
  }
}

export function computeTickerTotals(grid: WheelGridData): Record<string, TickerTotals> {
  const totals: Record<string, TickerTotals> = {}

  grid.tickers.forEach(({ symbol }) => {
    let positionCount = 0
    let cspExposure = 0
    let totalPnl = 0

    grid.expiryRows.forEach(row => {
      const cell = row.cells[symbol]
      if (!cell) return
      cell.positions.forEach(pos => {
        positionCount++
        if (pos.type === 'CSP') {
          cspExposure += pos.strike * 100 * pos.quantity
        }
        totalPnl += pos.pnl ?? 0
      })
    })

    totals[symbol] = { positionCount, cspExposure, totalPnl }
  })

  return totals
}

export function computeCapitalMetrics(
  optionPositions: BrokerPosition[],
  stockPositions: BrokerPosition[],
  tickers: string[],
  cashBalance: number,
): CapitalMetrics {
  let deployedCsp = 0
  let ccsWritten = 0
  let totalPremium = 0
  let unrealizedPnl = 0

  optionPositions.forEach(p => {
    if (!isOptionPosition(p) || !tickers.includes(p.underlyingSymbol!)) return
    const strike = p.strikePrice!
    const qty = Math.abs(p.quantity)
    const cost = Math.abs(p.averageCost ?? 0)

    if (p.optionType === 'PUT') {
      deployedCsp += strike * 100 * qty
    } else {
      ccsWritten += strike * 100 * qty
    }
    totalPremium += cost * 100 * qty
    unrealizedPnl += p.totalPnl ?? 0
  })

  const sharesHeld = stockPositions
    .filter(p => tickers.includes(p.symbol))
    .reduce((sum, p) => sum + (p.currentValue ?? 0), 0)

  return {
    availableCash: cashBalance,
    deployedCsp,
    sharesHeld,
    ccsWritten,
    totalPremium,
    unrealizedPnl,
  }
}

export function useWheelPositions(
  tickers: string[],
  connectionId?: number,
) {
  const positionsQuery = useQuery({
    queryKey: ['wheel-positions', connectionId ?? 'all'],
    queryFn: () =>
      connectionId
        ? getConnectionPositions(connectionId)
        : getAggregatedPositions(),
    staleTime: 60_000,
    enabled: tickers.length > 0,
  })

  const gridData = useMemo(() => {
    if (!positionsQuery.data) return null

    const today = new Date()
    let positions: BrokerPosition[]
    let accountName: string | null = null
    let accountNumber: string | null = null
    let connId = 0

    if ('positions' in positionsQuery.data && 'connectionId' in positionsQuery.data) {
      const resp = positionsQuery.data as ConnectionPositionsResponse
      positions = resp.positions
      accountName = resp.broker
      accountNumber = resp.accountNumber
      connId = resp.connectionId
    } else {
      const resp = positionsQuery.data as AggregatedPositionsResponse
      positions = resp.positions as unknown as BrokerPosition[]
    }

    return buildWheelGrid(positions, tickers, [], today, accountName, accountNumber, connId)
  }, [positionsQuery.data, tickers])

  return {
    gridData,
    isLoading: positionsQuery.isLoading,
    error: positionsQuery.error,
    refetch: positionsQuery.refetch,
  }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `cd frontend && npx vitest run src/hooks/__tests__/useWheelPositions.test.ts 2>&1 | tail -15`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useWheelPositions.ts frontend/src/hooks/__tests__/useWheelPositions.test.ts
git commit -m "feat(wheel): add useWheelPositions hook with grid grouping logic"
```

---

## Task 3: CapitalSummary Component

**Files:**
- Create: `frontend/src/components/wheel/CapitalSummary.tsx`
- Create: `frontend/src/components/wheel/CapitalSummary.css`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/components/wheel/CapitalSummary.tsx
import type { CapitalMetrics } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './CapitalSummary.css'

interface CapitalSummaryProps {
  metrics: CapitalMetrics | null
}

export function CapitalSummary({ metrics }: CapitalSummaryProps) {
  if (!metrics) return null

  const items = [
    { label: 'Available Cash', value: metrics.availableCash },
    { label: 'Deployed (CSPs)', value: metrics.deployedCsp },
    { label: 'Shares Held', value: metrics.sharesHeld },
    { label: 'CCs Written', value: metrics.ccsWritten },
    { label: 'Total Premium', value: metrics.totalPremium, isPnl: true },
    { label: 'Unrealized P&L', value: metrics.unrealizedPnl, isPnl: true },
  ]

  return (
    <div className="wheel-capital-bar">
      {items.map(item => (
        <div key={item.label} className="wheel-capital-item">
          <div className="wheel-capital-label">{item.label}</div>
          <div
            className={`wheel-capital-value ${
              item.isPnl
                ? item.value >= 0
                  ? 'wheel-pnl-positive'
                  : 'wheel-pnl-negative'
                : ''
            }`}
          >
            {item.isPnl && item.value >= 0 ? '+' : ''}
            {formatCurrency(item.value, 'USD')}
          </div>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Create styles**

```css
/* frontend/src/components/wheel/CapitalSummary.css */
.wheel-capital-bar {
  display: flex;
  gap: 28px;
  padding: 14px 20px;
  background: var(--card-bg);
  border-radius: 10px;
  margin-bottom: 20px;
  border: 1px solid var(--border);
}

.wheel-capital-label {
  color: var(--text-muted);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 3px;
  font-weight: 500;
}

.wheel-capital-value {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
  font-size: 14px;
  color: var(--text-primary);
}

.wheel-pnl-positive {
  color: var(--success);
}

.wheel-pnl-negative {
  color: var(--error);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -10`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/CapitalSummary.tsx frontend/src/components/wheel/CapitalSummary.css
git commit -m "feat(wheel): add CapitalSummary component"
```

---

## Task 4: PositionCard Component

**Files:**
- Create: `frontend/src/components/wheel/PositionCard.tsx`
- Create: `frontend/src/components/wheel/PositionCard.css`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/components/wheel/PositionCard.tsx
import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './PositionCard.css'

interface PositionCardProps {
  position: WheelPosition
  showAccount: boolean
  onClick: (position: WheelPosition) => void
}

export function PositionCard({ position, showAccount, onClick }: PositionCardProps) {
  const typeClass = position.type === 'CSP' ? 'wheel-card-csp' : 'wheel-card-cc'

  return (
    <div
      className={`wheel-position-card ${typeClass}`}
      onClick={() => onClick(position)}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onClick(position) }}
    >
      {showAccount && position.accountName && (
        <span className={`wheel-account-badge wheel-account-${position.connectionId % 3}`}>
          {position.accountName}
          {position.accountNumber ? ` ${position.accountNumber}` : ''}
        </span>
      )}
      <div className="wheel-card-row">
        <span className="wheel-card-label">Strike</span>
        <span className="wheel-card-value wheel-card-strike">
          {formatCurrency(position.strike, 'USD')}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">Premium</span>
        <span className="wheel-card-value wheel-card-small">
          {position.premium != null ? `+${formatCurrency(position.premium, 'USD')}` : '—'}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">P&L</span>
        <span
          className={`wheel-card-value wheel-card-small ${
            (position.pnl ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'
          }`}
        >
          {position.pnl != null
            ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}`
            : '—'}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">OTM</span>
        <span className="wheel-card-value wheel-card-small">
          {position.otmPercent != null ? `${position.otmPercent.toFixed(1)}%` : '—'}
        </span>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create styles**

```css
/* frontend/src/components/wheel/PositionCard.css */
.wheel-position-card {
  padding: 9px 11px;
  border-radius: 8px;
  font-size: 11px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.wheel-position-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.wheel-position-card + .wheel-position-card {
  margin-top: 6px;
}

.wheel-card-csp {
  background: linear-gradient(135deg, #1d2750, #263168);
  border: 1px solid rgba(100, 140, 220, 0.2);
  border-left: 3px solid rgba(100, 150, 230, 0.6);
}

.wheel-card-cc {
  background: linear-gradient(135deg, #3d1f35, #4e2845);
  border: 1px solid rgba(200, 130, 170, 0.2);
  border-left: 3px solid rgba(200, 130, 170, 0.6);
}

html:not(.dark) .wheel-card-csp {
  background: linear-gradient(135deg, #dde4f8, #e8edfb);
  border-color: rgba(80, 110, 180, 0.2);
  border-left-color: rgba(80, 110, 180, 0.5);
}

html:not(.dark) .wheel-card-cc {
  background: linear-gradient(135deg, #f4dde9, #f8e6ef);
  border-color: rgba(170, 90, 130, 0.2);
  border-left-color: rgba(170, 90, 130, 0.5);
}

.wheel-card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 3px;
}

.wheel-card-row:last-child {
  margin-bottom: 0;
}

.wheel-card-label {
  font-size: 9px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
  min-width: 52px;
  font-weight: 500;
}

.wheel-card-value {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
  font-size: 12px;
  text-align: right;
  color: var(--text-primary);
}

.wheel-card-strike {
  font-size: 13px;
  font-weight: 600;
}

.wheel-card-small {
  font-size: 11px;
  font-weight: 400;
}

/* Account badge */
.wheel-account-badge {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.3px;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.wheel-account-0 {
  background: rgba(90, 140, 200, 0.12);
  color: var(--info);
  border: 1px solid rgba(90, 140, 200, 0.2);
}

.wheel-account-1 {
  background: rgba(80, 180, 130, 0.12);
  color: var(--success);
  border: 1px solid rgba(80, 180, 130, 0.2);
}

.wheel-account-2 {
  background: rgba(200, 170, 70, 0.12);
  color: var(--warning);
  border: 1px solid rgba(200, 170, 70, 0.2);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -10`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/PositionCard.tsx frontend/src/components/wheel/PositionCard.css
git commit -m "feat(wheel): add PositionCard component with CSP/CC styling"
```

---

## Task 5: ClosePositionDialog Component

**Files:**
- Create: `frontend/src/components/wheel/ClosePositionDialog.tsx`
- Create: `frontend/src/components/wheel/ClosePositionDialog.css`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/components/wheel/ClosePositionDialog.tsx
import { useState } from 'react'
import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './ClosePositionDialog.css'

interface ClosePositionDialogProps {
  position: WheelPosition
  ticker: string
  expiryDate: string
  onConfirm: () => void
  onCancel: () => void
}

export function ClosePositionDialog({
  position,
  ticker,
  expiryDate,
  onConfirm,
  onCancel,
}: ClosePositionDialogProps) {
  const [confirming, setConfirming] = useState(false)

  const handleConfirm = () => {
    setConfirming(true)
    onConfirm()
  }

  return (
    <div className="wheel-dialog-overlay" onClick={onCancel}>
      <div className="wheel-dialog" onClick={e => e.stopPropagation()}>
        <h3 className="wheel-dialog-title">Close Position</h3>
        <p className="wheel-dialog-subtitle">Buy to close this {position.type} contract</p>

        <div className="wheel-dialog-details">
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Ticker</span>
            <span className="wheel-dialog-value">{ticker}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Type</span>
            <span className="wheel-dialog-value">{position.type === 'CSP' ? 'Cash-Secured Put' : 'Covered Call'}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Strike</span>
            <span className="wheel-dialog-value">{formatCurrency(position.strike, 'USD')}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Expiry</span>
            <span className="wheel-dialog-value">{expiryDate}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Current Price</span>
            <span className="wheel-dialog-value">
              {position.currentPrice != null ? formatCurrency(position.currentPrice, 'USD') : '—'}
            </span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Unrealized P&L</span>
            <span className={`wheel-dialog-value ${(position.pnl ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'}`}>
              {position.pnl != null ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}` : '—'}
            </span>
          </div>
        </div>

        <div className="wheel-dialog-actions">
          <button className="wheel-dialog-btn wheel-dialog-btn-cancel" onClick={onCancel} disabled={confirming}>
            Cancel
          </button>
          <button className="wheel-dialog-btn wheel-dialog-btn-confirm" onClick={handleConfirm} disabled={confirming}>
            {confirming ? 'Closing...' : 'Close Position'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create styles**

```css
/* frontend/src/components/wheel/ClosePositionDialog.css */
.wheel-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.wheel-dialog {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 24px;
  width: 420px;
  max-width: 90vw;
}

.wheel-dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.wheel-dialog-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 20px;
}

.wheel-dialog-details {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}

.wheel-dialog-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wheel-dialog-label {
  font-size: 12px;
  color: var(--text-muted);
}

.wheel-dialog-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.wheel-dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.wheel-dialog-btn {
  padding: 8px 18px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--border);
  transition: background 0.2s;
}

.wheel-dialog-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.wheel-dialog-btn-cancel {
  background: transparent;
  color: var(--text-secondary);
}

.wheel-dialog-btn-cancel:hover:not(:disabled) {
  background: var(--bg-secondary);
}

.wheel-dialog-btn-confirm {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.wheel-dialog-btn-confirm:hover:not(:disabled) {
  background: var(--accent-hover);
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wheel/ClosePositionDialog.tsx frontend/src/components/wheel/ClosePositionDialog.css
git commit -m "feat(wheel): add ClosePositionDialog component"
```

---

## Task 6: WheelGrid Component

**Files:**
- Create: `frontend/src/components/wheel/WheelGrid.tsx`
- Create: `frontend/src/components/wheel/WheelGrid.css`

- [ ] **Step 1: Create the grid component**

```typescript
// frontend/src/components/wheel/WheelGrid.tsx
import type { WheelGridData, WheelPosition, DteUrgency } from '@/types/wheel'
import { getDteUrgency } from '@/types/wheel'
import { PositionCard } from './PositionCard'
import { formatCurrency } from '@/services/brokerService'
import './WheelGrid.css'

interface WheelGridProps {
  data: WheelGridData
  showAccount: boolean
  onPositionClick: (position: WheelPosition, ticker: string, expiryDate: string) => void
  onEmptySlotClick: (ticker: string) => void
}

export function WheelGrid({ data, showAccount, onPositionClick, onEmptySlotClick }: WheelGridProps) {
  return (
    <div className="wheel-grid-wrapper">
      <div className="wheel-grid-legend">
        <div className="wheel-legend-item">
          <div className="wheel-legend-swatch wheel-legend-csp" />
          <span>Cash-Secured Put (CSP)</span>
        </div>
        <div className="wheel-legend-item">
          <div className="wheel-legend-swatch wheel-legend-cc" />
          <span>Covered Call (CC)</span>
        </div>
        <div className="wheel-legend-item">
          <span className="wheel-legend-dashed">- - -</span>
          <span>Open slot</span>
        </div>
      </div>

      <div className="wheel-grid-container">
        <table className="wheel-grid-table">
          <thead>
            <tr>
              <th className="wheel-grid-expiry-header">Expiry</th>
              {data.tickers.map(t => (
                <th key={t.symbol} className="wheel-grid-ticker-header">
                  {t.symbol}
                  <div className="wheel-grid-ticker-price">
                    {t.currentPrice != null ? formatCurrency(t.currentPrice, 'USD') : '—'}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="wheel-grid-body">
            {data.expiryRows.map(row => {
              const urgency = getDteUrgency(row.dte)
              return (
                <tr key={row.expiryDate}>
                  <td className="wheel-grid-expiry-cell">
                    <div className="wheel-expiry-label">
                      {formatExpiryDate(row.expiryDate)}
                      <span className={`wheel-dte-badge wheel-dte-${urgency}`}>
                        {row.dte} DTE
                      </span>
                      <span className="wheel-expiry-sub">
                        {row.dayOfWeek}{row.isMonthly ? ' (Monthly)' : ''}
                      </span>
                    </div>
                  </td>
                  {data.tickers.map(t => {
                    const cell = row.cells[t.symbol]
                    const hasPositions = cell && cell.positions.length > 0
                    return (
                      <td key={t.symbol} className="wheel-grid-cell">
                        <div className="wheel-cell-content">
                          {hasPositions ? (
                            cell.positions.map(pos => (
                              <PositionCard
                                key={pos.id}
                                position={pos}
                                showAccount={showAccount}
                                onClick={p => onPositionClick(p, t.symbol, row.expiryDate)}
                              />
                            ))
                          ) : (
                            <div
                              className="wheel-empty-slot"
                              onClick={() => onEmptySlotClick(t.symbol)}
                              role="button"
                              tabIndex={0}
                              onKeyDown={e => { if (e.key === 'Enter') onEmptySlotClick(t.symbol) }}
                            >
                              +
                            </div>
                          )}
                        </div>
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr className="wheel-grid-totals-row">
              <td className="wheel-grid-expiry-cell">
                <span className="wheel-totals-label">Totals</span>
              </td>
              {data.tickers.map(t => {
                const totals = data.totals[t.symbol]
                return (
                  <td key={t.symbol} className="wheel-grid-cell">
                    <div className="wheel-totals-data">
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">Positions</span>
                        <span className="wheel-totals-item-value">{totals?.positionCount ?? 0}</span>
                      </div>
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">CSP Exposure</span>
                        <span className="wheel-totals-item-value">
                          {formatCurrency(totals?.cspExposure ?? 0, 'USD')}
                        </span>
                      </div>
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">P&L</span>
                        <span
                          className={`wheel-totals-item-value ${
                            (totals?.totalPnl ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'
                          }`}
                        >
                          {(totals?.totalPnl ?? 0) >= 0 ? '+' : ''}
                          {formatCurrency(totals?.totalPnl ?? 0, 'USD')}
                        </span>
                      </div>
                    </div>
                  </td>
                )
              })}
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  )
}

function formatExpiryDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
```

- [ ] **Step 2: Create grid styles (sticky header, scrollable body, sticky totals)**

```css
/* frontend/src/components/wheel/WheelGrid.css */

/* Legend */
.wheel-grid-legend {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  font-size: 11px;
  color: var(--text-muted);
}

.wheel-legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.wheel-legend-swatch {
  width: 14px;
  height: 14px;
  border-radius: 4px;
}

.wheel-legend-csp {
  background: linear-gradient(135deg, #1d2750, #263168);
  border: 1px solid rgba(100, 140, 220, 0.35);
}

html:not(.dark) .wheel-legend-csp {
  background: linear-gradient(135deg, #dde4f8, #e8edfb);
  border-color: rgba(80, 110, 180, 0.3);
}

.wheel-legend-cc {
  background: linear-gradient(135deg, #3d1f35, #4e2845);
  border: 1px solid rgba(200, 130, 170, 0.35);
}

html:not(.dark) .wheel-legend-cc {
  background: linear-gradient(135deg, #f4dde9, #f8e6ef);
  border-color: rgba(170, 90, 130, 0.3);
}

.wheel-legend-dashed {
  color: var(--text-muted);
  font-size: 14px;
  letter-spacing: 2px;
}

/* Grid container — scrollable body, sticky header + footer */
.wheel-grid-container {
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--card-bg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 280px);
}

.wheel-grid-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

/* Header */
.wheel-grid-table thead {
  position: sticky;
  top: 0;
  z-index: 10;
}

.wheel-grid-table thead th {
  padding: 12px 14px;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  background: var(--card-bg);
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border);
}

.wheel-grid-expiry-header {
  text-align: left !important;
  width: 150px;
  min-width: 150px;
}

.wheel-grid-ticker-header {
  min-width: 200px;
}

.wheel-grid-ticker-price {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 400;
  margin-top: 3px;
}

/* Body — scrollable */
.wheel-grid-body {
  overflow-y: auto;
}

.wheel-grid-table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

.wheel-grid-table tbody tr:hover td {
  background: rgba(100, 140, 220, 0.025);
}

/* Expiry label */
.wheel-expiry-label {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}

.wheel-expiry-sub {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
  margin-top: 3px;
}

/* DTE badges */
.wheel-dte-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
  margin-left: 6px;
}

.wheel-dte-critical {
  background: rgba(207, 92, 88, 0.2);
  color: var(--error);
  border: 1px solid rgba(207, 92, 88, 0.25);
}

.wheel-dte-warning {
  background: rgba(210, 150, 70, 0.2);
  color: var(--warning);
  border: 1px solid rgba(210, 150, 70, 0.25);
}

.wheel-dte-normal {
  background: rgba(200, 180, 70, 0.15);
  color: var(--warning);
  border: 1px solid rgba(200, 180, 70, 0.2);
}

.wheel-dte-safe {
  background: rgba(80, 180, 130, 0.15);
  color: var(--success);
  border: 1px solid rgba(80, 180, 130, 0.2);
}

.wheel-dte-far {
  background: rgba(80, 140, 200, 0.15);
  color: var(--info);
  border: 1px solid rgba(80, 140, 200, 0.2);
}

/* Cell content */
.wheel-cell-content {
  min-height: 42px;
}

/* Empty slot */
.wheel-empty-slot {
  min-height: 42px;
  border: 1px dashed var(--border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 18px;
  cursor: pointer;
  transition: all 0.2s;
}

.wheel-empty-slot:hover {
  border-color: var(--accent-secondary);
  color: var(--accent-secondary);
  background: rgba(84, 109, 132, 0.05);
}

/* Footer / Totals — sticky at bottom */
.wheel-grid-table tfoot {
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.wheel-grid-totals-row td {
  background: var(--card-bg) !important;
  border-top: 2px solid var(--border);
  padding: 12px 14px !important;
}

.wheel-totals-label {
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 500;
}

.wheel-totals-data {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.wheel-totals-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
}

.wheel-totals-item-label {
  color: var(--text-muted);
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  font-weight: 500;
}

.wheel-totals-item-value {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
  font-size: 12px;
  color: var(--text-primary);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -10`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/WheelGrid.tsx frontend/src/components/wheel/WheelGrid.css
git commit -m "feat(wheel): add WheelGrid timeline table with sticky header and totals"
```

---

## Task 7: WheelPage Component

**Files:**
- Create: `frontend/src/pages/WheelPage.tsx`
- Create: `frontend/src/pages/WheelPage.css`

- [ ] **Step 1: Create the page component**

```typescript
// frontend/src/pages/WheelPage.tsx
import { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
import { useWheelPositions, computeCapitalMetrics } from '@/hooks/useWheelPositions'
import { useDashboardCash } from '@/hooks/useDashboardWidgets'
import { CapitalSummary } from '@/components/wheel/CapitalSummary'
import { WheelGrid } from '@/components/wheel/WheelGrid'
import { ClosePositionDialog } from '@/components/wheel/ClosePositionDialog'
import type { WheelPosition } from '@/types/wheel'
import './WheelPage.css'

const WHEEL_TICKERS = ['SOXL', 'TECL', 'TQQQ', 'UPRO']

interface CloseDialogState {
  position: WheelPosition
  ticker: string
  expiryDate: string
}

export function WheelPage() {
  const navigate = useNavigate()
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | undefined>(undefined)
  const [closeDialog, setCloseDialog] = useState<CloseDialogState | null>(null)

  const { data: connectionsData } = useBrokerConnections()
  const connections = connectionsData?.connections ?? []
  const activeConnections = connections.filter(c => c.status === 'ACTIVE')

  const { gridData, isLoading, error } = useWheelPositions(WHEEL_TICKERS, selectedConnectionId)
  const { data: cashData } = useDashboardCash(selectedConnectionId)

  const capitalMetrics = useMemo(() => {
    if (!gridData) return null
    const cashBalance = cashData?.totalCashCAD ?? 0
    // Capital metrics are derived from the grid's position data
    // For now, compute from the grid totals
    const allPositions = gridData.expiryRows.flatMap(row =>
      Object.values(row.cells).flatMap(cell => cell.positions)
    )
    let deployedCsp = 0
    let ccsWritten = 0
    let totalPremium = 0
    let unrealizedPnl = 0

    allPositions.forEach(p => {
      if (p.type === 'CSP') {
        deployedCsp += p.strike * 100 * p.quantity
      } else {
        ccsWritten += p.strike * 100 * p.quantity
      }
      totalPremium += p.premium ?? 0
      unrealizedPnl += p.pnl ?? 0
    })

    return {
      availableCash: cashBalance,
      deployedCsp,
      sharesHeld: 0,
      ccsWritten,
      totalPremium,
      unrealizedPnl,
    }
  }, [gridData, cashData])

  const showAccount = selectedConnectionId === undefined

  const handlePositionClick = useCallback((position: WheelPosition, ticker: string, expiryDate: string) => {
    setCloseDialog({ position, ticker, expiryDate })
  }, [])

  const handleEmptySlotClick = useCallback((ticker: string) => {
    navigate(`/options?ticker=${encodeURIComponent(ticker)}`)
  }, [navigate])

  const handleCloseConfirm = useCallback(() => {
    // TODO: integrate with OrderExecutionService in a future task
    setCloseDialog(null)
  }, [])

  return (
    <div className="wheel-page">
      <div className="wheel-page-header">
        <h1 className="wheel-page-title">Wheel Strategy</h1>
        <p className="wheel-page-subtitle">Manage CSP and CC positions across your accounts</p>
      </div>

      {/* Account Tabs */}
      <div className="wheel-account-tabs">
        <button
          className={`wheel-account-tab ${selectedConnectionId === undefined ? 'wheel-account-tab-active' : ''}`}
          onClick={() => setSelectedConnectionId(undefined)}
        >
          All Accounts
        </button>
        {activeConnections.map(conn => (
          <button
            key={conn.id}
            className={`wheel-account-tab ${selectedConnectionId === conn.id ? 'wheel-account-tab-active' : ''}`}
            onClick={() => setSelectedConnectionId(conn.id)}
          >
            {conn.accountName || conn.broker?.name || 'Account'}
            {conn.accountNumber ? ` ${conn.accountNumber}` : ''}
          </button>
        ))}
      </div>

      <CapitalSummary metrics={capitalMetrics} />

      {isLoading && (
        <div className="wheel-loading">Loading positions...</div>
      )}

      {error && (
        <div className="wheel-error">Failed to load positions. Please try again.</div>
      )}

      {gridData && !isLoading && (
        <WheelGrid
          data={gridData}
          showAccount={showAccount}
          onPositionClick={handlePositionClick}
          onEmptySlotClick={handleEmptySlotClick}
        />
      )}

      {closeDialog && (
        <ClosePositionDialog
          position={closeDialog.position}
          ticker={closeDialog.ticker}
          expiryDate={closeDialog.expiryDate}
          onConfirm={handleCloseConfirm}
          onCancel={() => setCloseDialog(null)}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Create page styles**

```css
/* frontend/src/pages/WheelPage.css */
.wheel-page {
  padding: 24px;
  max-width: 1600px;
}

.wheel-page-header {
  margin-bottom: 20px;
}

.wheel-page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.3px;
  margin-bottom: 4px;
}

.wheel-page-subtitle {
  color: var(--text-muted);
  font-size: 13px;
}

/* Account tabs */
.wheel-account-tabs {
  display: flex;
  gap: 2px;
  background: var(--card-bg);
  padding: 3px;
  border-radius: 10px;
  width: fit-content;
  margin-bottom: 20px;
}

.wheel-account-tab {
  padding: 7px 16px;
  border: none;
  background: none;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.wheel-account-tab:hover {
  color: var(--text-secondary);
}

.wheel-account-tab-active {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* Loading & Error */
.wheel-loading,
.wheel-error {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  font-size: 14px;
  color: var(--text-muted);
}

.wheel-error {
  color: var(--error);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: No errors (may need to check `useDashboardCash` export name — adjust if the actual hook name differs)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/WheelPage.tsx frontend/src/pages/WheelPage.css
git commit -m "feat(wheel): add WheelPage with account tabs and grid"
```

---

## Task 8: Routing and Sidebar Integration

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/AppSidebar.tsx`

- [ ] **Step 1: Add lazy import and route in App.tsx**

Add the lazy import next to the other page imports (near line 38):
```typescript
const WheelPage = lazy(() => import('./pages/WheelPage').then(m => ({ default: m.WheelPage })))
```

Add the route inside the protected routes block (after the `options` route, near line 101):
```typescript
<Route path="wheel" element={<WheelPage />} />
```

- [ ] **Step 2: Add sidebar nav item in AppSidebar.tsx**

Import `RotateCw` from lucide-react (add to the existing import line):
```typescript
import { ..., RotateCw } from 'lucide-react'
```

Add the Wheel item to the Options section in the `navSections` array:
```typescript
{
  title: 'Options',
  items: [
    { to: '/options', icon: LineChart, label: 'Options Trading' },
    { to: '/wheel', icon: RotateCw, label: 'Wheel Strategy' },
  ],
},
```

- [ ] **Step 3: Verify the app compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -10`
Expected: No errors

- [ ] **Step 4: Verify lint passes**

Run: `cd frontend && npm run lint 2>&1 | tail -5`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/components/layout/AppSidebar.tsx
git commit -m "feat(wheel): add /wheel route and sidebar navigation"
```

---

## Task 9: OptionsPage Ticker Query Param Support

**Files:**
- Modify: `frontend/src/pages/OptionsPage.tsx`

- [ ] **Step 1: Add query param reading to OptionsPage**

At the top of the `OptionsPage` component, add `useSearchParams` and an effect to auto-load when a `ticker` param is present:

Add to imports:
```typescript
import { useSearchParams } from 'react-router-dom'
```

Inside the component, after existing hook calls:
```typescript
const [searchParams, setSearchParams] = useSearchParams()
```

Add a `useEffect` that triggers search when `ticker` query param is present on mount:
```typescript
useEffect(() => {
  const ticker = searchParams.get('ticker')
  if (ticker && !selectedUnderlying) {
    handleSearch(ticker)
    setSearchParams({}, { replace: true })
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [])
```

This runs once on mount. If a `?ticker=SOXL` param is present and no underlying is already selected, it auto-searches for that ticker and then clears the param from the URL.

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -10`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/OptionsPage.tsx
git commit -m "feat(options): support ?ticker= query param for pre-filling from Wheel page"
```

---

## Task 10: Google Fonts and Final Verification

**Files:**
- Modify: `frontend/index.html`

- [ ] **Step 1: Add Google Fonts link to index.html**

Add inside the `<head>` section of `frontend/index.html`:
```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
```

Inter is already available via the system font stack (`-apple-system`, `Segoe UI`). JetBrains Mono needs to be loaded for the numeric values.

- [ ] **Step 2: Run the full test suite**

Run: `cd frontend && npm run test:run 2>&1 | tail -15`
Expected: All tests PASS

- [ ] **Step 3: Run lint**

Run: `cd frontend && npm run lint 2>&1 | tail -5`
Expected: No errors

- [ ] **Step 4: Run build**

Run: `cd frontend && npm run build 2>&1 | tail -10`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/index.html
git commit -m "feat(wheel): add JetBrains Mono font for numeric display"
```

---

## Task 11: Manual Verification

- [ ] **Step 1: Start the dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Verify in browser**

Open http://localhost:3000/wheel and verify:
1. Page loads with "Wheel Strategy" header
2. Account tabs show "All Accounts" + connected broker accounts
3. Capital summary bar displays (values may be 0 if no positions)
4. Timeline grid renders with expiry rows and ticker columns
5. Grid body scrolls while header and totals stay fixed
6. Click an empty slot → navigates to `/options?ticker=SOXL` (or whichever column)
7. Click a position card (if any exist) → close dialog appears
8. Switch account tabs → grid filters correctly
9. Sidebar shows "Wheel Strategy" under Options section
10. Light and dark themes both look correct

---

## Documentation Updates

After all tasks are complete, update the following per CLAUDE.md:

- [ ] `docs/reference/frontend-map.md` — add WheelPage, WheelGrid, PositionCard, CapitalSummary, ClosePositionDialog, useWheelPositions, wheel.ts
- [ ] `docs/reference/api-endpoints.md` — no new endpoints, but note that `/wheel` page consumes existing position and chain endpoints
