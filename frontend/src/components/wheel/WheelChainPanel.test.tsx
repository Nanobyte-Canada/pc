import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WheelChainPanel } from './WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'

// Mock services
vi.mock('@/services/marketDataService', () => ({
  getOptionExpirations: vi.fn().mockResolvedValue({ expirations: [] }),
  getOptionsChainForExpiry: vi.fn().mockResolvedValue({ expirations: {} }),
}))

vi.mock('@/services/brokerService', () => ({
  formatCurrency: vi.fn((val: number) => `$${val.toFixed(2)}`),
}))

// Mock toast store
vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  }),
}))

// Mock quote store
vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
    if (!selector) return { chains: {}, quotes: {}, setChain: vi.fn(), ibkrConnected: true }
    return selector({ chains: {}, quotes: {}, setChain: vi.fn(), ibkrConnected: true })
  }),
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

describe('WheelChainPanel', () => {
  const defaultProps = {
    spotPrice: 150,
    onClose: vi.fn(),
    onStrikeSelect: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state in search mode', () => {
    it('renders placeholder when searchMode is true and ticker is empty', () => {
      const context: ChainPanelContext = {
        ticker: '',
        expiryDate: '2025-08-15',
        optionSide: 'put',
      }

      render(<WheelChainPanel {...defaultProps} context={context} searchMode />)

      const placeholders = screen.getAllByText('Select a ticker to view options chain')
      expect(placeholders.length).toBeGreaterThanOrEqual(1)
    })

    it('does not show error banner when searchMode is true and ticker is empty', () => {
      const context: ChainPanelContext = {
        ticker: '',
        expiryDate: '2025-08-15',
        optionSide: 'put',
      }

      render(<WheelChainPanel {...defaultProps} context={context} searchMode />)

      // No error banners should be present
      expect(screen.queryByText(/Failed to load/)).not.toBeInTheDocument()
      expect(screen.queryByText(/IBKR Gateway/)).not.toBeInTheDocument()
    })

    it('does not render placeholder when searchMode is false', () => {
      const context: ChainPanelContext = {
        ticker: 'AAPL',
        expiryDate: '2025-08-15',
        optionSide: 'put',
      }

      render(<WheelChainPanel {...defaultProps} context={context} searchMode={false} />)

      expect(screen.queryByText('Select a ticker to view options chain')).not.toBeInTheDocument()
    })

    it('does not render placeholder when searchMode is true but ticker is selected', () => {
      const context: ChainPanelContext = {
        ticker: 'AAPL',
        expiryDate: '2025-08-15',
        optionSide: 'put',
      }

      render(<WheelChainPanel {...defaultProps} context={context} searchMode />)

      expect(screen.queryByText('Select a ticker to view options chain')).not.toBeInTheDocument()
    })
  })
})
