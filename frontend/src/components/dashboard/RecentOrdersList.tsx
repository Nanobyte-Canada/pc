import type { TradeOrder } from '../../types/trading'
import { formatCurrency } from '../../services/brokerService'

interface RecentOrdersListProps {
  orders: TradeOrder[]
}

const statusColors: Record<string, string> = {
  PENDING: '#b45309',
  SUBMITTED: '#2563eb',
  FILLED: '#059669',
  PARTIALLY_FILLED: '#7c3aed',
  REJECTED: '#dc2626',
  CANCELLED: '#6b7280',
  FAILED: '#dc2626'
}

export function RecentOrdersList({ orders }: RecentOrdersListProps) {
  if (orders.length === 0) {
    return (
      <div className="dashboard-card">
        <h3>Recent Orders</h3>
        <p className="text-muted">No orders yet.</p>
      </div>
    )
  }

  return (
    <div className="dashboard-card">
      <h3>Recent Orders</h3>
      <div className="recent-orders-list">
        {orders.map(order => (
          <div key={order.id} className="recent-order-item">
            <div className="order-item-left">
              <span className={`trade-action-badge ${order.action.toLowerCase()}`}>{order.action}</span>
              <span className="order-item-symbol">{order.symbol}</span>
              <span className="order-item-amount">{formatCurrency(order.requestedAmount, order.currency)}</span>
            </div>
            <span
              className="order-item-status"
              style={{ color: statusColors[order.status] || '#6b7280' }}
            >
              {order.status}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
