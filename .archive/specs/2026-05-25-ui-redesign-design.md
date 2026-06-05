# UI Redesign — Verdant Dark Design System

## Overview

Complete UI redesign of the portfolio construction app with a modern fintech aesthetic. Replaces the current warm brown/cool blue theme with a new emerald-on-dark design system. Mobile-first, responsive across phone, tablet, and desktop.

## Design Direction

- **Style:** Modern Fintech — clean, spacious, approachable yet professional
- **Theme:** Dark mode default (light mode supported via toggle)
- **Motion:** Subtle and purposeful — smooth transitions on modals/drawers, gentle hover effects, loading skeletons. No decorative animation.
- **Color strategy:** Restrained — tinted neutrals with emerald accent

## Design System Foundation

### Typography

| Role | Font | Usage |
|------|------|-------|
| Primary | DM Sans (variable, Google Fonts) | Headings, body, UI labels |
| Monospace | JetBrains Mono | Financial numbers, prices, tickers, percentages, table data |

**Scale:** 12 / 13 / 14 / 16 / 20 / 24 / 32 / 40px (1.25 ratio)
**Body:** 14px, line-height 1.6

### Color Palette — Dark Mode

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-primary` | `#0a0f1a` | Page background |
| `--bg-secondary` | `#111827` | Cards, panels |
| `--bg-tertiary` | `#1a2332` | Elevated surfaces, hover states |
| `--bg-surface` | `#0f1520` | Sidebar, header |
| `--text-primary` | `#e2e8f0` | Headings, primary content |
| `--text-secondary` | `#94a3b8` | Labels, descriptions |
| `--text-muted` | `#64748b` | Placeholders, disabled |
| `--accent` | `#10b981` | Primary actions, positive values, active states |
| `--accent-hover` | `#059669` | Accent hover |
| `--accent-light` | `#6ee7b7` | Accent on dark surfaces |
| `--accent-subtle` | `rgba(16,185,129,0.08)` | Tinted card backgrounds |
| `--accent-border` | `rgba(16,185,129,0.15)` | Accent-tinted borders |
| `--border` | `rgba(255,255,255,0.06)` | Default borders |
| `--border-hover` | `rgba(255,255,255,0.1)` | Hover borders |
| `--error` | `#f87171` | Negative values, errors |
| `--warning` | `#fbbf24` | Warnings |
| `--info` | `#60a5fa` | Info, secondary accent |
| `--csp` | `#6366f1` (indigo) | Cash-Secured Put positions |
| `--cc` | `#f97316` (orange) | Covered Call positions |

### Color Palette — Light Mode

| Token | Value |
|-------|-------|
| `--bg-primary` | `#f8fafb` |
| `--bg-secondary` | `#ffffff` |
| `--bg-tertiary` | `#f1f5f9` |
| `--text-primary` | `#0f172a` |
| `--text-secondary` | `#475569` |
| `--accent` | `#059669` (darkened for contrast) |
| `--border` | `#e2e8f0` |

### Spacing

4px base unit: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64

### Border Radius

- 6px — inputs, small elements
- 10px — cards
- 12px — panels, modals
- 16px — large containers
- 9999px — pills, avatars

### Elevation

No box-shadows in dark mode. Use border + background opacity layering for depth.

### Motion Tokens

- `--transition-fast`: 150ms ease-out (hover, active states)
- `--transition-base`: 200ms ease-out (panels, dropdowns)
- `--transition-slow`: 300ms ease-out (modals, page transitions)
- Easing: `cubic-bezier(0.16, 1, 0.3, 1)` (expo-out) for entries

### Brokerage Brand Colors

| Broker | Icon | Background | Text |
|--------|------|-----------|------|
| Questrade | Q | `#1a5c3a` | `#4ade80` |
| Wealthsimple | W | `#1a1a3a` | `#a78bfa` |
| Interactive Brokers | IB | `#3a1a1a` | `#f87171` |

