import { describe, it, expect } from 'vitest'
import { derivePriceFor } from './liveMids'
import type { OptionsChain } from '@/types/options'

const chain = {
  SPY: {
    underlying: 'SPY',
    spotPrice: 100,
    expirations: {
      '2026-12-18': {
        '100': {
          call: { bid: 2.4, ask: 2.6, last: 2.5, mid: 2.5 },
          put: null,
        },
      },
    },
  },
} as unknown as Record<string, OptionsChain>

const leg = {
  action: 'BUY' as const,
  optionType: 'CALL' as const,
  strike: 100,
  expiry: '2026-12-18',
  quantity: 1,
  symbol: 'SPY',
}

describe('derivePriceFor', () => {
  it('returns the stored mid for a matching leg', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor(leg)).toBe(2.5)
  })

  it('falls back to the bid/ask midpoint when mid is missing', () => {
    const noMid = {
      SPY: {
        ...chain.SPY,
        expirations: {
          '2026-12-18': {
            '100': { call: { bid: 2.4, ask: 2.6, last: 2.5 }, put: null },
          },
        },
      },
    } as unknown as Record<string, OptionsChain>
    const priceFor = derivePriceFor(noMid, 'SPY')
    expect(priceFor(leg)).toBe(2.5)
  })

  it('returns undefined when the leg has no live quote', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor({ ...leg, strike: 999 })).toBeUndefined()
  })

  it('returns undefined when the leg carries no symbol', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor({ ...leg, symbol: undefined })).toBeUndefined()
  })
})
