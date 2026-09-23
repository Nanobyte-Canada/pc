import { useState, useEffect, useRef } from 'react'
import type { OptionsChain, OptionQuoteData, Leg, LegAction, OptionTypeName, StrikeData } from '@/types/options'
import { useStrategyStore } from '@/stores/strategyStore'
import './OptionsChainTable.css'

interface OptionsChainTableProps {
  chain: OptionsChain
  onExpiryChange?: (expiry: string) => void
  strikesPerSide: number
  onStrikesPerSideChange: (n: number) => void
}

type ChainSide = 'calls' | 'puts'

function useFlashDirection(price: number | undefined, key: string) {
  const prev = useRef<Map<string, number>>(new Map())
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const [flash, setFlash] = useState<'' | 'up' | 'down'>('')

  useEffect(() => {
    clearTimeout(timeoutRef.current)
    if (price === undefined) {
      setFlash('')
      return
    }
    const last = prev.current.get(key)
    prev.current.set(key, price)
    if (last === undefined || last === price) {
      setFlash('')
      return
    }
    setFlash(price > last ? 'up' : 'down')
    timeoutRef.current = setTimeout(() => setFlash(''), 400)
    return () => clearTimeout(timeoutRef.current)
  }, [price, key])

  return flash
}

interface ChainRowProps {
  strikeKey: string
  data: StrikeData
  spot: number
  legs: Leg[]
  toggleLeg: (quote: OptionQuoteData, action: LegAction) => void
  variant: 'desktop' | 'mobile'
  mobileSide: ChainSide
}

function ChainRow({ strikeKey, data, spot, toggleLeg, variant, mobileSide }: ChainRowProps) {
  const strike = parseFloat(strikeKey)
  const isATM = Math.abs(strike - spot) <= spot * 0.01
  const callITM = strike < spot
  const putITM = strike > spot

  const callBidFlash = useFlashDirection(data.call?.bid, `${strikeKey}:call:bid`)
  const callAskFlash = useFlashDirection(data.call?.ask, `${strikeKey}:call:ask`)
  const putBidFlash = useFlashDirection(data.put?.bid, `${strikeKey}:put:bid`)
  const putAskFlash = useFlashDirection(data.put?.ask, `${strikeKey}:put:ask`)
  const mobileQuote = mobileSide === 'calls' ? data.call : data.put
  const mobileBidFlash = useFlashDirection(mobileQuote?.bid, `${strikeKey}:${mobileSide}:bid`)
  const mobileAskFlash = useFlashDirection(mobileQuote?.ask, `${strikeKey}:${mobileSide}:ask`)

  if (variant === 'mobile') {
    const isITM = mobileSide === 'calls' ? strike < spot : strike > spot

    return (
      <tr className={isATM ? 'chain-table__atm-row' : ''}>
        <td className="chain-table__strike-cell">{Math.round(strike)}</td>
        <td
          className={`chain-table__${mobileSide === 'calls' ? 'call' : 'put'}-side ${isITM ? 'chain-table__itm' : ''} ${mobileBidFlash ? `chain-table__flash--${mobileBidFlash}` : ''}`}
          data-flash={mobileBidFlash}
          onClick={() => mobileQuote && toggleLeg(mobileQuote, 'BUY')}
        >
          {mobileQuote?.bid?.toFixed(2) ?? '-'}
        </td>
        <td
          className={`chain-table__${mobileSide === 'calls' ? 'call' : 'put'}-side ${isITM ? 'chain-table__itm' : ''} ${mobileAskFlash ? `chain-table__flash--${mobileAskFlash}` : ''}`}
          data-flash={mobileAskFlash}
          onClick={() => mobileQuote && toggleLeg(mobileQuote, 'SELL')}
        >
          {mobileQuote?.ask?.toFixed(2) ?? '-'}
        </td>
        <td className={`chain-table__delta ${isITM ? 'chain-table__itm' : ''}`}>
          {mobileQuote?.greeks?.delta?.toFixed(3) ?? '-'}
        </td>
      </tr>
    )
  }

  return (
    <tr className={isATM ? 'chain-table__atm-row' : ''}>
      <td
        className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''} ${callBidFlash ? `chain-table__flash--${callBidFlash}` : ''}`}
        data-flash={callBidFlash}
        onClick={() => data.call && toggleLeg(data.call, 'BUY')}
      >
        {data.call?.bid?.toFixed(2) ?? '-'}
      </td>
      <td
        className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''} ${callAskFlash ? `chain-table__flash--${callAskFlash}` : ''}`}
        data-flash={callAskFlash}
        onClick={() => data.call && toggleLeg(data.call, 'SELL')}
      >
        {data.call?.ask?.toFixed(2) ?? '-'}
      </td>
      <td className={`chain-table__delta ${callITM ? 'chain-table__itm' : ''}`}>
        {data.call?.greeks?.delta?.toFixed(3) ?? '-'}
      </td>
      <td className="chain-table__strike-cell">{Math.round(strike)}</td>
      <td className={`chain-table__delta ${putITM ? 'chain-table__itm' : ''}`}>
        {data.put?.greeks?.delta?.toFixed(3) ?? '-'}
      </td>
      <td
        className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''} ${putBidFlash ? `chain-table__flash--${putBidFlash}` : ''}`}
        data-flash={putBidFlash}
        onClick={() => data.put && toggleLeg(data.put, 'BUY')}
      >
        {data.put?.bid?.toFixed(2) ?? '-'}
      </td>
      <td
        className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''} ${putAskFlash ? `chain-table__flash--${putAskFlash}` : ''}`}
        data-flash={putAskFlash}
        onClick={() => data.put && toggleLeg(data.put, 'SELL')}
      >
        {data.put?.ask?.toFixed(2) ?? '-'}
      </td>
    </tr>
  )
}

