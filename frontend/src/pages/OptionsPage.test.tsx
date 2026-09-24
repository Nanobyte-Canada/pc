import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ApiError } from '@/services/api'
import { calculateStrategy } from '@/services/optionsStrategyService'
import { useQuoteStore } from '@/stores/quoteStore'
import { useStrategyStore } from '@/stores/strategyStore'
import { OptionsPage } from './OptionsPage'
import type { Leg, OptionsChain, Quote } from '@/types/options'

vi.mock('@/services/optionsStrategyService', () => ({
  getStrategies: vi.fn().mockResolvedValue([]),
  getStrategyInfo: vi.fn(),
  calculateStrategy: vi.fn(),
}))

vi.mock('@/hooks/useMarketDataWebSocket', () => ({
  useMarketDataWebSocket: () => ({
    isConnected: false,
    connect: vi.fn(),
    disconnect: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    subscribeChain: vi.fn(),
    unsubscribeChain: vi.fn(),
    subscribeChainExpiry: vi.fn(),
    switchChainExpiry: vi.fn(),
    subscribeOption: vi.fn(),
    unsubscribeOption: vi.fn(),
  }),
}))

vi.mock('@/hooks/useBrokerConnection', () => ({
  useBrokerConnection: () => ({ connection: null, loading: false }),
}))

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
})

const quote: Quote = {
  symbol: 'SPY',
  bid: 599.9,
  ask: 600.1,
  last: 600,
  mid: 600,
  spread: 0.2,
  volume: 1000000,
  timestamp: '2026-09-23T14:00:00Z',
}

const chain: OptionsChain = {
  underlying: 'SPY',
  spotPrice: 600,
  expirations: {},
}

// Too few legs for an Iron Condor — the backend rejects this with
// "Invalid legs: Iron Condor requires exactly 4 legs" (Task 9).
const legs: Leg[] = [
  { action: 'BUY', optionType: 'CALL', strike: 595, expiry: '2026-09-25', quantity: 1, price: 6.2, symbol: 'SPY' },
  { action: 'SELL', optionType: 'CALL', strike: 605, expiry: '2026-09-25', quantity: 1, price: 3.1, symbol: 'SPY' },
]

describe('OptionsPage', () => {
  beforeEach(() => {
    useQuoteStore.setState({
      selectedUnderlying: 'SPY',
      quotes: { SPY: quote },
      chains: { SPY: chain },
    })
    useStrategyStore.setState({
      strategies: [],
      selectedStrategy: 'IRON_CONDOR',
      selectedStrategyEducation: null,
      legs,
      calculationResult: null,
      isCalculating: false,
    })
    vi.mocked(calculateStrategy).mockRejectedValue(
      new ApiError(400, 'VALIDATION_ERROR', 'Invalid legs: Iron Condor requires exactly 4 legs')
    )
  })

  it('surfaces the calculate error message inline when the API rejects', async () => {
    render(
      <MemoryRouter>
        <OptionsPage />
      </MemoryRouter>
    )

    const calcButtons = screen.getAllByRole('button', { name: 'Calculate P&L' })
    fireEvent.click(calcButtons[0])

    expect(await screen.findByText(/requires exactly 4 legs/i)).toBeInTheDocument()
  })
})
