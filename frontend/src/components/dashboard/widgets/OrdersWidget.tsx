import { useState } from 'react'
import { useOpenOrders } from '@/hooks/useDashboardWidgets'
import { usePendingOrders } from '@/hooks/useModelPortfolios'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { ClipboardList, ShoppingCart, AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react'
import './OrdersWidget.css'

type Tab = 'open' | 'pending'

function fmt(value: number, currency: string = 'CAD') {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency }).format(value)
}

export default function OrdersWidget({ connectionId }: { connectionId?: number }) {
  const [activeTab, setActiveTab] = useState<Tab>('open')
  const { data: openData } = useOpenOrders()
  const { data: pendingData } = usePendingOrders(connectionId ?? 0, !!connectionId)

  const openCount = openData?.orders?.length ?? 0
  const pendingCount = pendingData?.orders?.length ?? 0

  return (
    <div className="ow-root">
      <div className="ow-tabs-bar">
        <button
          className={`ow-tab ${activeTab === 'open' ? 'ow-tab-active' : ''}`}
          onClick={() => setActiveTab('open')}
        >
          Open{openCount > 0 && <span className="ow-tab-count">{openCount}</span>}
        </button>
        <button
          className={`ow-tab ${activeTab === 'pending' ? 'ow-tab-active' : ''}${!connectionId ? ' ow-tab-disabled' : ''}`}
          onClick={() => connectionId && setActiveTab('pending')}
          disabled={!connectionId}
          title={!connectionId ? 'Select an account to view pending orders' : undefined}
        >
          Pending{pendingCount > 0 && <span className="ow-tab-count">{pendingCount}</span>}
        </button>
      </div>
      <div className="ow-content">
        {activeTab === 'open' ? (
          <OpenOrdersTab />
        ) : (
          <PendingOrdersTab connectionId={connectionId} />
        )}
      </div>
    </div>
  )
}

function OpenOrdersTab() {
  const { data, isLoading } = useOpenOrders()

  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (data.orders.length === 0) {
    return (
      <div className="ow-empty">
        <ClipboardList style={{ height: '2rem', width: '2rem' }} />
        <span>No open orders</span>
      </div>
    )
  }

  return (
    <div className="ow-table">
      <div className="ow-table-headers">
        <span>Action</span>
        <span>Symbol</span>
        <span>Type</span>
        <span>Qty</span>
      </div>
      {data.orders.map(order => (
        <div key={order.id} className="ow-table-row">
          <span className="ow-table-cell">
            <Badge
              variant={order.action === 'BUY' ? 'success' : 'destructive'}
              className="ow-action-badge"
            >
              {order.action}
            </Badge>
          </span>
          <span className="ow-table-cell ow-cell-symbol">{order.symbol}</span>
          <span className="ow-table-cell ow-cell-muted">{order.orderType}</span>
          <span className="ow-table-cell">{order.requestedUnits}</span>
        </div>
      ))}
    </div>
  )
}

function PendingOrdersTab({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = usePendingOrders(connectionId ?? 0, !!connectionId)
  const [showExplanation, setShowExplanation] = useState(false)

  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (!connectionId || data.orders.length === 0) {
    return (
      <div className="ow-empty">
        <ShoppingCart style={{ height: '2rem', width: '2rem' }} />
        <span>No pending orders</span>
      </div>
    )
  }

  return (
    <div>
      {data.cashWarning && (
        <div className="ow-cash-warning">
          <AlertTriangle size={16} />
          <span>{data.cashWarning}</span>
        </div>
      )}

      <div className="ow-table">
        <div className="ow-table-headers">
          <span>Action</span>
          <span>Security</span>
          <span>Price</span>
          <span>Units</span>
          <span>Amount</span>
        </div>
        {data.orders.map((order, idx) => (
          <div
            key={idx}
            className={`ow-table-row${order.cashInsufficient ? ' ow-row-insufficient' : ''}`}
          >
            <span className="ow-table-cell">
              <Badge
                variant={order.action === 'BUY' ? 'success' : 'destructive'}
                className="ow-action-badge"
              >
                {order.action}
              </Badge>
            </span>
            <span className="ow-table-cell ow-cell-symbol">
              {order.symbol}
              {order.cashInsufficient && <AlertTriangle size={12} className="ow-insufficient-icon" />}
            </span>
            <span className="ow-table-cell">{fmt(order.price, order.currency)}</span>
            <span className="ow-table-cell">{order.units}</span>
            <span className="ow-table-cell">{fmt(order.amount, order.currency)}</span>
          </div>
        ))}
      </div>

      <div className="ow-summary">
        <div className="ow-summary-row">
          <span className="ow-summary-label">Total Sells</span>
          <span className="ow-summary-value ow-summary-sell">{fmt(data.totalSellAmount)}</span>
        </div>
        <div className="ow-summary-row">
          <span className="ow-summary-label">Total Buys</span>
          <span className="ow-summary-value ow-summary-buy">{fmt(data.totalBuyAmount)}</span>
        </div>
        <div className="ow-summary-row ow-summary-row-cash">
          <span className="ow-summary-label">Cash Remaining</span>
          <span className={`ow-summary-value${data.cashRemaining < 0 ? ' ow-summary-negative' : ''}`}>
            {fmt(data.cashRemaining)}
          </span>
        </div>
      </div>

      <div className="ow-footer">
        <button className="ow-preview-btn">Preview Orders</button>
        <button
          className="ow-explanation-toggle"
          onClick={() => setShowExplanation(!showExplanation)}
        >
          <span>Details</span>
          {showExplanation ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      </div>

      {showExplanation && (
        <div className="ow-explanation">
          <p className="ow-explanation-text">
            These orders will rebalance your portfolio to match the target model allocation.
            Review the orders before executing them in your brokerage account.
          </p>
          <div className="ow-explanation-total">
            Total: <strong>{fmt(Math.abs(data.totalAmount))}</strong>
          </div>
        </div>
      )}
    </div>
  )
}
