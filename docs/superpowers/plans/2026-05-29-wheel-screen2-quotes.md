# Wheel Screen 2: Options Quotes & Live Feed — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the WheelChainPanel as a side panel (desktop) / bottom sheet (mobile) that opens inline on the Wheel page when clicking "+" or "Sell CC" slots, showing live-streaming options chain with discount, yield, and delta per strike.

**Architecture:** Replace the old full-screen overlay `WheelChainPanel` with a side panel that sits in the same `wheel-page__panel` slot as `OrderPanel`. WheelPage manages which panel is open (chain vs order) via a `chainPanelContext` state. The chain panel reuses the existing chain-building logic (discount, yield, ATM/ITM), WebSocket streaming, and Zustand store. Put/call mode is auto-selected from click context. Clicking a strike row transitions to the OrderPanel (Screen 3) with pre-filled data.

**Tech Stack:** React 18, TypeScript, CSS custom properties, `useMarketDataWebSocket`, `useQuoteStore` (Zustand), `getOptionsChainWithGreeks` (REST)

**Design spec:** `docs/superpowers/specs/2026-05-29-wheel-screen2-quotes-design.md`
**Mockup:** `.superpowers/brainstorm/51960-1780073119/content/screen2-v3.html`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/src/types/wheel.ts` | Modify | Add `ChainPanelContext` type |
| `frontend/src/components/wheel/WheelChainPanel.tsx` | Rewrite | Side panel (desktop) + bottom sheet (mobile), expiry dropdown, chain table, live streaming |
| `frontend/src/components/wheel/WheelChainPanel.css` | Rewrite | Side panel + bottom sheet styling matching theme tokens |
| `frontend/src/components/wheel/WheelChainRow.tsx` | Keep | Already has discount/yield display — unchanged |
| `frontend/src/pages/WheelPage.tsx` | Modify | Add `chainPanelContext` state, wire slot clicks to open chain, handle strike selection → order |
| `frontend/src/pages/WheelPage.css` | No change | Panel slot already exists at 340px |

---

### Task 1: Add ChainPanelContext type

**Files:**
- Modify: `frontend/src/types/wheel.ts`

- [ ] **Step 1: Add the type**

Add after the existing `CalendarGridData` interface at the end of `frontend/src/types/wheel.ts`:

```typescript
export interface ChainPanelContext {
  ticker: string
  expiryDate: string
  optionSide: 'put' | 'call'
}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/wheel.ts
git commit -m "feat(wheel): add ChainPanelContext type for Screen 2"
```

---

### Task 2: Rewrite WheelChainPanel as side panel / bottom sheet

**Files:**
- Rewrite: `frontend/src/components/wheel/WheelChainPanel.tsx`
- Rewrite: `frontend/src/components/wheel/WheelChainPanel.css`

- [ ] **Step 1: Rewrite WheelChainPanel.tsx**

Replace the entire file. The new component:
- Accepts `context: ChainPanelContext` (ticker, expiry, put/call)
- Accepts `spotPrice: number`
- Accepts `onClose`, `onStrikeSelect` callbacks
- Loads chain via REST on mount, subscribes to WebSocket
- Shows expiry dropdown (desktop) or trigger+dots (mobile)
- Builds strike rows from chain store using `context.optionSide`
- ATM/ITM logic flips based on put vs call

```typescript
import { useState, useEffect, useMemo, useCallback } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { getOptionsChainWithGreeks } from '@/services/marketDataService'
import { formatCurrency } from '@/services/brokerService'
import { WheelChainRow } from './WheelChainRow'
import type { ChainPanelContext, WheelChainStrike } from '@/types/wheel'
import { X, ChevronDown } from 'lucide-react'
import './WheelChainPanel.css'

interface WheelChainPanelProps {
  context: ChainPanelContext
  spotPrice: number
  onClose: () => void
  onStrikeSelect: (ticker: string, expiry: string, strike: number, optionSide: 'put' | 'call') => void
}

