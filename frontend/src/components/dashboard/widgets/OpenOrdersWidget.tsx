import { useOpenOrders } from '@/hooks/useDashboardWidgets'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { ClipboardList } from 'lucide-react'
import './OpenOrdersWidget.css'

export default function OpenOrdersWidget(_props: { connectionId?: number }) {
  const { data, isLoading } = useOpenOrders()
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (data.orders.length === 0) {
    return (
      <div className="oo-empty">
        <ClipboardList style={{ height: '2rem', width: '2rem' }} />
        <span>No open orders</span>
      </div>
    )
  }

  return (
    <div className="oo-list">
      {data.orders.map(order => (
        <div key={order.id} className="oo-item">
          <div className="oo-item-left">
            <Badge
              variant={order.action === 'BUY' ? 'success' : 'destructive'}
              style={{ fontSize: '0.75rem', fontWeight: 700, minWidth: 40, justifyContent: 'center' }}
            >
              {order.action}
            </Badge>
            <div className="oo-item-info">
              <span className="oo-symbol">{order.symbol}</span>
              {order.accountName && (
                <span className="oo-account">{order.accountName}</span>
              )}
            </div>
          </div>
          <div className="oo-item-right">
            <div className="oo-units">{order.requestedUnits} units</div>
            <div className="oo-type">{order.orderType}</div>
          </div>
        </div>
      ))}
    </div>
  )
}
