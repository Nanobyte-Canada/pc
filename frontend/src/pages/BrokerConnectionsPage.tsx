import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { LayoutGrid, Table } from 'lucide-react'
import { BrokerCard } from '../components/broker/BrokerCard'
import { BrokerageMatrix } from '../components/broker/BrokerageMatrix'
import { BrokerConnectionCard } from '../components/broker/BrokerConnectionCard'
import { SnapTradeBadge } from '../components/broker/SnapTradeBadge'
import {
  useAvailableBrokers,
  useBrokerConnections,
  useConnectBroker,
  useDisconnectBroker,
  useTriggerPositionFetch,
  useSyncConnections,
  useSyncActivities
} from '../hooks/useBrokerConnections'
import './BrokerConnectionsPage.css'

export function BrokerConnectionsPage() {
  const [searchParams] = useSearchParams()
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [fetchingConnectionId, setFetchingConnectionId] = useState<number | null>(null)
  const [syncingActivitiesId, setSyncingActivitiesId] = useState<number | null>(null)
  const [brokerView, setBrokerView] = useState<'cards' | 'matrix'>('cards')

  const { data: brokersData, isLoading: brokersLoading } = useAvailableBrokers()
  const { data: connectionsData, isLoading: connectionsLoading, refetch: refetchConnections } = useBrokerConnections()

  const connectBroker = useConnectBroker()
  const disconnectBroker = useDisconnectBroker()
  const triggerFetch = useTriggerPositionFetch()
  const sync = useSyncConnections()
  const syncActivities = useSyncActivities()

  // Always sync connections from SnapTrade on page load
  useEffect(() => {
    sync.mutate(undefined, {
      onSuccess: () => refetchConnections()
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Handle return from SnapTrade portal (query params) — show notification + auto-fetch
  useEffect(() => {
    const success = searchParams.get('success')
    const error = searchParams.get('error')
    const status = searchParams.get('status')

    if (success === 'true' || status === 'SUCCESS') {
      setNotification({ type: 'success', message: 'Broker connected successfully! Fetching positions...' })
      // Sync first, then auto-fetch for new connections with no positions
      sync.mutate(undefined, {
        onSuccess: () => {
          refetchConnections().then(({ data }) => {
            const conns = data?.connections || []
            conns.filter(c => c.positionsCount === 0).forEach(c => {
              triggerFetch.mutate(c.id)
            })
          })
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
  }, [searchParams])

  // Auto-dismiss notification
  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 5000)
      return () => clearTimeout(timer)
    }
  }, [notification])

  const handleConnect = (brokerSlug?: string) => {
    connectBroker.mutate(brokerSlug ? { broker: brokerSlug } : undefined, {
      onError: () => {
        setNotification({ type: 'error', message: 'Failed to initiate broker connection. Please try again.' })
      }
    })
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

  const handleSyncActivities = (connectionId: number) => {
    setSyncingActivitiesId(connectionId)
    syncActivities.mutate(connectionId, {
      onSuccess: (data) => {
        setNotification({ type: 'success', message: `Synced ${data.activitiesSynced} activities` })
      },
      onError: () => {
        setNotification({ type: 'error', message: 'Failed to sync activities' })
      },
      onSettled: () => {
        setTimeout(() => setSyncingActivitiesId(null), 2000)
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
  const connectedBrokerSlugs = new Set(connections.map(c => c.broker.slug).filter((s): s is string => !!s))

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
        <div className="broker-section-header">
          <h2>Available Brokers</h2>
          <div className="broker-view-toggle">
            <button
              className={`view-toggle-btn${brokerView === 'cards' ? ' active' : ''}`}
              onClick={() => setBrokerView('cards')}
              title="Card view"
            >
              <LayoutGrid size={16} />
            </button>
            <button
              className={`view-toggle-btn${brokerView === 'matrix' ? ' active' : ''}`}
              onClick={() => setBrokerView('matrix')}
              title="Matrix view"
            >
              <Table size={16} />
            </button>
          </div>
        </div>

        {brokers.length > 0 ? (
          brokerView === 'cards' ? (
            <div className="broker-cards-grid">
              {brokers.map((broker, index) => (
                <BrokerCard
                  key={broker.slug || index}
                  broker={broker}
                  onConnect={handleConnect}
                  isConnecting={connectBroker.isPending}
                  hasExistingConnection={connectedBrokerSlugs.has(broker.slug || '')}
                />
              ))}
            </div>
          ) : (
            <BrokerageMatrix
              brokers={brokers}
              onConnect={handleConnect}
              connectedSlugs={connectedBrokerSlugs}
              isConnecting={connectBroker.isPending}
            />
          )
        ) : (
          <div className="broker-no-data">
            No brokerages available. Check your SnapTrade configuration.
          </div>
        )}
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
                onSyncActivities={handleSyncActivities}
                onDisconnect={handleDisconnect}
                onReconnect={handleReconnect}
                isFetching={fetchingConnectionId === connection.id}
                isSyncingActivities={syncingActivitiesId === connection.id}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
