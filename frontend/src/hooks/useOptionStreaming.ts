import { useEffect, useRef, useMemo } from 'react'
import { useMarketDataWebSocket } from './useMarketDataWebSocket'
import { useQuoteStore } from '@/stores/quoteStore'

// ─── Types ──────────────────────────────────────────────────────────────────

export interface OptionTick {
  bid: number
  ask: number
  last: number
  volume?: number
  timestamp?: string
}

interface Contract {
  strike: number
  optionType: 'CALL' | 'PUT'
}

export interface UseOptionStreamingOptions {
  underlying: string
  expiry: string
  contracts: Contract[]
}

export interface UseOptionStreamingResult {
  livePrices: Map<string, OptionTick>
  isStreaming: boolean
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function contractKey(underlying: string, expiry: string, strike: number, optionType: string): string {
  return `${underlying}:${expiry}:${strike}:${optionType}`
}

function parseContractKey(key: string): { symbol: string; expiry: string; strike: string; optionType: string } {
  const [symbol, expiry, strike, optionType] = key.split(':')
  return { symbol, expiry, strike, optionType }
}

// ─── Hook ───────────────────────────────────────────────────────────────────

/**
 * Manages streaming subscriptions for a list of visible option contracts.
 *
 * - Subscribes to new contracts and unsubscribes from removed ones on change.
 * - Unsubscribes all contracts on unmount.
 * - Returns a live price map keyed by `underlying:expiry:strike:optionType`
 *   with bid/ask/last values, populated from the quote store (which receives
 *   option_quote messages from the WebSocket).
 */
export function useOptionStreaming({ underlying, expiry, contracts }: UseOptionStreamingOptions): UseOptionStreamingResult {
  const { subscribeOption, unsubscribeOption, isConnected } = useMarketDataWebSocket()
  const prevContractsRef = useRef<string[]>([])

  // ── Subscription lifecycle ──────────────────────────────────────────────
  useEffect(() => {
    if (!isConnected || !underlying || !expiry || contracts.length === 0) return

    const newKeys = contracts.map(c => contractKey(underlying, expiry, c.strike, c.optionType))
    const oldKeys = prevContractsRef.current

    // Unsubscribe contracts that were removed
    const toUnsubscribe = oldKeys.filter(k => !newKeys.includes(k))
    // Subscribe contracts that are new
    const toSubscribe = newKeys.filter(k => !oldKeys.includes(k))

    for (const key of toUnsubscribe) {
      const { symbol, expiry: exp, strike, optionType } = parseContractKey(key)
      unsubscribeOption(symbol, exp, strike, optionType)
    }

    for (const key of toSubscribe) {
      const { symbol, expiry: exp, strike, optionType } = parseContractKey(key)
      subscribeOption(symbol, exp, strike, optionType)
    }

    prevContractsRef.current = newKeys

    return () => {
      // Cleanup: unsubscribe all on unmount or dependency change
      for (const key of newKeys) {
        const { symbol, expiry: exp, strike, optionType } = parseContractKey(key)
        unsubscribeOption(symbol, exp, strike, optionType)
      }
      prevContractsRef.current = []
    }
  }, [isConnected, underlying, expiry, contracts, subscribeOption, unsubscribeOption])

  // ── Live prices from quote store ────────────────────────────────────────
  // The WebSocket singleton already routes option_quote messages into the
  // Zustand quote store. We derive a Map<string, OptionTick> from it.
  const chains = useQuoteStore(state => state.chains)
  const chain = chains[underlying]

  const livePrices = useMemo(() => {
    const map = new Map<string, OptionTick>()
    if (!chain || !expiry || contracts.length === 0) return map

    for (const contract of contracts) {
      const strikeKey = String(contract.strike)
      // Try common strike key formats (the store normalizes strikes)
      const expiryData = chain.expirations[expiry]
      if (!expiryData) continue

      // Find strike data — try direct match then fallbacks
      let strikeData = expiryData[strikeKey]
      if (!strikeData) {
        const numStrike = contract.strike
        const candidates = [
          String(numStrike),
          numStrike.toFixed(1),
          numStrike.toFixed(2),
          numStrike.toFixed(4),
          `${numStrike}.0`,
          `${numStrike}.00`,
        ]
        for (const candidate of candidates) {
          if (expiryData[candidate]) {
            strikeData = expiryData[candidate]
            break
          }
        }
      }
      if (!strikeData) continue

      const side = contract.optionType === 'CALL' ? 'call' : 'put'
      const oq = strikeData[side]
      if (!oq) continue

      const key = contractKey(underlying, expiry, contract.strike, contract.optionType)
      map.set(key, {
        bid: oq.bid,
        ask: oq.ask,
        last: oq.last,
        volume: oq.volume,
        timestamp: oq.timestamp,
      })
    }

    return map
  }, [chain, expiry, contracts, underlying])

  return {
    livePrices,
    isStreaming: isConnected && contracts.length > 0,
  }
}

export default useOptionStreaming
