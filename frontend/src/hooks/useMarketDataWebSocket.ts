import { useEffect, useRef, useCallback, useState } from 'react'
import { useQuoteStore } from '@/stores/quoteStore'
import type { Quote } from '@/types/options'

interface UseMarketDataWebSocketOptions {
  autoConnect?: boolean
}

export function useMarketDataWebSocket(options: UseMarketDataWebSocketOptions = {}) {
  const { autoConnect = true } = options
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout>>()
  const reconnectDelayRef = useRef(1000)
  const subscribedSymbolsRef = useRef<Set<string>>(new Set())
  const [isConnected, setIsConnected] = useState(false)
  const setQuote = useQuoteStore((state) => state.setQuote)

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/quotes`

    const ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      setIsConnected(true)
      reconnectDelayRef.current = 1000
      subscribedSymbolsRef.current.forEach((symbol) => {
        ws.send(JSON.stringify({ action: 'subscribe', symbol }))
      })
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as Quote
        if (data.symbol) {
          setQuote(data.symbol, data)
        }
      } catch {
        // ignore malformed messages
      }
    }

    ws.onclose = () => {
      setIsConnected(false)
      wsRef.current = null
      reconnectTimeoutRef.current = setTimeout(() => {
        reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, 30000)
        connect()
      }, reconnectDelayRef.current)
    }

    ws.onerror = () => {
      ws.close()
    }

    wsRef.current = ws
  }, [setQuote])

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current)
    wsRef.current?.close()
    wsRef.current = null
    setIsConnected(false)
  }, [])

  const subscribe = useCallback((symbol: string) => {
    subscribedSymbolsRef.current.add(symbol)
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ action: 'subscribe', symbol }))
    }
  }, [])

  const unsubscribe = useCallback((symbol: string) => {
    subscribedSymbolsRef.current.delete(symbol)
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ action: 'unsubscribe', symbol }))
    }
  }, [])

  const subscribeOption = useCallback(
    (symbol: string, expiry: string, strike: string, optionType: string) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({ action: 'subscribe_option', symbol, expiry, strike, optionType })
        )
      }
    },
    []
  )

  const unsubscribeOption = useCallback(
    (symbol: string, expiry: string, strike: string, optionType: string) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({ action: 'unsubscribe_option', symbol, expiry, strike, optionType })
        )
      }
    },
    []
  )

  useEffect(() => {
    if (autoConnect) connect()
    return () => disconnect()
  }, [autoConnect, connect, disconnect])

  return {
    isConnected,
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    subscribeOption,
    unsubscribeOption,
  }
}
