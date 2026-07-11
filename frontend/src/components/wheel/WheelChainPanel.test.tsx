import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WheelChainPanel } from './WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'

// Mock dependencies
vi.mock('@/services/marketDataService', () => ({
  getOptionExpirations: vi.fn(() => Promise.resolve({ expirations: ['2026-08-15', '2026-09-15'] })),
  getOptionsChainForExpiry: vi.fn(() => Promise.resolve({ expirations: {} })),
}))

vi.mock('@/hooks/useMarketDataWebSocket', () => ({
  useMarketDataWebSocket: () => ({
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    subscribeChainExpiry: vi.fn(),
    unsubscribeChain: vi.fn(),
    switchChainExpiry: vi.fn(),
  }),
}))

vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: vi.fn((selector) => {
    const state = {
      chains: {},
      quotes: {},
      ibkrConnected: true,
      setChain: vi.fn(),
    }
    return selector(state)
  }),
}))

vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  }),
}))

vi.mock('./WheelChainRow', () => ({
  WheelChainRow: ({ strike, onClick }: { strike: { strike: number }, onClick: () => void }) => (
    <tr data-testid={`strike-${strike.strike}`} onClick={onClick}>
      <td>{strike.strike}</td>
    </tr>
  ),
}))

function renderPanel(context: Partial<ChainPanelContext> = {}) {
  const defaultContext: ChainPanelContext = {
    ticker: '',
    expiryDate: '2026-08-15',
    optionSide: 'put',
    searchMode: false,
    ...context,
  }

  return render(
    <WheelChainPanel
      context={defaultContext}
      spotPrice={150}
      onClose={vi.fn()}
      onStrikeSelect={vi.fn()}
    />
  )
}

describe('WheelChainPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('searchMode behavior', () => {
    it('hides expiry selector when searchMode is true and ticker is empty', () => {
      renderPanel({ searchMode: true, ticker: '' })
      
      // Expiry label should not be present
      expect(screen.queryByText('Expiry')).not.toBeInTheDocument()
    })

    it('hides strike columns header when searchMode is true and ticker is empty', () => {
      renderPanel({ searchMode: true, ticker: '' })
      
      // Column headers should not be present
      expect(screen.queryByText('Strike')).not.toBeInTheDocument()
      expect(screen.queryByText('Bid')).not.toBeInTheDocument()
      expect(screen.queryByText('Ask')).not.toBeInTheDocument()
    })

    it('hides strike table when searchMode is true and ticker is empty', () => {
      renderPanel({ searchMode: true, ticker: '' })
      
      // Loading or table content should not be present
      expect(screen.queryByText('Loading chain...')).not.toBeInTheDocument()
      expect(screen.queryByText('No data for this expiry')).not.toBeInTheDocument()
    })

    it('shows footer message to search for ticker when searchMode is true and ticker is empty', () => {
      renderPanel({ searchMode: true, ticker: '' })
      
      expect(screen.getAllByText('Search for a ticker to get started').length).toBeGreaterThan(0)
    })

    it('shows footer message to tap strike when searchMode is false', () => {
      renderPanel({ searchMode: false, ticker: 'AAPL' })
      
      expect(screen.getAllByText('Tap a strike to place order').length).toBeGreaterThan(0)
    })

    it('shows expiry selector when searchMode is true but ticker is selected', () => {
      renderPanel({ searchMode: true, ticker: 'AAPL' })
      
      // Expiry label should be present when ticker is selected
      expect(screen.getAllByText('Expiry').length).toBeGreaterThan(0)
    })

    it('shows strike columns when searchMode is true but ticker is selected', () => {
      renderPanel({ searchMode: true, ticker: 'AAPL' })
      
      // Column headers should be present when ticker is selected
      expect(screen.getAllByText('Strike').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Bid').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Ask').length).toBeGreaterThan(0)
    })

    it('shows footer message to tap strike when searchMode is true and ticker is selected', () => {
      renderPanel({ searchMode: true, ticker: 'AAPL' })
      
      expect(screen.getAllByText('Tap a strike to place order').length).toBeGreaterThan(0)
    })
  })

  describe('normal mode behavior', () => {
    it('shows expiry selector in normal mode', () => {
      renderPanel({ searchMode: false, ticker: 'AAPL' })
      
      expect(screen.getAllByText('Expiry').length).toBeGreaterThan(0)
    })

    it('shows strike columns in normal mode', () => {
      renderPanel({ searchMode: false, ticker: 'AAPL' })
      
      expect(screen.getAllByText('Strike').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Bid').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Ask').length).toBeGreaterThan(0)
    })

    it('shows footer message to tap strike in normal mode', () => {
      renderPanel({ searchMode: false, ticker: 'AAPL' })
      
      expect(screen.getAllByText('Tap a strike to place order').length).toBeGreaterThan(0)
    })
  })
})