export function OptionsChainTable({ chain, onExpiryChange, strikesPerSide, onStrikesPerSideChange }: OptionsChainTableProps) {
  const expirations = Object.keys(chain.expirations).sort()
  const [selectedExpiry, setSelectedExpiry] = useState(expirations[0] ?? '')
  const [mobileSide, setMobileSide] = useState<ChainSide>('calls')
  const [isMobile, setIsMobile] = useState(false)
  const addLeg = useStrategyStore((s) => s.addLeg)
  const removeLeg = useStrategyStore((s) => s.removeLeg)
  const legs = useStrategyStore((s) => s.legs)

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)')
    setIsMobile(mq.matches)
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const toggleLeg = (quote: OptionQuoteData, action: LegAction) => {
    const existing = legs.findIndex(
      (l) =>
        l.strike === quote.strike &&
        l.expiry === quote.expiry &&
        l.optionType === quote.optionType &&
        l.action === action
    )
    if (existing >= 0) {
      removeLeg(existing)
      return
    }
    addLeg({
      action,
      optionType: quote.optionType as OptionTypeName,
      strike: quote.strike,
      expiry: quote.expiry,
      quantity: 1,
      price: quote.mid,
      bid: quote.bid,
      ask: quote.ask,
      mid: quote.mid,
      delta: quote.greeks?.delta,
      symbol: quote.underlying,
    })
  }

  if (expirations.length === 0) {
    return <div className="chain-table__empty">No options chain data available</div>
  }

  const strikes = chain.expirations[selectedExpiry]
  if (!strikes) return null

  const strikeEntries = Object.entries(strikes).sort(([a], [b]) => parseFloat(a) - parseFloat(b))
  const spot = chain.spotPrice

  return (
    <div className="chain-table">
      {/* Desktop: expiry tabs */}
      <div className="chain-table__expiry-tabs">
        {expirations.map((exp) => (
          <button
            key={exp}
            className={`chain-table__expiry-tab ${selectedExpiry === exp ? 'chain-table__expiry-tab--active' : ''}`}
            onClick={() => { setSelectedExpiry(exp); onExpiryChange?.(exp) }}
          >
            {exp}
          </button>
        ))}
        <div className="chain-table__strikes-selector">
          <span className="chain-table__strikes-label">Strikes</span>
          {[25, 50, 60].map(n => (
            <button
              key={n}
              className={`chain-table__strikes-btn ${strikesPerSide === n ? 'chain-table__strikes-btn--active' : ''}`}
              onClick={() => onStrikesPerSideChange(n)}
            >
              {n}
            </button>
          ))}
        </div>
      </div>

      {/* Mobile: expiry dropdown + calls/puts toggle */}
      <div className="chain-table__mobile-controls">
        <select
          className="chain-table__expiry-dropdown"
          value={selectedExpiry}
          onChange={(e) => { setSelectedExpiry(e.target.value); onExpiryChange?.(e.target.value) }}
        >
          {expirations.map((exp) => (
            <option key={exp} value={exp}>{exp}</option>
          ))}
        </select>

        <div className="chain-table__side-toggle">
          <button
            className={`chain-table__side-btn ${mobileSide === 'calls' ? 'chain-table__side-btn--active' : ''}`}
            onClick={() => setMobileSide('calls')}
          >
            Calls
          </button>
          <button
            className={`chain-table__side-btn ${mobileSide === 'puts' ? 'chain-table__side-btn--active' : ''}`}
            onClick={() => setMobileSide('puts')}
          >
            Puts
          </button>
        </div>
        <div className="chain-table__strikes-selector chain-table__strikes-selector--mobile">
          {[25, 50, 60].map(n => (
            <button
              key={n}
              className={`chain-table__strikes-btn ${strikesPerSide === n ? 'chain-table__strikes-btn--active' : ''}`}
              onClick={() => onStrikesPerSideChange(n)}
            >
              {n}
            </button>
          ))}
        </div>
      </div>

      {/* Desktop: CALLS / PUTS header labels */}
      <div className="chain-table__header-labels">
        <div className="chain-table__header-calls">Calls</div>
        <div className="chain-table__header-strike-spacer" />
        <div className="chain-table__header-puts">Puts</div>
      </div>

      {/* ── Desktop: bidirectional 7-column table ── */}
      {!isMobile && (
        <table>
          <thead>
            <tr>
              <th>Bid</th>
              <th>Ask</th>
              <th>Delta</th>
              <th className="chain-table__strike-col">Strike</th>
              <th>Delta</th>
              <th>Bid</th>
              <th>Ask</th>
            </tr>
          </thead>
          <tbody>
            {strikeEntries.map(([strikeKey, data]) => {
              if (!data) return null
              return (
                <ChainRow
                  key={strikeKey}
                  strikeKey={strikeKey}
                  data={data}
                  spot={spot}
                  legs={legs}
                  toggleLeg={toggleLeg}
                  variant="desktop"
                  mobileSide={mobileSide}
                />
              )
            })}
          </tbody>
        </table>
      )}

      {/* ── Mobile: 4-column single-side table ── */}
      {isMobile && (
        <table>
          <thead>
            <tr>
              <th>Strike</th>
              <th>Bid</th>
              <th>Ask</th>
              <th>Delta</th>
            </tr>
          </thead>
          <tbody>
            {strikeEntries.map(([strikeKey, data]) => {
              if (!data) return null
              return (
                <ChainRow
                  key={strikeKey}
                  strikeKey={strikeKey}
                  data={data}
                  spot={spot}
                  legs={legs}
                  toggleLeg={toggleLeg}
                  variant="mobile"
                  mobileSide={mobileSide}
                />
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
