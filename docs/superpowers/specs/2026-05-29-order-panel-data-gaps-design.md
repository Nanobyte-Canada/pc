# OrderPanel Data Gaps — Design Spec

Bridges 3 data-wiring gaps between the Wheel Strategy OrderPanel UI and existing backend APIs.

## Gap 1: Buying Power Display

### Data Flow

`WheelPage` already calls `useDashboardCash(selectedConnectionId)` (line 46) which returns:

```typescript
interface DashboardCashResponse {
  availableCash: CurrencyAmount[]
  buyingPower: CurrencyAmount[]       // ← this is unused
  totalCashCAD: number
  totalBuyingPowerCAD?: number
}

interface CurrencyAmount {
  currency: string  // "USD" | "CAD"
  amount: number
}
```

### Changes

**WheelPage.tsx** — pass `buyingPower` array to OrderPanel:

```tsx
<OrderPanel
  ...existing props
  buyingPower={cashData?.buyingPower ?? []}
/>
```

**OrderPanel.tsx** — add prop and render:

- Add `buyingPower: CurrencyAmount[]` to `OrderPanelProps`
- Import `CurrencyAmount` from `@/types/dashboard`
- In the account selector section (line 320-351), after the account dropdown, add a right-aligned buying power display on the same row
- Filter `buyingPower` by the option's currency:
  - Map `getCurrencyLabel(ticker)` result: `'US$'` → `'USD'`, `'C$'` → `'CAD'`
  - Find matching `CurrencyAmount` from the array
  - Display: `US$ 33,108` (formatted with currency label)
- If no match found, show `--`

### Visual Placement

Inside the existing `.order-panel__account-selector` div, after the account info row:

```
┌─────────────────────────────────────┐
│ [Q] Questrade TFSA ••0190  ▼       │
│                      US$ 33,108    │
└─────────────────────────────────────┘
```

The buying power value sits right-aligned below the account dropdown, styled with `order-panel__buying-power` class — small label "Buying Power" on left, amount on right.

## Gap 2: Live Option Quote via WebSocket

### Current State

OrderPanel's bid/ask/mid cards (lines 170-185) show hardcoded `--` values. The WebSocket infrastructure exists:

- `useMarketDataWebSocket` hook — subscribe/unsubscribe to specific options
- `useQuoteStore` — Zustand store holding `chains[underlying].expirations[expiry][strike].call/put`
- Backend WebSocket at `/ws/quotes` accepts `subscribe_option` / `unsubscribe_option` actions

### Changes

**OrderPanel.tsx** — add WebSocket subscription and store reads:

1. Import `useMarketDataWebSocket` and `useQuoteStore`
2. Derive a subscription key from the current form state: `{ ticker, expiration, strike, optionType }`
3. Add a `useEffect` that:
   - When all 4 fields are populated, calls `subscribeOption(ticker, expiration, strike, optionType.toUpperCase())`
   - On cleanup or when any field changes, calls `unsubscribeOption` with the previous values
   - Skips subscription if any field is empty/unset
4. Read live quote from the store. The store keys strikes as `"100.0"` (with decimal) while the form stores `"100"`, so normalize the key:
   ```typescript
   const chainData = useQuoteStore(s => s.chains[ticker])
   const strikeKey = strike.includes('.') ? strike : strike + '.0'
   const optionQuote = chainData?.expirations?.[expiration]?.[strikeKey]?.[optionType === 'Call' ? 'call' : 'put']
   ```
5. Update the 3 quote cards:
   - **Bid**: `optionQuote?.bid?.toFixed(2) ?? '--'`
   - **Mid**: `optionQuote?.mid?.toFixed(2) ?? '--'` (falls back to `position?.currentPrice`)
   - **Ask**: `optionQuote?.ask?.toFixed(2) ?? '--'`
6. Also update the main price display and the "Last" line from `optionQuote.last` when available

### Initial Load

On panel open, also fire a one-time REST fetch via `getOptionsChain(ticker)` from `marketDataService` to seed the store immediately (WebSocket may take a moment to deliver first quote). The `setChain` store action populates the same data path the subscription reads from.

### Subscription Lifecycle

```
Panel opens → subscribeOption(SOXL, 2026-06-18, 100, PUT)
User changes strike to 105 → unsubscribeOption(...100...) → subscribeOption(...105...)
User changes expiry → unsubscribe old → subscribe new
Panel closes → unsubscribeOption (cleanup)
```

## Gap 3: Currency-Aware Buying Power

### Logic

The existing `getCurrencyLabel(ticker)` returns `'US$'` or `'C$'`. Add a small helper to map this to the API currency code:

```typescript
function getOptionCurrency(ticker: string): string {
  return getCurrencyLabel(ticker) === 'C$' ? 'CAD' : 'USD'
}
```

Use this to:

1. Filter `buyingPower` array to show the relevant currency amount in the account section
2. Format the estimated total with the correct currency label (already working via `currencyLabel`)

### Edge Case

If `buyingPower` array is empty or has no entry for the option's currency, display `--` rather than `$0`.

## Files Changed

| File | Change |
|------|--------|
| `frontend/src/components/wheel/OrderPanel.tsx` | Add `buyingPower` prop, WebSocket subscription, store reads, buying power display, currency mapping |
| `frontend/src/components/wheel/OrderPanel.css` | Add `.order-panel__buying-power` styles |
| `frontend/src/pages/WheelPage.tsx` | Pass `buyingPower={cashData?.buyingPower ?? []}` to OrderPanel |

## No Backend Changes

All APIs already exist and return the needed data. This is purely frontend wiring.
