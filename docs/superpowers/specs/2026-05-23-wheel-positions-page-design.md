# Wheel Positions Page — Design Spec

## Context

The wheel strategy (selling CSPs, getting assigned, selling CCs, getting called away, repeat) requires
a dedicated view to monitor open options positions across multiple accounts and expiry dates.
Today, options positions are scattered across the generic positions table with no wheel-specific
organisation. This page gives the user a single management hub to see coverage, spot gaps,
and act on positions — organised the way a wheel writer thinks: by expiry first, then by ticker.

## Page Overview

A new page at `/wheel` accessible from the sidebar under the "Options" section.
Displays a **timeline grid** of all open CSP and CC positions across a 90-day window,
with expiry dates on the Y-axis and tickers on the X-axis.

### Visual Reference

See mockup: `.superpowers/brainstorm/13920-1779511337/content/timeline-layout-v4.html`

---

## Layout (top to bottom)

### 1. Account Tabs

Horizontal tab bar at the top of the page.

- **"All Accounts"** tab (default) — consolidated view across all broker connections
- **One tab per broker connection** — e.g., "IBKR Margin", "IBKR TFSA", "Questrade"
- Tabs sourced from existing `useBrokerConnections()` hook
- Switching tabs filters the entire page: grid, capital bar, and totals

### 2. Capital Summary Bar

Horizontal bar showing key capital metrics for the selected account scope.

| Metric | Description |
|--------|-------------|
| Available Cash | Cash available to deploy as new CSPs |
| Deployed (CSPs) | Total capital at risk from open CSPs (sum of strike x 100 x contracts) |
| Shares Held | Value of shares held that could have CCs written against them |
| CCs Written | Notional value of shares covered by open CCs |
| Total Premium | Sum of premium collected on all open positions |
| Unrealized P&L | Mark-to-market P&L across all open positions |

All monetary values use `JetBrains Mono` font for alignment and readability.

### 3. Legend

Inline legend showing:
- Blue/indigo swatch = Cash-Secured Put (CSP)
- Pink/plum swatch = Covered Call (CC)
- Dashed border = Open slot (click to add)
- Account badge colours (consolidated view only)

### 4. Timeline Grid (core view)

An HTML table (not AG Grid — cards don't fit AG Grid's cell model).

#### Y-Axis: Expiry Dates

- All available option expiry dates within the next 90 days
- Sorted nearest-first (ascending by date)
- Each row shows:
  - Date (e.g., "May 30")
  - DTE badge with urgency colouring:
    - 0-10 DTE: coral/red (critical)
    - 11-21 DTE: amber (warning)
    - 22-45 DTE: yellow-green (normal)
    - 46-70 DTE: green (safe)
    - 71-90 DTE: blue (far)
  - Day of week + "(Monthly)" indicator for monthly expiries

#### X-Axis: Tickers

- Tickers configured for wheel writing
- Header shows ticker symbol + current underlying price
- Price from existing market data services

#### Cells

Each cell (expiry x ticker) contains either:

**Position card(s)** — one card per open position at that expiry/ticker:
- Colour indicates type: blue/indigo gradient = CSP, pink/plum gradient = CC
- Thick left border accent reinforces the type
- Labeled data rows:
  - **Strike** — strike price (prominent, 13px)
  - **Premium** — premium collected when sold
  - **P&L** — unrealized P&L, green/coral colour
  - **OTM** — moneyness percentage (distance from current underlying price)
- Multiple positions in the same cell stack vertically (e.g., two CSPs at different strikes)

**Empty slot** — dashed border with "+" icon:
- Indicates a gap where a position could be opened
- Hover effect highlights the slot

#### Consolidated View ("All Accounts")

When "All Accounts" tab is selected, each position card shows an **account badge** at the top:
- Colour-coded by account (e.g., blue for IBKR Margin, green for IBKR TFSA, amber for Questrade)
- Badge shows account name text

When a specific account tab is selected, no badge is shown.

#### Scrolling Behaviour

- **Table header** (ticker row): sticky at the top
- **Table body** (expiry rows): scrollable vertically within a fixed-height container
- **Totals row**: sticky at the bottom, always visible regardless of scroll position

#### Totals Row (per ticker column)

Each ticker column shows:
- **Positions** — count of open positions
- **CSP Exposure** — total capital at risk from CSPs for this ticker
- **P&L** — total unrealized P&L, colour-coded

---

## Interactions

### Click Position Card

Opens a confirmation dialog to close the position (buy-to-close).
- Dialog shows: ticker, strike, expiry, current bid/ask, estimated cost to close
- Confirm submits a buy-to-close market order via existing `OrderExecutionService`
- Future: rolling to a new expiry/strike (not in initial scope)

### Click Empty Slot (+)

Navigates to the existing Options page (`/options`) with the ticker pre-filled based on the column.
The user places their order from that existing screen.

Navigation: `navigate('/options', { state: { ticker: '<ticker>' } })` or query param.

### Account Tab Switching

- Filters all position data to the selected account
- Updates capital summary bar values
- Updates totals row
- "All Accounts" shows positions from all connections with account badges

---

## Data Sources

All data comes from existing backend infrastructure — no new API endpoints required for the initial version.

### Positions

- **Source**: `GET /api/v1/brokers/positions` (aggregated) or `GET /api/v1/brokers/connections/{connectionId}/positions` (per account)
- **Filter**: `instrumentType = OPTION`, `optionType IN ('CALL', 'PUT')`
- **Fields used**: symbol, strikePrice, expirationDate, optionType, underlyingSymbol, currentPrice, currentValue, totalPnl, quantity
- **Hook**: existing `useAggregatedPositions()` and `useConnectionPositions(connectionId)`

