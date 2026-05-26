import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import { Plus } from 'lucide-react'
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
      {/* Emerald "+" button in top-right */}
      <span className="wheel-card-add-btn" aria-hidden="true">
        <Plus size={12} strokeWidth={2.5} />
      </span>

      {showAccount && position.accountName && (
        <span className={`wheel-account-badge wheel-account-${position.connectionId % 3}`}>
          {position.accountName}
          {position.accountNumber ? ` ${position.accountNumber}` : ''}
        </span>
      )}

      {/* Row 1: Strike + OTM% */}
      <div className="wheel-card-row">
        <span className="wheel-card-strike">
          {formatCurrency(position.strike, 'USD')}
        </span>
        <span className="wheel-card-otm">
          {position.otmPercent != null ? `${position.otmPercent.toFixed(1)}% OTM` : '--'}
        </span>
      </div>

      {/* Row 2: Premium + P&L */}
      <div className="wheel-card-row">
        <span className="wheel-card-premium">
          {position.premium != null ? `+${formatCurrency(position.premium, 'USD')}` : '--'}
        </span>
        <span
          className={`wheel-card-pnl ${
            (position.pnl ?? 0) >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative'
          }`}
        >
          {position.pnl != null
            ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}`
            : '--'}
        </span>
      </div>
    </div>
  )
}
