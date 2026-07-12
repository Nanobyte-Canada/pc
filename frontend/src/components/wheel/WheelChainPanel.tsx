import { useState, useEffect, useMemo, useCallback, useRef } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { getOptionExpirations, getOptionsChainForExpiry, getQuote } from '@/services/marketDataService'
import { searchInstruments } from '@/services/screenerService'
import { formatCurrency } from '@/services/brokerService'
import { useToast } from '@/stores/toastStore'
import { WheelChainRow } from './WheelChainRow'
import type { ChainPanelContext, WheelChainStrike } from '@/types/wheel'
import type { SearchResult } from '@/types/screener'
import type { Quote } from '@/types/options'
import { X, ChevronDown, AlertTriangle, Search } from 'lucide-react'
import './WheelChainPanel.css'

interface WheelChainPanelProps {
  context: ChainPanelContext
  spotPrice: number
  onClose: () => void
  onStrikeSelect: (ticker: string, expiry: string, strike: number, optionSide: 'put' | 'call') => void
  /** Called when user selects a ticker in search mode, allowing parent to update context. */
  onTickerSelect?: (ticker: string, optionSide: 'put' | 'call') => void
}

/**
 * WheelChainPanel — left panel for options chain view.
 *
 * Supports two modes:
 * 1. **Chain mode** (default): shows header with ticker, quote, expiry selector, and strike table.
 * 2. **Search-first mode** (`context.searchMode && !context.ticker`): shows a search bar at the top,
 *    search results with ticker/name/quote, and CSP/CC buttons to enter chain mode.
 *
 * In search-first mode the search bar remains visible after selecting a ticker,
 * allowing users to search for another ticker without closing the panel.
 */
