import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WheelChainPanel } from './WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'

// Mock market data service
vi.mock('@/services/marketDataService', () => ({
  getOptionExpirations: vi.fn().mockResolvedValue({ expirations: ['2026-08-21'] }),
  getOptionsChainForExpiry: vi.fn().mockResolvedValue({
    underlying: 'AAPL',
    spotPrice: 150,
    expirations: {},
  }),
}))

// Mock broker service
vi.mock('@/services/brokerService', () => ({
  formatCurrency: vi.fn((value: number | null, _currency: string) =>
    value != null ? `$${value.toFixed(2)}` : '$0.00'
  ),
}))

// Mock zustand quote store
vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: vi.fn((selector: (state: Record<string, unknown>) => unknown) =>
    selector({
      quotes: {},
      chains: {},
      ibkrConnected: true,
      setChain: vi.fn(),
    })
  ),
}))

// Mock WebSocket hook
vi.mock('@/hooks/useMarketDataWebSocket', () => ({
  useMarketDataWebSocket: () => ({
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    subscribeChainExpiry: vi.fn(),
    unsubscribeChain: vi.fn(),
    switchChainExpiry: vi.fn(),
  }),
}))

// Mock toast store
vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  }),
}))

describe('WheelChainPanel — empty state in searchMode', () => {
  const defaultProps = {
    spotPrice: 0,
    onClose: vi.fn(),
    onStrikeSelect: vi.fn(),
  }

  const searchModeContext: ChainPanelContext = {
    ticker: '',
    expiryDate: '2026-08-21',
    optionSide: 'put',
    searchMode: true,
  }

  const normalContext: ChainPanelContext = {
    ticker: 'AAPL',
    expiryDate: '2026-08-21',
    optionSide: 'put',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders empty state placeholder with search icon and guidance text in searchMode with no ticker', () => {
    render(<WheelChainPanel context={searchModeContext} {...defaultProps} />)

    // Empty state placeholder text should be visible (rendered in both desktop and mobile views)
    const elements = screen.getAllByText('Select a ticker from the watchlist')
    expect(elements.length).toBeGreaterThanOrEqual(1)
  })

  it('hides the expiry selector in searchMode with no ticker', () => {
    render(<WheelChainPanel context={searchModeContext} {...defaultProps} />)

    // Expiry label should not be present
    expect(screen.queryByText('Expiry')).not.toBeInTheDocument()
  })

  it('hides the strike columns header in searchMode with no ticker', () => {
    render(<WheelChainPanel context={searchModeContext} {...defaultProps} />)

    // Strike column header should not be present
    expect(screen.queryByText('Strike')).not.toBeInTheDocument()
  })

  it('hides the quote section in searchMode with no ticker', () => {
    render(<WheelChainPanel context={searchModeContext} {...defaultProps} />)

    // Live indicator should not be present
    expect(screen.queryByText('Live')).not.toBeInTheDocument()
  })

  it('shows search-oriented footer text in searchMode', () => {
    render(<WheelChainPanel context={searchModeContext} {...defaultProps} />)

    const elements = screen.getAllByText('Search for a ticker to get started')
    expect(elements.length).toBeGreaterThanOrEqual(1)
  })

  it('shows order-oriented footer text in normal mode', () => {
    render(<WheelChainPanel context={normalContext} {...defaultProps} />)

    const elements = screen.getAllByText('Tap a strike to place order')
    expect(elements.length).toBeGreaterThanOrEqual(1)
  })

  it('does not show empty state placeholder in normal mode with a ticker', () => {
    render(<WheelChainPanel context={normalContext} {...defaultProps} />)

    expect(screen.queryByText('Select a ticker from the watchlist')).not.toBeInTheDocument()
  })
})
