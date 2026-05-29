# Wheel Strategy — Screen 1: Positions & Financials

## Overview

Redesign of the Wheel Strategy landing page (`/wheel`). Replaces the current expiry×ticker grid with a ticker-first calendar grid, dynamic ticker discovery, and covered call opportunity detection.

**Mockup reference:** `.superpowers/brainstorm/51960-1780073119/content/option-a-final.html`

## Navigation Flow (3-Screen Architecture)

```
Screen 1: Positions (/wheel)
  ├── Click position card → Screen 3: Order (close existing, pre-filled)
  └── Click "+" or "Sell CC" → Screen 2: Quotes (browse strikes for ticker/expiry)
                                  └── Select strike → Screen 3: Order (new position)
```

## Layout Structure

### Desktop (1024px+)
- AccountNavBar (kept as-is, pill tabs)
- Page title: "Wheel Strategy" (22px, font-weight 700)
- 5 KPI cards in a single row (`grid-template-columns: repeat(5, 1fr)`, gap 12px)
- Timeline navigator with ← → arrows and "Today" button
- Calendar grid: tickers on Y-axis (rows), 4 weekly expiry columns on X-axis
- Legend bar + "Add Ticker" row at bottom

### Mobile (< 768px)
- AccountNavBar mobile dropdown (trigger + dots, as-is)
- 5 KPI cards: 2×2 grid + 1 full-width CC row
- Timeline navigator (compact)
- Same calendar grid but 2 weekly expiry columns
- Bottom tab bar (as-is)

## KPI Cards (5 total)

| # | Label | Value | Breakdown |
|---|-------|-------|-----------|
| 1 | Capital Available | C$ total buying power | C$ / US$ split |
| 2 | Capital Deployed | US$ total | CSP / CC split |
| 3 | CC Available | N contracts (orange) | Per-ticker breakdown |
| 4 | Premium & P&L | Net P&L (green/red) | Premium collected / Unrealized split |
| 5 | Positions | Total count | CSP / CC / Expiring count |

Styled using existing `KpiCard` component pattern: `--bg-secondary` background, `--border` border, `--radius-md` radius, 16px padding, 11px uppercase label, 18px mono value, 1px divider, 10px/11px breakdown rows.

## Calendar Grid

### Axes
- **Y-axis (rows):** Tickers — dynamically discovered from account positions (not hardcoded)
- **X-axis (columns):** Weekly Friday expiry dates
  - Desktop: 4 columns (4-week window)
  - Mobile: 2 columns (2-week window)

### Timeline Navigation
- ← → buttons shift the window by the column count (4 weeks desktop, 2 weeks mobile)
- "Today" button resets to the window starting at the current week
- Date range label shows "May 29 — Jun 19, 2026" format
- Columns with expiring-today positions get a red tint (`rgba(248,113,113,0.03)`)

### Expiry Column Headers
- Date: 12px font-weight 600
- DTE badge: colored by urgency (0-5d red, 6-13d yellow, 14d+ green)
- Day of week: 9px muted
- Monthly expiry badge (3rd Friday): indigo "Monthly" pill

### Ticker Column (sticky left)
- Ticker name: 13px font-weight 700
- Current price: 10px mono `--text-secondary`
- Total exposure: 10px mono `--text-secondary`, in base currency (US$ for US tickers, C$ for .TO/.TSX)
- For tickers with 100+ shares: shares count + orange CC badge ("▲ N CC")

### Position Cards
- CSP: `--csp-bg` background, `--csp-border` border, `--indigo-text` strike color
- CC: `--cc-bg` background, `--cc-border` border, `--orange-text` strike color
- Each card shows: strike + type (e.g., "$100 Put"), OTM%, P&L
- Click → navigates to Screen 3 (Order) with position pre-filled for closing

### Empty Slots
- CSP: dashed border `rgba(255,255,255,0.06)`, "+" icon, hover indigo
- CC opportunity: dashed border `rgba(249,115,22,0.15)`, "Sell CC" / "+ CC" label, hover orange
- Click → navigates to Screen 2 (Quotes) for that ticker + expiry