### Currency Display

- Use `C$` for Canadian dollars, `US$` for US dollars
- Dual-currency cards show total in base currency (C$) as primary, with C$ and US$ breakdown below a divider line

## Navigation Architecture

### Desktop (1024px+)

- **Icon rail** (56px fixed) with emerald logo, nav icons, theme toggle, avatar
- **Flyout panel** (200px) appears on hover — shows sub-navigation, account balances
- **Active state:** Emerald left-edge indicator (2px) + tinted background on rail icon

### Tablet (768px–1023px)

- **Icon rail** (52px) always visible
- **Flyout** is tap-triggered (not hover), slides over as overlay
- Two-column metric grids, full-width tables

### Mobile (<768px)

- **No sidebar** — bottom tab bar with 5 items: Home, Accounts, Screener, Options, More
- **"More" tab** opens a bottom sheet for overflow: Wheel Strategy, Connections, Reporting, Admin, Profile
- Limited to 5 bottom tabs per Material Design guidelines

## Screen Designs

### 1. Dashboard

**Desktop:**
- Connected accounts horizontal strip at top (broker icon, account type, account number ••XXXX, value in C$/US$, gain/loss amount + %)
- 5-column KPI row: Investment (C$ total + C$/US$ breakdown), Cash (same), Buying Power (same), Returns (total gain in C$ + ROI% + IRR%), Sectors (horizontal bar chart)
- Full-width positions table with Holdings/Orders tabs, columns: Symbol, Market Value, Qty, Avg Cost, Price, P&L, Weight

**Mobile:**
- Hero: total portfolio value (C$) + gain/loss
- 2x2 KPI grid (Investment, Returns, Cash, Buying Power — no horizontal scrolling)
- Stacked account cards with broker icon + account number + value + gain/loss
- Compact holdings list with ticker badges

### 2. Broker Connections

**Desktop:**
- Available Brokers grid at top (primary action area) — clicking a broker card initiates connection
- Broker cards show connection state: green dot + "1 Account Connected" or amber "Reconnect"
- Connected Accounts list below with broker icon, status dot, account number, sync time, value, Sync/More actions
- No separate "Connect Broker" button

**Mobile:**
- 3-column equal-width broker grid at top (flex:1 min-width:0)
- Stacked account cards below
- Error state: amber border + inline Reconnect pill

### 3. Account Detail

**Desktop:**
- Breadcrumb navigation (Accounts > TFSA)
- Account header with broker icon, account type, number, status dot, sync time
- 4-column KPI row: Total Value (emerald tint), Investment (C$/US$ breakdown), Cash (breakdown), Returns (ROI/IRR)
- Tabbed content: Positions / Activities / Dividends

**Mobile:**
- Back arrow + breadcrumb
- Broker icon + account header
- 2x2 KPI grid (Total Value + Returns top row)
- Tabbed compact position list

### 4. Options Trading

**Desktop:**
- Integrated search bar + "Load Chain" button + Live status indicator in header
- Full-width quote bar (symbol, price, bid, ask, spread, volume, change)
- Strategy selector pills (Custom, Bull Call, Bear Put, Iron Condor, Straddle)
- Two-column layout (55% chain / 45% sidebar):
  - Chain: 7-column bidirectional (Bid, Ask, Delta | Strike | Delta, Bid, Ask). ATM row highlighted with emerald border. Bid in green, ask in red.
  - Sidebar: Leg builder cards (3-column: Strike, Mid, Expiry) + P&L results (2x2 metrics + payoff chart + breakeven)

**Mobile:**
- Search bar + compact quote
- Strategy dropdown + Expiry dropdown side by side (no horizontal scroll)
- Calls/Puts segmented toggle (show one side at a time)
- 4-column chain (Strike, Bid, Ask, Delta)
- Floating "N Legs Selected" + Calculate bar
- P&L results as bottom sheet with drag handle: leg summary cards, payoff diagram with shaded zones, 2x2 metrics (Max Profit green-tinted, Max Loss red-tinted), breakeven row

