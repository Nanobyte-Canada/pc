import { useState, useEffect, useMemo, useCallback } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { getOptionsChainWithGreeks } from '@/services/marketDataService'
import { submitOptionsOrder } from '@/services/optionsStrategyService'
import { formatCurrency } from '@/services/brokerService'
import { WheelChainRow } from './WheelChainRow'
import type { WheelChainStrike } from '@/types/wheel'
import type { OptionsOrderRequest } from '@/types/options'
import './WheelChainPanel.css'

interface WheelChainPanelProps {
  ticker: string
  expiryDate: string
  spotPrice: number
  onClose: () => void
}

export function WheelChainPanel({ ticker, expiryDate, spotPrice: initialSpotPrice, onClose }: WheelChainPanelProps) {
  const [loading, setLoading] = useState(true)
  const [orderStrike, setOrderStrike] = useState<WheelChainStrike | null>(null)
  const [ordering, setOrdering] = useState(false)
  const [expiryOverride, setExpiryOverride] = useState<string | null>(null)

  const chain = useQuoteStore(s => s.chains[ticker])
  const quote = useQuoteStore(s => s.quotes[ticker])
  const setChain = useQuoteStore(s => s.setChain)
  const { subscribe, subscribeChain, unsubscribe, unsubscribeChain } = useMarketDataWebSocket()

  const spotPrice = quote?.last ?? quote?.mid ?? initialSpotPrice

  // Find best matching expiry in chain data (handles ±2 day offsets between broker and chain)
  const [selectedExpiry, availableExpiries] = useMemo(() => {
    if (!chain?.expirations) return [expiryDate, []]
    const keys = Object.keys(chain.expirations).sort()
    if (keys.length === 0) return [expiryDate, []]

    // Try exact match first
    if (chain.expirations[expiryDate]) return [expiryDate, keys]

    // Find closest within ±3 days
    const target = new Date(expiryDate + 'T00:00:00').getTime()
    let bestKey = keys[0]
    let bestDiff = Infinity
    for (const k of keys) {
      const diff = Math.abs(new Date(k + 'T00:00:00').getTime() - target)
      if (diff < bestDiff) { bestDiff = diff; bestKey = k }
    }
    // Only use closest if within 3 days
    if (bestDiff <= 3 * 86400000) return [bestKey, keys]
    // No close match — show first available
    return [keys[0], keys]
  }, [chain, expiryDate])

  const activeExpiry = expiryOverride ?? selectedExpiry

  // Compute DTE from active expiry
  const dte = useMemo(() => {
    const now = new Date()
    const exp = new Date(activeExpiry + 'T00:00:00')
    return Math.max(1, Math.round((exp.getTime() - now.getTime()) / 86400000))
  }, [activeExpiry])

  // Load chain on mount, subscribe to streaming
  useEffect(() => {
    let cancelled = false

    async function loadChain() {
      try {
        const chainData = await getOptionsChainWithGreeks(ticker)
        if (!cancelled) {
          setChain(ticker, chainData)
          setLoading(false)
        }
      } catch {
        if (!cancelled) setLoading(false)
      }
    }

    loadChain()
    subscribe(ticker)
    subscribeChain(ticker)

    return () => {
      cancelled = true
      unsubscribe(ticker)
      unsubscribeChain(ticker)
    }
  }, [ticker, subscribe, subscribeChain, unsubscribe, unsubscribeChain, setChain])

  // Build strike rows from chain data filtered to selected expiry
  const strikes: WheelChainStrike[] = useMemo(() => {
    if (!chain?.expirations) return []

    const expiryData = chain.expirations[activeExpiry]
    if (!expiryData) return []

    const rows: WheelChainStrike[] = []

    for (const [strikeKey, data] of Object.entries(expiryData)) {
      const strikeNum = parseFloat(strikeKey)
      // For CSP, show puts
      const option = data.put
      if (!option) continue

      const bid: number | null = option.bid ?? null
      const ask: number | null = option.ask ?? null
      const delta: number | null = option.greeks?.delta ?? null

      // Discount: (spot - strike + premium) / spot
      const bidDiscount = bid != null && spotPrice > 0
        ? (spotPrice - strikeNum + bid) / spotPrice
        : null
      const askDiscount = ask != null && spotPrice > 0
        ? (spotPrice - strikeNum + ask) / spotPrice
        : null

      // Annualized yield: (premium / strike) * (365 / dte)
      const bidYield = bid != null && strikeNum > 0 && bid > 0
        ? (bid / strikeNum) * (365 / dte)
        : null
      const askYield = ask != null && strikeNum > 0 && ask > 0
        ? (ask / strikeNum) * (365 / dte)
        : null

      const isATM = spotPrice > 0 && Math.abs(strikeNum - spotPrice) / spotPrice < 0.01
      const isITM = strikeNum > spotPrice // For puts, ITM when strike > spot

      rows.push({
        strike: strikeNum,
        bid,
        ask,
        delta,
        bidDiscount,
        askDiscount,
        bidYield,
        askYield,
        isATM,
        isITM,
      })
    }

    return rows.sort((a, b) => b.strike - a.strike) // Descending: ITM at top, OTM at bottom
  }, [chain, activeExpiry, spotPrice, dte])

  const handleStrikeClick = useCallback((strike: WheelChainStrike) => {
    setOrderStrike(strike)
  }, [])

  const handlePlaceOrder = useCallback(async () => {
    if (!orderStrike || !orderStrike.bid) return
    setOrdering(true)
    try {
      const order: OptionsOrderRequest = {
        strategyType: 'PROTECTIVE_PUT',
        underlying: ticker,
        legs: [{
          action: 'SELL',
          optionType: 'PUT',
          strike: orderStrike.strike,
          expiry: activeExpiry,
          quantity: 1,
          price: orderStrike.bid,
        }],
        quantity: 1,
        orderType: 'LIMIT',
        netPrice: orderStrike.bid,
      }
      await submitOptionsOrder(order)
      setOrderStrike(null)
      onClose()
    } catch {
      // Order failed — stay on dialog
    } finally {
      setOrdering(false)
    }
  }, [orderStrike, ticker, activeExpiry, onClose])

  return (
    <div className="wcp-overlay" onClick={onClose}>
      <div className="wcp-panel" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="wcp-header">
          <div>
            <h3 className="wcp-title">{ticker} CSP — {formatExpiryDate(activeExpiry)} ({dte} DTE)</h3>
            <div className="wcp-spot">Spot: {formatCurrency(spotPrice, 'USD')}</div>
          </div>
          <button className="wcp-close" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        {/* Expiry tabs when multiple available */}
        {availableExpiries.length > 1 && (
          <div className="wcp-expiry-tabs">
            {availableExpiries.map(exp => (
              <button
                key={exp}
                className={`wcp-expiry-tab ${(expiryOverride ?? selectedExpiry) === exp ? 'wcp-expiry-tab-active' : ''}`}
                onClick={() => setExpiryOverride(exp)}
              >
                {formatExpiryDate(exp)}
              </button>
            ))}
          </div>
        )}

        {/* Column labels */}
        <div className="wcp-col-labels">
          <div className="wcp-col-label wcp-col-strike">
            <span>Strike</span>
            <span className="wcp-col-sublabel">Delta</span>
          </div>
          <div className="wcp-col-label wcp-col-bid">
            <span>Bid</span>
            <span className="wcp-col-sublabel">Discount &middot; Yield</span>
          </div>
          <div className="wcp-col-label wcp-col-ask">
            <span>Ask</span>
            <span className="wcp-col-sublabel">Discount &middot; Yield</span>
          </div>
        </div>

        {/* Chain table */}
        <div className="wcp-body">
          {loading ? (
            <div className="wcp-loading">Loading chain...</div>
          ) : strikes.length === 0 ? (
            <div className="wcp-loading">No data for this expiry</div>
          ) : (
            <table className="wcp-table">
              <tbody>
                {strikes.map(s => (
                  <WheelChainRow key={s.strike} strike={s} onClick={handleStrikeClick} />
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Footer */}
        <div className="wcp-footer">Tap a row to place a sell order</div>

        {/* Order confirmation dialog */}
        {orderStrike && (
          <div className="wcp-order-overlay" onClick={() => setOrderStrike(null)}>
            <div className="wcp-order-dialog" onClick={e => e.stopPropagation()}>
              <h3 className="wcp-order-title">Sell to Open</h3>
              <div className="wcp-order-details">
                <div className="wcp-order-row">
                  <span>Ticker</span><span>{ticker}</span>
                </div>
                <div className="wcp-order-row">
                  <span>Type</span><span>Cash-Secured Put</span>
                </div>
                <div className="wcp-order-row">
                  <span>Strike</span><span>{formatCurrency(orderStrike.strike, 'USD')}</span>
                </div>
                <div className="wcp-order-row">
                  <span>Expiry</span><span>{expiryDate}</span>
                </div>
                <div className="wcp-order-row">
                  <span>Bid</span><span>{formatCurrency(orderStrike.bid, 'USD')}</span>
                </div>
                <div className="wcp-order-row">
                  <span>Yield</span><span>{orderStrike.bidYield != null ? `${(orderStrike.bidYield * 100).toFixed(1)}% annualized` : '—'}</span>
                </div>
                <div className="wcp-order-row">
                  <span>Capital Required</span><span>{formatCurrency(orderStrike.strike * 100, 'USD')}</span>
                </div>
              </div>
              <div className="wcp-order-actions">
                <button className="wcp-order-btn wcp-order-cancel" onClick={() => setOrderStrike(null)} disabled={ordering}>Cancel</button>
                <button className="wcp-order-btn wcp-order-confirm" onClick={handlePlaceOrder} disabled={ordering}>
                  {ordering ? 'Placing...' : 'Place Order'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function formatExpiryDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
