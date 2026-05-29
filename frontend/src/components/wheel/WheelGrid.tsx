import { useState } from 'react'
import type { WheelGridData, WheelPosition } from '@/types/wheel'
import { getDteUrgency } from '@/types/wheel'
import { PositionCard } from './PositionCard'
import { formatCurrency } from '@/services/brokerService'
import { ChevronDown, ChevronRight, Plus } from 'lucide-react'
import './WheelGrid.css'

interface WheelGridProps {
  data: WheelGridData
  showAccount: boolean
  onPositionClick: (position: WheelPosition, ticker: string, expiryDate: string) => void
  onEmptySlotClick: (ticker: string, expiryDate: string) => void
}

export function WheelGrid({ data, onPositionClick, onEmptySlotClick }: Omit<WheelGridProps, 'showAccount'>) {
  return (
    <>
      {/* Desktop: table grid */}
      <div className="wheel-grid-desktop">
        <DesktopGrid
          data={data}
          onPositionClick={onPositionClick}
          onEmptySlotClick={onEmptySlotClick}
        />
      </div>

      {/* Mobile: expiry-grouped cards */}
      <div className="wheel-grid-mobile">
        <MobileExpiryGroups
          data={data}
          onPositionClick={onPositionClick}
          onEmptySlotClick={onEmptySlotClick}
        />
      </div>
    </>
  )
}

/* ── Desktop grid (table) ── */
function DesktopGrid({ data, onPositionClick, onEmptySlotClick }: Omit<WheelGridProps, 'showAccount'>) {
  return (
    <div className="wheel-grid-container">
      <table className="wheel-grid-table">
        <thead>
          <tr>
            <th className="wheel-grid-expiry-header">Expiry</th>
            {data.tickers.map(t => (
              <th key={t.symbol} className="wheel-grid-ticker-header">
                <span className="wheel-ticker-name">{t.symbol}</span>
                <span className="wheel-ticker-price">
                  {t.currentPrice != null ? formatCurrency(t.currentPrice, 'USD') : '--'}
                </span>
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
                      {row.dte}d
                    </span>
                  </div>
                  <span className="wheel-expiry-sub">
                    {row.dayOfWeek}{row.isMonthly ? ' (Monthly)' : ''}
                  </span>
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
                            onClick={p => onPositionClick(p, t.symbol, row.expiryDate)}
                          />
                        ))}
                        {/* Empty slot "+" */}
                        {!hasPositions ? (
                          <button
                            className="wheel-empty-slot"
                            onClick={() => onEmptySlotClick(t.symbol, row.expiryDate)}
                            aria-label={`Add position for ${t.symbol} expiring ${row.expiryDate}`}
                          >
                            <Plus size={16} />
                          </button>
                        ) : (
                          <button
                            className="wheel-empty-slot wheel-empty-slot-compact"
                            onClick={() => onEmptySlotClick(t.symbol, row.expiryDate)}
                            aria-label={`Add another position for ${t.symbol} expiring ${row.expiryDate}`}
                          >
                            <Plus size={12} />
                          </button>
                        )}
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
  )
}

/* ── Mobile: collapsible expiry sections with position cards ── */
function MobileExpiryGroups({ data, onPositionClick, onEmptySlotClick }: Omit<WheelGridProps, 'showAccount'>) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})

  const toggle = (expiryDate: string) => {
    setCollapsed(prev => ({ ...prev, [expiryDate]: !prev[expiryDate] }))
  }

  return (
    <div className="wheel-mobile-groups">
      {data.expiryRows.map(row => {
        const urgency = getDteUrgency(row.dte)
        const isCollapsed = collapsed[row.expiryDate] ?? false

        // Collect all positions across all tickers for this expiry
        const allPositions: { pos: WheelPosition; ticker: string }[] = []
        data.tickers.forEach(t => {
          const cell = row.cells[t.symbol]
          if (cell) {
            cell.positions.forEach(pos => allPositions.push({ pos, ticker: t.symbol }))
          }
        })

        return (
          <div key={row.expiryDate} className="wheel-mobile-group">
            <button
              className="wheel-mobile-group-header"
              onClick={() => toggle(row.expiryDate)}
              aria-expanded={!isCollapsed}
            >
              <div className="wheel-mobile-group-left">
                {isCollapsed
                  ? <ChevronRight size={16} className="wheel-mobile-chevron" />
                  : <ChevronDown size={16} className="wheel-mobile-chevron" />
                }
                <span className="wheel-mobile-group-date">
                  {formatExpiryDate(row.expiryDate)}
                </span>
                <span className={`wheel-dte-badge wheel-dte-${urgency}`}>
                  {row.dte}d
                </span>
              </div>
              <span className="wheel-mobile-group-count">
                {allPositions.length} position{allPositions.length !== 1 ? 's' : ''}
              </span>
            </button>

            {!isCollapsed && (
              <div className="wheel-mobile-group-body">
                {allPositions.length === 0 && (
                  <div className="wheel-mobile-empty">
                    No positions for this expiry
                  </div>
                )}
                {allPositions.map(({ pos, ticker }) => (
                  <div key={pos.id} className="wheel-mobile-card-wrap">
                    <span className="wheel-mobile-card-ticker">{ticker}</span>
                    <PositionCard
                      position={pos}
                      onClick={p => onPositionClick(p, ticker, row.expiryDate)}
                    />
                  </div>
                ))}
                {/* Add button for each ticker */}
                <div className="wheel-mobile-add-row">
                  {data.tickers.map(t => (
                    <button
                      key={t.symbol}
                      className="wheel-mobile-add-ticker"
                      onClick={() => onEmptySlotClick(t.symbol, row.expiryDate)}
                    >
                      <Plus size={14} />
                      {t.symbol}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function formatExpiryDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