### 5. Wheel Strategy

**Desktop:**
- Legend in header: CSP (indigo swatch), Covered Call (orange swatch), Open Slot (dashed)
- Account tab pills
- Capital summary bar: Available Cash (C$/US$ breakdown), CSP Deployed, CCs Written, Premium, Unrealized P&L
- Grid: Expiry rows x Ticker columns. Position tiles colored by type (indigo = CSP, orange = CC). DTE badges (red 0-5d, yellow 6-20d). Emerald "+" button on every tile corner for adding positions. Empty cells show dashed "+" button. Totals row.

**Mobile:**
- Header "+" button (40px emerald, no FAB blocking content)
- Legend below title (CSP, CC, Open)
- Account dropdown
- Capital summary 2x2 grid
- Positions grouped by expiry date — section headers with DTE badges, position cards colored by type (indigo/orange borders)

### 6. Admin Panel

**Desktop:**
- 6-column summary stats (Total Instruments, Enriched with coverage %, Pending, Daily Quota with progress bar, Exchanges, Last Run status)
- Instrument type breakdown in 6-column grid
- Two-column bottom: Workflows (Exchange Sync + Full Ingestion with Run buttons) alongside Recent Runs (expandable rows)

**Mobile:**
- 2x2 summary grid (consolidated)
- Stacked workflow cards with Run buttons
- Recent Runs as expandable list
- Accessed via "More" tab (admin-only)

## Component Patterns

### Cards
- Background: `--bg-secondary` (#111827)
- Border: `--border` (rgba(255,255,255,0.06))
- Border radius: 10px
- Padding: 14-16px
- No box shadow

### Buttons
- Primary: `--accent` background (#10b981), white text, 8px radius
- Secondary: transparent background, `--border` border, `--text-secondary` text
- Destructive: `--error` background for dangerous actions

### Tables / Data Grids
- AG Grid themed to match: `--bg-secondary` background, `--border` row separators
- Headers: uppercase, 10-11px, `--text-muted`, letter-spacing 0.5px
- Data: JetBrains Mono for numbers, 12-14px
- Row hover: `--bg-tertiary`

### Dropdowns (Mobile)
- Used instead of horizontal-scrolling pills for Strategy, Expiry, Account selection
- Shows current value + chevron icon
- Opens native select or bottom sheet

### Bottom Sheets (Mobile)
- Slide up with drag handle (36px wide, 4px tall, rounded, centered)
- Background dims (overlay)
- Dismissible via swipe down or X button
- Used for: P&L results, "More" navigation, leg details

### Status Indicators
- Green dot (6-8px): Active, Completed, Connected
- Amber dot: Needs Reconnection, Warning
- Red dot: Error, Failed
- DTE badges: Red (0-5d urgent), Yellow (6-20d medium), Green (21+d safe)

## Responsive Breakpoints

| Breakpoint | Layout | Navigation |
|-----------|--------|------------|
| <768px | Single column, 2x2 grids, stacked cards | Bottom tab bar (5 items) |
| 768-1023px | 2-column grids, full-width tables | Icon rail (52px) + tap flyout |
| 1024px+ | 4-5 column grids, side panels | Icon rail (56px) + hover flyout |

## Fonts to Load

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,100..1000;1,9..40,100..1000&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
```

## Implementation Notes

- All CSS uses custom properties — no Tailwind (per project constraint)
- Component styles in companion `.css` files
- AG Grid themed via `ag-theme-quartz` class overrides
- Lucide React icons (already in use)
- `prefers-reduced-motion` respected — disable transitions when set
- `prefers-color-scheme` can inform initial theme, user toggle overrides
- No horizontal scrolling on mobile screens — use dropdowns or wrapping layouts
