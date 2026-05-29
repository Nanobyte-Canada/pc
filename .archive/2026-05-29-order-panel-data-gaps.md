# OrderPanel Data Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bridge 3 data-wiring gaps in the Wheel Strategy OrderPanel — buying power display, live WebSocket option quotes, and currency-aware filtering.

**Architecture:** All backend APIs already exist. This is purely frontend wiring: pass `buyingPower` from WheelPage into OrderPanel, subscribe to the market-data WebSocket for the selected option's bid/ask/mid, and filter buying power by the option's currency. Follows the same patterns already used by WheelChainPanel for WebSocket + store reads.

**Tech Stack:** React 18, TypeScript, Zustand (useQuoteStore), WebSocket (useMarketDataWebSocket), CSS custom properties

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/src/components/wheel/OrderPanel.tsx` | Modify | Add `buyingPower` prop, `getOptionCurrency` helper, WebSocket subscription effect, store reads for live quotes, buying power display row |
| `frontend/src/components/wheel/OrderPanel.css` | Modify | Add `.order-panel__buying-power` and `.order-panel__buying-power-value` styles |
| `frontend/src/pages/WheelPage.tsx` | Modify | Pass `buyingPower={cashData?.buyingPower ?? []}` to OrderPanel |

No new files. No backend changes. No new dependencies.

---

### Task 1: Add buying power prop and currency helper to OrderPanel

**Files:**
- Modify: `frontend/src/components/wheel/OrderPanel.tsx`
- Modify: `frontend/src/pages/WheelPage.tsx`

- [ ] **Step 1: Add `getOptionCurrency` helper and update `OrderPanelProps` in OrderPanel.tsx**

Add import for `CurrencyAmount` and a currency mapper, then extend the props interface:

```typescript
// At top of file, add import:
import type { CurrencyAmount } from '@/types/dashboard'

// After the existing getCurrencyLabel function (~line 36), add:
function getOptionCurrency(ticker: string): string {
  return getCurrencyLabel(ticker) === 'C$' ? 'CAD' : 'USD'
}

