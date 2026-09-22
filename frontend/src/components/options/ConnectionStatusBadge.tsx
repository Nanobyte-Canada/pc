import { useBrokerConnection } from '@/hooks/useBrokerConnection'
import './ConnectionStatusBadge.css'

export function ConnectionStatusBadge() {
  const { connection, loading } = useBrokerConnection()

  if (loading) {
    return (
      <div className="conn-badge">
        <span className="conn-badge__dot conn-badge__dot--loading" />
        <span className="conn-badge__text">Checking...</span>
      </div>
    )
  }

  if (!connection) {
    return (
      <div className="conn-badge conn-badge--disconnected">
        <span className="conn-badge__dot conn-badge__dot--disconnected" />
        <span className="conn-badge__text">No Broker</span>
      </div>
    )
  }

  return (
    <div className={`conn-badge ${connection.connected ? 'conn-badge--connected' : 'conn-badge--disconnected'}`}>
      <span className={`conn-badge__dot ${connection.connected ? 'conn-badge__dot--connected' : 'conn-badge__dot--disconnected'}`} />
      <span className="conn-badge__text">
        {connection.connected ? 'Connected' : 'Disconnected'}
      </span>
      {connection.brokerType && (
        <span className="conn-badge__broker">{connection.brokerType}</span>
      )}
    </div>
  )
}
