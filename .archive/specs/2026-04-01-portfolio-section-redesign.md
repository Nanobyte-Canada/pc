# Portfolio Section Redesign — Design Spec

## Context

The current Portfolio section has three separate navigation items (Model Portfolios, Portfolio Groups, Portfolio Builder) with overlapping concerns. Portfolio Groups add an unnecessary layer of indirection between model portfolios and connected accounts. This redesign merges all three into a single unified Portfolio page, eliminates Portfolio Groups entirely, and enables direct model-to-account application with pending orders and rebalancing progress tracking.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Portfolio Groups | Remove entirely | Unnecessary abstraction layer between models and accounts |
| Page layout | Card grid + expandable detail | 5 model cards at top, click to expand analysis below |
| Custom portfolios | 1 editable slot | User gets one custom portfolio, editable anytime |
| Apply target | Connected Accounts directly | Multi-select accounts in modal |
| Post-apply behavior | Toast notification, stay on page | No navigation on apply |
| Account detail access | Both Portfolio page and Connected Accounts page | Same component, two entry points |
| Dashboard color scheme | Unified row-based accent colors | Consistent across dashboard and account detail |

## 1. Merged Portfolio Page (`/portfolios`)

### Layout
- **Top**: 5 model portfolio cards in a horizontal row
  - 4 system models: Conservative, Balanced, Growth, Aggressive (from V58 migration seed data)
  - 1 custom slot: shows "+" when empty, named card when saved
- **Below**: Expandable analysis panel when a card is clicked

### Model Cards
Each card displays:
- Model name
- Risk level label (Low Risk / Moderate / High Risk / Aggressive)
- Risk bar (color-coded: green 25% / blue 50% / amber 75% / red 100%)
- Active/selected state with accent border

### Expanded Analysis Panel
When a card is clicked, an inline panel expands below showing dashboard-style widgets:
- **Sector & Industry Concentration** — reuse `SectorExposureWidget` display
- **Geographic Concentration** — reuse `GeographyExposureWidget` display
- **Risk Profile** — reuse `RiskProfileWidget` display
- **Positions & Holdings** — reuse `PositionsTableWidget` / `HoldingsTableWidget` display
- **"Apply to Account" button** — opens the apply modal

### Custom Portfolio (5th card)
- When empty: "+" card with "Build Your Own" label
- When clicked (empty): shows Portfolio Builder UI inline (reuse `InstrumentTabs` for search, weight editor table)
- Live-updating metrics: as user adds/removes instruments or changes weights, the analysis widgets update in real-time
- Save button to persist the custom model
- When saved: card shows custom model name, clicking expands analysis like system models
- Editable anytime: user can modify allocations and re-save

### Apply to Account Modal
- Triggered by "Apply to Account" button
- Shows list of connected accounts with:
  - Account name, broker, masked account number
  - Total value
  - Current model badge (if already has one)
- **Multi-select**: user can select multiple accounts
- Accounts with existing models can be re-assigned (warning shown: "This will replace the current model")
- "Apply Model" button → applies model to all selected accounts
- **Post-apply**: success toast ("Model applied to N accounts"), stay on portfolio page
- **Empty state**: if no connected accounts exist, modal shows message with link to broker connection page

## 2. Account Detail Page Updates (`/brokers/accounts/:connectionId`)

### Header Changes
- **Model badge** displayed on the right side, next to the accuracy percentage circle
- Badge shows applied model name (e.g., "Growth Model")
- When no model applied: badge hidden, accuracy circle shows "—"

### New Widgets (Row 3 in Zone A — Blue accent)
Added as a new row below Risk Profile/Sector/Geography, **only visible when a model is applied**:

#### Rebalancing Progress Widget (left half)
- Single-column list of all securities in the model
- Each security shows:
  - Symbol + abbreviated name
  - Target % (blue) and Actual % (green) values
  - Dual progress bar: target (blue, top) and actual (green, bottom)
- Legend: "● Target" (blue) / "● Actual" (green)

