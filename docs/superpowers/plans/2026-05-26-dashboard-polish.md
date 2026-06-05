# Dashboard Polish — Design Alignment Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the live dashboard with the approved Verdant Dark design mockup. Fix layout gaps, remove extra widgets, add missing elements.

**Architecture:** CSS and JSX changes only. No backend changes. Keep all React Query hooks, data fetching, and business logic unchanged.

**Tech Stack:** React 18, TypeScript, plain CSS custom properties, Zustand stores.

**Design Reference:** The finalized dashboard design from the brainstorming phase (`.superpowers/brainstorm/` content files, dashboard-screen-v8.html).

---

## Findings Summary

### Missing from current implementation
1. "All Accounts" aggregate card with emerald tint in the accounts strip
2. Masked account numbers (••XXXX) on account cards
3. Gain/loss percentage pill on account cards (only shows dollar amount)
4. Mobile: "Good morning" greeting + user avatar hero section
5. Mobile: proper 2x2 KPI grid (Investment + Returns top row, Cash + Buying Power bottom row)

### Extra in current implementation (not in design)
6. Fees & Commission widget — should be removed from dashboard layout
7. Dividend Calendar widget — should be removed from dashboard layout
8. Rebalancing Progress widget — should be removed from dashboard layout
9. Separate Orders widget — should be merged as a tab in Positions table

### Layout differences
10. Sector exposure uses pie chart — design spec calls for horizontal progress bars
11. Portfolio Summary card takes too much vertical space (huge empty area below KPI values)
12. Mobile: Portfolio Value is a separate hero section inside Portfolio Summary instead of being the page-level hero

---

## Tasks

### Task 1: Add "All Accounts" Aggregate Card to Accounts Strip

**Files:**
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.css`

- [ ] **Step 1: Read the current ConnectedAccountsWidget to understand data flow**

The widget receives connection data. We need to compute the aggregate total across all accounts.

- [ ] **Step 2: Add aggregate card as the first item in the strip**

Add a card before the individual account cards with:
- Label: "All Accounts" in `var(--accent-light)` (#6ee7b7)
- Total value: sum of all account values in C$ (large font, JetBrains Mono)
- Gain/loss: sum of all gains, percentage of total
- Background: `var(--accent-subtle)` (rgba(16,185,129,0.08))
- Border: `1px solid var(--accent-border)` (rgba(16,185,129,0.15))

- [ ] **Step 3: Add masked account number to each account card**

Display `••` + last 4 digits of account number in `var(--font-mono)` with `var(--text-muted)` color, next to the account type label.

- [ ] **Step 4: Add percentage pill to gain/loss display**

After the gain/loss amount (+$46,089), add a pill showing the percentage:
```css
.ca-gain-pct {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(16,185,129,0.1);  /* or danger variant for negative */
  color: var(--success-text);
}
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(ui): add All Accounts card, account numbers, gain % pill to dashboard strip"
```

---

### Task 2: Fix Mobile Dashboard Layout — Hero + 2x2 KPI Grid

**Files:**
- Modify: `frontend/src/components/dashboard/widgets/PortfolioSummaryWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/PortfolioSummaryWidget.css`

- [ ] **Step 1: Update mobile layout to show greeting hero**

On mobile (<768px), the top section should show:
- Left: "Good morning" (14px, muted), total portfolio value (26px, JetBrains Mono, bold), gain/loss amount + percentage pill
- Right: User avatar circle (40px, emerald gradient, initials)

Import `useAuthStore` to get user name for the greeting and initials.

- [ ] **Step 2: Fix 2x2 KPI grid on mobile**

The 4 KPI cards (Investment, Returns, Cash, Buying Power) should be in a `grid-template-columns: 1fr 1fr` layout on mobile with:
- Row 1: Investment (left) + Returns (right)
- Row 2: Cash (left) + Buying Power (right)

Currently the layout has Investment + Cash on row 1 and Buying Power orphaned. Reorder the items so Returns comes second.

- [ ] **Step 3: Remove "Portfolio Value" hero from inside the widget on mobile**

The current widget shows a centered "PORTFOLIO VALUE / C$ 0 / Day change unavailable" block on mobile. This should be replaced with the greeting hero from Step 1. The total value is now in the greeting, not duplicated inside the KPI grid.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(ui): add mobile greeting hero, fix 2x2 KPI grid order on dashboard"
```

