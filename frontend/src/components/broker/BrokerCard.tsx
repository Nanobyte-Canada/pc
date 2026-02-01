import type { Broker } from '../../types/broker'

interface BrokerCardProps {
  broker: Broker
  onConnect: (brokerCode: string) => void
  isConnecting: boolean
  hasExistingConnection: boolean
}

export function BrokerCard({ broker, onConnect, isConnecting, hasExistingConnection }: BrokerCardProps) {
  const isDisabled = broker.status !== 'ACTIVE' || isConnecting

  return (
    <div
      style={{
        border: '1px solid #e5e7eb',
        borderRadius: '8px',
        padding: '16px',
        backgroundColor: '#fff',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '12px',
        minWidth: '180px'
      }}
    >
      <div
        style={{
          width: '48px',
          height: '48px',
          borderRadius: '8px',
          backgroundColor: '#f3f4f6',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '20px',
          fontWeight: 'bold',
          color: '#6b7280'
        }}
      >
        {broker.name.charAt(0)}
      </div>

      <div style={{ textAlign: 'center' }}>
        <div style={{ fontWeight: 600, fontSize: '14px', color: '#111827' }}>
          {broker.name}
        </div>
        {broker.description && (
          <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px' }}>
            {broker.description}
          </div>
        )}
      </div>

      <button
        onClick={() => onConnect(broker.code)}
        disabled={isDisabled}
        style={{
          width: '100%',
          padding: '8px 16px',
          borderRadius: '6px',
          border: 'none',
          backgroundColor: isDisabled ? '#e5e7eb' : '#3b82f6',
          color: isDisabled ? '#9ca3af' : '#fff',
          fontSize: '14px',
          fontWeight: 500,
          cursor: isDisabled ? 'not-allowed' : 'pointer'
        }}
      >
        {isConnecting ? 'Connecting...' : hasExistingConnection ? 'Add Account' : 'Connect'}
      </button>

      {broker.status !== 'ACTIVE' && (
        <div style={{ fontSize: '11px', color: '#f59e0b' }}>
          {broker.status === 'MAINTENANCE' ? 'Under Maintenance' : 'Unavailable'}
        </div>
      )}
    </div>
  )
}
