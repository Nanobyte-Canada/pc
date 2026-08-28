import { create } from 'zustand'
import type { Quote, OptionsChain, OptionQuoteData } from '@/types/options'

interface QuoteState {
  quotes: Record<string, Quote>
  chains: Record<string, OptionsChain>
  selectedUnderlying: string | null
  providerConnected: boolean | null
  setQuote: (symbol: string, quote: Quote) => void
  setChain: (underlying: string, chain: OptionsChain) => void
  updateChainQuote: (underlying: string, optionQuote: OptionQuoteData) => void
  batchUpdateChainQuotes: (updates: Array<{ underlying: string; optionQuote: OptionQuoteData }>) => void
  setSelectedUnderlying: (symbol: string | null) => void
  setProviderConnected: (connected: boolean | null) => void
  clearQuotes: () => void
}

/**
 * Normalize a strike value to a consistent string key for chain lookups.
 * Handles number inputs (150, 150.5) and string inputs ("150", "150.0", "150.0000").
 * Strips trailing zeros after the decimal point and uses at most 4 decimal places.
 */
function normalizeStrikeKey(strike: number | string): string {
  const num = typeof strike === 'number' ? strike : parseFloat(strike)
  if (isNaN(num)) return String(strike)
  // Use toFixed(4) then strip trailing zeros to match backend BigDecimal serialization
  const fixed = num.toFixed(4)
  // Remove trailing zeros after decimal, but keep at least one digit after decimal if present
  return fixed.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '.0')
}

export const useQuoteStore = create<QuoteState>()((set) => ({
  quotes: {},
  chains: {},
  selectedUnderlying: null,
  providerConnected: null,
  setQuote: (symbol, quote) =>
    set((state) => ({ quotes: { ...state.quotes, [symbol]: quote } })),
  setChain: (underlying, chain) =>
    set((state) => ({ chains: { ...state.chains, [underlying]: chain } })),
  updateChainQuote: (underlying, optionQuote) =>
    set((state) => {
      const chain = state.chains[underlying]
      if (!chain) return state

      // Normalize expiry: backend may send [2026,8,21] array or "2026-08-21" string
      const rawExpiry = optionQuote.expiry as unknown
      const expiryKey = Array.isArray(rawExpiry)
        ? `${rawExpiry[0]}-${String(rawExpiry[1]).padStart(2, '0')}-${String(rawExpiry[2]).padStart(2, '0')}`
        : String(rawExpiry)

      const strikeKey = normalizeStrikeKey(optionQuote.strike)
      const expiryData = chain.expirations[expiryKey]
      if (!expiryData) return state

      // Try direct match first, then fallback strategies
      let strikeData = expiryData[strikeKey]
      if (!strikeData) {
        // Fallback: try matching with different decimal precision
        const numStrike = typeof optionQuote.strike === 'number' ? optionQuote.strike : parseFloat(String(optionQuote.strike))
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
        if (!strikeData) {
          return state
        }
      }

      const side = optionQuote.optionType === 'CALL' ? 'call' : 'put'
      const updatedStrikeData = { ...strikeData, [side]: optionQuote }
      const updatedExpiry = { ...expiryData, [strikeKey]: updatedStrikeData }
      const updatedExpirations = { ...chain.expirations, [expiryKey]: updatedExpiry }
      const updatedChain = { ...chain, expirations: updatedExpirations }

      return { chains: { ...state.chains, [underlying]: updatedChain } }
    }),
  batchUpdateChainQuotes: (updates) =>
    set((state) => {
      // Apply all updates in a single state transition to avoid 50+ renders
      let chains = state.chains
      for (const { underlying, optionQuote } of updates) {
        const chain = chains[underlying]
        if (!chain) continue

        const rawExpiry = optionQuote.expiry as unknown
        const expiryKey = Array.isArray(rawExpiry)
          ? `${rawExpiry[0]}-${String(rawExpiry[1]).padStart(2, '0')}-${String(rawExpiry[2]).padStart(2, '0')}`
          : String(rawExpiry)

        const strikeKey = normalizeStrikeKey(optionQuote.strike)
        const expiryData = chain.expirations[expiryKey]
        if (!expiryData) continue

        let strikeData = expiryData[strikeKey]
        if (!strikeData) {
          const numStrike = typeof optionQuote.strike === 'number' ? optionQuote.strike : parseFloat(String(optionQuote.strike))
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
          if (!strikeData) continue
        }

        const side = optionQuote.optionType === 'CALL' ? 'call' : 'put'
        const updatedStrikeData = { ...strikeData, [side]: optionQuote }
        const updatedExpiry = { ...expiryData, [strikeKey]: updatedStrikeData }
        const updatedExpirations = { ...chain.expirations, [expiryKey]: updatedExpiry }
        const updatedChain = { ...chain, expirations: updatedExpirations }
        chains = { ...chains, [underlying]: updatedChain }
      }
      return { chains }
    }),
  setSelectedUnderlying: (symbol) => set({ selectedUnderlying: symbol }),
  setProviderConnected: (connected) => set({ providerConnected: connected }),
  clearQuotes: () => set({ quotes: {}, chains: {} }),
}))
