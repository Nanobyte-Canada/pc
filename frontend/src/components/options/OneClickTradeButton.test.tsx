import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { OneClickTradeButton } from './OneClickTradeButton'

const mockConnection = { connected: true, connectionId: 1, accountNumber: '123' }
vi.mock('@/hooks/useBrokerConnection', () => ({
  useBrokerConnection: () => ({ connection: mockConnection, loading: false }),
}))
vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: (selector: (s: unknown) => unknown) =>
    selector({ selectedUnderlying: 'SPY', quotes: { SPY: { last: 100 } } }),
}))
vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }),
}))
vi.mock('@/stores/strategyStore', () => ({
  // Component destructures without a selector, but keep selector support for safety
  useStrategyStore: (selector?: (s: unknown) => unknown) => {
    const state = {
      legs: [{ action: 'BUY', optionType: 'CALL', strike: 100, expiry: '2026-12-18', quantity: 1, price: 2.5, symbol: 'SPY' }],
      selectedStrategy: 'BULL_CALL_SPREAD',
      tradeInProgress: false,
      setTradeInProgress: vi.fn(),
      connectionStatus: { connected: true, brokerType: 'questrade' },
    }
    return selector ? selector(state) : state
  },
}))

describe('OneClickTradeButton', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
  })

  it('shows the limit price in the confirmation', () => {
    render(<OneClickTradeButton />)
    fireEvent.click(screen.getByRole('button', { name: /trade on questrade/i }))
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('$'))
  })
})
