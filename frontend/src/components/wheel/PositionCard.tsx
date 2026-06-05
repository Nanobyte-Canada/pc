import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './PositionCard.css'

interface PositionCardProps {
  position: WheelPosition
  onClick: (position: WheelPosition) => void
}

export function PositionCard({ position, onClick }: PositionCardProps) {
  const typeClass = position.type === 'CSP' ? 'wpc--csp' : 'wpc--cc'
  const strikeClass = position.type === 'CSP' ? 'wpc__strike--csp' : 'wpc__strike--cc'
  const typeLabel = position.type === 'CSP' ? 'Put' : 'CC'
  const pnlClass = (position.pnl ?? 0) >= 0 ? 'wpc__pnl--pos' : 'wpc__pnl--neg'

  return (
    <div
      className={`wpc ${typeClass}`}
      onClick={() => onClick(position)}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onClick(position) }}
    >
      <div className="wpc__row">
        <span className={`wpc__strike ${strikeClass}`}>
          ${position.strike} {typeLabel}
        </span>
        <span className="wpc__otm">
          {position.otmPercent != null ? `${position.otmPercent.toFixed(1)}%` : ''}
        </span>
      </div>
      <div className="wpc__row">
        <span className={`wpc__pnl ${pnlClass}`}>
          {position.pnl != null
            ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}`
            : '--'}
        </span>
      </div>
    </div>
  )
}