### "+ Add Ticker" Row
- Full-width dashed row at bottom of grid
- Opens ticker search to add a new ticker to the wheel watchlist

## Dynamic Ticker Discovery

### Current behavior (to replace)
Hardcoded `WHEEL_TICKERS = ['SOXL', 'TECL', 'TQQQ', 'UPRO']`.

### New behavior
1. Fetch all positions from selected account(s)
2. Show all tickers that have **options positions** (puts or calls)
3. Show all tickers where user owns **100+ shares** (CC-eligible), even without options
4. CC-eligible tickers show share count and available contracts (`Math.floor(shares / 100)`)
5. "Add Ticker" allows manually adding tickers not currently held

### Data source
Same `useWheelPositions` hook but remove the ticker filter. Add stock position detection from `getAggregatedPositions()` or `getConnectionPositions()` to find CC-eligible tickers.

## Responsive Behavior

| Aspect | Desktop (1024+) | Mobile (< 768px) |
|--------|-----------------|-------------------|
| Account nav | Pill tabs | Dropdown + dots |
| KPI cards | 5 columns | 2×2 + 1 full-width |
| Grid columns | 4 weekly expiries | 2 weekly expiries |
| Timeline shift | 4 weeks per click | 2 weeks per click |
| Ticker column | 120px sticky | 85px sticky |
| Position cards | 7px padding, 11px strike | 5px padding, 10px strike |
| CC slot labels | "+ Sell CC" | "+ CC" |
| Bottom nav | Icon rail | Bottom tab bar |

## Theme Tokens Used

All values from `index.css` dark theme. No custom colors — uses existing CSS custom properties:
- Backgrounds: `--bg-primary`, `--bg-secondary`
- Text: `--text-primary`, `--text-secondary`, `--text-muted`
- CSP: `--csp`, `--csp-bg`, `--csp-border`
- CC: `--cc`, `--cc-bg`, `--cc-border`
- Success/Danger: `--success-text` (#6ee7b7), `--danger-text` (#f87171)
- Font: `--font-mono` for all numeric values
- Radius: `--radius-sm` (6px), `--radius-md` (10px)

## Backend Changes

**None for Screen 1.** All data comes from existing APIs:
- Positions: `useWheelPositions` (remove hardcoded ticker filter)
- Cash/buying power: `useDashboardCash`
- Accounts: `useDashboardAccounts`
- FX rate: `useExchangeRate`
- Activities/premium: `useWheelActivities`
- Stock quotes: `useQueries` with `getQuote`

New frontend logic needed:
- Detect CC-eligible tickers (stock positions with qty >= 100)
- Compute total exposure per ticker in base currency
- Weekly expiry column generation with calendar navigation state

## Files to Change

| File | Action |
|------|--------|
| `frontend/src/pages/WheelPage.tsx` | Rewrite — new layout, dynamic tickers, calendar state |
| `frontend/src/pages/WheelPage.css` | Rewrite — new grid layout, responsive breakpoints |
| `frontend/src/components/wheel/WheelGrid.tsx` | Rewrite — ticker rows × expiry columns, CC slots |
| `frontend/src/components/wheel/WheelGrid.css` | Rewrite — calendar grid styling |
| `frontend/src/components/wheel/PositionCard.tsx` | Simplify — remove premium, show exposure in P&L only |
| `frontend/src/components/wheel/PositionCard.css` | Update — streamlined card |
| `frontend/src/components/wheel/CapitalSummary.tsx` | Replace with 5 KPI cards using existing KpiCard pattern |
| `frontend/src/components/wheel/CapitalSummary.css` | Remove (replaced by KpiCard) |
| `frontend/src/hooks/useWheelPositions.ts` | Modify — remove hardcoded ticker filter, add CC detection |
| `frontend/src/types/wheel.ts` | Update — add CC-eligible fields, calendar types |
