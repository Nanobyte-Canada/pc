import { useState } from 'react'
import type { BrokerConnection } from '../../types/broker'
import { ConnectionStatus } from './ConnectionStatus'
import { formatCurrency, getRelativeTime } from '../../services/brokerService'

interface BrokerConnectionCardProps {
  connection: BrokerConnection
  onFetch: (connectionId: number) => void
  onDisconnect: (connectionId: number) => void
  isFetching: boolean
}

export function BrokerConnectionCard({
  connection,
  onFetch,
  onDisconnect,
  isFetching
}: BrokerConnectionCardProps) {
  const [showDisconnectConfirm, setShowDisconnectConfirm] = useState(false)

  const canFetch = connection.status === 'ACTIVE' && !isFetching
  const needsReauth = connection.status === 'EXPIRED' || connection.status === 'ERROR'

  return (
    <div
      style={{
        border: '1px solid #e5e7eb',
        borderRadius: '8px',
        padding: '16px',
        backgroundColor: '#fff',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '16px'
      }}
    >
      {/* Left: Broker info */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
        <div
          style={{
            width: '40px',
            height: '40px',
            borderRadius: '8px',
            backgroundColor: '#f3f4f6',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '16px',
            fontWeight: 'bold',
            color: '#6b7280'
          }}
        >
          {connection.broker.name.charAt(0)}
        </div>

        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontWeight: 600, fontSize: '14px', color: '#111827' }}>
              {connection.broker.name}
            </span>
            {connection.accountType && (
              <span style={{ fontSize: '12px', color: '#6b7280' }}>
                - {connection.accountType}
              </span>
            )}
            {connection.accountNumber && (
              <span style={{ fontSize: '12px', color: '#9ca3af' }}>
                ({connection.accountNumber})
              </span>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '4px' }}>
            <ConnectionStatus status={connection.status} />

            {connection.lastPositionsFetchedAt && (
              <span style={{ fontSize: '12px', color: '#6b7280' }}>
                Last fetched: {getRelativeTime(connection.lastPositionsFetchedAt)}
              </span>
            )}
          </div>

          {connection.errorMessage && (
            <div style={{ fontSize: '12px', color: '#ef4444', marginTop: '4px' }}>
              {connection.errorMessage}
            </div>
          )}
        </div>
      </div>

      {/* Middle: Stats */}
      <div style={{ textAlign: 'right', minWidth: '120px' }}>
        {connection.totalValue !== null && (
          <div style={{ fontWeight: 600, fontSize: '16px', color: '#111827' }}>
            {formatCurrency(connection.totalValue)}
          </div>
        )}
        <div style={{ fontSize: '12px', color: '#6b7280' }}>
          {connection.positionsCount} position{connection.positionsCount !== 1 ? 's' : ''}
        </div>
      </div>

      {/* Right: Actions */}
      <div style={{ display: 'flex', gap: '8px' }}>
        {needsReauth ? (
          <button
            onClick={() => window.location.href = `/api/v1/brokers/${connection.broker.code}/connect`}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: 'none',
              backgroundColor: '#f59e0b',
              color: '#fff',
              fontSize: '13px',
              fontWeight: 500,
              cursor: 'pointer'
            }}
          >
            Reconnect
          </button>
        ) : (
          <button
            onClick={() => onFetch(connection.id)}
            disabled={!canFetch}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: '1px solid #e5e7eb',
              backgroundColor: canFetch ? '#fff' : '#f3f4f6',
              color: canFetch ? '#374151' : '#9ca3af',
              fontSize: '13px',
              fontWeight: 500,
              cursor: canFetch ? 'pointer' : 'not-allowed'
            }}
          >
            {isFetching ? 'Fetching...' : 'Fetch Now'}
          </button>
        )}

        {showDisconnectConfirm ? (
          <div style={{ display: 'flex', gap: '4px' }}>
            <button
              onClick={() => {
                onDisconnect(connection.id)
                setShowDisconnectConfirm(false)
              }}
              style={{
                padding: '8px 12px',
                borderRadius: '6px',
                border: 'none',
                backgroundColor: '#ef4444',
                color: '#fff',
                fontSize: '13px',
                fontWeight: 500,
                cursor: 'pointer'
              }}
            >
              Confirm
            </button>
            <button
              onClick={() => setShowDisconnectConfirm(false)}
              style={{
                padding: '8px 12px',
                borderRadius: '6px',
                border: '1px solid #e5e7eb',
                backgroundColor: '#fff',
                color: '#374151',
                fontSize: '13px',
                cursor: 'pointer'
              }}
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowDisconnectConfirm(true)}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: '1px solid #fecaca',
              backgroundColor: '#fff',
              color: '#dc2626',
              fontSize: '13px',
              fontWeight: 500,
              cursor: 'pointer'
            }}
          >
            Disconnect
          </button>
        )}
      </div>
    </div>
  )
}
