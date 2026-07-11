import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WheelChainPanel } from './WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'
import * as marketDataService from '@/services/marketDataService'

// Mock services and stores
vi.mock('@/services/marketDataService', () => ({
  getOptionExpirations: vi.fn(),
  getOptionsChainForExpiry: vi.fn(),
}))

vi.mock('@/services/brokerService', () => ({
  formatCurrency: vi.fn((value: number) => `$${value.toFixed(2)}`),
}))

vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({ error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() }),
}))

vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: vi.fn((selector?: (state: unknown) => unknown) => {
    // Return a minimal mock store
    const state = {
      quotes: {},
      chains: {},
      ibkrConnected: true,
      setQuote: vi.fn(),
      setChain: vi.fn(),
      updateChainQuote: vi.fn(),
      setSelectedUnderlying: vi.fn(),
      setIbkrConnected: vi.fn(),
      clearQuotes: vi.fn(),
    }
    return selector ? selector(state) : state
  }),
}))

const mockSubscribe = vi.fn()
const mockUnsubscribe = vi.fn()
const mockSubscribeChainExpiry = vi.fn()
const mockUnsubscribeChain = vi.fn()
const mockSwitchChainExpiry = vi.fn()

vi.mock('@/hooks/useMarketDataWebSocket', () => ({
  useMarketDataWebSocket: () => ({
    subscribe: mockSubscribe,
    unsubscribe: mockUnsubscribe,
    subscribeChainExpiry: mockSubscribeChainExpiry,
    unsubscribeChain: mockUnsubscribeChain,
    switchChainExpiry: mockSwitchChainExpiry,
    isConnected: true,
  }),
}))

vi.mock('./WheelChainRow', () => ({
  WheelChainRow: () => <tr data-testid="chain-row" />,
}))

describe('WheelChainPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const defaultProps = {
    spotPrice: 150,
    onClose: vi.fn(),
    onStrikeSelect: vi.fn(),
  }

  describe('Empty state (search mode with no ticker)', () => {
    const searchContext: ChainPanelContext = {
      ticker: '',
      expiryDate: '2026-08-21',
      optionSide: 'put',
      searchMode: true,
    }

    it('renders empty state placeholder when searchMode is true and ticker is empty', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      const emptyStates = document.querySelectorAll('.wcp2-empty')
      expect(emptyStates.length).toBeGreaterThan(0)
    })

    it('renders search icon in empty state', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      // The Search icon from lucide-react renders an SVG
      const emptyState = document.querySelector('.wcp2-empty')
      expect(emptyState).toBeInTheDocument()
      const icon = emptyState?.querySelector('.wcp2-empty__icon')
      expect(icon).toBeInTheDocument()
    })

    it('renders guidance text in empty state', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      // Component renders both desktop and sheet views, so text appears twice
      const guidanceTexts = screen.getAllByText('Select a ticker to view options chain')
      expect(guidanceTexts.length).toBeGreaterThan(0)
    })

    it('shows "Search" as header label when no ticker', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      // Component renders both desktop and sheet views, so text appears twice
      const searchLabels = screen.getAllByText('Search')
      expect(searchLabels.length).toBeGreaterThan(0)
    })

    it('does not fire API calls or WebSocket subscriptions with empty ticker', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      // The useEffect should early-return when ticker is empty
      expect(marketDataService.getOptionExpirations).not.toHaveBeenCalled()
      expect(mockSubscribe).not.toHaveBeenCalled()
      expect(mockSubscribeChainExpiry).not.toHaveBeenCalled()
    })

    it('does not render expiry selector, strikes table, or footer', () => {
      render(<WheelChainPanel context={searchContext} {...defaultProps} />)

      expect(screen.queryByText('Expiry')).not.toBeInTheDocument()
      expect(screen.queryByText('Strikes')).not.toBeInTheDocument()
      expect(screen.queryByText(/Tap a strike/)).not.toBeInTheDocument()
    })
  })

  describe('Normal mode (with ticker)', () => {
    const normalContext: ChainPanelContext = {
      ticker: 'AAPL',
      expiryDate: '2026-08-21',
      optionSide: 'put',
    }

    it('does not render empty state when ticker is provided', () => {
      render(<WheelChainPanel context={normalContext} {...defaultProps} />)

      const emptyState = document.querySelector('.wcp2-empty')
      expect(emptyState).not.toBeInTheDocument()
    })

    it('renders ticker symbol in header', () => {
      render(<WheelChainPanel context={normalContext} {...defaultProps} />)

      // Component renders both desktop and sheet views, so text appears twice
      const tickerLabels = screen.getAllByText('AAPL')
      expect(tickerLabels.length).toBeGreaterThan(0)
    })
  })
})
