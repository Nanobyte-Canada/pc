import type { Broker } from '../../types/broker'
import './BrokerCard.css'

interface BrokerCardProps {
  broker: Broker
  onConnect: (brokerSlug?: string) => void
  isConnecting: boolean
  hasExistingConnection: boolean
}

export function BrokerCard({ broker, onConnect, isConnecting, hasExistingConnection }: BrokerCardProps) {
  const isDisabled = isConnecting

  return (
    <div className="broker-card">
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

      <div className="broker-card-info">
        <div className="broker-card-name">{broker.name}</div>
        {broker.description && (
          <div className="broker-card-description">{broker.description}</div>
        )}
      </div>

      <button
        onClick={() => onConnect(broker.slug || undefined)}
        disabled={isDisabled}
        className="broker-card-connect-btn"
      >
        {isConnecting ? 'Connecting...' : hasExistingConnection ? 'Add Account' : 'Connect'}
      </button>
    </div>
  )
}
