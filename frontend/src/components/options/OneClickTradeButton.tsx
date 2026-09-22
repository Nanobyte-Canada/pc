import { useState, useCallback } from 'react'
import { useStrategyStore } from '@/stores/strategyStore'
import { useQuoteStore } from '@/stores/quoteStore'
import { useBrokerConnection } from '@/hooks/useBrokerConnection'
import { useToast } from '@/stores/toastStore'
import { proxyFetch, parseErrorResponse } from '@/services/api'
import type { TradeRequest, TradeResponse } from '@/types/options'
import './OneClickTradeButton.css'

export function OneClickTradeButton() {
  const { legs, selectedStrategy, tradeInProgress, setTradeInProgress, connectionStatus } = useStrategyStore()
  const { connection, loading: connLoading } = useBrokerConnection()
  const selectedUnderlying = useQuoteStore((s) => s.selectedUnderlying)
  const quote = useQuoteStore((s) => selectedUnderlying ? s.quotes[selectedUnderlying] : null)
  const toast = useToast()
  const [tradeResult, setTradeResult] = useState<TradeResponse | null>(null)

  const isConnected = connection?.connected ?? connectionStatus.connected
  const hasTradableLegs = legs.length > 0 && legs.every((l) => !!l.symbol)
  const isDisabled = !isConnected || connLoading || tradeInProgress || !hasTradableLegs || !quote

  const handleTrade = useCallback(async () => {
    if (isDisabled || !selectedStrategy || !connection) return

    const confirmed = window.confirm(
      `Submit ${legs.length}-leg ${selectedStrategy.replace(/_/g, ' ').toLowerCase()} order to Questrade?`
    )
    if (!confirmed) return

    setTradeInProgress(true)
    setTradeResult(null)

    try {
      const limitPrice = legs.reduce((sum, l) => {
        if (l.action === 'BUY') return sum + (l.price ?? 0)
        return sum - (l.price ?? 0)
      }, 0)

      const request: TradeRequest = {
        strategyType: selectedStrategy,
        legs: legs.map(l => ({
          action: l.action,
          optionType: l.optionType,
          strike: l.strike,
          expiry: l.expiry,
          quantity: l.quantity ?? 1,
          price: l.price,
          bid: l.bid,
          ask: l.ask,
          mid: l.mid,
          delta: l.delta,
          symbol: l.symbol,
        })),
        spotPrice: quote!.last,
        connectionId: connection.connectionId,
        accountId: connection.accountNumber ?? '',
        limitPrice: Math.abs(limitPrice),
        orderType: 'LIMIT',
        timeInForce: 'DAY',
      }

      const response = await proxyFetch('/strategy-api/api/v1/strategies/trade', {
        method: 'POST',
        body: JSON.stringify(request),
      })

      if (!response.ok) throw await parseErrorResponse(response)

      const result: TradeResponse = await response.json()
      setTradeResult(result)

      if (result.status === 'SUBMITTED') {
        toast.success(`Order submitted: ${result.message}`)
      } else if (result.status === 'REJECTED') {
        toast.warning(`Order rejected: ${result.message}`)
      } else {
        toast.error(`Trade failed: ${result.message}`)
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Trade submission failed'
      toast.error(message)
      setTradeResult({ orderId: null, status: 'ERROR', message })
    } finally {
      setTradeInProgress(false)
    }
  }, [isDisabled, selectedStrategy, connection, legs, quote, setTradeInProgress, toast])

  if (connLoading) {
    return (
      <div className="one-click-trade">
        <button className="one-click-trade__btn" disabled>
          Loading...
        </button>
      </div>
    )
  }

  if (!isConnected) {
    return (
      <div className="one-click-trade">
        <div className="one-click-trade__disconnected">
          <p className="one-click-trade__disconnected-text">
            Connect your Questrade account to trade
          </p>
          <a href="/brokers/connections" className="one-click-trade__connect-link">
            Go to Connections
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="one-click-trade">
      <button
        className={`one-click-trade__btn ${tradeInProgress ? 'one-click-trade__btn--loading' : ''}`}
        onClick={handleTrade}
        disabled={isDisabled}
      >
        {tradeInProgress ? (
          <>
            <span className="one-click-trade__spinner" />
            Submitting...
          </>
        ) : (
          <>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            Trade on Questrade
          </>
        )}
      </button>

      {legs.length === 0 && isConnected && (
        <p className="one-click-trade__hint">
          Add legs to the builder to enable trading
        </p>
      )}

      {legs.length > 0 && !hasTradableLegs && isConnected && (
        <p className="one-click-trade__hint">
          Every leg needs an underlying symbol. Add legs from the options chain.
        </p>
      )}

      {tradeResult && (
        <div className={`one-click-trade__result one-click-trade__result--${tradeResult.status.toLowerCase()}`}>
          {tradeResult.orderId && (
            <span className="one-click-trade__order-id">Order #{tradeResult.orderId}</span>
          )}
          <span className="one-click-trade__result-message">{tradeResult.message}</span>
        </div>
      )}
    </div>
  )
}
