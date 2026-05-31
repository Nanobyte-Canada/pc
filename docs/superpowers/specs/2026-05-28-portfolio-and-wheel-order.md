# Portfolio View + Wheel Order Flow — Design Spec

## 1. Portfolio View (replaces Dashboard + Account Detail)

### Navigation Changes
- Rename "Dashboard" → "Portfolio" in nav (icon rail + bottom tab bar)
- Remove Screener and Options from primary nav
- Add Wheel Strategy to nav (target icon)
- Rename "Accounts" → "Connections" (link icon)
- Nav order: Portfolio | Wheel | Connections | (bottom: Theme, Admin, Profile)
- Mobile tab bar: Portfolio | Wheel | Connections | More

### Account Switcher
- **Desktop:** Pill tabs in header row next to "Portfolio" title. "All Accounts" first, then individual accounts with broker icon + type + masked ••number. Active tab has emerald background.
- **Tablet:** Dropdown instead of pill tabs (less horizontal space).
- **Mobile:** Dropdown at top center ("ALL ACCOUNTS" or "TFSA ••0190" with chevron). Dot indicators below. Tapping opens bottom sheet to select account.

### 4 Standardized KPI Cards (same for aggregate and individual)

**Card 1 — Total Value / Investment / Cash (combined):**
- Total Value: large C$ + smaller US$ below
- Divider
- Investment (left) + Cash (right) side by side, each C$ + smaller US$
- Total Value = Investment + Cash

**Card 2 — Buying Power:**
- Large C$ + smaller US$ below

**Card 3 — Returns:**
- Large +C$ gain in green/red
- Divider, ROI + IRR + Div Yield rows

**Card 4 — Sectors:**
- Horizontal progress bars

**Layout:**
- Desktop: 4 columns
- Tablet: 2x2 grid
- Mobile: Card 1 spans full width, Cards 2+3 side by side, Card 4 full width below

### Content Area
- **All Accounts:** Positions table with Holdings/Orders tabs
- **Individual account:** Positions/Activities/Dividends tabs

### Routing
- Single URL: `/portfolio` (or `/` as home)
- Account selection is state, not route change
- No breadcrumbs, no back button

## 2. Wheel Strategy Order Flow

### Desktop: Side Panel (340px)
- Clicking a position opens panel on right, grid compresses
- Panel contents (top to bottom):
  - Symbol + name + close button
  - Live quote: price, change, bid×size / mid / ask×size
  - Contract: option type, expiration, strike (editable dropdowns in 2x2 grid), order type
  - Quantity stepper (−/+)
  - Limit price input with currency
  - Duration dropdown
  - Account selector with buying power in option's currency
  - Estimated order total
  - Buy (green) / Sell (red) buttons

### Mobile: Bottom Sheet
- Same fields in scrollable bottom sheet over dimmed grid
- Form fields in 2-column grid
- Limit price + Duration side by side

### Key Behaviors
- Pre-fills from clicked position's contract details
- All fields editable (user can change option type, expiry, strike)
- Buying power shows in option's currency (US$ for US options, C$ for Canadian)
- Estimated total = quantity × limit price × 100
- Opening new positions (clicking "+") opens same panel with defaults
