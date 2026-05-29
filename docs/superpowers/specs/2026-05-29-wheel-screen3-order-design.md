# Wheel Strategy — Screen 3: Order Submission

## Overview

Polished order entry form that opens when clicking a position card (Screen 1) or tapping a strike row (Screen 2). All fields are editable. Order type dropdown is populated from the selected broker's capabilities.

**Mockup reference:** `.superpowers/brainstorm/51960-1780073119/content/screen3-order.html`

## Navigation — Two Entry Paths

### Path A: Close existing position (from Screen 1)
```
Screen 1 → Click position card ($100 Put) → Screen 3
```
- Pre-filled: ticker, option type (Put/Call), strike, expiry from the position
- Pre-filled: quantity from position, limit price from current market mid
- Action context: closing an existing position (Buy to Close)

### Path B: Open new position (from Screen 2)
```
Screen 1 → Click "+" → Screen 2 → Tap strike row → Screen 3
```
- Pre-filled: ticker, option type (auto from context), strike from tapped row, expiry from selected tab
- Pre-filled: quantity = 1, limit price from bid (for sells)
- Action context: opening a new position (Sell to Open)

### Both paths: all fields remain fully editable after pre-fill.

## Layout

### Desktop — Side Panel (340px)
Same pattern as Screen 2. Grid compresses on the left, 340px order panel on the right. The clicked position card gets an indigo highlight with glow to show which position is being acted on.

### Mobile — Bottom Sheet
Scrollable bottom sheet over dimmed Screen 1 background. Drag handle to dismiss. All form fields use larger touch targets (8px padding, 13px font) than desktop.

## Editable Fields

All fields are editable dropdowns, inputs, or steppers — even when pre-filled from context.

| # | Field | Type | Pre-fill Logic | Notes |
|---|-------|------|----------------|-------|
| 1 | Option Type | Dropdown | Put (from CSP) or Call (from CC) | Put / Call |
| 2 | Expiration | Dropdown | From position expiry or Screen 2 tab | All available Friday expiries out to 6 months |
| 3 | Strike | Dropdown | From position strike or Screen 2 row | ±20 strikes around current price, step varies by price |
| 4 | Order Type | Dropdown | Limit (default) | **Broker-specific** — see below |
| 5 | Quantity | Stepper (+/−) | Position qty (Path A) or 1 (Path B) | Min 1, no max |
| 6 | Limit Price | Text input | Current mid price | Only shown when order type = Limit or Stop Limit |
| 7 | Stop Price | Text input | N/A | Only shown when order type = Stop or Stop Limit |
| 8 | Duration | Dropdown | Day (default) | Day / GTC |
| 9 | Account | Dropdown | Position's account (Path A) or first active (Path B) | Shows broker icon + account type + masked number |

## Broker-Specific Order Types

The Order Type dropdown is populated dynamically from the selected broker's `BrokerCapabilities.supportedOrderTypes`. When the user switches accounts, the order type options update.

| Broker | Supported Order Types | Options Trading |
|--------|----------------------|-----------------|
| IBKR | Market, Limit, Stop, Stop Limit | Yes |
| Questrade | Market, Limit, Stop, Stop Limit | Yes |
| Wealthsimple | Market, Limit | No (hidden from wheel) |

### Conditional Fields by Order Type

| Order Type | Limit Price | Stop Price |
|------------|------------|------------|
| Market | Hidden | Hidden |
| Limit | Shown | Hidden |
| Stop | Hidden | Shown |
| Stop Limit | Shown | Shown |

### Backend API
The `BrokerCapabilities` data is already available via the broker gateway. The frontend needs to fetch capabilities for the selected broker connection and filter the Order Type dropdown accordingly. This requires a new lightweight API call or piggybacking on existing connection data.

## Panel Sections (top to bottom)

### 1. Header
- Ticker symbol (16px bold)
- Description: "Cash-Secured Put" or "Covered Call" or "New Order" (10px muted uppercase)
- Close (×) button

### 2. Live Quote
- Underlying spot price (20px mono bold)
- P&L from position (green/red, 13px mono) — only for Path A
- Three cards: Bid / Mid / Ask (from WebSocket, same as current implementation)

### 3. Contract Title
- "SOXL 19 Jun 2026 100 Put" (11px semibold secondary)
- "Last: US$500.00" (10px muted)

### 4. Contract Fields (2×2 grid)
- Option Type, Expiration, Strike, Order Type — all `<select>` dropdowns

### 5. Quantity Stepper
- −/+ buttons, numeric display (mono 13px bold)

### 6. Price Inputs (conditional)
- Limit Price: currency label + text input (shown for Limit / Stop Limit)
- Stop Price: currency label + text input (shown for Stop / Stop Limit)

### 7. Duration
- Day / GTC dropdown

### 8. Account Selector
- Broker icon (Q/IB/W colored badge) + dropdown
- Buying power displayed below in option's currency (existing implementation)
- Changing account updates Order Type dropdown options

### 9. Estimated Total
- `quantity × limitPrice × 100` (or "Market" if market order)
- Divider line above

### 10. Action Buttons
- Buy (green: `rgba(16,185,129,0.15)` bg, `#10b981` text)
- Sell (red: `rgba(248,113,113,0.15)` bg, `#f87171` text)

## Theme Tokens

Same as Screen 1 and 2 — all from `index.css` dark theme. No new colors.

## Backend Changes

### Required
- Expose `supportedOrderTypes` per broker connection to the frontend. Options:
  - A: Add to existing connections response (already has broker info)
  - B: New endpoint `GET /api/v1/brokers/connections/{id}/capabilities`
  - **Recommended: Option A** — add `supportedOrderTypes: string[]` to `BrokerConnectionDto`

### Optional (future)
- Order impact preview: `POST /gateway/connections/{id}/accounts/{acc}/orders/impact` already exists but isn't wired to the frontend

## Files to Change

| File | Action |
|------|--------|
| `frontend/src/components/wheel/OrderPanel.tsx` | Update — add Stop/StopLimit fields, broker-specific order types, ensure all fields editable |
| `frontend/src/components/wheel/OrderPanel.css` | Update — add stop price styling, polish to match theme |
| `frontend/src/pages/WheelPage.tsx` | Update — wire position clicks to OrderPanel with close-position context |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/BrokerDtos.kt` | Update — add `supportedOrderTypes` to connection DTO |
| `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerConnectionService.kt` | Update — populate order types from gateway capabilities |