export function WheelChainPanel({ context, spotPrice: initialSpotPrice, onClose, onStrikeSelect, onTickerSelect }: WheelChainPanelProps) {
  const [loading, setLoading] = useState(!context.searchMode || !!context.ticker)
  const [expirations, setExpirations] = useState<string[]>([])
  const [selectedExpiry, setSelectedExpiry] = useState(context.expiryDate)
  const [loadingExpiry, setLoadingExpiry] = useState(false)
  const [strikesPerSide, setStrikesPerSide] = useState(25)
  const [chainError, setChainError] = useState<string | null>(null)
  const [ibkrDisconnected, setIbkrDisconnected] = useState(false)

  // Search state
  const [searchQuery, setSearchQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [searchResults, setSearchResults] = useState<SearchResult[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [selectedSearchResult, setSelectedSearchResult] = useState<SearchResult | null>(null)
  const [searchResultQuotes, setSearchResultQuotes] = useState<Record<string, Quote>>({})
  const [isSearchMode, setIsSearchMode] = useState(context.searchMode === true && !context.ticker)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const searchContainerRef = useRef<HTMLDivElement>(null)

  const chain = useQuoteStore(s => s.chains[context.ticker])
  const quote = useQuoteStore(s => s.quotes[context.ticker])
  const setChain = useQuoteStore(s => s.setChain)
  const ibkrConnected = useQuoteStore(s => s.ibkrConnected)
  const toast = useToast()
  const { subscribe, unsubscribe, subscribeChainExpiry, unsubscribeChain, switchChainExpiry } = useMarketDataWebSocket()

  const isTickerSelected = !isSearchMode && !!context.ticker
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

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(searchQuery), 250)
    return () => clearTimeout(timer)
  }, [searchQuery])

  // Execute search when debounced query changes
  useEffect(() => {
    if (!debouncedQuery || debouncedQuery.length < 1) {
      setSearchResults([])
      setSearchError(null)
      return
    }
    let cancelled = false
    setSearchLoading(true)
    searchInstruments(debouncedQuery, undefined, 8)
      .then(resp => {
        if (cancelled) return
        setSearchResults(resp.data)
        setSearchError(null)
        setSearchLoading(false)
      })
      .catch(err => {
        if (cancelled) return
        setSearchError(err instanceof Error ? err.message : 'Search failed')
        setSearchLoading(false)
      })
    return () => { cancelled = true }
  }, [debouncedQuery])

  // Fetch quotes for search results
  const fetchedQuoteTickersRef = useRef<Set<string>>(new Set())
  useEffect(() => {
    if (searchResults.length === 0) return
    let cancelled = false
    const toFetch = searchResults
      .map(r => r.ticker)
      .filter(t => !fetchedQuoteTickersRef.current.has(t))
    if (toFetch.length === 0) return

    toFetch.forEach(t => fetchedQuoteTickersRef.current.add(t))

    Promise.allSettled(
      toFetch.map(async ticker => {
        const q = await getQuote(ticker)
        return { ticker, quote: q }
      })
    ).then(results => {
      if (cancelled) return
      const updates: Record<string, Quote> = {}
      results.forEach(r => {
        if (r.status === 'fulfilled') {
          updates[r.value.ticker] = r.value.quote
        }
      })
      if (Object.keys(updates).length > 0) {
        setSearchResultQuotes(prev => ({ ...prev, ...updates }))
      }
    })
    return () => { cancelled = true }
  }, [searchResults])

  // Close search dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        searchContainerRef.current &&
        !searchContainerRef.current.contains(event.target as Node)
      ) {
        // Don't close if clicking inside the chain panel
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Initialize chain data when ticker is selected
  useEffect(() => {
    if (!isTickerSelected) return
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
  }, [context.ticker, context.expiryDate, isTickerSelected, subscribe, unsubscribe, subscribeChainExpiry, unsubscribeChain, setChain, strikesPerSide, side, toast])

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

  /** Handle selecting a ticker from search results */
  const handleSearchResultSelect = useCallback((result: SearchResult) => {
    setSelectedSearchResult(result)
  }, [])

  /** Handle CSP/CC button click after search result selection — enters chain mode */
  const handleSearchOptionSide = useCallback((ticker: string, optionSide: 'put' | 'call') => {
    if (onTickerSelect) {
      onTickerSelect(ticker, optionSide)
    }
    setIsSearchMode(false)
  }, [onTickerSelect])

  const priceChange = quote ? (quote.last - (quote.bid || quote.last)) : 0
  const priceChangePct = quote && quote.bid > 0 ? ((quote.last / quote.bid - 1) * 100) : 0
  const hasChange = quote != null && Math.abs(priceChange) > 0.001

  /** Render the search bar (shown in search mode and optionally in chain mode) */
  const renderSearchBar = () => (
    <div className="wcp2-search" ref={searchContainerRef}>
      <div className="wcp2-search__input-wrap">
        <Search size={14} className="wcp2-search__icon" />
        <input
          ref={searchInputRef}
          type="text"
          className="wcp2-search__input"
          placeholder="Search by ticker or name..."
          value={searchQuery}
          onChange={e => {
            setSearchQuery(e.target.value)
            setSelectedSearchResult(null)
          }}
          onKeyDown={e => {
            if (e.key === 'Escape') {
              if (isTickerSelected) {
                setIsSearchMode(false)
                setSearchQuery('')
              }
            }
          }}
          autoFocus
        />
        {searchQuery && (
          <button className="wcp2-search__clear" onClick={() => {
            setSearchQuery('')
            setSelectedSearchResult(null)
            searchInputRef.current?.focus()
          }} aria-label="Clear search">
            <X size={12} />
          </button>
        )}
      </div>

      {/* Search results */}
      {searchQuery.length >= 1 && !selectedSearchResult && (
        <div className="wcp2-search__results">
          {searchLoading && <div className="wcp2-search__loading">Searching...</div>}
          {searchError && <div className="wcp2-search__error">{searchError}</div>}
          {!searchLoading && !searchError && searchResults.length === 0 && (
            <div className="wcp2-search__empty">No results found</div>
          )}
          {!searchLoading && !searchError && searchResults.map(result => {
            const resultQuote = searchResultQuotes[result.ticker]
            return (
              <div
                key={result.id}
                className="wcp2-search__result"
                onClick={() => handleSearchResultSelect(result)}
              >
                <div className="wcp2-search__result-main">
                  <span className="wcp2-search__result-ticker">{result.ticker}</span>
                  <span className="wcp2-search__result-name">{result.name}</span>
                </div>
                {resultQuote && (
                  <div className="wcp2-search__result-quote">
                    <span className="wcp2-search__result-price">{formatCurrency(resultQuote.last, 'USD')}</span>
                    {resultQuote.bid > 0 && (
                      <span className="wcp2-search__result-bidask">
                        BID {resultQuote.bid.toFixed(2)} · ASK {resultQuote.ask.toFixed(2)}
                      </span>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* CSP/CC selection for a search result */}
      {selectedSearchResult && (
        <div className="wcp2-search__option-select">
          <div className="wcp2-search__option-header">
            <span className="wcp2-search__option-ticker">{selectedSearchResult.ticker}</span>
            <span className="wcp2-search__option-name">{selectedSearchResult.name}</span>
            <button className="wcp2-search__option-back" onClick={() => setSelectedSearchResult(null)}>
              Back
            </button>
          </div>
          <div className="wcp2-search__option-buttons">
            <button
              className="wcp2-search__option-btn wcp2-search__option-btn--csp"
              onClick={() => handleSearchOptionSide(selectedSearchResult.ticker, 'put')}
            >
              <span className="wcp2-search__option-btn-label">CSP</span>
              <span className="wcp2-search__option-btn-sub">Cash-Secured Put</span>
            </button>
            <button
              className="wcp2-search__option-btn wcp2-search__option-btn--cc"
              onClick={() => handleSearchOptionSide(selectedSearchResult.ticker, 'call')}
            >
              <span className="wcp2-search__option-btn-label">CC</span>
              <span className="wcp2-search__option-btn-sub">Covered Call</span>
            </button>
          </div>
        </div>
      )}
    </div>
  )

  const panelContent = (
    <>
      {/* Header — always show with close button */}
      <div className="wcp2-header">
        {isTickerSelected ? (
          <>
            <span className="wcp2-ticker">{context.ticker}</span>
            <span className={`wcp2-type ${isCsp ? 'wcp2-type--csp' : 'wcp2-type--cc'}`}>{typeLabelShort}</span>
          </>
        ) : (
          <span className="wcp2-ticker">Select Ticker</span>
        )}
        <button className="wcp2-close" onClick={onClose} aria-label="Close"><X size={16} /></button>
      </div>

      {/* Search bar — always visible */}
      {renderSearchBar()}

      {/* IBKR disconnected warning */}
      {ibkrDisconnected && (
        <div className="wcp2-banner wcp2-banner--warning">
          <AlertTriangle size={14} />
          <span>IBKR Gateway is disconnected. Data may be stale or unavailable.</span>
        </div>
      )}

      {/* Chain error banner */}
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

      {/* Chain view — only when ticker is selected and not actively searching */}
      {isTickerSelected && !selectedSearchResult && (
        <>
          <div className="wcp2-quote">
            <span className="wcp2-quote__price">{formatCurrency(spotPrice, 'USD')}</span>
            {hasChange && (
              <span className={`wcp2-quote__change ${priceChange >= 0 ? 'wcp2-quote__change--up' : 'wcp2-quote__change--down'}`}>
                {priceChange >= 0 ? '+' : ''}{formatCurrency(Math.abs(priceChange), 'USD')} ({priceChangePct >= 0 ? '+' : ''}{priceChangePct.toFixed(1)}%)
              </span>
            )}
            <span className="wcp2-quote__live"><span className="wcp2-quote__dot" /> Live</span>
          </div>

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

          <div className="wcp2-footer">Tap a strike to place order</div>
        </>
      )}
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
