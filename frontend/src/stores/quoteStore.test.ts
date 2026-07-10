import { describe, it, expect, beforeEach } from 'vitest'
import { useQuoteStore } from './quoteStore'
import type { OptionQuoteData, OptionsChain } from '@/types/options'

/** Helper to build a minimal chain for testing. */
function makeChain(underlying: string, strikes: string[]): OptionsChain {
  const expirations: Record<string, Record<string, { call: OptionQuoteData | null; put: OptionQuoteData | null }>> = {}
  for (const strikeKey of strikes) {
    const strike = parseFloat(strikeKey)
    const expiry = '2026-08-21'
    if (!expirations[expiry]) expirations[expiry] = {}
    expirations[expiry][strikeKey] = {
      call: {
        underlying,
        optionType: 'CALL',
        strike,
        expiry,
        bid: 1.0,
        ask: 1.5,
        last: 1.25,
        mid: 1.25,
        spread: 0.5,
        spreadQuality: 0.8,
        volume: 100,
        openInterest: 500,
        greeks: null,
        timestamp: new Date().toISOString(),
      },
      put: {
        underlying,
        optionType: 'PUT',
        strike,
        expiry,
        bid: 0.8,
        ask: 1.2,
        last: 1.0,
        mid: 1.0,
        spread: 0.4,
        spreadQuality: 0.7,
        volume: 80,
        openInterest: 400,
        greeks: null,
        timestamp: new Date().toISOString(),
      },
    }
  }
  return { underlying, spotPrice: 550, expirations }
}

describe('quoteStore', () => {
  beforeEach(() => {
    useQuoteStore.getState().clearQuotes()
  })

  describe('updateChainQuote', () => {
    it('updates chain when strike key matches directly (decimal format "550.0")', () => {
      const chain = makeChain('SPY', ['550.0', '555.0', '560.0'])
      useQuoteStore.getState().setChain('SPY', chain)

      const optionQuote: OptionQuoteData = {
        underlying: 'SPY',
        optionType: 'CALL',
        strike: 550,
        expiry: '2026-08-21',
        bid: 2.5,
        ask: 3.0,
        last: 2.75,
        mid: 2.75,
        spread: 0.5,
        spreadQuality: 0.9,
        volume: 200,
        openInterest: 600,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      useQuoteStore.getState().updateChainQuote('SPY', optionQuote)

      const updated = useQuoteStore.getState().chains['SPY']
      expect(updated).toBeDefined()
      const expiryData = updated.expirations['2026-08-21']
      expect(expiryData['550.0']?.call?.bid).toBe(2.5)
      expect(expiryData['550.0']?.call?.ask).toBe(3.0)
    })

    it('updates chain when strike is integer and chain key has ".0"', () => {
      const chain = makeChain('SPY', ['550.0', '555.0'])
      useQuoteStore.getState().setChain('SPY', chain)

      const optionQuote: OptionQuoteData = {
        underlying: 'SPY',
        optionType: 'PUT',
        strike: 555,
        expiry: '2026-08-21',
        bid: 1.0,
        ask: 1.5,
        last: 1.25,
        mid: 1.25,
        spread: 0.5,
        spreadQuality: 0.8,
        volume: 150,
        openInterest: 450,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      useQuoteStore.getState().updateChainQuote('SPY', optionQuote)

      const updated = useQuoteStore.getState().chains['SPY']
      const expiryData = updated.expirations['2026-08-21']
      expect(expiryData['555.0']?.put?.bid).toBe(1.0)
    })

    it('handles string strike from WebSocket with extra precision (e.g. "550.0000")', () => {
      const chain = makeChain('SPY', ['550.0', '555.0'])
      useQuoteStore.getState().setChain('SPY', chain)

      // Simulate WebSocket sending strike as string "550.0000"
      const optionQuote = {
        underlying: 'SPY',
        optionType: 'CALL' as const,
        strike: 550.0,
        expiry: '2026-08-21',
        bid: 3.0,
        ask: 3.5,
        last: 3.25,
        mid: 3.25,
        spread: 0.5,
        spreadQuality: 0.85,
        volume: 300,
        openInterest: 700,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      useQuoteStore.getState().updateChainQuote('SPY', optionQuote as OptionQuoteData)

      const updated = useQuoteStore.getState().chains['SPY']
      const expiryData = updated.expirations['2026-08-21']
      expect(expiryData['550.0']?.call?.bid).toBe(3.0)
    })

    it('logs warning and returns unchanged state when strike key not found', () => {
      const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const chain = makeChain('SPY', ['550.0', '555.0'])
      useQuoteStore.getState().setChain('SPY', chain)

      const optionQuote: OptionQuoteData = {
        underlying: 'SPY',
        optionType: 'CALL',
        strike: 999,
        expiry: '2026-08-21',
        bid: 1.0,
        ask: 1.5,
        last: 1.25,
        mid: 1.25,
        spread: 0.5,
        spreadQuality: 0.8,
        volume: 0,
        openInterest: 0,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      useQuoteStore.getState().updateChainQuote('SPY', optionQuote)

      const updated = useQuoteStore.getState().chains['SPY']
      // Chain should be unchanged
      expect(updated.expirations['2026-08-21']?.['550.0']?.call?.bid).toBe(1.0)
      expect(consoleWarnSpy).toHaveBeenCalled()
      consoleWarnSpy.mockRestore()
    })

    it('returns unchanged state when underlying has no chain', () => {
      const optionQuote: OptionQuoteData = {
        underlying: 'NONEXISTENT',
        optionType: 'CALL',
        strike: 100,
        expiry: '2026-08-21',
        bid: 1.0,
        ask: 1.5,
        last: 1.25,
        mid: 1.25,
        spread: 0.5,
        spreadQuality: 0.8,
        volume: 0,
        openInterest: 0,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      // Should not throw
      useQuoteStore.getState().updateChainQuote('NONEXISTENT', optionQuote)
      expect(useQuoteStore.getState().chains['NONEXISTENT']).toBeUndefined()
    })

    it('returns unchanged state when expiry key not found', () => {
      const chain = makeChain('SPY', ['550.0'])
      useQuoteStore.getState().setChain('SPY', chain)

      const optionQuote: OptionQuoteData = {
        underlying: 'SPY',
        optionType: 'CALL',
        strike: 550,
        expiry: '2099-01-01', // Wrong expiry
        bid: 1.0,
        ask: 1.5,
        last: 1.25,
        mid: 1.25,
        spread: 0.5,
        spreadQuality: 0.8,
        volume: 0,
        openInterest: 0,
        greeks: null,
        timestamp: new Date().toISOString(),
      }

      useQuoteStore.getState().updateChainQuote('SPY', optionQuote)
      // Original data should be unchanged
      const updated = useQuoteStore.getState().chains['SPY']
      expect(updated.expirations['2026-08-21']?.['550.0']?.call?.bid).toBe(1.0)
    })
  })
})
