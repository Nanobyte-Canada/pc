import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { BrokerCard } from '../components/broker/BrokerCard'
import { BrokerConnectionCard } from '../components/broker/BrokerConnectionCard'
import { SnapTradeBadge } from '../components/broker/SnapTradeBadge'
import {
  useAvailableBrokers,
  useBrokerConnections,
  useConnectBroker,
  useDisconnectBroker,
  useTriggerPositionFetch,
  useSyncConnections
} from '../hooks/useBrokerConnections'
import './BrokerConnectionsPage.css'

export function BrokerConnectionsPage() {
  const [searchParams] = useSearchParams()
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [fetchingConnectionId, setFetchingConnectionId] = useState<number | null>(null)

  const { data: brokersData, isLoading: brokersLoading } = useAvailableBrokers()
  const { data: connectionsData, isLoading: connectionsLoading, refetch: refetchConnections } = useBrokerConnections()

  const connectBroker = useConnectBroker()
  const disconnectBroker = useDisconnectBroker()
  const triggerFetch = useTriggerPositionFetch()
  const sync = useSyncConnections()

  // Handle return from SnapTrade portal (query params)
  useEffect(() => {
    const success = searchParams.get('success')
    const error = searchParams.get('error')
    const status = searchParams.get('status')

    if (success === 'true' || status === 'SUCCESS') {
      sync.mutate(undefined, {
        onSuccess: () => {
          refetchConnections()
          setNotification({ type: 'success', message: 'Broker connected successfully!' })
        },
        onError: () => {
          refetchConnections()
          setNotification({ type: 'success', message: 'Broker connected! Sync may be delayed.' })
        }
      })
    } else if (error) {
      const errorMessages: Record<string, string> = {
        state_invalid: 'Connection session expired. Please try again.',
        connection_failed: 'Failed to connect to broker. Please try again.',
        ABANDONED: 'Connection was cancelled.'
      }
      setNotification({ type: 'error', message: errorMessages[error] || 'An error occurred' })
    }

    // Clear params from URL
    if (success || error || status) {
      window.history.replaceState({}, '', '/brokers/connections')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, refetchConnections])

  // Auto-dismiss notification
  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 5000)
      return () => clearTimeout(timer)
    }
  }, [notification])

  const handleConnect = (brokerSlug?: string) => {
    connectBroker.mutate(brokerSlug ? { broker: brokerSlug } : undefined)
  }

  const handleReconnect = (authorizationId: string) => {
    connectBroker.mutate({ reconnectAuthId: authorizationId })
  }

  const handleFetch = (connectionId: number) => {
    setFetchingConnectionId(connectionId)
    triggerFetch.mutate(connectionId, {
      onSettled: () => {
        setTimeout(() => setFetchingConnectionId(null), 2000)
      }
    })
  }

  const handleDisconnect = (authorizationId: string) => {
    disconnectBroker.mutate(authorizationId, {
      onSuccess: () => {
        setNotification({ type: 'success', message: 'Broker disconnected successfully' })
      },
      onError: () => {
        setNotification({ type: 'error', message: 'Failed to disconnect broker' })
      }
    })
  }

  const brokers = brokersData?.brokers || []
  const connections = connectionsData?.connections || []

  // Check which brokers have existing connections
  const connectedBrokerSlugs = new Set(connections.map(c => c.broker.slug).filter(Boolean))

  if (brokersLoading || connectionsLoading) {
    return (
      <div className="broker-connections-page page-loading">
        <div>Loading...</div>
      </div>
    )
  }

  return (
    <div className="broker-connections-page">
      {/* Header */}
      <div className="broker-connections-header">
        <h1>Broker Connections</h1>
        <SnapTradeBadge />
      </div>

      {/* Notification */}
      {notification && (
        <div className={`broker-notification ${notification.type}`}>
          <span>{notification.message}</span>
          <button className="dismiss-btn" onClick={() => setNotification(null)}>
            x
          </button>
        </div>
      )}

      {/* Available Brokers */}
      <section className="broker-section">
        <h2>Available Brokers</h2>
        <div className="broker-cards-grid">
          {brokers.length > 0 ? (
            brokers.map((broker, index) => (
              <BrokerCard
                key={broker.slug || index}
                broker={broker}
                onConnect={handleConnect}
                isConnecting={connectBroker.isPending}
                hasExistingConnection={connectedBrokerSlugs.has(broker.slug || '')}
              />
            ))
          ) : (
            <div className="broker-no-data">
              No brokerages available. Check your SnapTrade configuration.
            </div>
          )}
        </div>
      </section>

      {/* Connected Accounts */}
      <section className="broker-section">
        <h2>Connected Accounts</h2>

        {connections.length === 0 ? (
          <div className="broker-empty-state">
            <p>No broker accounts connected yet.</p>
            <p>Connect a broker above to start tracking your positions.</p>
          </div>
        ) : (
          <div className="broker-connections-list">
            {connections.map(connection => (
              <BrokerConnectionCard
                key={connection.id}
                connection={connection}
                onFetch={handleFetch}
                onDisconnect={handleDisconnect}
                onReconnect={handleReconnect}
                isFetching={fetchingConnectionId === connection.id}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
