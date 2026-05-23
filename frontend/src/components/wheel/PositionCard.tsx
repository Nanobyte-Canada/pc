import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './PositionCard.css'

interface PositionCardProps {
  position: WheelPosition
  showAccount: boolean
  onClick: (position: WheelPosition) => void
}

export function PositionCard({ position, showAccount, onClick }: PositionCardProps) {
  const typeClass = position.type === 'CSP' ? 'wheel-card-csp' : 'wheel-card-cc'

  return (
    <div
      className={`wheel-position-card ${typeClass}`}
      onClick={() => onClick(position)}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onClick(position) }}
    >
      {showAccount && position.accountName && (
        <span className={`wheel-account-badge wheel-account-${position.connectionId % 3}`}>
          {position.accountName}
          {position.accountNumber ? ` ${position.accountNumber}` : ''}
        </span>
      )}
      <div className="wheel-card-row">
        <span className="wheel-card-label">Strike</span>
        <span className="wheel-card-value wheel-card-strike">
          {formatCurrency(position.strike, 'USD')}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">Premium</span>
        <span className="wheel-card-value wheel-card-small">
          {position.premium != null ? `+${formatCurrency(position.premium, 'USD')}` : '—'}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">P&L</span>
        <span
          className={`wheel-card-value wheel-card-small ${
            (position.pnl ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'
          }`}
        >
          {position.pnl != null
            ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}`
            : '—'}
        </span>
      </div>
      <div className="wheel-card-row">
        <span className="wheel-card-label">OTM</span>
        <span className="wheel-card-value wheel-card-small">
          {position.otmPercent != null ? `${position.otmPercent.toFixed(1)}%` : '—'}
        </span>
      </div>
    </div>
  )
}
