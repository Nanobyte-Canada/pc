import { useState } from 'react'
import { usePendingOrders } from '@/hooks/useModelPortfolios'
import { Skeleton } from '@/components/ui/skeleton'
import { ShoppingCart, ChevronDown, ChevronUp, AlertTriangle } from 'lucide-react'
import './PendingOrdersWidget.css'

function fmt(value: number, currency: string = 'CAD') {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency }).format(value)
}

export default function PendingOrdersWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = usePendingOrders(connectionId ?? 0, !!connectionId)
  const [showExplanation, setShowExplanation] = useState(false)

  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (!connectionId || data.orders.length === 0) {
    return (
      <div className="pow-empty">
        <ShoppingCart style={{ height: '2rem', width: '2rem' }} />
        <span>No pending orders</span>
      </div>
    )
  }

  const buyOrders = data.orders.filter(o => o.action === 'BUY')
  const sellOrders = data.orders.filter(o => o.action === 'SELL')

  return (
    <div>
      <div className="pow-header">
        <span className="pow-header-label">PENDING ORDERS</span>
        <button className="pow-preview-btn">Preview Orders</button>
      </div>

      {data.cashWarning && (
        <div className="pow-cash-warning">
          <AlertTriangle size={16} />
          <span>{data.cashWarning}</span>
        </div>
      )}

      <div className="pow-orders">
        {sellOrders.length > 0 && (
          <div className="pow-section">
            <div className="pow-section-header pow-section-header-sell">SELL</div>
            <div className="pow-grid">
              <div className="pow-grid-headers">
                <span>Security</span>
                <span>Price</span>
                <span>Units</span>
                <span>Amount</span>
              </div>
              {sellOrders.map((order, idx) => (
                <div key={idx} className={`pow-grid-row${order.cashInsufficient ? ' pow-order-insufficient' : ''}`}>
                  <span className="pow-grid-cell pow-grid-symbol">
                    {order.symbol}
                    {order.cashInsufficient && <AlertTriangle size={12} className="pow-insufficient-icon" />}
                  </span>
                  <span className="pow-grid-cell">{fmt(order.price, order.currency)}</span>
                  <span className="pow-grid-cell">{order.units}</span>
                  <span className="pow-grid-cell">{fmt(order.amount, order.currency)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {buyOrders.length > 0 && (
          <div className="pow-section">
            <div className="pow-section-header pow-section-header-buy">BUY</div>
            <div className="pow-grid">
              <div className="pow-grid-headers">
                <span>Security</span>
                <span>Price</span>
                <span>Units</span>
                <span>Amount</span>
              </div>
              {buyOrders.map((order, idx) => (
                <div key={idx} className={`pow-grid-row${order.cashInsufficient ? ' pow-order-insufficient' : ''}`}>
                  <span className="pow-grid-cell pow-grid-symbol">
                    {order.symbol}
                    {order.cashInsufficient && <AlertTriangle size={12} className="pow-insufficient-icon" />}
                  </span>
                  <span className="pow-grid-cell">{fmt(order.price, order.currency)}</span>
                  <span className="pow-grid-cell">{order.units}</span>
                  <span className="pow-grid-cell">{fmt(order.amount, order.currency)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="pow-summary">
        <div className="pow-summary-row">
          <span className="pow-summary-label">Total Sells</span>
          <span className="pow-summary-value pow-summary-sell">{fmt(data.totalSellAmount)}</span>
        </div>
        <div className="pow-summary-row">
          <span className="pow-summary-label">Total Buys</span>
          <span className="pow-summary-value pow-summary-buy">{fmt(data.totalBuyAmount)}</span>
        </div>
        <div className="pow-summary-row pow-summary-row-cash">
          <span className="pow-summary-label">Cash Remaining</span>
          <span className={`pow-summary-value${data.cashRemaining < 0 ? ' pow-summary-negative' : ''}`}>
            {fmt(data.cashRemaining)}
          </span>
        </div>
      </div>

      <div className="pow-footer">
        <button
          className="pow-explanation-toggle"
          onClick={() => setShowExplanation(!showExplanation)}
        >
          <span>Show Explanation</span>
          {showExplanation ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
        <span className="pow-total">
          Total: <strong>{fmt(Math.abs(data.totalAmount))}</strong>
        </span>
      </div>

      {showExplanation && (
        <div className="pow-explanation">
          <p className="pow-explanation-text">
            These orders will rebalance your portfolio to match the target model allocation.
            Review the orders before executing them in your brokerage account.
          </p>
        </div>
      )}
    </div>
  )
}
