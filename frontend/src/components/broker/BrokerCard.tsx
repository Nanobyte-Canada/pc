import type { Broker } from '../../types/broker'
import './BrokerCard.css'

interface BrokerCardProps {
  broker: Broker
  onConnect: (brokerSlug?: string) => void
  isConnecting: boolean
  hasExistingConnection: boolean
}

export function BrokerCard({ broker, onConnect, isConnecting, hasExistingConnection }: BrokerCardProps) {
  const handleClick = () => {
    if (isConnecting) return
    onConnect(broker.slug || undefined)
  }

  return (
    <div
      className={`broker-card${hasExistingConnection ? ' connected' : ''}${isConnecting ? ' disabled' : ''}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleClick() }}
      title={isConnecting ? 'Connecting...' : hasExistingConnection ? `Add another ${broker.name} account` : `Connect to ${broker.name}`}
    >
      {hasExistingConnection && (
        <span className="broker-card-connected-badge">&#10003;</span>
      )}

      {broker.logoUrl ? (
        <img
          src={broker.logoUrl}
          alt={broker.name}
          className="broker-card-logo"
        />
      ) : (
        <div className="broker-card-placeholder">
          {broker.name.charAt(0)}
        </div>
      )}

      <div className="broker-card-name">{broker.name}</div>
    </div>
  )
}
