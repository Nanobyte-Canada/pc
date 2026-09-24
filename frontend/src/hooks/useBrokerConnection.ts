import { useEffect, useState } from 'react'
import { proxyFetch } from '@/services/api'

// ─── Types ──────────────────────────────────────────────────────────────────

export interface BrokerConnection {
  connectionId: number
  brokerType: string
  connected: boolean
  accountNumber: string | null
}

export interface UseBrokerConnectionResult {
  connection: BrokerConnection | null
  loading: boolean
}

// ─── Hook ───────────────────────────────────────────────────────────────────

/**
 * Fetches the first broker connection status on mount and provides it
 * to the strategy UI through the shared authenticated proxy wrapper.
 */
export function useBrokerConnection(): UseBrokerConnectionResult {
  const [connection, setConnection] = useState<BrokerConnection | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function fetchConnection() {
      try {
        const response = await proxyFetch('/api/v1/brokers/connections')
        if (response.ok) {
          const data = await response.json()
          const connections = data.connections ?? (Array.isArray(data) ? data : [])
          if (connections.length > 0) {
            const conn = connections[0]
            if (!cancelled) {
              setConnection({
                connectionId: conn.id,
                brokerType: conn.broker?.slug ?? conn.connectionType ?? conn.brokerType ?? 'unknown',
                connected: conn.status === 'ACTIVE',
                accountNumber: conn.accountNumberActual ?? conn.accountNumber ?? null,
              })
            }
          }
        }
      } catch (error) {
        console.error('Failed to load broker connection', error)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchConnection()

    return () => {
      cancelled = true
    }
  }, [])

  return { connection, loading }
}

export default useBrokerConnection
