import type { RebalanceTrade } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'
import '../portfolio-groups/CreateGroupModal.css'

interface TradeConfirmationModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  trades: RebalanceTrade[]
  isLoading: boolean
}

export function TradeConfirmationModal({
  isOpen,
  onClose,
  onConfirm,
  trades,
  isLoading
}: TradeConfirmationModalProps) {
  if (!isOpen) return null

  const buyTrades = trades.filter(t => t.action === 'BUY')
  const sellTrades = trades.filter(t => t.action === 'SELL')
  const totalBuyAmount = buyTrades.reduce((sum, t) => sum + t.amount, 0)
  const totalSellAmount = sellTrades.reduce((sum, t) => sum + t.amount, 0)

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()} style={{ maxWidth: '560px' }}>
        <div className="modal-header">
          <h3>Confirm Trade Execution</h3>
          <button className="modal-close-btn" onClick={onClose}>&times;</button>
        </div>

        <div className="modal-body">
          <div className="trade-confirm-summary">
            <p style={{ marginTop: 0 }}>
              You are about to execute <strong>{trades.length}</strong> trade{trades.length !== 1 ? 's' : ''}:
            </p>

            {sellTrades.length > 0 && (
              <div className="trade-confirm-group">
                <h4 style={{ color: '#dc2626', margin: '0.5rem 0 0.25rem' }}>
                  SELL ({sellTrades.length} trade{sellTrades.length !== 1 ? 's' : ''})
                </h4>
                {sellTrades.map((trade, i) => (
                  <div key={i} className="trade-confirm-item">
                    <span>{trade.units.toFixed(2)} {trade.symbol}</span>
                    <span>{formatCurrency(trade.amount, trade.currency)}</span>
                  </div>
                ))}
                <div className="trade-confirm-subtotal">
                  <span>Total Sell</span>
                  <span>{formatCurrency(totalSellAmount)}</span>
                </div>
              </div>
            )}

            {buyTrades.length > 0 && (
              <div className="trade-confirm-group">
                <h4 style={{ color: '#059669', margin: '0.5rem 0 0.25rem' }}>
                  BUY ({buyTrades.length} trade{buyTrades.length !== 1 ? 's' : ''})
                </h4>
                {buyTrades.map((trade, i) => (
                  <div key={i} className="trade-confirm-item">
                    <span>{trade.units.toFixed(2)} {trade.symbol}</span>
                    <span>{formatCurrency(trade.amount, trade.currency)}</span>
                  </div>
                ))}
                <div className="trade-confirm-subtotal">
                  <span>Total Buy</span>
                  <span>{formatCurrency(totalBuyAmount)}</span>
                </div>
              </div>
            )}

            <div className="trade-confirm-warning">
              Market orders will be executed at the current market price. Actual fill prices may differ from estimates.
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading ? 'Executing...' : `Execute ${trades.length} Trade${trades.length !== 1 ? 's' : ''}`}
          </button>
        </div>
      </div>
    </div>
  )
}
