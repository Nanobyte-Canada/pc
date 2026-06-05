import { useState, useEffect } from 'react'
import type { OptionsChain, OptionQuoteData, Leg, LegAction, OptionTypeName } from '@/types/options'
import { useStrategyStore } from '@/stores/strategyStore'
import './OptionsChainTable.css'

interface OptionsChainTableProps {
  chain: OptionsChain
  onExpiryChange?: (expiry: string) => void
}

type ChainSide = 'calls' | 'puts'

export function OptionsChainTable({ chain, onExpiryChange }: OptionsChainTableProps) {
  const expirations = Object.keys(chain.expirations).sort()
  const [selectedExpiry, setSelectedExpiry] = useState(expirations[0] ?? '')
  const [mobileSide, setMobileSide] = useState<ChainSide>('calls')
  const [isMobile, setIsMobile] = useState(false)
  const addLeg = useStrategyStore((s) => s.addLeg)

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)')
    setIsMobile(mq.matches)
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const handleClickOption = (quote: OptionQuoteData, action: LegAction) => {
    const leg: Leg = {
      action,
      optionType: quote.optionType as OptionTypeName,
      strike: quote.strike,
      expiry: quote.expiry,
      quantity: 1,
      price: quote.mid,
    }
    addLeg(leg)
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
              const strike = parseFloat(strikeKey)
              if (!data) return null
              const isATM = Math.abs(strike - spot) <= (spot * 0.01)
              const callITM = strike < spot
              const putITM = strike > spot

              return (
                <tr key={strikeKey} className={isATM ? 'chain-table__atm-row' : ''}>
                  <td
                    className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''}`}
                    onClick={() => data.call && handleClickOption(data.call, 'BUY')}
                  >
                    {data.call?.bid.toFixed(2) ?? '-'}
                  </td>
                  <td
                    className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''}`}
                    onClick={() => data.call && handleClickOption(data.call, 'SELL')}
                  >
                    {data.call?.ask.toFixed(2) ?? '-'}
                  </td>
                  <td className={`chain-table__delta ${callITM ? 'chain-table__itm' : ''}`}>
                    {data.call?.greeks?.delta.toFixed(3) ?? '-'}
                  </td>
                  <td className="chain-table__strike-cell">{Math.round(strike)}</td>
                  <td className={`chain-table__delta ${putITM ? 'chain-table__itm' : ''}`}>
                    {data.put?.greeks?.delta.toFixed(3) ?? '-'}
                  </td>
                  <td
                    className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''}`}
                    onClick={() => data.put && handleClickOption(data.put, 'BUY')}
                  >
                    {data.put?.bid.toFixed(2) ?? '-'}
                  </td>
                  <td
                    className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''}`}
                    onClick={() => data.put && handleClickOption(data.put, 'SELL')}
                  >
                    {data.put?.ask.toFixed(2) ?? '-'}
                  </td>
                </tr>
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
              const strike = parseFloat(strikeKey)
              if (!data) return null
              const isATM = Math.abs(strike - spot) <= (spot * 0.01)
              const side = mobileSide === 'calls' ? data.call : data.put
              const isITM = mobileSide === 'calls' ? strike < spot : strike > spot

              return (
                <tr key={strikeKey} className={isATM ? 'chain-table__atm-row' : ''}>
                  <td className="chain-table__strike-cell">{Math.round(strike)}</td>
                  <td
                    className={`chain-table__${mobileSide === 'calls' ? 'call' : 'put'}-side ${isITM ? 'chain-table__itm' : ''}`}
                    onClick={() => side && handleClickOption(side, 'BUY')}
                  >
                    {side?.bid.toFixed(2) ?? '-'}
                  </td>
                  <td
                    className={`chain-table__${mobileSide === 'calls' ? 'call' : 'put'}-side ${isITM ? 'chain-table__itm' : ''}`}
                    onClick={() => side && handleClickOption(side, 'SELL')}
                  >
                    {side?.ask.toFixed(2) ?? '-'}
                  </td>
                  <td className={`chain-table__delta ${isITM ? 'chain-table__itm' : ''}`}>
                    {side?.greeks?.delta.toFixed(3) ?? '-'}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
