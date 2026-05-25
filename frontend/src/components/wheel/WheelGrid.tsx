import type { WheelGridData, WheelPosition } from '@/types/wheel'
import { getDteUrgency } from '@/types/wheel'
import { PositionCard } from './PositionCard'
import { formatCurrency } from '@/services/brokerService'
import './WheelGrid.css'

interface WheelGridProps {
  data: WheelGridData
  showAccount: boolean
  onPositionClick: (position: WheelPosition, ticker: string, expiryDate: string) => void
  onEmptySlotClick: (ticker: string, expiryDate: string) => void
}

export function WheelGrid({ data, showAccount, onPositionClick, onEmptySlotClick }: WheelGridProps) {
  return (
    <div className="wheel-grid-wrapper">
      <div className="wheel-grid-legend">
        <div className="wheel-legend-item">
          <div className="wheel-legend-swatch wheel-legend-csp" />
          <span>Cash-Secured Put (CSP)</span>
        </div>
        <div className="wheel-legend-item">
          <div className="wheel-legend-swatch wheel-legend-cc" />
          <span>Covered Call (CC)</span>
        </div>
        <div className="wheel-legend-item">
          <span className="wheel-legend-dashed">- - -</span>
          <span>Open slot</span>
        </div>
      </div>

      <div className="wheel-grid-container">
        <table className="wheel-grid-table">
          <thead>
            <tr>
              <th className="wheel-grid-expiry-header">Expiry</th>
              {data.tickers.map(t => (
                <th key={t.symbol} className="wheel-grid-ticker-header">
                  {t.symbol}
                  <div className="wheel-grid-ticker-price">
                    {t.currentPrice != null ? formatCurrency(t.currentPrice, 'USD') : '—'}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="wheel-grid-body">
            {data.expiryRows.map(row => {
              const urgency = getDteUrgency(row.dte)
              return (
                <tr key={row.expiryDate}>
                  <td className="wheel-grid-expiry-cell">
                    <div className="wheel-expiry-label">
                      {formatExpiryDate(row.expiryDate)}
                      <span className={`wheel-dte-badge wheel-dte-${urgency}`}>
                        {row.dte} DTE
                      </span>
                      <span className="wheel-expiry-sub">
                        {row.dayOfWeek}{row.isMonthly ? ' (Monthly)' : ''}
                      </span>
                    </div>
                  </td>
                  {data.tickers.map(t => {
                    const cell = row.cells[t.symbol]
                    const hasPositions = cell && cell.positions.length > 0
                    return (
                      <td key={t.symbol} className="wheel-grid-cell">
                        <div className="wheel-cell-content">
                          {hasPositions && cell.positions.map(pos => (
                            <PositionCard
                              key={pos.id}
                              position={pos}
                              showAccount={showAccount}
                              onClick={p => onPositionClick(p, t.symbol, row.expiryDate)}
                            />
                          ))}
                          <div
                            className={`wheel-empty-slot${hasPositions ? ' wheel-empty-slot-compact' : ''}`}
                            onClick={() => onEmptySlotClick(t.symbol, row.expiryDate)}
                            role="button"
                            tabIndex={0}
                            onKeyDown={e => { if (e.key === 'Enter') onEmptySlotClick(t.symbol, row.expiryDate) }}
                          >
                            +
                          </div>
                        </div>
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr className="wheel-grid-totals-row">
              <td className="wheel-grid-expiry-cell">
                <span className="wheel-totals-label">Totals</span>
              </td>
              {data.tickers.map(t => {
                const totals = data.totals[t.symbol]
                const pnlClass = (totals?.totalPnl.usd ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'
                const pnlPrefix = (totals?.totalPnl.usd ?? 0) >= 0 ? '+' : ''
                const cadPnlPrefix = (totals?.totalPnl.cad ?? 0) >= 0 ? '+' : ''
                return (
                  <td key={t.symbol} className="wheel-grid-cell">
                    <div className="wheel-totals-data">
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">Positions</span>
                        <span className="wheel-totals-item-value">{totals?.positionCount ?? 0}</span>
                      </div>
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">CSP Exposure</span>
                        <div>
                          <span className="wheel-totals-item-value">
                            {formatCurrency(totals?.cspExposure.usd ?? 0, 'USD')}
                          </span>
                          <span className="wheel-totals-cad">
                            {formatCurrency(totals?.cspExposure.cad ?? 0, 'CAD')}
                          </span>
                        </div>
                      </div>
                      <div className="wheel-totals-item">
                        <span className="wheel-totals-item-label">P&L</span>
                        <div>
                          <span className={`wheel-totals-item-value ${pnlClass}`}>
                            {pnlPrefix}{formatCurrency(totals?.totalPnl.usd ?? 0, 'USD')}
                          </span>
                          <span className={`wheel-totals-cad ${pnlClass}`}>
                            {cadPnlPrefix}{formatCurrency(totals?.totalPnl.cad ?? 0, 'CAD')}
                          </span>
                        </div>
                      </div>
                    </div>
                  </td>
                )
              })}
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  )
}

function formatExpiryDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
