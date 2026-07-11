import { useState, useEffect, useMemo, useCallback } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { getOptionExpirations, getOptionsChainForExpiry } from '@/services/marketDataService'
import { formatCurrency } from '@/services/brokerService'
import { useToast } from '@/stores/toastStore'
import { WheelChainRow } from './WheelChainRow'
import type { ChainPanelContext, WheelChainStrike } from '@/types/wheel'
import { X, ChevronDown, AlertTriangle, Search } from 'lucide-react'
import './WheelChainPanel.css'

interface WheelChainPanelProps {
  context: ChainPanelContext
  spotPrice: number
  onClose: () => void
  onStrikeSelect: (ticker: string, expiry: string, strike: number, optionSide: 'put' | 'call') => void
}

export function WheelChainPanel({ context, spotPrice: initialSpotPrice, onClose, onStrikeSelect }: WheelChainPanelProps) {
  const [loading, setLoading] = useState(true)
  const [expirations, setExpirations] = useState<string[]>([])
  const [selectedExpiry, setSelectedExpiry] = useState(context.expiryDate)
  const [loadingExpiry, setLoadingExpiry] = useState(false)
  const [strikesPerSide, setStrikesPerSide] = useState(25)
  const [chainError, setChainError] = useState<string | null>(null)
  const [ibkrDisconnected, setIbkrDisconnected] = useState(false)

  const chain = useQuoteStore(s => s.chains[context.ticker])
  const quote = useQuoteStore(s => s.quotes[context.ticker])
  const setChain = useQuoteStore(s => s.setChain)
  const ibkrConnected = useQuoteStore(s => s.ibkrConnected)
  const toast = useToast()
  const { subscribe, unsubscribe, subscribeChainExpiry, unsubscribeChain, switchChainExpiry } = useMarketDataWebSocket()

  const spotPrice = quote?.last ?? quote?.mid ?? initialSpotPrice
  const isCsp = context.optionSide === 'put'
  const side = isCsp ? 'put' as const : 'call' as const
  const typeLabelShort = isCsp ? 'CSP' : 'CC'

  const availableExpiries = expirations

  const dte = useMemo(() => {
    const now = new Date()
    const exp = new Date(selectedExpiry + 'T00:00:00')
    return Math.max(1, Math.round((exp.getTime() - now.getTime()) / 86400000))
  }, [selectedExpiry])

  useEffect(() => {
    let cancelled = false
    async function init() {
      try {
        const expData = await getOptionExpirations(context.ticker)
        if (cancelled) return

        const expList = expData.expirations
        setExpirations(expList)

        const target = new Date(context.expiryDate + 'T00:00:00').getTime()
        let bestExpiry = expList[0]
        let bestDiff = Infinity
        for (const k of expList) {
          const diff = Math.abs(new Date(k + 'T00:00:00').getTime() - target)
          if (diff < bestDiff) { bestDiff = diff; bestExpiry = k }
        }
        const chosenExpiry = bestDiff <= 3 * 86400000 ? bestExpiry : expList[0]
        setSelectedExpiry(chosenExpiry)

        const chainData = await getOptionsChainForExpiry(context.ticker, chosenExpiry, { strikesPerSide, side })
        if (cancelled) return
        setChain(context.ticker, chainData)
        setLoading(false)
        setChainError(null)

        subscribeChainExpiry(context.ticker, chosenExpiry, side)
      } catch (err) {
        if (!cancelled) {
          setLoading(false)
          const msg = err instanceof Error && err.message.includes('503')
            ? 'IBKR Gateway may be unavailable. Please check the connection and try again.'
            : 'Failed to load options chain. Please try again.'
          setChainError(msg)
          toast.error(msg)
        }
      }
    }
    init()
    subscribe(context.ticker)
    return () => {
      cancelled = true
      unsubscribe(context.ticker)
      unsubscribeChain(context.ticker)
    }
  }, [context.ticker, context.expiryDate, subscribe, unsubscribe, subscribeChainExpiry, unsubscribeChain, setChain, strikesPerSide, side, toast])

  // Track IBKR connection status via WebSocket
  useEffect(() => {
    setIbkrDisconnected(ibkrConnected === false)
  }, [ibkrConnected])

  const handleExpiryChange = useCallback(async (newExpiry: string) => {
    setSelectedExpiry(newExpiry)
    setLoadingExpiry(true)
    setChainError(null)
    try {
      const chainData = await getOptionsChainForExpiry(context.ticker, newExpiry, { strikesPerSide, side })
      const existing = chain
      if (existing && chainData.expirations) {
        setChain(context.ticker, { ...existing, expirations: { ...existing.expirations, ...chainData.expirations } })
      } else {
        setChain(context.ticker, chainData)
      }
      switchChainExpiry(context.ticker, newExpiry, side)
    } catch (err) {
      const msg = err instanceof Error && err.message.includes('503')
        ? 'IBKR Gateway may be unavailable. Please check the connection and try again.'
        : 'Failed to load expiry data. Please try again.'
      setChainError(msg)
      toast.error(msg)
    } finally {
      setLoadingExpiry(false)
    }
  }, [context.ticker, chain, setChain, switchChainExpiry, strikesPerSide, side, toast])

  const handleStrikesChange = useCallback(async (newStrikes: number) => {
    setStrikesPerSide(newStrikes)
    setLoadingExpiry(true)
    setChainError(null)
    try {
      const chainData = await getOptionsChainForExpiry(context.ticker, selectedExpiry, { strikesPerSide: newStrikes, side })
      const existing = chain
      if (existing && chainData.expirations) {
        setChain(context.ticker, { ...existing, expirations: { ...existing.expirations, ...chainData.expirations } })
      } else {
        setChain(context.ticker, chainData)
      }
    } catch (err) {
      const msg = err instanceof Error && err.message.includes('503')
        ? 'IBKR Gateway may be unavailable. Please check the connection and try again.'
        : 'Failed to reload chain. Please try again.'
      setChainError(msg)
      toast.error(msg)
    } finally {
      setLoadingExpiry(false)
    }
  }, [context.ticker, selectedExpiry, side, setChain, chain, toast])

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

      const discount = spotPrice > 0 ? (spotPrice - strikeNum) / spotPrice : null
      const bidYield = bid != null && strikeNum > 0 && bid > 0
        ? (bid / strikeNum) * (365 / dte) : null
      const askYield = ask != null && strikeNum > 0 && ask > 0
        ? (ask / strikeNum) * (365 / dte) : null

      const isATM = spotPrice > 0 && Math.abs(strikeNum - spotPrice) / spotPrice < 0.01
      const isITM = isCsp ? strikeNum > spotPrice : strikeNum < spotPrice

      rows.push({ strike: strikeNum, bid, ask, delta, discount, bidYield, askYield, isATM, isITM })
    }
    return isCsp
      ? rows.sort((a, b) => a.strike - b.strike)
      : rows.sort((a, b) => b.strike - a.strike)
  }, [chain, selectedExpiry, spotPrice, dte, isCsp])

  const handleStrikeClick = useCallback((strike: WheelChainStrike) => {
    onStrikeSelect(context.ticker, selectedExpiry, strike.strike, context.optionSide)
  }, [context.ticker, selectedExpiry, context.optionSide, onStrikeSelect])

  const priceChange = quote ? (quote.last - (quote.bid || quote.last)) : 0
  const priceChangePct = quote && quote.bid > 0 ? ((quote.last / quote.bid - 1) * 100) : 0
  const hasChange = quote != null && Math.abs(priceChange) > 0.001

  const panelContent = (
    <>
      <div className="wcp2-header">
        <span className="wcp2-ticker">{context.ticker}</span>
        <span className={`wcp2-type ${isCsp ? 'wcp2-type--csp' : 'wcp2-type--cc'}`}>{typeLabelShort}</span>
        <button className="wcp2-close" onClick={onClose} aria-label="Close"><X size={16} /></button>
      </div>

      {ibkrDisconnected && (
        <div className="wcp2-banner wcp2-banner--warning">
          <AlertTriangle size={14} />
          <span>IBKR Gateway is disconnected. Data may be stale or unavailable.</span>
        </div>
      )}

      {chainError && (
        <div className="wcp2-banner wcp2-banner--error">
          <AlertTriangle size={14} />
          <span>{chainError}</span>
          <button className="wcp2-banner__retry" onClick={() => handleExpiryChange(selectedExpiry)}>
            Retry
          </button>
          <button className="wcp2-banner__dismiss" onClick={() => setChainError(null)} aria-label="Dismiss">
            <X size={12} />
          </button>
        </div>
      )}

      <div className="wcp2-quote">
        <span className="wcp2-quote__price">{formatCurrency(spotPrice, 'USD')}</span>
        {hasChange && (
          <span className={`wcp2-quote__change ${priceChange >= 0 ? 'wcp2-quote__change--up' : 'wcp2-quote__change--down'}`}>
            {priceChange >= 0 ? '+' : ''}{formatCurrency(Math.abs(priceChange), 'USD')} ({priceChangePct >= 0 ? '+' : ''}{priceChangePct.toFixed(1)}%)
          </span>
        )}
        <span className="wcp2-quote__live"><span className="wcp2-quote__dot" /> Live</span>
      </div>

      {context.searchMode && !context.ticker ? (
        <div className="wcp2-empty">
          <Search size={32} className="wcp2-empty__icon" />
          <span className="wcp2-empty__text">Select a ticker from the watchlist</span>
        </div>
      ) : (
        <>
          <div className="wcp2-expiry">
            <span className="wcp2-expiry__label">Expiry</span>
            <div className="wcp2-expiry__desktop">
              <select
                className="wcp2-expiry__select"
                value={selectedExpiry}
                onChange={e => handleExpiryChange(e.target.value)}
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
            <div className="wcp2-expiry__mobile">
              <button className="wcp2-expiry__trigger" onClick={() => {
                const idx = availableExpiries.indexOf(selectedExpiry)
                const next = (idx + 1) % availableExpiries.length
                if (availableExpiries.length > 0) handleExpiryChange(availableExpiries[next])
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
                    onClick={() => handleExpiryChange(exp)}
                  />
                ))}
              </div>
            </div>
            <div className="wcp2-strikes">
              <span className="wcp2-strikes__label">Strikes</span>
              <div className="wcp2-strikes__options">
                {[25, 50, 60].map(n => (
                  <button
                    key={n}
                    className={`wcp2-strikes__btn ${strikesPerSide === n ? 'wcp2-strikes__btn--active' : ''}`}
                    onClick={() => handleStrikesChange(n)}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="wcp2-cols">
            <div className="wcp2-col wcp2-col--strike">Strike<div className="wcp2-col-sub">Delta</div></div>
            <div className="wcp2-col wcp2-col--bid">Bid<div className="wcp2-col-sub">Disc · Yield</div></div>
            <div className="wcp2-col wcp2-col--ask">Ask<div className="wcp2-col-sub">Disc · Yield</div></div>
          </div>

          <div className="wcp2-scroll">
            {loading || loadingExpiry ? (
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
        </>
      )}

      <div className="wcp2-footer">{context.searchMode ? 'Search for a ticker to get started' : 'Tap a strike to place order'}</div>
    </>
  )

  return (
    <>
      <div className="wcp2 wcp2--desktop">{panelContent}</div>
      <div className="wcp2-sheet-overlay" onClick={onClose}>
        <div className="wcp2-sheet" onClick={e => e.stopPropagation()}>
          <div className="wcp2-sheet__handle" />
          {panelContent}
        </div>
      </div>
    </>
  )
}
