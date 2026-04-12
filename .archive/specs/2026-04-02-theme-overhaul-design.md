# Theme Overhaul — Design Spec

## Context

The current theme system has three categories of issues:
1. **Light theme is too bright** — harsh `#e8e8e8` industrial gray background with pure white cards causes eye fatigue
2. **Theme-breaking bugs** — login page "Sign In" title invisible in dark mode (white text on white card), portfolio group model badges unreadable in light theme
3. **Hardcoded colors** — 171 instances of hex colors across 38 CSS files bypass the theme variable system, causing inconsistencies when switching themes

## Decisions

- **Light palette**: Slate Blue (#f0f2f5 base) — cool professional feel with subtle blue undertones
- **Dark palette**: Keep current near-black (#09090b base) — no changes to dark theme colors
- **Auth page**: Fully theme-aware card (adapts to active theme)
- **Scope**: Full audit — replace all hardcoded colors with CSS variables

## Design

### 1. Light Theme Palette Update

Update `:root` variables in `frontend/src/index.css`:

| Variable | Current | New | Rationale |
|----------|---------|-----|-----------|
| `--bg-primary` | `#e8e8e8` | `#f0f2f5` | Softer cool gray, less harsh |
| `--bg-secondary` | `#ffffff` | `#fafbfc` | Slight off-white, reduces glare |
| `--bg-tertiary` | `#cbd4d9` | `#e8ebf0` | Lighter hover state, better with new bg |
| `--card-bg` | `#ffffff` | `#fafbfc` | Matches bg-secondary |
| `--text-primary` | `#303030` | `#1e2328` | Slightly deeper for contrast on lighter bg |
| `--text-secondary` | `#4a5568` | `#4a5567` | Minimal shift, already good |
| `--text-muted` | `#6b7280` | `#6c7689` | Cool blue undertone |
| `--border` | `#cbd4d9` | `#dce1e8` | Softer border, blue tint |
| `--input-bg` | `#ffffff` | `#fafbfc` | Consistent with cards |
| `--input-border` | `#cbd4d9` | `#dce1e8` | Matches border |
| `--popover-bg` | `#ffffff` | `#fafbfc` | Consistent |
| `--popover-text` | `#303030` | `#1e2328` | Matches text-primary |
| `--secondary-bg` | `#f1f5f9` | `#eef0f5` | Slightly more contrast with new bg |
| `--secondary-text` | `#303030` | `#1e2328` | Matches text-primary |
| `--muted-bg` | `#f1f5f9` | `#eef0f5` | Matches secondary-bg |
| `--gray-light` | `#f3f4f6` | `#eef0f4` | Slightly more visible on new bg |
| `--gray-stroke` | `#e5e7eb` | `#dce0e7` | Blue tint |

Light theme AG Grid overrides in `index.css` also need updating:
- `--ag-header-background-color`: `#f1f5f9` → `#eef0f5`
- `--ag-odd-row-background-color`: `#f8fafc` → `#f5f6f9`

### 2. New CSS Variables

Add to both `:root` and `html.dark`:

```css
/* ---- Light theme ---- */
--info: #3b82f6;
--info-hover: #2563eb;
--info-light: #dbeafe;    /* already exists */
--info-text: #2563eb;     /* already exists */
--model-badge-bg: #1e3a5f;
--model-badge-text: #60a5fa;
--error-dark: #991b1b;
--warning-dark: #92400e;
--success-dark: #065f46;

/* ---- Dark theme ---- */
--info: #60a5fa;
--info-hover: #3b82f6;
--model-badge-bg: #1e3a5f;
--model-badge-text: #60a5fa;
/* "dark" variants flip in dark mode to lighter shades readable on dark backgrounds */
--error-dark: #fca5a5;
--warning-dark: #fde68a;
--success-dark: #6ee7b7;
```

### 3. Auth Page Fix

**File**: `frontend/src/pages/auth/AuthPages.css`

Replace all hardcoded colors with CSS variables:
- `.auth-card` background: `#ffffff` → `var(--bg-secondary)`
- `.auth-title` already uses `var(--text-primary)` — works once card bg is fixed
- `.form-group label` color: `#374151` → `var(--text-secondary)`
- `.form-group input` border: `#d1d5db` → `var(--input-border)`, background: add `var(--input-bg)`
- `.form-hint` color: `#9ca3af` → `var(--text-muted)`
- `.auth-error` background: `#fef2f2` → `var(--danger-light)`, border: `#fecaca` → `var(--error)` with opacity
- `.auth-button:disabled` background: `#9ca3af` → `var(--text-muted)`
- Loading container gradient and spinner: use CSS variables

### 4. Hardcoded Color Migration

**38 CSS files** with hardcoded colors, organized by migration type:

#### White text on colored backgrounds (16 instances)
All `color: #fff`, `color: #ffffff`, `color: white` on accent/status backgrounds → `var(--nav-text)`

Files: `button.css`, `badge.css`, `Pagination.css`, `ScreenerGrid.css`, `ScreenerFilters.css`, `InstrumentSearchAutocomplete.css`, `BrokerageMatrix.css`, `BrokerConnectionCard.css`, `AccountActivitiesGrid.css`, `BrokerCard.css`, `ReportingPage.css`, `BrokerPositionsPage.css`, `PositionDetailsPage.css`, `DashboardPage.css`, `AppSidebar.css`, `RebalancingProgressWidget.css`, `CustomPortfolioBuilder.css`

#### Info blue #3b82f6 (18 instances)
All `#3b82f6` → `var(--info)`, all `#2563eb` → `var(--info-hover)`

Files: `CustomPortfolioBuilder.css`, `ModelPortfolioCard.css`, `PendingOrdersWidget.css`, `RebalancingProgressWidget.css`, `BrokerCard.css`, `BrokerageMatrix.css`

#### Success green (12 instances)
`#059669` and `#22c55e` → `var(--success)`

Files: `SnapTradeBadge.css`, `BrokerageMatrix.css`, `BrokerCard.css`, `CustomPortfolioBuilder.css`, `ModelPortfolioCard.css`, `PendingOrdersWidget.css`, `RebalancingProgressWidget.css`

#### Error red (12 instances)
`#dc2626` and `#ef4444` → `var(--error)`

Files: `SnapTradeBadge.css`, `BrokerageMatrix.css`, `BrokerCard.css`, `NotificationBell.css`, `PendingOrdersWidget.css`, `RebalancingProgressWidget.css`, `CustomPortfolioBuilder.css`

#### Warning amber (11 instances)
`#d97706` and `#f59e0b` → `var(--warning)`

Files: `SnapTradeBadge.css`, `BrokerageMatrix.css`, `BrokerCard.css`, `CustomPortfolioBuilder.css`, `ModelPortfolioCard.css`, `PendingOrdersWidget.css`

#### Model badge colors (2 files)
`#1e3a5f` → `var(--model-badge-bg)`, `#60a5fa` → `var(--model-badge-text)`

Files: `AccountDetailPage.css`, `ConnectedAccountsWidget.css`

#### Dark variant colors (2 files)
`#991b1b` → `var(--error-dark)`, `#92400e` → `var(--warning-dark)`, `#065f46` → `var(--success-dark)`

Files: `PositionDetailsPage.css`, `BrokerConnectionsPage.css`

#### Gradient backgrounds (3 files)
Gradients using `#02605c` / `#2a8a81` → `var(--nav-bg)` / `var(--accent)`

Files: `AuthPages.css`, `DashboardPage.css`

#### Special cases
- `DashboardPage.css` lines 304/310: `#86efac` / `#fca5a5` for change indicators — keep as-is (these are always on the dark gradient hero, not theme-dependent)
- `App.css` line 58: `#b91c1c` → `var(--error)`
- `ApplyToAccountModal.css`: `#ef4444` → `var(--error)`

#### TSX inline styles
- `PortfolioGroupsList.tsx`: Accuracy color inline styles (`#059669`, `#d97706`, `#dc2626`) → use `var(--success)`, `var(--warning)`, `var(--error)` via CSS classes instead of inline styles
- `SessionTimeoutWarning.tsx` line 28: `color: '#666'` → use CSS class with `var(--text-muted)`

### 5. Files NOT Changed
- `index.css` dark theme block — no palette changes (user choice)
- AG Grid dark mode overrides — already correct
- Status badge light/text pairs in both themes — already use CSS variables correctly

## Verification

1. Start the app: `docker compose up --build`
2. Toggle between light and dark themes on every page
3. Check these specific scenarios:
   - Login page: title and button visible in both themes
   - Dashboard: portfolio groups readable, KPI hero gradient looks good
   - Broker cards: status badges (online/offline/degraded) readable in both themes
   - Model portfolio cards: risk level colors consistent
   - Custom portfolio builder: all interactive elements visible
   - Screener: add button text readable
4. Run frontend build: `npm run build` — no CSS errors
5. Run frontend lint: `npm run lint`