### Wheel Ticker List

- **Source**: existing `WheelConfig` / `WheelAccount` from `GET /api/v1/strategies/wheel/config`
- Provides the list of tickers to display as columns

### Underlying Prices

- **Source**: `GET /api/v1/market-data/quotes/{symbol}` or WebSocket quote subscription
- **Hook**: existing `useMarketDataWebSocket()`

### Expiry Dates

- Derived from positions data + available expiry calendar
- **Source for full expiry list**: `GET /api/v1/chains/{underlying}` returns available expirations
- Filter to next 90 calendar days

### Account List

- **Source**: existing `useBrokerConnections()` hook
- Provides connection IDs, account names, broker names

### Capital Metrics

- Available Cash: from `GET /api/v1/brokers/connections/{id}/balances` or dashboard balance data
- Deployed/Shares/CCs: computed client-side from positions data
  - CSP Exposure = sum(strike x 100 x contracts) for all open CSPs
  - Shares Held = sum(currentValue) for underlying stock positions in wheel tickers
  - CCs Written = sum(strike x 100 x contracts) for all open CCs

---

## Frontend Architecture

### New Files

| File | Purpose |
|------|---------|
| `pages/WheelPage.tsx` | Page component, data fetching, account tab state |
| `pages/WheelPage.css` | Page-level styles |
| `components/wheel/WheelGrid.tsx` | Timeline grid table component |
| `components/wheel/WheelGrid.css` | Grid styles (cards, cells, sticky behaviour) |
| `components/wheel/PositionCard.tsx` | Individual position card component |
| `components/wheel/CapitalSummary.tsx` | Capital metrics bar component |
| `hooks/useWheelPositions.ts` | Hook to fetch, filter, and organise positions into grid structure |

### Modified Files

| File | Change |
|------|--------|
| `App.tsx` | Add lazy import and route for WheelPage |
| `components/layout/AppSidebar.tsx` | Add "Wheel" nav item under Options section |

### Data Flow

```
WheelPage
  ├── useBrokerConnections()        → account tabs
  ├── useWheelPositions(connectionId?)  → filtered & grouped positions
  │     ├── useAggregatedPositions() or useConnectionPositions()
  │     ├── filters to OPTION type, CSP/CC only
  │     └── groups by (expiryDate, underlyingSymbol) → grid structure
  ├── market data quotes             → underlying prices, OTM calculation
  └── balance data                   → capital summary metrics
```

### Grid Data Structure

```typescript
interface WheelGridData {
  tickers: WheelTicker[]              // x-axis columns
  expiryRows: WheelExpiryRow[]        // y-axis rows
  totals: Record<string, TickerTotals>  // per-ticker summary
}

interface WheelTicker {
  symbol: string
  currentPrice: number
}

interface WheelExpiryRow {
  expiryDate: string                  // ISO date
  dte: number
  dayOfWeek: string
  isMonthly: boolean
  cells: Record<string, WheelCell>    // keyed by ticker symbol
}

interface WheelCell {
  positions: WheelPosition[]          // 0 or more
}

interface WheelPosition {
  id: number
  type: 'CSP' | 'CC'
  strike: number
  premium: number
  currentPrice: number
  pnl: number
  otmPercent: number
  accountName?: string                // only in consolidated view
  accountId?: number
  connectionId: number
}

interface TickerTotals {
  positionCount: number
  cspExposure: number
  totalPnl: number
}
```

---

## Styling

- **Font**: Inter for UI labels, JetBrains Mono for all numeric values
- **Theme**: Dark palette — background #151720, surfaces #1c1f2b, borders #252836
- **Text**: body #c8cdd5, labels #6b7385, headings #e2e6ed
- **P&L**: positive #5ec49e, negative #e0716f
- **CSP cards**: indigo gradient (#1d2750 → #263168), left border rgba(100,150,230,0.6)
- **CC cards**: plum gradient (#3d1f35 → #4e2845), left border rgba(200,130,170,0.6)
- **DTE badges**: muted outlined style (not solid fills)
- **Account badges**: subtle tinted backgrounds matching account colour
- **Empty slots**: dashed #303446 border, hover highlight
- **No Tailwind** — plain CSS with CSS custom properties, companion `.css` files

---

## Scope Boundaries

### In scope (initial version)
- Timeline grid with all expiry dates in 90-day window
- Position cards with Strike, Premium, P&L, OTM
- Account tabs with consolidated + per-account views
- Capital summary bar
- Click-to-close on position cards
- Click empty slot navigates to Options page with ticker
- Sticky header, scrollable body, sticky totals

### Out of scope (future iterations)
- Position rolling (roll to new expiry/strike)
- Wheel automation rules (auto-roll at X DTE)
- P&L history / closed positions log
- Annualized yield calculation on cards
- Greeks (delta, theta) on cards
- Recommendations engine suggesting optimal strikes
- Dashboard widget version of the grid

---

## Verification

1. Navigate to `/wheel` — page loads with account tabs and grid
2. Account tabs switch correctly — positions filter, capital bar updates
3. All option expiry dates within 90 days appear as rows
4. Positions appear in correct cells (expiry x ticker)
5. Multiple positions stack in same cell
6. Consolidated view shows account badges, single account view does not
7. Grid body scrolls while header and totals remain sticky
8. Click position card → close position action triggers
9. Click empty slot → navigates to `/options` with correct ticker
10. Capital summary values are correct for selected account scope
11. `npm run build` succeeds
12. `npm run lint` passes
13. `npm run test:run` passes
