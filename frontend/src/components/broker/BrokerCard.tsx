import type { Broker, BrokerConnection } from '../../types/broker'
import './BrokerCard.css'

interface BrokerCardProps {
  broker: Broker
  onConnect: (brokerSlug?: string) => void
  isConnecting: boolean
  hasExistingConnection: boolean
  connections?: BrokerConnection[]
}

/* Broker brand colours */
const BROKER_BRAND: Record<string, { abbr: string; bg: string; color: string }> = {
  questrade:     { abbr: 'Q',  bg: '#1a5c3a', color: '#4ade80' },
  wealthsimple:  { abbr: 'W',  bg: '#1a1a3a', color: '#a78bfa' },
  ibkr:          { abbr: 'IB', bg: '#3a1a1a', color: '#f87171' },
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

export function BrokerCard({ broker, onConnect, isConnecting, hasExistingConnection, connections }: BrokerCardProps) {
  const isDisabled = isConnecting || broker.maintenanceMode || broker.enabled === false

  const handleClick = () => {
    if (isDisabled) return
    onConnect(broker.slug || undefined)
  }

  const authLabel = getAuthTypeLabel(broker)
  const slug = broker.slug?.toLowerCase() || ''
  const brand = BROKER_BRAND[slug]

  /* Connection state: count connected accounts, detect reconnection needs */
  const brokerConns = connections?.filter(c => c.broker.slug === broker.slug) || []
  const activeCount = brokerConns.filter(c => c.status === 'ACTIVE').length
  const needsReconnect = brokerConns.some(c => c.status === 'EXPIRED' || c.status === 'ERROR')

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
      {broker.maintenanceMode && (
        <span className="broker-card-status-badge maintenance">Maintenance</span>
      )}

      {broker.isDegraded && !broker.maintenanceMode && (
        <span className="broker-card-status-badge degraded">Degraded</span>
      )}

      {/* Broker icon — brand-colored letter badge */}
      {broker.logoUrl ? (
        <img
          src={broker.logoUrl}
          alt={broker.name}
          className="broker-card-logo"
        />
      ) : brand ? (
        <div
          className="broker-card-icon"
          style={{ backgroundColor: brand.bg, color: brand.color }}
        >
          {brand.abbr}
        </div>
      ) : (
        <div className="broker-card-placeholder">
          {broker.name.charAt(0)}
        </div>
      )}

      {/* Name */}
      <div className="broker-card-name">{broker.name}</div>

      {/* Account type badges */}
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

      {/* Connection state pill */}
      {hasExistingConnection && !needsReconnect && activeCount > 0 && (
        <span className="broker-card-state broker-card-state--connected">
          <span className="state-dot" />
          {activeCount} Account{activeCount !== 1 ? 's' : ''} Connected
        </span>
      )}

      {needsReconnect && (
        <span className="broker-card-state broker-card-state--reconnect">
          Reconnect
        </span>
      )}
    </div>
  )
}