#### Pending Orders Widget (right half)
- Header with "Preview Orders" button
- BUY section (green header) and SELL section (red header)
- Each order row: Price, Units, Amount, Security symbol
- Footer: "Show Explanation" toggle + Total amount
- Orders are generated from model allocation vs actual positions

### Existing Widgets — Unchanged
- **Open Orders** stays in Zone B (right column) — can coexist with Pending Orders
- All other widgets remain in their current positions

### Widget Visibility
- When no model applied: Row 3 (Rebalancing Progress + Pending Orders) is hidden
- When model applied: Row 3 appears, header shows model badge

## 3. Unified Color Scheme

Applied consistently to both Dashboard and Account Detail pages:

| Color | Accent Hex | Widgets |
|-------|-----------|---------|
| Teal | `#14b8a6` | Portfolio Value, Available Cash, Buying Power |
| Orange | `#f97316` | Risk Profile, Sector & Industry Exposure, Geographic Exposure |
| Blue | `#3b82f6` | Connected Accounts (dashboard) / Rebalancing Progress + Pending Orders (account) |
| Red | `#ef4444` | Open Orders, Fees & Commission, Dividend Calendar |
| Purple | `#8b5cf6` | Positions / Holdings table |

Implementation: Each widget's left border uses `border-left: 3px solid <accent>`. Widgets in the same logical row share the same accent color.

## 4. Dashboard Updates

### Connected Accounts Widget Enhancement
Each account card now shows:
- Account name + broker + masked number
- **Accuracy circle** (top-right): color-coded border (green ≥80%, amber ≥50%, red <50%, grey "—" if no model)
- Total value
- **Model badge**: shows applied model name or "No model"
- **Mini progress bar** at bottom: visualizes accuracy percentage with color matching the accuracy circle

### Color Scheme
Same 5-color row-based scheme as account detail page (see table above).

## 5. Navigation Changes

### Sidebar (`AppSidebar.tsx`)
**Before**: 3 items under "Portfolios":
- Model Portfolios (`/models`)
- Portfolio Groups (`/portfolios`)
- Portfolio Builder (`/builder`)

**After**: 1 item:
- Portfolio (`/portfolios`)

### Routes (`App.tsx`)
**Remove**:
- `/models` → ModelPortfoliosPage
- `/builder` → PortfolioBuilderPage
- `/portfolios/:groupId` → PortfolioGroupDetailPage

**Keep/Update**:
- `/portfolios` → new merged PortfolioPage

**Unchanged**:
- `/brokers/accounts/:connectionId` → AccountDetailPage (enhanced with new widgets)

## 6. Backend Changes

### Remove Portfolio Group concept
- Portfolio Groups table and related tables (`portfolio_targets`, `portfolio_group_accounts`, `portfolio_group_settings`, `excluded_assets`) — create migration to drop or mark deprecated
- Remove `PortfolioGroupController`, `PortfolioGroupService`, related DTOs
- Remove `portfolio_group_id` and `benchmark_model_id` from relevant entities

### New: Account-Model Linking
- Add `model_portfolio_id` foreign key to `broker_connections` table (Flyway migration)
- New endpoint: `POST /api/v1/model-portfolios/{id}/apply-to-accounts` — accepts list of connection IDs
- New endpoint: `GET /api/v1/broker-connections/{id}/rebalance-progress` — returns target vs actual for the account
- New endpoint: `GET /api/v1/broker-connections/{id}/pending-orders` — returns calculated pending orders

### Model Portfolio Analysis Endpoints
- `GET /api/v1/model-portfolios/{id}/analysis` — returns sector exposure, geography exposure, risk profile, holdings for a model portfolio (computed from its allocations)

### Reuse Existing Services
- `DriftCalculationService` — adapt to work with account + model (instead of portfolio group)
- `RebalanceService` — adapt to generate trades for single account against model
- `LookThroughService` — reuse for model portfolio analysis (ETF decomposition)

## 7. Frontend Architecture

