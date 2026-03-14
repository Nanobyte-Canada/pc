import { useState } from 'react'
import { RebalanceTradesTable } from './RebalanceTradesTable'
import { TradeConfirmationModal } from './TradeConfirmationModal'
import { useExecuteTrades, useExecuteSingleTrade } from '../../hooks/useTrading'
import type { RebalanceTradesResult, RebalanceTrade } from '../../types/portfolioGroup'
import type { TradeExecutionInput } from '../../types/trading'

interface TradeExecutionPanelProps {
  groupId: number
  rebalance: RebalanceTradesResult | null
  isLoading: boolean
}

export function TradeExecutionPanel({ groupId, rebalance, isLoading }: TradeExecutionPanelProps) {
  const [showConfirmAll, setShowConfirmAll] = useState(false)
  const [showConfirmSingle, setShowConfirmSingle] = useState(false)
  const [selectedTrade, setSelectedTrade] = useState<RebalanceTrade | null>(null)
  const executeTrades = useExecuteTrades()
  const executeSingleTrade = useExecuteSingleTrade()

  if (isLoading) {
    return <p className="text-muted">Calculating trades...</p>
  }

  if (!rebalance || rebalance.trades.length === 0) {
    return (
      <div className="empty-trades">
        <p>No trades needed. Portfolio is balanced or no cash available.</p>
      </div>
    )
  }

  const mapToExecutionInput = (trade: RebalanceTrade): TradeExecutionInput => ({
    symbol: trade.symbol,
    action: trade.action,
    units: trade.units,
    price: trade.price,
    amount: trade.amount,
    currency: trade.currency,
    connectionId: trade.connectionId
  })

  const handleExecuteAll = () => {
    executeTrades.mutate(
      {
        groupId,
        trades: rebalance.trades.map(mapToExecutionInput)
      },
      { onSuccess: () => setShowConfirmAll(false) }
    )
  }

  const handleExecuteSingle = () => {
    if (!selectedTrade) return
    executeSingleTrade.mutate(
      { groupId, trade: mapToExecutionInput(selectedTrade) },
      { onSuccess: () => { setShowConfirmSingle(false); setSelectedTrade(null) } }
    )
  }

  const handleRowExecute = (trade: RebalanceTrade) => {
    setSelectedTrade(trade)
    setShowConfirmSingle(true)
  }

  return (
    <div>
      <div className="trades-summary">
        <span>
          {rebalance.trades.length} trade{rebalance.trades.length !== 1 ? 's' : ''} recommended
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span>Resulting accuracy: <strong>{rebalance.resultingAccuracy.toFixed(1)}%</strong></span>
          <button
            className="btn-primary"
            onClick={() => setShowConfirmAll(true)}
            disabled={executeTrades.isPending}
          >
            Execute All
          </button>
        </div>
      </div>

      <RebalanceTradesTable trades={rebalance.trades} onExecute={handleRowExecute} />

      {Object.keys(rebalance.cashRemaining).length > 0 && (
        <div className="cash-remaining">
          <h4>Cash Remaining After Trades</h4>
          {Object.entries(rebalance.cashRemaining).map(([currency, amount]) => (
            <span key={currency} className="cash-remaining-item">
              {currency}: ${Number(amount).toFixed(2)}
            </span>
          ))}
        </div>
      )}

      <TradeConfirmationModal
        isOpen={showConfirmAll}
        onClose={() => setShowConfirmAll(false)}
        onConfirm={handleExecuteAll}
        trades={rebalance.trades}
        isLoading={executeTrades.isPending}
      />

      <TradeConfirmationModal
        isOpen={showConfirmSingle}
        onClose={() => { setShowConfirmSingle(false); setSelectedTrade(null) }}
        onConfirm={handleExecuteSingle}
        trades={selectedTrade ? [selectedTrade] : []}
        isLoading={executeSingleTrade.isPending}
      />
    </div>
  )
}
