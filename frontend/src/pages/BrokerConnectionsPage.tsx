import { useState, useEffect, useCallback, useRef } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { LayoutGrid, Table, Loader2 } from 'lucide-react'
import { BrokerCard } from '../components/broker/BrokerCard'
import { BrokerageMatrix } from '../components/broker/BrokerageMatrix'
import { BrokerConnectionCard } from '../components/broker/BrokerConnectionCard'
import { SnapTradeBadge } from '../components/broker/SnapTradeBadge'
import {
  useAvailableBrokers,
  useBrokerConnections,
  useConnectBroker,
  useDisconnectBroker,
  useSyncConnections,
  useSyncAll
} from '../hooks/useBrokerConnections'
import { syncAllConnectionData } from '../services/brokerService'
import type { SyncAllResponse } from '../services/brokerService'
import './BrokerConnectionsPage.css'

export function BrokerConnectionsPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [syncingConnectionId, setSyncingConnectionId] = useState<number | null>(null)
  const [brokerView, setBrokerView] = useState<'cards' | 'matrix'>('cards')
  const [isSyncingNewConnection, setIsSyncingNewConnection] = useState(false)
  const [syncStatus, setSyncStatus] = useState('')
  const syncCalledRef = useRef(false)

  const { data: brokersData, isLoading: brokersLoading } = useAvailableBrokers()
  const { data: connectionsData, isLoading: connectionsLoading, refetch: refetchConnections } = useBrokerConnections()

  const connectBroker = useConnectBroker()
  const disconnectBroker = useDisconnectBroker()
  const sync = useSyncConnections()
  const syncAll = useSyncAll()

  useEffect(() => {
    if (syncCalledRef.current) return
    const success = searchParams.get('success')
    const status = searchParams.get('status')
    if (success === 'true' || status === 'SUCCESS') return
    syncCalledRef.current = true
    sync.mutate(undefined, {
      onSuccess: () => refetchConnections()
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const runPostConnectionSync = useCallback(async () => {
    syncCalledRef.current = true
    setIsSyncingNewConnection(true)
    setSyncStatus('Discovering accounts...')

    try {
      await new Promise<void>((resolve) => {
        sync.mutate(undefined, {
          onSuccess: () => resolve(),
          onError: () => resolve()
        })
      })

      const { data } = await refetchConnections()
      const activeConnections = (data?.connections || []).filter(c => c.status === 'ACTIVE')

      if (activeConnections.length === 0) {
        setNotification({ type: 'success', message: 'Broker connected successfully!' })
        setIsSyncingNewConnection(false)
        setTimeout(() => navigate('/dashboard'), 1500)
        return
      }

      let totalPositions = 0
      let totalActivities = 0
      const syncResults: { connectionId: number; positionsFetched: number; activitiesSynced: number }[] = []

      for (let i = 0; i < activeConnections.length; i++) {
        const conn = activeConnections[i]
        const accountLabel = conn.accountName || `Account ${i + 1}`
        setSyncStatus(`Syncing ${accountLabel} (${i + 1}/${activeConnections.length})...`)

        try {
          const result: SyncAllResponse = await syncAllConnectionData(conn.id)
          totalPositions += result.positionsFetched
          totalActivities += result.activitiesSynced
          syncResults.push({ connectionId: conn.id, positionsFetched: result.positionsFetched, activitiesSynced: result.activitiesSynced })
        } catch (e) {
          console.warn(`Sync failed for connection ${conn.id}:`, e)
          syncResults.push({ connectionId: conn.id, positionsFetched: 0, activitiesSynced: 0 })
        }
      }

      // Auto-retry accounts with positions but 0 activities (broker hasn't cached data yet)
      let pendingIds = syncResults
        .filter(r => r.activitiesSynced === 0 && r.positionsFetched > 0)
        .map(r => r.connectionId)

      const retryDelays = [30000, 60000, 120000, 240000]

      for (let retry = 0; retry < retryDelays.length && pendingIds.length > 0; retry++) {
        const pendingNames = pendingIds
          .map(id => activeConnections.find(c => c.id === id)?.accountName || 'account')
          .join(', ')
        setSyncStatus(`Waiting for broker to prepare ${pendingNames}... (retry ${retry + 1}/${retryDelays.length})`)

        await new Promise(resolve => setTimeout(resolve, retryDelays[retry]))

        const stillPending: number[] = []
        for (const connId of pendingIds) {
          const conn = activeConnections.find(c => c.id === connId)
          setSyncStatus(`Retrying ${conn?.accountName || 'account'}... (retry ${retry + 1}/${retryDelays.length})`)
          try {
            const result = await syncAllConnectionData(connId)
            if (result.activitiesSynced > 0) {
              totalActivities += result.activitiesSynced
            } else {
              stillPending.push(connId)
            }
          } catch {
            stillPending.push(connId)
          }
        }
        pendingIds = stillPending
      }

      syncAll.reset()
      await refetchConnections()

      if (pendingIds.length > 0) {
        setNotification({
          type: 'success',
          message: `Connected! Synced ${totalPositions} positions and ${totalActivities} activities. ` +
            `${pendingIds.length} account(s) still being set up by your broker — click Sync All to check later.`
        })
      } else {
        setNotification({
          type: 'success',
          message: `Connected! Synced ${totalPositions} positions and ${totalActivities} activities across ${activeConnections.length} accounts.`
        })
      }

      setTimeout(() => navigate('/dashboard'), 1500)
    } catch (e) {
      setNotification({ type: 'error', message: 'Failed to sync accounts. Please try refreshing.' })
    } finally {
      setIsSyncingNewConnection(false)
      setSyncStatus('')
    }
  }, [sync, refetchConnections, syncAll, navigate])

  // Handle return from SnapTrade portal
  useEffect(() => {
    const success = searchParams.get('success')
    const error = searchParams.get('error')
    const status = searchParams.get('status')

    if (success === 'true' || status === 'SUCCESS') {
      window.history.replaceState({}, '', '/brokers/connections')
      runPostConnectionSync()
    } else if (error) {
      const errorMessages: Record<string, string> = {
        state_invalid: 'Connection session expired. Please try again.',
        connection_failed: 'Failed to connect to broker. Please try again.',
        ABANDONED: 'Connection was cancelled.'
      }
      setNotification({ type: 'error', message: errorMessages[error] || 'An error occurred' })
      window.history.replaceState({}, '', '/brokers/connections')
    } else if (status) {
      window.history.replaceState({}, '', '/brokers/connections')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  // Auto-dismiss notification
  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 8000)
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

  const handleSyncAll = (connectionId: number) => {
    setSyncingConnectionId(connectionId)
    syncAll.mutate(connectionId, {
      onSuccess: (data) => {
        setNotification({
          type: 'success',
          message: `Synced ${data.positionsFetched} positions and ${data.activitiesSynced} activities`
        })
      },
      onError: () => {
        setNotification({ type: 'error', message: 'Failed to sync connection data' })
      },
      onSettled: () => {
        setTimeout(() => setSyncingConnectionId(null), 2000)
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

      {/* Syncing overlay for new connections */}
      {isSyncingNewConnection && (
        <div className="broker-syncing-overlay">
          <Loader2 className="broker-syncing-spinner" size={24} />
          <span>{syncStatus || 'Setting up your accounts...'}</span>
        </div>
      )}

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
                onSyncAll={handleSyncAll}
                onDisconnect={handleDisconnect}
                onReconnect={handleReconnect}
                isSyncing={syncingConnectionId === connection.id}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