### New Components
- `PortfolioPage.tsx` — merged page with card grid + expandable detail
- `ModelPortfolioCard.tsx` — individual model card (reuse/adapt from existing)
- `ModelAnalysisPanel.tsx` — expandable analysis panel reusing dashboard widgets
- `CustomPortfolioBuilder.tsx` — inline builder reusing `InstrumentTabs` + weight editor
- `ApplyToAccountModal.tsx` — multi-select account modal
- `RebalancingProgressWidget.tsx` — new dashboard widget
- `PendingOrdersWidget.tsx` — new dashboard widget

### Reuse Existing Components
- `SectorExposureWidget` display logic
- `GeographyExposureWidget` display logic
- `RiskProfileWidget` display logic
- `PositionsTableWidget` / `HoldingsTableWidget` display logic
- `InstrumentTabs` for custom portfolio search
- `AccuracyGauge` for account accuracy display
- Toast notification system

### New Hooks
- `useModelAnalysis(modelId)` — fetches model portfolio analysis data
- `useApplyModelToAccounts()` — mutation to apply model to multiple accounts
- `useRebalanceProgress(connectionId)` — fetches target vs actual for an account
- `usePendingOrders(connectionId)` — fetches pending orders for an account

### Remove
- `ModelPortfoliosPage.tsx` and related components (`CreateModelModal`, `ApplyModelModal`)
- `PortfolioGroupsPage.tsx`, `PortfolioGroupDetailPage.tsx` and all sub-components
- `PortfolioBuilderPage.tsx` (logic moved into `CustomPortfolioBuilder`)
- `usePortfolioGroups.ts` hook
- `portfolioGroupService.ts` service

## 8. Database Migrations

### New Migration: `V60__account_model_linking.sql`
```sql
-- Add model portfolio reference to broker connections
ALTER TABLE broker_connections ADD COLUMN model_portfolio_id BIGINT
  REFERENCES model_portfolios(id) ON DELETE SET NULL;

-- Add accuracy tracking to broker connections
ALTER TABLE broker_connections ADD COLUMN model_accuracy DECIMAL(5,2);
ALTER TABLE broker_connections ADD COLUMN last_rebalanced_at TIMESTAMP;

-- Index for quick lookup
CREATE INDEX idx_broker_connections_model ON broker_connections(model_portfolio_id);

-- Migrate rebalance_events from group_id to connection_id
ALTER TABLE rebalance_events ADD COLUMN connection_id BIGINT
  REFERENCES broker_connections(id) ON DELETE CASCADE;
CREATE INDEX idx_rebalance_events_connection ON rebalance_events(connection_id);
```

### Existing Tables to Deprecate (future migration)
- `portfolio_groups`
- `portfolio_targets`
- `portfolio_group_accounts`
- `portfolio_group_settings`
- `excluded_assets`
- `rebalance_events.group_id` column (after data migrated to `connection_id`)

Note: These tables will be dropped in a separate migration after confirming no data loss. The V60 migration only adds the new columns.

## 9. Documentation Updates

All project documentation must be updated to reflect the redesign:

| File | Priority | Changes |
|------|----------|---------|
| `CLAUDE.md` | Critical | Remove PortfolioGroup from package structure, update entity relationships (BrokerConnection → ModelPortfolio), update API routes table (remove portfolio-groups, add new model-portfolio and broker-connection endpoints), update frontend directory structure |
| `docs/api.md` | High | Remove portfolio-group endpoints, add new endpoints: model portfolio analysis, apply-to-accounts, rebalance-progress, pending-orders. Update response schema examples |
| `docs/deployment.md` | Medium | Document V60 migration (new columns on broker_connections), deprecation timeline for portfolio group tables |
| `docs/development.md` | Medium | Update backend package structure, remove PortfolioGroupController/Service references, add migration notes |
| `README.md` | Low | Update API endpoints section if present, update project feature descriptions |
| `docs/architecture.md` | Low | Update if it references portfolio group data flow |

**Content to add across docs:**
- New widget descriptions: RebalancingProgressWidget, PendingOrdersWidget
- Unified color scheme (teal/orange/blue/red/purple) for dashboard widgets
- Account-model linking relationship (replaces group-based linking)
- Apply-to-accounts multi-select flow

