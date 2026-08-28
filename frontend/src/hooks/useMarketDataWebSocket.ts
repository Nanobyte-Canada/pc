import { useEffect, useCallback, useState } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import type { Quote, OptionQuoteData } from '@/types/options'

// ─── Singleton WebSocket manager ────────────────────────────────────────────
// A single WebSocket connection shared across all hook instances.
// Ref-counted: connects on first subscriber, disconnects when last unsubscribes.

interface ChainSubscription {
  expiry: string
  side?: 'put' | 'call'
}

let wsInstance: WebSocket | null = null
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = 1000
let refCount = 0
const subscribedSymbols = new Set<string>()
const subscribedChains = new Set<string>()
const expirySubscriptions = new Map<string, ChainSubscription>()
const connectionListeners = new Set<(connected: boolean) => void>()

// ─── Message batching ───────────────────────────────────────────────────────
// Buffers option_quote messages and flushes them in a single Zustand update
// via requestAnimationFrame, collapsing 50+ renders into 1.

const pendingUpdates = new Map<string, { underlying: string; optionQuote: OptionQuoteData }>()
let rafId = 0

function flushBatch() {
  rafId = 0
  if (pendingUpdates.size === 0) return
  const updates = Array.from(pendingUpdates.values())
  pendingUpdates.clear()
  useQuoteStore.getState().batchUpdateChainQuotes(updates)
}

function queueUpdate(underlying: string, oq: OptionQuoteData) {
  // Deduplicate by underlying:expiry:strike:optionType key
  const key = `${underlying}:${oq.expiry}:${oq.strike}:${oq.optionType}`
  pendingUpdates.set(key, { underlying, optionQuote: oq })
  if (!rafId) {
    rafId = requestAnimationFrame(flushBatch)
  }
}

// ─── Connection management ──────────────────────────────────────────────────

function connect() {
  if (wsInstance?.readyState === WebSocket.OPEN) return

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/quotes`

  const ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    reconnectDelay = 1000
    connectionListeners.forEach((l) => l(true))
    // Re-send subscriptions
    subscribedSymbols.forEach((symbol) => {
      ws.send(JSON.stringify({ action: 'subscribe', symbol }))
    })
    expirySubscriptions.forEach((info, underlying) => {
      const msg: Record<string, string> = { action: 'subscribe_chain_expiry', underlying, expiry: info.expiry }
      if (info.side) msg.side = info.side
      ws.send(JSON.stringify(msg))
    })
    subscribedChains.forEach((underlying) => {
      if (!expirySubscriptions.has(underlying)) {
        ws.send(JSON.stringify({ action: 'subscribe_chain', underlying }))
      }
    })
  }

  ws.onmessage = (event) => {
    try {
      const raw = JSON.parse(event.data)
      if (raw.type === 'connection_status') {
        connectionListeners.forEach((l) => l(raw.connected))
        return
      }
      if (raw.type === 'option_quote' && raw.data) {
        const oq = raw.data as OptionQuoteData
        if (oq.underlying) {
          queueUpdate(oq.underlying, oq)
        }
        return
      }
      const data = raw as Quote
      if (data.symbol) {
        useQuoteStore.getState().setQuote(data.symbol, data)
      }
    } catch {
      // ignore malformed messages
    }
  }

  ws.onclose = () => {
    connectionListeners.forEach((l) => l(false))
    wsInstance = null
    reconnectTimeout = setTimeout(() => {
      reconnectDelay = Math.min(reconnectDelay * 2, 30000)
      connect()
    }, reconnectDelay)
  }

  ws.onerror = () => {
    ws.close()
  }

  wsInstance = ws
}

function disconnect() {
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
  pendingUpdates.clear()
  wsInstance?.close()
  wsInstance = null
  connectionListeners.forEach((l) => l(false))
}

function send(msg: object) {
  if (wsInstance?.readyState === WebSocket.OPEN) {
    wsInstance.send(JSON.stringify(msg))
  }
}

// ─── React hook ─────────────────────────────────────────────────────────────

interface UseMarketDataWebSocketOptions {
  autoConnect?: boolean
}

export function useMarketDataWebSocket(options: UseMarketDataWebSocketOptions = {}) {
  const { autoConnect = true } = options
  const [isConnected, setIsConnected] = useState(false)
  const setProviderConnected = useQuoteStore((state) => state.setProviderConnected)

  useEffect(() => {
    if (!autoConnect) return

    refCount++
    const listener = (connected: boolean) => {
      setIsConnected(connected)
      setProviderConnected(connected)
    }
    connectionListeners.add(listener)

    if (refCount === 1) {
      connect()
    } else {
      // Already connected — sync current state
      setIsConnected(wsInstance?.readyState === WebSocket.OPEN)
    }

    return () => {
      connectionListeners.delete(listener)
      refCount--
      if (refCount <= 0) {
        refCount = 0
        disconnect()
      }
    }
  }, [autoConnect, setProviderConnected])

  const subscribe = useCallback((symbol: string) => {
    subscribedSymbols.add(symbol)
    send({ action: 'subscribe', symbol })
  }, [])

  const unsubscribe = useCallback((symbol: string) => {
    subscribedSymbols.delete(symbol)
    send({ action: 'unsubscribe', symbol })
  }, [])

  const subscribeChain = useCallback((underlying: string) => {
    subscribedChains.add(underlying)
    send({ action: 'subscribe_chain', underlying })
  }, [])

  const unsubscribeChain = useCallback((underlying: string) => {
    subscribedChains.delete(underlying)
    expirySubscriptions.delete(underlying)
    send({ action: 'unsubscribe_chain', underlying })
  }, [])

  const subscribeChainExpiry = useCallback((underlying: string, expiry: string, side?: 'put' | 'call') => {
    subscribedChains.add(underlying)
    expirySubscriptions.set(underlying, { expiry, side })
    const msg: Record<string, string> = { action: 'subscribe_chain_expiry', underlying, expiry }
    if (side) msg.side = side
    send(msg)
  }, [])

  const switchChainExpiry = useCallback((underlying: string, expiry: string, side?: 'put' | 'call') => {
    expirySubscriptions.set(underlying, { expiry, side })
    const msg: Record<string, string> = { action: 'switch_chain_expiry', underlying, expiry }
    if (side) msg.side = side
    send(msg)
  }, [])

  const subscribeOption = useCallback(
    (symbol: string, expiry: string, strike: string, optionType: string) => {
      send({ action: 'subscribe_option', symbol, expiry, strike, optionType })
    },
    []
  )

  const unsubscribeOption = useCallback(
    (symbol: string, expiry: string, strike: string, optionType: string) => {
      send({ action: 'unsubscribe_option', symbol, expiry, strike, optionType })
    },
    []
  )

  return {
    isConnected,
    connect: () => connect(),
    disconnect: () => disconnect(),
    subscribe,
    unsubscribe,
    subscribeChain,
    unsubscribeChain,
    subscribeChainExpiry,
    switchChainExpiry,
    subscribeOption,
    unsubscribeOption,
  }
}
