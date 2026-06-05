import { useState, useEffect, useRef } from 'react'
import { LayoutGrid, Table, Loader2 } from 'lucide-react'
import { BrokerCard } from '../components/broker/BrokerCard'
import { BrokerageMatrix } from '../components/broker/BrokerageMatrix'
import { BrokerConnectionCard } from '../components/broker/BrokerConnectionCard'
import { ConnectBrokerDialog } from '../components/broker/ConnectBrokerDialog'
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
import type { ConnectBrokerRequest } from '../types/broker'
import './BrokerConnectionsPage.css'

export function BrokerConnectionsPage() {
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [syncingConnectionId, setSyncingConnectionId] = useState<number | null>(null)
  const [brokerView, setBrokerView] = useState<'cards' | 'matrix'>('cards')
  const [isSyncingNewConnection, setIsSyncingNewConnection] = useState(false)
  const [syncStatus, setSyncStatus] = useState('')
  const [connectDialogBroker, setConnectDialogBroker] = useState<string | null>(null)
  const [connectError, setConnectError] = useState<string | null>(null)
  const syncCalledRef = useRef(false)

  const { data: brokersData, isLoading: brokersLoading } = useAvailableBrokers()
  const { data: connectionsData, isLoading: connectionsLoading, refetch: refetchConnections } = useBrokerConnections()

  const connectBroker = useConnectBroker()
  const disconnectBroker = useDisconnectBroker()
  const sync = useSyncConnections()
  const syncAll = useSyncAll()

  useEffect(() => {
    if (syncCalledRef.current) return
    syncCalledRef.current = true
    sync.mutate(undefined, {
      onSuccess: () => refetchConnections()
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Auto-dismiss notification
  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 8000)
      return () => clearTimeout(timer)
    }
  }, [notification])

  const handleConnect = (brokerSlug?: string) => {
    setConnectError(null)
    setConnectDialogBroker(brokerSlug || null)
  }

  const handleConnectSubmit = async (request: ConnectBrokerRequest) => {
    setConnectError(null)
    connectBroker.mutate(request, {
      onSuccess: async (data) => {
        setConnectDialogBroker(null)
        const accountCount = data.connections?.length || 0
        setNotification({
          type: 'success',
          message: `Connected! Found ${accountCount} account${accountCount !== 1 ? 's' : ''}.`
        })

        if (accountCount > 0) {
          setIsSyncingNewConnection(true)
          setSyncStatus('Syncing accounts...')

          let totalPositions = 0
          let totalActivities = 0
          const newConnections = data.connections || []

          for (let i = 0; i < newConnections.length; i++) {
            const conn = newConnections[i]
            setSyncStatus(`Syncing ${conn.accountName || conn.accountType || `Account ${i + 1}`} (${i + 1}/${newConnections.length})...`)
            try {
              const result: SyncAllResponse = await syncAllConnectionData(conn.id)
              totalPositions += result.positionsFetched
              totalActivities += result.activitiesSynced
            } catch (e) {
              console.warn(`Sync failed for connection ${conn.id}:`, e)
            }
          }

          await refetchConnections()
          setIsSyncingNewConnection(false)
          setSyncStatus('')
          setNotification({
            type: 'success',
            message: `Synced ${totalPositions} positions and ${totalActivities} activities across ${newConnections.length} accounts.`
          })
        }
      },
      onError: (error) => {
        setConnectError(error.message || 'Failed to connect. Please check your token and try again.')
      }
    })
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleReconnect = (_gatewayConnectionId: string) => {
    setConnectError(null)
    setConnectDialogBroker('questrade')
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
      {/* Header — no connect button; clicking broker cards initiates connection */}
      <div className="broker-connections-header">
        <h1>Broker Connections</h1>
      </div>

      {/* Syncing overlay for new connections */}
      {isSyncingNewConnection && (
        <div className="broker-syncing-overlay">
          <Loader2 className="broker-syncing-spinner" size={20} />
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

      {/* Available Brokers — primary section, above connected accounts */}
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
                  connections={connections}
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
            No brokerages available. Check your broker gateway configuration.
          </div>
        )}
      </section>

      {/* Connected Accounts */}
      <section className="broker-section">
        <div className="broker-section-header">
          <h2>Connected Accounts</h2>
        </div>

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

      {/* Connect Broker Dialog */}
      {connectDialogBroker && (
        <ConnectBrokerDialog
          brokerType={connectDialogBroker}
          onConnect={handleConnectSubmit}
          onCancel={() => { setConnectDialogBroker(null); setConnectError(null) }}
          isConnecting={connectBroker.isPending}
          error={connectError}
        />
      )}
    </div>
  )
}
