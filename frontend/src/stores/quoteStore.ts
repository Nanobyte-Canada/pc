import { create } from 'zustand'
import type { Quote, OptionsChain } from '@/types/options'

interface QuoteState {
  quotes: Record<string, Quote>
  chains: Record<string, OptionsChain>
  selectedUnderlying: string | null
  setQuote: (symbol: string, quote: Quote) => void
  setChain: (underlying: string, chain: OptionsChain) => void
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
  setSelectedUnderlying: (symbol) => set({ selectedUnderlying: symbol }),
  clearQuotes: () => set({ quotes: {}, chains: {} }),
}))
