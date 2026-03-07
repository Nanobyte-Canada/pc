import { useSnapTradeStatus } from '../../hooks/useBrokerConnections'
import './SnapTradeBadge.css'

export function SnapTradeBadge() {
  const { data, isLoading } = useSnapTradeStatus()

  if (isLoading) {
    return <span className="snaptrade-badge loading">SnapTrade: Checking...</span>
  }

  const status = data?.status?.status ?? 'UNKNOWN'
  const statusClass = status === 'ONLINE' ? 'online'
    : status === 'DEGRADED' ? 'degraded'
    : status === 'OFFLINE' ? 'offline'
    : 'unknown'

  const statusLabel = status === 'ONLINE' ? 'Online'
    : status === 'DEGRADED' ? 'Degraded'
    : status === 'OFFLINE' ? 'Offline'
    : 'Unknown'

  return (
    <span className={`snaptrade-badge ${statusClass}`}>
      <span className="snaptrade-badge-dot" />
      SnapTrade: {statusLabel}
    </span>
  )
}