---

### Task 3: Remove Extra Widgets from Dashboard Layout

**Files:**
- Modify: `frontend/src/components/dashboard/DashboardGrid.tsx`

- [ ] **Step 1: Read DashboardGrid.tsx to find the widget order array**

The `DASHBOARD_WIDGET_ORDER` array controls which widgets appear on the dashboard.

- [ ] **Step 2: Remove widgets not in the design spec**

Remove these from the dashboard layout order:
- `FEES_COMMISSION` — Fees & Commission widget
- `DIVIDEND_CALENDAR` — Dividend Calendar widget
- `REBALANCING_PROGRESS` — Rebalancing Progress widget

Keep:
- `CONNECTED_ACCOUNTS` (accounts strip)
- `PORTFOLIO_SUMMARY` (KPI row: Investment, Cash, Buying Power)
- `IRR` (Returns card)
- `SECTOR_EXPOSURE` (Sectors card)
- `POSITIONS_HOLDINGS` (Positions table with Holdings/Orders tabs)
- `ORDERS` — merge into the Positions table as a tab (or keep if already tabbed)

- [ ] **Step 3: Verify the grid layout with fewer widgets**

With only 5-6 widgets, the grid should be cleaner. The desktop layout should be:
- Row 1: Connected Accounts (span full width)
- Row 2: Portfolio Summary (span 3) + Returns (span 1) + Sectors (span 1)
- Row 3: Positions table (span full width)

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(ui): remove Fees, Calendar, Rebalancing widgets from dashboard per design spec"
```

---

### Task 4: Fix Sector Exposure to Use Horizontal Bars

**Files:**
- Modify: `frontend/src/components/dashboard/widgets/SectorExposureWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/SectorExposureWidget.css`

- [ ] **Step 1: Replace pie/donut chart with horizontal progress bars**

The design spec shows sector exposure as horizontal bars:
```
Tech     ████████████████░░░░░  42%
Finance  █████████░░░░░░░░░░░░  22%
Health   ██████░░░░░░░░░░░░░░░  15%
Other    █████░░░░░░░░░░░░░░░░  21%
```

Each bar:
- Label (left, `var(--text-secondary)`, 10px) + percentage (right, `var(--font-mono)`, 10px)
- Progress bar: 3px height, `var(--border)` track, emerald shades for fill
- Use different emerald shades: #10b981, #059669, #6ee7b7, #34d399

- [ ] **Step 2: Remove AG Charts dependency from this widget**

Replace the AG Charts pie chart with pure CSS horizontal bars. No chart library needed.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(ui): replace sector pie chart with horizontal progress bars per design spec"
```

---

### Task 5: Reduce Portfolio Summary Card Vertical Space

**Files:**
- Modify: `frontend/src/components/dashboard/widgets/PortfolioSummaryWidget.css`

- [ ] **Step 1: Remove excessive empty space below KPI values**

The current Portfolio Summary card has a huge empty area below the Investment/Cash/Buying Power values. This is because the card spans 3 columns and has min-height or flex-grow that creates wasted space.

Fix by:
- Removing any `min-height` on the card
- Setting the card content to `align-content: start` so content hugs the top
- Ensuring the card height is determined by its content, not by the grid row height

- [ ] **Step 2: Commit**

```bash
git commit -m "fix(ui): reduce Portfolio Summary card vertical space on dashboard"
```

---

### Task 6: Visual Verification

- [ ] **Step 1: Start dev server and test at 1440px desktop**

Verify:
- "All Accounts" emerald card is first in the strip
- Account cards show ••XXXX numbers and gain % pills
- 5-column KPI row: Portfolio Summary (3 cols) + Returns + Sectors
- No Fees, Calendar, or Rebalancing widgets
- Sector exposure shows horizontal bars
- Positions table is visible near the top (not buried under extra widgets)

- [ ] **Step 2: Test at 375px mobile**

Verify:
- "Good morning" greeting with avatar at top
- 2x2 KPI grid (Investment + Returns / Cash + Buying Power)
- No duplicate "Portfolio Value" hero
- Stacked account cards with ••numbers and % pills
- Bottom tab bar visible

- [ ] **Step 3: Run build and lint**

```bash
cd frontend && npm run build && npm run lint
```

- [ ] **Step 4: Commit if any fixes needed**

```bash
git commit -m "fix(ui): dashboard visual polish after verification"
```
