# Wheel Strategy — Screen 2: Options Quotes & Live Feed

## Overview

Options chain viewer that opens when clicking a "+" (CSP) or "Sell CC" slot on Screen 1. Shows live-streaming strike data with delta, discount, and annualized yield for the selected ticker and expiry. Selecting a strike navigates to Screen 3 (Order).

**Mockup reference:** `.superpowers/brainstorm/51960-1780073119/content/screen2-v3.html`

## Navigation Context

```
Screen 1 → Click "+" slot → Screen 2 (puts, auto-selected expiry)
Screen 1 → Click "Sell CC" slot → Screen 2 (calls, auto-selected expiry)
Screen 2 → Tap a strike row → Screen 3 (Order, pre-filled with ticker/expiry/strike/type)
Screen 2 → Close/Back → Screen 1
```

**Put/Call auto-selection:** No toggle. Arriving from a CSP "+" slot shows puts only. Arriving from a CC "Sell CC" slot shows calls only. The context determines the option type.

## Layout

### Desktop — Side Panel (340px)

The grid from Screen 1 compresses on the left. A 340px side panel opens on the right (same pattern as the existing OrderPanel). The clicked slot is highlighted with an indigo border to show which cell opened the panel.

**Panel contents (top to bottom):**
1. **Header:** Ticker name + CSP/CC type pill + close (×) button
2. **Live quote bar:** Spot price (18px mono bold) + change amount/percent + pulsing green "Live" dot
3. **Expiry dropdown:** `<select>` showing "Jun 5, 2026 — 7 DTE" with all available expiries, Monthly tags included. Auto-set to the clicked slot's expiry.
4. **Column headers:** Strike/Delta | Bid (Discount · Yield) | Ask (Discount · Yield)
5. **Chain table (scrollable):** One row per strike, sorted descending (ITM at top, OTM at bottom)
6. **Footer:** "Tap a strike to place order"

### Mobile — Bottom Sheet Modal

A bottom sheet slides up over a dimmed Screen 1 background. Drag handle at top to dismiss.

**Modal contents (top to bottom):**
1. **Header row:** Ticker + CSP pill + spot price + change% + live dot
2. **Expiry selector:** Dropdown trigger (date label + DTE + chevron) with dot indicators below — matching the AccountNavBar mobile pattern exactly (active dot = green #10b981, inactive = rgba(255,255,255,0.2))
3. **Column headers:** Same 3-column layout as desktop but slightly smaller fonts
4. **Chain table (scrollable):** Same data, tap a row → Screen 3

**Modal height:** max-height 88%, scrollable content area

## Chain Table Data

### Columns

| Column | Data | Format |
|--------|------|--------|
| Strike | Strike price | $XXX.XX, mono 13px bold |
| Delta | Option delta | δ -0.XX, mono 9px muted |
| Bid | Bid price | $XX.XX, mono 13px bold |
| Bid Discount | (spot − strike + bid) / spot | +X.X% or -X.X%, secondary color |
| Bid Yield | Annualized: (bid / strike) × (365 / DTE) | XXX%, green (success-text) |
| Ask | Ask price | Same format as bid |
| Ask Discount | Same formula with ask | Same format |
| Ask Yield | Same formula with ask | Same format |

### Row Highlighting

- **ATM row:** Green tint background (`rgba(16,185,129,0.06)`) + left border accent (`var(--accent)`) — strike within 1% of spot
- **ITM rows:** Dimmed to 40% opacity — for puts: strike > spot; for calls: strike < spot
- **OTM rows:** Normal styling
- **Hover:** Indigo tint (`rgba(99,102,241,0.06)`)

### Formulas

```
Discount = (spotPrice - strike + premium) / spotPrice
Yield = (premium / strike) × (365 / DTE)
ATM = |strike - spotPrice| / spotPrice < 0.01
ITM (put) = strike > spotPrice
ITM (call) = strike < spotPrice
```

## Live Streaming

- Initial load: REST fetch via `getOptionsChainWithGreeks(ticker)` to seed the store
- Ongoing: WebSocket subscription via `subscribeChain(ticker)` for real-time bid/ask/delta updates
- Pulsing green dot indicates active WebSocket connection
- Cleanup: `unsubscribeChain` on panel close or ticker change

## Expiry Selection

### Desktop
Standard `<select>` dropdown. Each option shows: "Jun 5, 2026 — 7 DTE" format. Monthly expiries (3rd Friday) include "(Monthly)" suffix. Auto-set to the expiry of the clicked slot.

### Mobile
Dropdown trigger + dot indicators (matching AccountNavBar mobile pattern):
- Trigger: "Jun 5, 2026" label + "7 DTE" in mono muted + chevron
- Dots below: one per available expiry, active = green (#10b981, 8px), inactive = white 20% opacity (6px)
- Tapping trigger opens a bottom sheet to select expiry (same as AccountNavBar sheet)

## Theme Tokens

Uses existing CSS custom properties — no new colors:
- Panel background: `--bg-secondary`
- Borders: `--border`
- CSP type pill: `--csp-bg`, `--csp-border`, `--indigo-text`
- CC type pill: `--cc-bg`, `--cc-border`, `--orange-text`
- ATM highlight: `var(--accent)` border, success-light background
- ITM dimming: `opacity: 0.4`
- Yield values: `--success-text` (#6ee7b7)
- All numerics: `--font-mono`

## Backend

**No changes needed.** Uses existing APIs:
- `GET /market-data-api/api/v1/chains/{underlying}/greeks` — options chain with Greeks
- WebSocket `/ws/quotes` — `subscribe_chain` / `unsubscribe_chain` for streaming
- `GET /market-data-api/api/v1/quotes/{symbol}` — spot price

Existing frontend infrastructure:
- `useMarketDataWebSocket` hook (subscribe/unsubscribe)
- `useQuoteStore` Zustand store (chains map, quotes map)
- `getOptionsChainWithGreeks` from `marketDataService`
- `WheelChainPanel` has the existing chain-building logic (compute discount, yield, ATM/ITM detection) — reuse this logic

## Files to Change

| File | Action |
|------|--------|
| `frontend/src/components/wheel/WheelChainPanel.tsx` | Rewrite — from modal overlay to side panel (desktop) + bottom sheet (mobile) |
| `frontend/src/components/wheel/WheelChainPanel.css` | Rewrite — side panel + modal styling |
| `frontend/src/components/wheel/WheelChainRow.tsx` | Update — add yield/discount display, mobile sizing |
| `frontend/src/pages/WheelPage.tsx` | Update — wire "+" and "Sell CC" clicks to open chain panel with context (ticker, expiry, put/call) |
| `frontend/src/types/wheel.ts` | Update — add chain panel context type (ticker, expiry, optionType) |
