import { useState } from 'react'
import type { WheelPosition } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import { X } from 'lucide-react'
import './ClosePositionDialog.css'

interface ClosePositionDialogProps {
  position: WheelPosition
  ticker: string
  expiryDate: string
  onConfirm: () => void
  onCancel: () => void
}

export function ClosePositionDialog({
  position,
  ticker,
  expiryDate,
  onConfirm,
  onCancel,
}: ClosePositionDialogProps) {
  const [confirming, setConfirming] = useState(false)
  const typeClass = position.type === 'CSP' ? 'wheel-dialog-type-csp' : 'wheel-dialog-type-cc'

  const handleConfirm = () => {
    setConfirming(true)
    onConfirm()
  }

  return (
    <div className="wheel-dialog-overlay" onClick={onCancel}>
      <div className="wheel-dialog" onClick={e => e.stopPropagation()}>
        <div className="wheel-dialog-header">
          <div>
            <h3 className="wheel-dialog-title">Close Position</h3>
            <p className="wheel-dialog-subtitle">Buy to close this {position.type} contract</p>
          </div>
          <button className="wheel-dialog-close" onClick={onCancel} aria-label="Close dialog">
            <X size={18} />
          </button>
        </div>

        <div className={`wheel-dialog-type-indicator ${typeClass}`}>
          {position.type === 'CSP' ? 'Cash-Secured Put' : 'Covered Call'} &middot; {ticker}
        </div>

        <div className="wheel-dialog-details">
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Strike</span>
            <span className="wheel-dialog-value">{formatCurrency(position.strike, 'USD')}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Expiry</span>
            <span className="wheel-dialog-value">{expiryDate}</span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Current Price</span>
            <span className="wheel-dialog-value">
              {position.currentPrice != null ? formatCurrency(position.currentPrice, 'USD') : '--'}
            </span>
          </div>
          <div className="wheel-dialog-row">
            <span className="wheel-dialog-label">Unrealized P&L</span>
            <span className={`wheel-dialog-value ${(position.pnl ?? 0) >= 0 ? 'wheel-dialog-pnl-pos' : 'wheel-dialog-pnl-neg'}`}>
              {position.pnl != null ? `${position.pnl >= 0 ? '+' : ''}${formatCurrency(position.pnl, 'USD')}` : '--'}
            </span>
          </div>
        </div>

        <div className="wheel-dialog-actions">
          <button className="wheel-dialog-btn wheel-dialog-btn-cancel" onClick={onCancel} disabled={confirming}>
            Cancel
          </button>
          <button className="wheel-dialog-btn wheel-dialog-btn-confirm" onClick={handleConfirm} disabled={confirming}>
            {confirming ? 'Closing...' : 'Close Position'}
          </button>
        </div>
      </div>
    </div>
  )
}
