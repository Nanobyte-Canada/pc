import { useState } from 'react'
import type { OptionsChain, OptionQuoteData, Leg, LegAction, OptionTypeName } from '@/types/options'
import { useStrategyStore } from '@/stores/strategyStore'
import './OptionsChainTable.css'

interface OptionsChainTableProps {
  chain: OptionsChain
}

export function OptionsChainTable({ chain }: OptionsChainTableProps) {
  const expirations = Object.keys(chain.expirations).sort()
  const [selectedExpiry, setSelectedExpiry] = useState(expirations[0] ?? '')
  const addLeg = useStrategyStore((s) => s.addLeg)

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
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
        {expirations.map((exp) => (
          <button
            key={exp}
            className={`strategy-selector__button ${selectedExpiry === exp ? 'strategy-selector__button--active' : ''}`}
            onClick={() => setSelectedExpiry(exp)}
          >
            {exp}
          </button>
        ))}
      </div>

      <table>
        <thead>
          <tr>
            <th>Bid</th>
            <th>Ask</th>
            <th>Last</th>
            <th>Delta</th>
            <th>Vol</th>
            <th className="chain-table__strike-col">Strike</th>
            <th>Bid</th>
            <th>Ask</th>
            <th>Last</th>
            <th>Delta</th>
            <th>Vol</th>
          </tr>
          <tr>
            <th colSpan={5} style={{ textAlign: 'center', color: 'var(--success, #22c55e)' }}>CALLS</th>
            <th></th>
            <th colSpan={5} style={{ textAlign: 'center', color: 'var(--error, #ef4444)' }}>PUTS</th>
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
                <td className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''}`}
                  onClick={() => data.call && handleClickOption(data.call, 'SELL')}>
                  {data.call?.ask.toFixed(2) ?? '-'}
                </td>
                <td className={callITM ? 'chain-table__itm' : ''}>{data.call?.last.toFixed(2) ?? '-'}</td>
                <td className={callITM ? 'chain-table__itm' : ''}>{data.call?.greeks?.delta.toFixed(3) ?? '-'}</td>
                <td className={callITM ? 'chain-table__itm' : ''}>{data.call?.volume.toLocaleString() ?? '-'}</td>
                <td className="chain-table__strike-cell">{Math.round(strike)}</td>
                <td
                  className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''}`}
                  onClick={() => data.put && handleClickOption(data.put, 'BUY')}
                >
                  {data.put?.bid.toFixed(2) ?? '-'}
                </td>
                <td className={`chain-table__put-side ${putITM ? 'chain-table__itm' : ''}`}
                  onClick={() => data.put && handleClickOption(data.put, 'SELL')}>
                  {data.put?.ask.toFixed(2) ?? '-'}
                </td>
                <td className={putITM ? 'chain-table__itm' : ''}>{data.put?.last.toFixed(2) ?? '-'}</td>
                <td className={putITM ? 'chain-table__itm' : ''}>{data.put?.greeks?.delta.toFixed(3) ?? '-'}</td>
                <td className={putITM ? 'chain-table__itm' : ''}>{data.put?.volume.toLocaleString() ?? '-'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
