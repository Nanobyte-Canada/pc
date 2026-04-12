import type { Broker } from '../../types/broker'
import './BrokerCard.css'

interface BrokerCardProps {
  broker: Broker
  onConnect: (brokerSlug?: string) => void
  isConnecting: boolean
  hasExistingConnection: boolean
}

const AUTH_TYPE_LABELS: Record<string, string> = {
  OAUTH: 'OAuth',
  SCRAPE: 'Credentials',
  UNOFFICIAL_API: 'API Key',
}

const AUTH_TYPE_CLASSES: Record<string, string> = {
  OAUTH: 'badge-oauth',
  SCRAPE: 'badge-credentials',
  UNOFFICIAL_API: 'badge-api-key',
}

function getAuthTypeLabel(broker: Broker): string | null {
  if (!broker.authTypes?.length) return null
  const authTypes = new Set(broker.authTypes.map(at => at.authType))
  if (authTypes.size === 1) {
    return AUTH_TYPE_LABELS[broker.authTypes[0].authType] ?? null
  }
  return null
}

function getAuthTypeClass(broker: Broker): string {
  if (!broker.authTypes?.length) return ''
  return AUTH_TYPE_CLASSES[broker.authTypes[0].authType] ?? ''
}

export function BrokerCard({ broker, onConnect, isConnecting, hasExistingConnection }: BrokerCardProps) {
  const isDisabled = isConnecting || broker.maintenanceMode || broker.enabled === false

  const handleClick = () => {
    if (isDisabled) return
    onConnect(broker.slug || undefined)
  }

  const authLabel = getAuthTypeLabel(broker)

  return (
    <div
      className={`broker-card${hasExistingConnection ? ' connected' : ''}${isDisabled ? ' disabled' : ''}${broker.isDegraded ? ' degraded' : ''}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleClick() }}
      title={
        broker.maintenanceMode ? `${broker.name} is in maintenance`
        : isConnecting ? 'Connecting...'
        : hasExistingConnection ? `Add another ${broker.name} account`
        : `Connect to ${broker.name}`
      }
    >
      {hasExistingConnection && (
        <span className="broker-card-connected-badge">&#10003;</span>
      )}

      {broker.maintenanceMode && (
        <span className="broker-card-status-badge maintenance">Maintenance</span>
      )}

      {broker.isDegraded && !broker.maintenanceMode && (
        <span className="broker-card-status-badge degraded">Degraded</span>
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

      <div className="broker-card-badges">
        {authLabel && (
          <span className={`broker-badge ${getAuthTypeClass(broker)}`}>{authLabel}</span>
        )}
        {broker.allowsTrading && (
          <span className="broker-badge badge-feature">Trading</span>
        )}
        {broker.isRealTimeConnection && (
          <span className="broker-badge badge-feature">Real-time</span>
        )}
      </div>
    </div>
  )
}
