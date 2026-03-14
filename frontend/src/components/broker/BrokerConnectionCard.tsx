import { useState } from 'react'
import type { BrokerConnection } from '../../types/broker'
import { ConnectionStatus } from './ConnectionStatus'
import { formatCurrency, getRelativeTime } from '../../services/brokerService'
import './BrokerConnectionCard.css'

interface BrokerConnectionCardProps {
  connection: BrokerConnection
  onFetch: (connectionId: number) => void
  onDisconnect: (authorizationId: string) => void
  onReconnect: (authorizationId: string) => void
  isFetching: boolean
}

export function BrokerConnectionCard({
  connection,
  onFetch,
  onDisconnect,
  onReconnect,
  isFetching
}: BrokerConnectionCardProps) {
  const [showDisconnectConfirm, setShowDisconnectConfirm] = useState(false)

  const canFetch = connection.status === 'ACTIVE' && !isFetching
  const needsReauth = connection.status === 'EXPIRED' || connection.status === 'ERROR'

  // Use real account number from meta if available, fallback to accountNumber
  const displayAccountNumber = connection.accountNumberActual || connection.accountNumber
  // Use meta type for the badge (e.g. "TFSA", "RRSP"), fallback to accountType
  const displayAccountType = connection.accountMetaType || connection.accountType

  const [imgError, setImgError] = useState(false)

  const brokerDisplayName = displayAccountType
    ? `${connection.broker.name} - ${displayAccountType}`
    : connection.broker.name

  return (
    <div className="broker-connection-card">
      {/* Left: Broker info */}
      <div className="connection-info">
        {connection.broker.logoUrl && !imgError ? (
          <img
            src={connection.broker.logoUrl}
            alt={connection.broker.name}
            className="connection-logo"
            onError={() => setImgError(true)}
          />
        ) : (
          <div className="connection-icon">
            {connection.broker.name.substring(0, 2)}
          </div>
        )}

        <div>
          <div className="connection-name-row">
            <span className="connection-broker-name">{brokerDisplayName}</span>
          </div>

          {displayAccountNumber && (
            <div className="connection-account-number">
              Account: {displayAccountNumber}
            </div>
          )}

          <div className="connection-status-row">
            <ConnectionStatus status={connection.status} />
            {connection.lastPositionsFetchedAt && (
              <span className="connection-last-fetched">
                Last fetched: {getRelativeTime(connection.lastPositionsFetchedAt)}
              </span>
            )}
          </div>

          {connection.errorMessage && (
            <div className="connection-error-msg">{connection.errorMessage}</div>
          )}
        </div>
      </div>

      {/* Middle: Stats */}
      <div className="connection-stats">
        {connection.totalValue !== null && (
          <div className="connection-total-value">{formatCurrency(connection.totalValue)}</div>
        )}
        <div className="connection-positions-count">
          {connection.positionsCount} position{connection.positionsCount !== 1 ? 's' : ''}
        </div>
      </div>

      {/* Right: Actions */}
      <div className="connection-actions">
        {needsReauth && connection.snaptradeAuthorizationId ? (
          <button
            onClick={() => onReconnect(connection.snaptradeAuthorizationId!)}
            className="connection-btn reconnect"
          >
            Reconnect
          </button>
        ) : (
          <button
            onClick={() => onFetch(connection.id)}
            disabled={!canFetch}
            className="connection-btn fetch"
          >
            {isFetching ? 'Fetching...' : 'Fetch Now'}
          </button>
        )}

        {showDisconnectConfirm ? (
          <div className="connection-confirm-group">
            <button
              onClick={() => {
                if (connection.snaptradeAuthorizationId) {
                  onDisconnect(connection.snaptradeAuthorizationId)
                }
                setShowDisconnectConfirm(false)
              }}
              className="connection-btn confirm-delete"
            >
              Confirm
            </button>
            <button
              onClick={() => setShowDisconnectConfirm(false)}
              className="connection-btn cancel"
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowDisconnectConfirm(true)}
            className="connection-btn disconnect"
          >
            Disconnect
          </button>
        )}
      </div>
    </div>
  )
}
