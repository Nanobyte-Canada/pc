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

  return (
    <div className="broker-connection-card">
      {/* Left: Broker info */}
      <div className="connection-info">
        <div className="connection-icon">
          {connection.broker.name.charAt(0)}
        </div>

        <div>
          <div className="connection-name-row">
            <span className="connection-broker-name">{connection.broker.name}</span>
            {connection.accountType && (
              <span className="connection-account-type">- {connection.accountType}</span>
            )}
            {connection.accountNumber && (
              <span className="connection-account-number">({connection.accountNumber})</span>
            )}
          </div>

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