// Update OrderPanelProps interface to add buyingPower:
interface OrderPanelProps {
  position?: WheelPosition | null
  ticker: string
  currentPrice?: number
  onClose: () => void
  accounts: OrderPanelAccount[]
  buyingPower: CurrencyAmount[]
}
```

Update the component destructuring to include `buyingPower`:

```typescript
export function OrderPanel({ position, ticker, currentPrice, onClose, accounts, buyingPower }: OrderPanelProps) {
```

- [ ] **Step 2: Compute the buying power amount for the option's currency**

After the existing `const currencyLabel = getCurrencyLabel(ticker)` line (~line 94), add:

```typescript
const optionCurrency = getOptionCurrency(ticker)
const buyingPowerAmount = buyingPower.find(bp => bp.currency === optionCurrency)?.amount ?? null
```

- [ ] **Step 3: Render buying power below the account selector**

In the account selector section (~line 320), after the closing `</div>` of `.order-panel__account-info` (line 348) and before the closing `</div>` of `.order-panel__account-selector` (which is the wrapper for the section content), add a buying power row. Replace the entire account selector section (lines 320–351) with:

```tsx
{/* 7. Account Selector */}
{accounts.length > 0 && (
  <div className="order-panel__section">
    <label className="order-panel__label">Account</label>
    <div className="order-panel__account-selector">
      <div className="order-panel__account-info">
        {selectedBrokerIcon && (
          <span
            className="order-panel__broker-icon"
            style={{ background: selectedBrokerIcon.bg, color: selectedBrokerIcon.color }}
          >
            {selectedBrokerIcon.letter}
          </span>
        )}
        <div className="order-panel__select-wrap order-panel__account-select-wrap">
          <select
            className="order-panel__select"
            value={selectedAccountId}
            onChange={e => setSelectedAccountId(Number(e.target.value))}
          >
            {accounts.map(a => (
              <option key={a.connectionId} value={a.connectionId}>
                {a.brokerName} {a.accountType ? `${a.accountType} ` : ''}
                {a.accountNumber ? `••${a.accountNumber.slice(-4)}` : ''}
              </option>
            ))}
          </select>
          <ChevronDown size={14} className="order-panel__select-chevron" />
        </div>
      </div>
      <div className="order-panel__buying-power">
        <span className="order-panel__buying-power-value">
          {buyingPowerAmount != null
            ? `${currencyLabel} ${buyingPowerAmount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
            : '--'}
        </span>
      </div>
    </div>
  </div>
)}
```

- [ ] **Step 4: Pass buyingPower from WheelPage to OrderPanel**

In `frontend/src/pages/WheelPage.tsx`, update the `<OrderPanel>` JSX (line 249–260) to include the new prop:

```tsx
<OrderPanel
  position={selectedPosition.position}
  ticker={selectedPosition.ticker}
  currentPrice={underlyingPrices[selectedPosition.ticker]}
  onClose={() => setSelectedPosition(null)}
  accounts={activeConnections.map(c => ({
    connectionId: c.id,
    accountType: c.accountType ?? '',
    accountNumber: c.accountNumber ?? '',
    brokerName: c.broker?.name ?? '',
  }))}
  buyingPower={cashData?.buyingPower ?? []}
/>
```

- [ ] **Step 5: Add CSS for buying power row**

In `frontend/src/components/wheel/OrderPanel.css`, after the `.order-panel__account-select-wrap` rule (line 329), add:

```css
/* ── Buying Power ── */
.order-panel__buying-power {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.order-panel__buying-power-value {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}
```

- [ ] **Step 6: Verify build**

Run: `cd frontend && npm run build`

Expected: Build succeeds with no type errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/wheel/OrderPanel.tsx frontend/src/components/wheel/OrderPanel.css frontend/src/pages/WheelPage.tsx
git commit -m "feat(wheel): display buying power in OrderPanel account section"
```

---

### Task 2: Add live WebSocket option quotes to OrderPanel

**Files:**
- Modify: `frontend/src/components/wheel/OrderPanel.tsx`

- [ ] **Step 1: Add imports for WebSocket hook, quote store, and chain fetcher**

At the top of `OrderPanel.tsx`, add these imports alongside the existing ones:

```typescript
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { useQuoteStore } from '@/stores/quoteStore'
import { getOptionsChain } from '@/services/marketDataService'
```

- [ ] **Step 2: Add initial chain load effect**

Inside the `OrderPanel` component, after the existing `useEffect` that initializes form state from `position` (~line 84-92), add:

```typescript
const setChain = useQuoteStore(s => s.setChain)

useEffect(() => {
  let cancelled = false
  getOptionsChain(ticker).then(chainData => {
    if (!cancelled) setChain(ticker, chainData)
  }).catch(() => {})
  return () => { cancelled = true }
}, [ticker, setChain])
```

- [ ] **Step 3: Add WebSocket subscription effect for the selected option**

After the chain load effect, add:

```typescript
const { subscribeOption, unsubscribeOption } = useMarketDataWebSocket()

useEffect(() => {
  if (!expiration || !strike) return

  const ot = optionType === 'Call' ? 'CALL' : 'PUT'
  subscribeOption(ticker, expiration, strike, ot)

  return () => {
    unsubscribeOption(ticker, expiration, strike, ot)
  }
}, [ticker, expiration, strike, optionType, subscribeOption, unsubscribeOption])
```

- [ ] **Step 4: Read the live option quote from the store**

After the subscription effect, add the store selector and derive display values:

```typescript
const chainData = useQuoteStore(s => s.chains[ticker])
const optionQuote = useMemo(() => {
  if (!chainData?.expirations || !expiration || !strike) return null
  const strikeKey = strike.includes('.') ? strike : strike + '.0'
  const side = optionType === 'Call' ? 'call' : 'put'
  return chainData.expirations[expiration]?.[strikeKey]?.[side] ?? null
}, [chainData, expiration, strike, optionType])
```

- [ ] **Step 5: Update the quote cards to use live data**

Replace the Live Quote section (lines 158-186) with:

```tsx
{/* 2. Live Quote */}
<div className="order-panel__quote">
  <div className="order-panel__quote-main">
    <span className="order-panel__quote-price">
      {price > 0 ? formatCurrency(price, 'USD') : '--'}
    </span>
    {position?.pnl != null && (
      <span className={`order-panel__quote-change ${position.pnl >= 0 ? 'order-panel__quote-change--up' : 'order-panel__quote-change--down'}`}>
        {position.pnl >= 0 ? '+' : ''}{formatCurrency(position.pnl, 'USD')}
      </span>
    )}
  </div>
  <div className="order-panel__quote-cards">
    <div className="order-panel__quote-card">
      <span className="order-panel__quote-card-label">Bid</span>
      <span className="order-panel__quote-card-value">
        {optionQuote?.bid != null ? optionQuote.bid.toFixed(2) : '--'}
      </span>
    </div>
    <div className="order-panel__quote-card">
      <span className="order-panel__quote-card-label">Mid</span>
      <span className="order-panel__quote-card-value">
        {optionQuote?.mid != null
          ? optionQuote.mid.toFixed(2)
          : position?.currentPrice != null
            ? position.currentPrice.toFixed(2)
            : '--'}
      </span>
    </div>
    <div className="order-panel__quote-card">
      <span className="order-panel__quote-card-label">Ask</span>
      <span className="order-panel__quote-card-value">
        {optionQuote?.ask != null ? optionQuote.ask.toFixed(2) : '--'}
      </span>
    </div>
  </div>
</div>
```

- [ ] **Step 6: Update the contract "Last" line to use live data**

Replace the contract last price display (~line 191-195) with:

```tsx
<div className="order-panel__contract-last">
  Last: {optionQuote?.last != null
    ? formatCurrency(optionQuote.last, 'USD')
    : position?.premium != null
      ? formatCurrency(position.premium, 'USD')
      : '--'}
</div>
```

Remove the `{position?.premium != null && ...}` conditional wrapper — always show the Last line.

- [ ] **Step 7: Verify build**

Run: `cd frontend && npm run build`

Expected: Build succeeds with no type errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/wheel/OrderPanel.tsx
git commit -m "feat(wheel): add live WebSocket option quotes to OrderPanel bid/ask/mid"
```

---

### Task 3: Verify end-to-end and lint

**Files:**
- All modified files from Tasks 1-2

- [ ] **Step 1: Run linter**

Run: `cd frontend && npm run lint`

Expected: No errors. Fix any that appear.

- [ ] **Step 2: Run tests**

Run: `cd frontend && npm run test:run`

Expected: All existing tests pass.

- [ ] **Step 3: Run full build**

Run: `cd frontend && npm run build`

Expected: Clean build, no warnings related to OrderPanel.

- [ ] **Step 4: Commit any lint/test fixes if needed**

```bash
git add -u
git commit -m "fix(wheel): lint and test fixes for OrderPanel data gaps"
```

Only run this step if Step 1 or 2 required changes. Skip if everything passed clean.

---

### Task 4: Update documentation

**Files:**
- Modify: `docs/reference/frontend-map.md`

- [ ] **Step 1: Update frontend-map.md**

In `docs/reference/frontend-map.md`, find the entry for `OrderPanel` and update its description to mention the new features:

- Buying power display with currency-aware filtering
- Live WebSocket option quotes (bid/ask/mid)
- Integration with `useMarketDataWebSocket` and `useQuoteStore`

- [ ] **Step 2: Move spec to archive**

```bash
git mv docs/superpowers/specs/2026-05-29-order-panel-data-gaps-design.md .archive/
git mv docs/superpowers/plans/2026-05-29-order-panel-data-gaps.md .archive/
```

- [ ] **Step 3: Commit**

```bash
git add docs/reference/frontend-map.md .archive/
git commit -m "docs: update frontend-map for OrderPanel data gaps, archive spec and plan"
```
