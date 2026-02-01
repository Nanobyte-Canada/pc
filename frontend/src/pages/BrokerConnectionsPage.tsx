import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { BrokerCard } from '../components/broker/BrokerCard'
import { BrokerConnectionCard } from '../components/broker/BrokerConnectionCard'
import {
  useAvailableBrokers,
  useBrokerConnections,
  useInitiateConnection,
  useDisconnectBroker,
  useTriggerPositionFetch,
  useBrokerPreferences,
  useUpdateBrokerPreferences
} from '../hooks/useBrokerConnections'

export function BrokerConnectionsPage() {
  const [searchParams] = useSearchParams()
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [fetchingConnectionId, setFetchingConnectionId] = useState<number | null>(null)

  const { data: brokersData, isLoading: brokersLoading } = useAvailableBrokers()
  const { data: connectionsData, isLoading: connectionsLoading, refetch: refetchConnections } = useBrokerConnections()
  const { data: prefsData } = useBrokerPreferences()

  const initiateConnection = useInitiateConnection()
  const disconnectBroker = useDisconnectBroker()
  const triggerFetch = useTriggerPositionFetch()
  const updatePrefs = useUpdateBrokerPreferences()

  // Handle URL params for success/error messages
  useEffect(() => {
    const success = searchParams.get('success')
    const error = searchParams.get('error')
    const broker = searchParams.get('broker')

    if (success === 'true' && broker) {
      setNotification({ type: 'success', message: `Successfully connected to ${broker}` })
      refetchConnections()
    } else if (error) {
      const errorMessages: Record<string, string> = {
        state_invalid: 'OAuth state expired or invalid. Please try again.',
        connection_failed: 'Failed to connect to broker. Please try again.'
      }
      setNotification({ type: 'error', message: errorMessages[error] || 'An error occurred' })
    }

    // Clear params from URL
    if (success || error) {
      window.history.replaceState({}, '', '/brokers/connections')
    }
  }, [searchParams, refetchConnections])

  // Auto-dismiss notification
  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 5000)
      return () => clearTimeout(timer)
    }
  }, [notification])

  const handleConnect = (brokerCode: string) => {
    initiateConnection.mutate(brokerCode)
  }

  const handleFetch = (connectionId: number) => {
    setFetchingConnectionId(connectionId)
    triggerFetch.mutate(connectionId, {
      onSettled: () => {
        setTimeout(() => setFetchingConnectionId(null), 2000)
      }
    })
  }

  const handleDisconnect = (connectionId: number) => {
    disconnectBroker.mutate(connectionId, {
      onSuccess: () => {
        setNotification({ type: 'success', message: 'Broker disconnected successfully' })
      },
      onError: () => {
        setNotification({ type: 'error', message: 'Failed to disconnect broker' })
      }
    })
  }

  const handleAutoFetchToggle = () => {
    if (prefsData) {
      updatePrefs.mutate({
        autoFetchEnabled: !prefsData.autoFetchEnabled,
        fetchTimeUtc: prefsData.fetchTimeUtc
      })
    }
  }

  const brokers = brokersData?.brokers || []
  const connections = connectionsData?.connections || []

  // Check which brokers have existing connections
  const connectedBrokerCodes = new Set(connections.map(c => c.broker.code))

  if (brokersLoading || connectionsLoading) {
    return (
      <div style={{ padding: '24px', textAlign: 'center' }}>
        <div>Loading...</div>
      </div>
    )
  }

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 600, color: '#111827', margin: 0 }}>
          Broker Connections
        </h1>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '14px', color: '#6b7280' }}>Daily Auto-fetch:</span>
          <button
            onClick={handleAutoFetchToggle}
            style={{
              padding: '6px 12px',
              borderRadius: '16px',
              border: 'none',
              backgroundColor: prefsData?.autoFetchEnabled ? '#10b981' : '#e5e7eb',
              color: prefsData?.autoFetchEnabled ? '#fff' : '#6b7280',
              fontSize: '13px',
              fontWeight: 500,
              cursor: 'pointer',
              transition: 'background-color 0.2s'
            }}
          >
            {prefsData?.autoFetchEnabled ? 'ON' : 'OFF'}
          </button>
        </div>
      </div>

      {/* Notification */}
      {notification && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: '8px',
            marginBottom: '16px',
            backgroundColor: notification.type === 'success' ? '#d1fae5' : '#fee2e2',
            color: notification.type === 'success' ? '#065f46' : '#991b1b',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <span>{notification.message}</span>
          <button
            onClick={() => setNotification(null)}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              fontSize: '18px',
              color: 'inherit'
            }}
          >
            x
          </button>
        </div>
      )}

      {/* Available Brokers */}
      <section style={{ marginBottom: '32px' }}>
        <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#374151', marginBottom: '16px' }}>
          Available Brokers
        </h2>
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          {brokers.map(broker => (
            <BrokerCard
              key={broker.id}
              broker={broker}
              onConnect={handleConnect}
              isConnecting={initiateConnection.isPending}
              hasExistingConnection={connectedBrokerCodes.has(broker.code)}
            />
          ))}
        </div>
      </section>

      {/* Connected Accounts */}
      <section>
        <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#374151', marginBottom: '16px' }}>
          Connected Accounts
        </h2>

        {connections.length === 0 ? (
          <div
            style={{
              border: '1px dashed #d1d5db',
              borderRadius: '8px',
              padding: '32px',
              textAlign: 'center',
              color: '#6b7280'
            }}
          >
            <p style={{ margin: 0 }}>No broker accounts connected yet.</p>
            <p style={{ margin: '8px 0 0', fontSize: '14px' }}>
              Connect a broker above to start tracking your positions.
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {connections.map(connection => (
              <BrokerConnectionCard
                key={connection.id}
                connection={connection}
                onFetch={handleFetch}
                onDisconnect={handleDisconnect}
                isFetching={fetchingConnectionId === connection.id}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