export function WheelChainPanel({ context, spotPrice: initialSpotPrice, onClose, onStrikeSelect }: WheelChainPanelProps) {
  const [loading, setLoading] = useState(true)
  const [selectedExpiry, setSelectedExpiry] = useState(context.expiryDate)

  const chain = useQuoteStore(s => s.chains[context.ticker])
  const quote = useQuoteStore(s => s.quotes[context.ticker])
  const setChain = useQuoteStore(s => s.setChain)
  const { subscribe, subscribeChain, unsubscribe, unsubscribeChain } = useMarketDataWebSocket()

  const spotPrice = quote?.last ?? quote?.mid ?? initialSpotPrice
  const isCsp = context.optionSide === 'put'
  const typeLabel = isCsp ? 'Cash-Secured Put' : 'Covered Call'
  const typeLabelShort = isCsp ? 'CSP' : 'CC'

  // Available expiries from chain data
  const availableExpiries = useMemo(() => {
    if (!chain?.expirations) return []
    return Object.keys(chain.expirations).sort()
  }, [chain])

  // Find best matching expiry
  useEffect(() => {
    if (availableExpiries.length === 0) return
    if (availableExpiries.includes(selectedExpiry)) return
    // Find closest within ±3 days
    const target = new Date(context.expiryDate + 'T00:00:00').getTime()
    let bestKey = availableExpiries[0]
    let bestDiff = Infinity
    for (const k of availableExpiries) {
      const diff = Math.abs(new Date(k + 'T00:00:00').getTime() - target)
      if (diff < bestDiff) { bestDiff = diff; bestKey = k }
    }
    if (bestDiff <= 3 * 86400000) setSelectedExpiry(bestKey)
    else setSelectedExpiry(availableExpiries[0])
  }, [availableExpiries, context.expiryDate, selectedExpiry])

  // DTE for selected expiry
  const dte = useMemo(() => {
    const now = new Date()
    const exp = new Date(selectedExpiry + 'T00:00:00')
    return Math.max(1, Math.round((exp.getTime() - now.getTime()) / 86400000))
  }, [selectedExpiry])

  // Load chain and subscribe to streaming
  useEffect(() => {
    let cancelled = false
    async function loadChain() {
      try {
        const chainData = await getOptionsChainWithGreeks(context.ticker)
        if (!cancelled) {
          setChain(context.ticker, chainData)
          setLoading(false)
        }
      } catch {
        if (!cancelled) setLoading(false)
      }
    }
    loadChain()
    subscribe(context.ticker)
    subscribeChain(context.ticker)
    return () => {
      cancelled = true
      unsubscribe(context.ticker)
      unsubscribeChain(context.ticker)
    }
  }, [context.ticker, subscribe, subscribeChain, unsubscribe, unsubscribeChain, setChain])

  // Build strike rows
  const strikes: WheelChainStrike[] = useMemo(() => {
    if (!chain?.expirations) return []
    const expiryData = chain.expirations[selectedExpiry]
    if (!expiryData) return []

    const rows: WheelChainStrike[] = []
    for (const [strikeKey, data] of Object.entries(expiryData)) {
      const strikeNum = parseFloat(strikeKey)
      const option = isCsp ? data.put : data.call
      if (!option) continue

      const bid: number | null = option.bid ?? null
      const ask: number | null = option.ask ?? null
      const delta: number | null = option.greeks?.delta ?? null

      const bidDiscount = bid != null && spotPrice > 0
        ? (spotPrice - strikeNum + bid) / spotPrice : null
      const askDiscount = ask != null && spotPrice > 0
        ? (spotPrice - strikeNum + ask) / spotPrice : null
      const bidYield = bid != null && strikeNum > 0 && bid > 0
        ? (bid / strikeNum) * (365 / dte) : null
      const askYield = ask != null && strikeNum > 0 && ask > 0
        ? (ask / strikeNum) * (365 / dte) : null

      const isATM = spotPrice > 0 && Math.abs(strikeNum - spotPrice) / spotPrice < 0.01
      const isITM = isCsp ? strikeNum > spotPrice : strikeNum < spotPrice

      rows.push({ strike: strikeNum, bid, ask, delta, bidDiscount, askDiscount, bidYield, askYield, isATM, isITM })
    }
    return rows.sort((a, b) => b.strike - a.strike)
  }, [chain, selectedExpiry, spotPrice, dte, isCsp])

  const handleStrikeClick = useCallback((strike: WheelChainStrike) => {
    onStrikeSelect(context.ticker, selectedExpiry, strike.strike, context.optionSide)
  }, [context.ticker, selectedExpiry, context.optionSide, onStrikeSelect])

  const priceChange = quote ? (quote.last - (quote.bid + quote.ask) / 2) : 0
  const priceChangePct = spotPrice > 0 && quote ? ((quote.last / spotPrice - 1) * 100) : 0

  const panelContent = (
    <>
      {/* Header */}
      <div className="wcp2-header">
        <span className="wcp2-ticker">{context.ticker}</span>
        <span className={`wcp2-type ${isCsp ? 'wcp2-type--csp' : 'wcp2-type--cc'}`}>{typeLabelShort}</span>
        <button className="wcp2-close" onClick={onClose} aria-label="Close"><X size={16} /></button>
      </div>

      {/* Quote bar */}
      <div className="wcp2-quote">
        <span className="wcp2-quote__price">{formatCurrency(spotPrice, 'USD')}</span>
        {quote && (
          <span className={`wcp2-quote__change ${priceChange >= 0 ? 'wcp2-quote__change--up' : 'wcp2-quote__change--down'}`}>
            {priceChange >= 0 ? '+' : ''}{priceChangePct.toFixed(1)}%
          </span>
        )}
        <span className="wcp2-quote__live"><span className="wcp2-quote__dot" /> Live</span>
      </div>

      {/* Expiry selector */}
      <div className="wcp2-expiry">
        <span className="wcp2-expiry__label">Expiry</span>
        {/* Desktop: dropdown */}
        <div className="wcp2-expiry__desktop">
          <select
            className="wcp2-expiry__select"
            value={selectedExpiry}
            onChange={e => setSelectedExpiry(e.target.value)}
          >
            {availableExpiries.map(exp => {
              const d = new Date(exp + 'T00:00:00')
              const expiryDte = Math.max(0, Math.round((d.getTime() - Date.now()) / 86400000))
              const label = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
              return <option key={exp} value={exp}>{label} — {expiryDte} DTE</option>
            })}
          </select>
          <ChevronDown size={12} className="wcp2-expiry__chevron" />
        </div>
        {/* Mobile: trigger + dots */}
        <div className="wcp2-expiry__mobile">
          <button className="wcp2-expiry__trigger" onClick={() => {
            const idx = availableExpiries.indexOf(selectedExpiry)
            const next = (idx + 1) % availableExpiries.length
            setSelectedExpiry(availableExpiries[next])
          }}>
            <span className="wcp2-expiry__trigger-label">
              {new Date(selectedExpiry + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
            </span>
            <span className="wcp2-expiry__trigger-dte">{dte} DTE</span>
            <ChevronDown size={10} className="wcp2-expiry__trigger-chevron" />
          </button>
          <div className="wcp2-expiry__dots">
            {availableExpiries.map(exp => (
              <span
                key={exp}
                className={`wcp2-expiry__dot ${exp === selectedExpiry ? 'wcp2-expiry__dot--active' : ''}`}
                onClick={() => setSelectedExpiry(exp)}
              />
            ))}
          </div>
        </div>
      </div>

      {/* Column headers */}
      <div className="wcp2-cols">
        <div className="wcp2-col wcp2-col--strike">Strike<div className="wcp2-col-sub">Delta</div></div>
        <div className="wcp2-col wcp2-col--bid">Bid<div className="wcp2-col-sub">Disc · Yield</div></div>
        <div className="wcp2-col wcp2-col--ask">Ask<div className="wcp2-col-sub">Disc · Yield</div></div>
      </div>

      {/* Chain table */}
      <div className="wcp2-scroll">
        {loading ? (
          <div className="wcp2-loading">Loading chain...</div>
        ) : strikes.length === 0 ? (
          <div className="wcp2-loading">No data for this expiry</div>
        ) : (
          <table className="wcp2-table">
            <tbody>
              {strikes.map(s => (
                <WheelChainRow key={s.strike} strike={s} onClick={handleStrikeClick} />
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="wcp2-footer">Tap a strike to place order</div>
    </>
  )

  return (
    <>
      {/* Desktop: inline panel */}
      <div className="wcp2 wcp2--desktop">{panelContent}</div>

      {/* Mobile: bottom sheet overlay */}
      <div className="wcp2-sheet-overlay" onClick={onClose}>
        <div className="wcp2-sheet" onClick={e => e.stopPropagation()}>
          <div className="wcp2-sheet__handle" />
          {panelContent}
        </div>
      </div>
    </>
  )
}
```

- [ ] **Step 2: Rewrite WheelChainPanel.css**

Replace the entire file with side panel + bottom sheet styling. Key sections:

```css
/* Desktop: inline panel (same slot as OrderPanel) */
.wcp2--desktop {
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  max-height: 100%;
}

/* Header */
.wcp2-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.wcp2-ticker { font-size: 16px; font-weight: 700; color: var(--text-primary); }

.wcp2-type {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.wcp2-type--csp { background: var(--csp-bg); border: 1px solid var(--csp-border); color: var(--indigo-text, #818cf8); }
.wcp2-type--cc { background: var(--cc-bg); border: 1px solid var(--cc-border); color: var(--orange-text, #fb923c); }

.wcp2-close {
  background: none; border: none; color: var(--text-muted); cursor: pointer;
  margin-left: auto; padding: 4px; border-radius: var(--radius-sm);
}
.wcp2-close:hover { color: var(--text-primary); }

/* Quote bar */
.wcp2-quote {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.wcp2-quote__price { font-size: 18px; font-weight: 700; font-family: var(--font-mono); color: var(--text-primary); }
.wcp2-quote__change { font-size: 11px; font-family: var(--font-mono); }
.wcp2-quote__change--up { color: var(--success-text); }
.wcp2-quote__change--down { color: var(--danger-text); }
.wcp2-quote__live { display: flex; align-items: center; gap: 4px; font-size: 9px; color: var(--success-text); margin-left: auto; }
.wcp2-quote__dot { width: 5px; height: 5px; border-radius: 50%; background: var(--accent); animation: wcp2-pulse 2s infinite; }
@keyframes wcp2-pulse { 0%,100%{opacity:1;} 50%{opacity:0.4;} }

/* Expiry selector */
.wcp2-expiry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.wcp2-expiry__label { font-size: 10px; text-transform: uppercase; letter-spacing: 0.4px; color: var(--text-muted); font-weight: 500; }
.wcp2-expiry__desktop { flex: 1; position: relative; display: flex; align-items: center; }
.wcp2-expiry__select {
  width: 100%; padding: 5px 28px 5px 10px; border-radius: var(--radius-sm);
  background: rgba(255,255,255,0.04); border: 1px solid var(--border);
  font-size: 12px; font-family: inherit; color: var(--text-primary);
  cursor: pointer; appearance: none; -webkit-appearance: none; outline: none;
}
.wcp2-expiry__chevron { position: absolute; right: 10px; color: var(--text-muted); pointer-events: none; }
.wcp2-expiry__mobile { display: none; }

/* Column headers */
.wcp2-cols {
  display: flex;
  padding: 6px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.wcp2-col { font-size: 9px; text-transform: uppercase; letter-spacing: 0.4px; color: var(--text-secondary); font-weight: 500; }
.wcp2-col-sub { font-size: 7px; color: var(--text-muted); text-transform: none; letter-spacing: 0; margin-top: 1px; }
.wcp2-col--strike { flex: 1.2; }
.wcp2-col--bid { flex: 1; text-align: right; }
.wcp2-col--ask { flex: 1; text-align: right; }

/* Chain scroll */
.wcp2-scroll { overflow-y: auto; flex: 1; }
.wcp2-table { width: 100%; border-collapse: collapse; }
.wcp2-loading { display: flex; align-items: center; justify-content: center; min-height: 120px; color: var(--text-muted); font-size: 13px; }
.wcp2-footer { padding: 6px 16px; text-align: center; font-size: 9px; color: var(--text-muted); border-top: 1px solid var(--border); flex-shrink: 0; }

/* Bottom sheet (mobile) */
.wcp2-sheet-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  z-index: 100;
  align-items: flex-end;
}
.wcp2-sheet {
  width: 100%;
  max-height: 88%;
  background: var(--bg-secondary);
  border-radius: var(--radius-xl, 16px) var(--radius-xl, 16px) 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.wcp2-sheet__handle { width: 36px; height: 4px; border-radius: 2px; background: rgba(255,255,255,0.15); margin: 10px auto 6px; }

/* Responsive */
@media (min-width: 768px) {
  .wcp2--desktop { display: flex; }
  .wcp2-sheet-overlay { display: none !important; }
}

@media (max-width: 767px) {
  .wcp2--desktop { display: none; }
  .wcp2-sheet-overlay { display: flex; }
  .wcp2-expiry__desktop { display: none; }
  .wcp2-expiry__mobile {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    flex: 1;
  }
  .wcp2-expiry__trigger {
    display: flex; align-items: center; gap: 6px;
    background: none; border: none; cursor: pointer; font-family: inherit;
  }
  .wcp2-expiry__trigger-label { font-size: 14px; font-weight: 600; color: var(--text-primary); }
  .wcp2-expiry__trigger-dte { font-size: 10px; font-family: var(--font-mono); color: var(--text-muted); }
  .wcp2-expiry__trigger-chevron { color: var(--text-muted); }
  .wcp2-expiry__dots { display: flex; align-items: center; gap: 6px; }
  .wcp2-expiry__dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.2); cursor: pointer; }
  .wcp2-expiry__dot--active { width: 8px; height: 8px; background: var(--accent); }
  .wcp2-expiry { flex-direction: column; align-items: center; }
  .wcp2-expiry__label { display: none; }
}
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/WheelChainPanel.tsx frontend/src/components/wheel/WheelChainPanel.css
git commit -m "feat(wheel): rewrite WheelChainPanel as side panel (desktop) + bottom sheet (mobile)"
```

---

### Task 3: Wire WheelPage to open chain panel from slot clicks

**Files:**
- Modify: `frontend/src/pages/WheelPage.tsx`

- [ ] **Step 1: Add chain panel state and imports**

Add to imports:
```typescript
import { WheelChainPanel } from '@/components/wheel/WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'
```

Add state after existing `selectedPosition` state:
```typescript
const [chainPanel, setChainPanel] = useState<ChainPanelContext | null>(null)
```

- [ ] **Step 2: Update slot click handlers**

Replace the `handleEmptySlotClick` and `handleCCSlotClick` handlers:

```typescript
const handleEmptySlotClick = useCallback((ticker: string, expiryDate: string) => {
  setChainPanel({ ticker, expiryDate, optionSide: 'put' })
  setSelectedPosition(null)
}, [])

const handleCCSlotClick = useCallback((ticker: string, expiryDate: string) => {
  setChainPanel({ ticker, expiryDate, optionSide: 'call' })
  setSelectedPosition(null)
}, [])
```

- [ ] **Step 3: Add strike selection handler**

Add a handler that transitions from chain panel to order panel:

```typescript
const handleStrikeSelect = useCallback((ticker: string, expiry: string, strike: number, optionSide: 'put' | 'call') => {
  setChainPanel(null)
  setSelectedPosition({
    ticker,
    expiryDate: expiry,
    position: {
      id: 0,
      type: optionSide === 'put' ? 'CSP' : 'CC',
      strike,
      premium: null,
      currentPrice: null,
      pnl: null,
      otmPercent: null,
      quantity: 1,
      currency: 'USD',
      accountName: null,
      accountNumber: null,
      connectionId: activeConnections[0]?.id ?? 0,
    },
  })
}, [activeConnections])
```

- [ ] **Step 4: Update position click handler to close chain panel**

```typescript
const handlePositionClick = useCallback((position: WheelPosition, ticker: string, expiryDate: string) => {
  setSelectedPosition({ position, ticker, expiryDate })
  setChainPanel(null)
}, [])
```

- [ ] **Step 5: Render chain panel in the panel slot**

Update the panel area in the JSX. Replace the existing `{selectedPosition && ...}` block:

```tsx
{(selectedPosition || chainPanel) && (
  <div className="wheel-page__panel">
    {chainPanel && !selectedPosition && (
      <WheelChainPanel
        context={chainPanel}
        spotPrice={underlyingPrices[chainPanel.ticker] ?? 0}
        onClose={() => setChainPanel(null)}
        onStrikeSelect={handleStrikeSelect}
      />
    )}
    {selectedPosition && (
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
    )}
  </div>
)}
```

- [ ] **Step 6: Verify build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 7: Run tests**

Run: `cd frontend && npm run test:run`
Expected: All existing tests pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/WheelPage.tsx
git commit -m "feat(wheel): wire slot clicks to chain panel, strike selection to order panel"
```

---

### Task 4: Deploy, UAT test, and update documentation

**Files:**
- Modify: `docs/reference/frontend-map.md`

- [ ] **Step 1: Build and deploy**

```bash
docker compose up --build -d frontend
```

- [ ] **Step 2: UAT test with Playwright**

Login → navigate to /wheel → verify:
- Click "+" slot → chain panel opens as side panel (desktop) showing puts
- Click "Sell CC" slot → chain panel opens showing calls
- Expiry dropdown changes the chain data
- Strike rows show discount · yield
- ATM row highlighted green, ITM rows dimmed
- Click a strike row → chain panel closes, order panel opens with pre-filled data
- Close button (×) → panel closes
- Mobile at 375px: bottom sheet modal with drag handle, expiry dots

- [ ] **Step 3: Update frontend-map.md**

Update the WheelChainPanel entry:
```
| `WheelChainPanel.tsx` + `.css` | Side panel (desktop 340px) + bottom sheet (mobile). Options chain with live WebSocket streaming. Shows Strike/Delta, Bid/Ask with discount·yield per strike. Expiry dropdown (desktop) / trigger+dots (mobile). Auto-selects puts or calls from context. ATM highlighted, ITM dimmed. Tap strike → transitions to OrderPanel. |
```

- [ ] **Step 4: Commit**

```bash
git add docs/reference/frontend-map.md
git commit -m "docs: update frontend-map for wheel Screen 2 chain panel redesign"
```
