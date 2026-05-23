import { create } from 'zustand'
import type { Quote, OptionsChain, OptionQuoteData } from '@/types/options'

interface QuoteState {
  quotes: Record<string, Quote>
  chains: Record<string, OptionsChain>
  selectedUnderlying: string | null
  setQuote: (symbol: string, quote: Quote) => void
  setChain: (underlying: string, chain: OptionsChain) => void
  updateChainQuote: (underlying: string, optionQuote: OptionQuoteData) => void
  setSelectedUnderlying: (symbol: string | null) => void
  clearQuotes: () => void
}

export const useQuoteStore = create<QuoteState>()((set) => ({
  quotes: {},
  chains: {},
  selectedUnderlying: null,
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
      const strikeKey = String(optionQuote.strike) + (String(optionQuote.strike).includes('.') ? '' : '.0')
      const expiryData = chain.expirations[expiryKey]
      if (!expiryData) return state

      const strikeData = expiryData[strikeKey]
      if (!strikeData) return state

      const side = optionQuote.optionType === 'CALL' ? 'call' : 'put'
      const updatedStrikeData = { ...strikeData, [side]: optionQuote }
      const updatedExpiry = { ...expiryData, [strikeKey]: updatedStrikeData }
      const updatedExpirations = { ...chain.expirations, [expiryKey]: updatedExpiry }
      const updatedChain = { ...chain, expirations: updatedExpirations }

      return { chains: { ...state.chains, [underlying]: updatedChain } }
    }),
  setSelectedUnderlying: (symbol) => set({ selectedUnderlying: symbol }),
  clearQuotes: () => set({ quotes: {}, chains: {} }),
}))
