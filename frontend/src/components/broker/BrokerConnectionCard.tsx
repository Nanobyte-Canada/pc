import { useState, useRef, useEffect } from 'react'
import { MoreVertical } from 'lucide-react'
import type { BrokerConnection } from '../../types/broker'
import { getRelativeTime } from '../../services/brokerService'
import './BrokerConnectionCard.css'

interface BrokerConnectionCardProps {
  connection: BrokerConnection
  onSyncAll: (connectionId: number) => void
  onDisconnect: (authorizationId: string) => void
  onReconnect: (authorizationId: string) => void
  isSyncing: boolean
}

/* Broker brand colours for icon */
const BROKER_BRAND: Record<string, { abbr: string; bg: string; color: string }> = {
  questrade:    { abbr: 'Q',  bg: '#1a5c3a', color: '#4ade80' },
  wealthsimple: { abbr: 'W',  bg: '#1a1a3a', color: '#a78bfa' },
  ibkr:         { abbr: 'IB', bg: '#3a1a1a', color: '#f87171' },
}

function getStatusDotClass(status: BrokerConnection['status']): string {
  switch (status) {
    case 'ACTIVE': return 'active'
    case 'EXPIRED': return 'reconnect'
    case 'ERROR': return 'error'
    case 'PENDING': return 'pending'
    case 'DISCONNECTED': return 'disconnected'
    default: return 'disconnected'
  }
}

export function BrokerConnectionCard({
  connection,
  onSyncAll,
  onDisconnect,
  onReconnect,
  isSyncing
}: BrokerConnectionCardProps) {
  const [showMore, setShowMore] = useState(false)
  const [showDisconnectConfirm, setShowDisconnectConfirm] = useState(false)
  const moreRef = useRef<HTMLDivElement>(null)

  const canSync = connection.status === 'ACTIVE' && !isSyncing
  const needsReauth = connection.status === 'EXPIRED' || connection.status === 'ERROR'
  const isError = needsReauth

  const displayAccountNumber = connection.accountNumberActual || connection.accountNumber
  const displayAccountType = connection.accountMetaType || connection.accountType

  const slug = connection.broker.slug?.toLowerCase() || ''
  const brand = BROKER_BRAND[slug]

  const [imgError, setImgError] = useState(false)

  /* Close "more" menu on outside click */
  useEffect(() => {
    if (!showMore) return
    function handleClickOutside(e: MouseEvent) {
      if (moreRef.current && !moreRef.current.contains(e.target as Node)) {
        setShowMore(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showMore])

  return (
    <div className={`broker-connection-card${isError ? ' error-state' : ''}`}>
      {/* Left: Broker info */}
      <div className="connection-info">
        {/* Broker icon */}
        {connection.broker.logoUrl && !imgError ? (
          <img
            src={connection.broker.logoUrl}
            alt={connection.broker.name}
            className="connection-logo"
            onError={() => setImgError(true)}
          />
        ) : brand ? (
          <div
            className="connection-icon"
            style={{ backgroundColor: brand.bg, color: brand.color }}
          >
            {brand.abbr}
          </div>
        ) : (
          <div className="connection-icon" style={{ backgroundColor: 'var(--bg-tertiary)', color: 'var(--text-muted)' }}>
            {connection.broker.name.substring(0, 2)}
          </div>
        )}

        <div className="connection-detail">
          {/* Account type + number row */}
          <div className="connection-name-row">
            <span className="connection-broker-name">
              {displayAccountType || connection.broker.name}
            </span>
          </div>

          {/* Meta row: masked account number, status dot, sync time, position count */}
          <div className="connection-account-meta">
            {displayAccountNumber && (
              <>
                <span className="connection-account-number">
                  ••{displayAccountNumber.slice(-4)}
                </span>
                <span className="connection-meta-separator" />
              </>
            )}
            <span className={`connection-status-dot ${getStatusDotClass(connection.status)}`} />
            {connection.lastPositionsFetchedAt && (
              <>
                <span className="connection-last-fetched">
                  {getRelativeTime(connection.lastPositionsFetchedAt)}
                </span>
                <span className="connection-meta-separator" />
              </>
            )}
            <span className="connection-positions-count">
              {connection.positionsCount} position{connection.positionsCount !== 1 ? 's' : ''}
            </span>
          </div>

          {connection.errorMessage && (
            <div className="connection-error-msg">{connection.errorMessage}</div>
          )}
        </div>
      </div>

      {/* Middle: Value */}
      <div className="connection-stats">
        {connection.totalValue != null && (
          <div className="connection-total-value">
            {(() => {
              const acctType = (connection.accountMetaType || connection.accountType || '').toUpperCase()
              const isUsd = acctType.includes('USD') || acctType.includes('US ')
              const prefix = isUsd ? 'US$' : 'C$'
              const formatted = Math.round(Math.abs(connection.totalValue!)).toLocaleString('en-CA')
              return `${connection.totalValue < 0 ? '-' : ''}${prefix} ${formatted}`
            })()}
          </div>
        )}
      </div>

      {/* Right: Actions — Sync + More */}
      <div className="connection-actions">
        {needsReauth && connection.gatewayConnectionId ? (
          <button
            onClick={() => onReconnect(connection.gatewayConnectionId!)}
            className="connection-btn reconnect"
          >
            Reconnect
          </button>
        ) : (
          <button
            onClick={() => onSyncAll(connection.id)}
            disabled={!canSync}
            className="connection-btn fetch"
          >
            {isSyncing ? 'Syncing...' : 'Sync'}
          </button>
        )}

        {/* More menu */}
        <div ref={moreRef} style={{ position: 'relative' }}>
          <button
            className="connection-more-btn"
            onClick={() => setShowMore(prev => !prev)}
            title="More actions"
          >
            <MoreVertical size={16} />
          </button>

          {showMore && (
            <div className="connection-more-menu">
              {showDisconnectConfirm ? (
                <>
                  <button
                    className="danger"
                    onClick={() => {
                      if (connection.gatewayConnectionId) {
                        onDisconnect(connection.gatewayConnectionId)
                      }
                      setShowDisconnectConfirm(false)
                      setShowMore(false)
                    }}
                  >
                    Confirm Disconnect
                  </button>
                  <button onClick={() => setShowDisconnectConfirm(false)}>
                    Cancel
                  </button>
                </>
              ) : (
                <button
                  className="danger"
                  onClick={() => setShowDisconnectConfirm(true)}
                >
                  Disconnect
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