**Content to remove across docs:**
- All Portfolio Groups references (tables, endpoints, controllers, services, components)
- Three-item portfolio navigation (replaced by single "Portfolio" nav item)
- Portfolio Group detail page tabs (Overview, Targets, Accounts, Orders, Performance, Settings)

## 10. Testing

### Backend Tests (JUnit 5 + MockK)

**New tests:**
- `ModelPortfolioServiceTest` — update to cover `applyToAccounts()` with multi-connection linking, re-assignment (replacing existing model), and validation (invalid connection IDs, inactive connections)
- `RebalanceServiceTest` — update to test trade generation against a single account + model (instead of portfolio group), including sell-to-rebalance, keep-currencies-separate, minimum trade amount ($10)
- `DriftCalculationServiceTest` — update to test accuracy calculation for account + model (instead of group)
- `BrokerConnectionServiceTest` — test rebalance-progress and pending-orders endpoints, model linking/unlinking
- `ModelPortfolioAnalysisTest` — test analysis endpoint: sector/geography/risk computation from model allocations via LookThroughService

**Tests to remove:**
- `PortfolioGroupServiceTest` and any portfolio group controller/service tests
- Tests referencing `PortfolioGroupController`, `PortfolioGroupDtos`, group-based drift/rebalance

**Tests to update:**
- Any existing test that references portfolio group entities or services — update imports and assertions to use account-model linking instead

### Frontend Tests (Vitest + Testing Library)

**New tests:**
- `PortfolioPage.test.tsx` — renders 5 model cards, card click expands analysis panel, custom card shows builder
- `ModelPortfolioCard.test.tsx` — renders model name, risk level, risk bar, selected state
- `ApplyToAccountModal.test.tsx` — multi-select accounts, shows existing model warning, empty state when no accounts, submit calls mutation
- `CustomPortfolioBuilder.test.tsx` — add/remove instruments, weight editing, normalize to 100%, save triggers mutation
- `RebalancingProgressWidget.test.tsx` — renders target vs actual bars, handles empty state (no model applied)
- `PendingOrdersWidget.test.tsx` — renders BUY/SELL sections, shows totals, handles empty state
- `ConnectedAccountsWidget.test.tsx` — update to verify accuracy circle, model badge, mini progress bar render correctly

**Tests to remove:**
- `ModelPortfoliosPage.test.tsx`, `PortfolioGroupsPage.test.tsx`, `PortfolioGroupDetailPage.test.tsx` (if they exist)
- `PortfolioBuilderPage.test.tsx` (logic moved into CustomPortfolioBuilder)

**Tests to update:**
- `AccountDetailPage.test.tsx` — verify model badge in header, new widget row visibility toggle (shown only when model applied)
- `DashboardGrid.test.tsx` — verify unified color scheme applied to widget borders
- `AppSidebar.test.tsx` — verify single "Portfolio" nav item instead of three

## Verification Plan

### Frontend
1. Navigate to `/portfolios` — verify 5 model cards render (4 system + 1 custom slot)
2. Click each system model — verify analysis panel expands with sector, geography, risk, holdings
3. Click custom "+" card — verify builder UI appears with instrument search
4. Add instruments to custom portfolio — verify live metric updates
5. Save custom portfolio — verify card updates with name
6. Click "Apply to Account" — verify modal shows connected accounts with multi-select
7. Apply model — verify toast notification, stay on page
8. Navigate to account detail — verify model badge, accuracy circle, rebalancing progress, pending orders
9. Verify dashboard connected accounts widget shows accuracy % and model badges
10. Verify unified color scheme across dashboard and account detail

### Backend
1. `POST /api/v1/model-portfolios/{id}/apply-to-accounts` — verify model linked to connections
2. `GET /api/v1/broker-connections/{id}/rebalance-progress` — verify target vs actual data
3. `GET /api/v1/broker-connections/{id}/pending-orders` — verify trade calculations
4. `GET /api/v1/model-portfolios/{id}/analysis` — verify sector/geography/risk data
5. Verify Flyway migration applies cleanly
6. Run `docker compose exec backend ./gradlew test`
7. Run `npm run test:run` and `npm run build` from frontend
