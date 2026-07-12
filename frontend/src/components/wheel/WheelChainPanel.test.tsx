import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, fireEvent, waitFor, within } from '@testing-library/react'
import { WheelChainPanel } from './WheelChainPanel'
import type { ChainPanelContext } from '@/types/wheel'
import type { SearchResult } from '@/types/screener'

// ── Mocks ────────────────────────────────────────────────────────────────────

const mockSearchInstruments = vi.fn()
vi.mock('@/services/screenerService', () => ({
  searchInstruments: (...args: unknown[]) => mockSearchInstruments(...args),
}))

const mockGetQuote = vi.fn()
const mockGetOptionExpirations = vi.fn()
const mockGetOptionsChainForExpiry = vi.fn()
vi.mock('@/services/marketDataService', () => ({
  getQuote: (...args: unknown[]) => mockGetQuote(...args),
  getOptionExpirations: (...args: unknown[]) => mockGetOptionExpirations(...args),
  getOptionsChainForExpiry: (...args: unknown[]) => mockGetOptionsChainForExpiry(...args),
}))

vi.mock('@/services/brokerService', () => ({
  formatCurrency: (val: number | null, _currency: string) =>
    val != null ? `$${val.toFixed(2)}` : '—',
}))

const mockUseMarketDataWebSocket = {
  subscribe: vi.fn(),
  unsubscribe: vi.fn(),
  subscribeChainExpiry: vi.fn(),
  unsubscribeChain: vi.fn(),
  switchChainExpiry: vi.fn(),
}
vi.mock('@/hooks/useMarketDataWebSocket', () => ({
  useMarketDataWebSocket: () => mockUseMarketDataWebSocket,
}))

vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: Object.assign(
    vi.fn((selector: (s: Record<string, unknown>) => unknown) =>
      selector({
        quotes: {} as Record<string, unknown>,
        chains: {} as Record<string, unknown>,
        ibkrConnected: null,
        setChain: vi.fn(),
        setQuote: vi.fn(),
      }),
    ),
    { getState: vi.fn(() => ({ chains: {}, quotes: {}, ibkrConnected: null, setChain: vi.fn() })) },
  ),
}))

vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({
    error: vi.fn(),
    success: vi.fn(),
  }),
}))

// ── Helpers ──────────────────────────────────────────────────────────────────

function makeContext(overrides: Partial<ChainPanelContext> = {}): ChainPanelContext {
  return {
    ticker: 'SOXL',
    expiryDate: '2026-07-17',
    optionSide: 'put',
    searchMode: false,
    ...overrides,
  }
}

function makeSearchResult(overrides: Partial<SearchResult> = {}): SearchResult {
  return {
    id: 1,
    type: 'STOCK',
    ticker: 'AAPL',
    name: 'Apple Inc.',
    exchange: 'NASDAQ',
    matchType: 'ticker',
    ...overrides,
  }
}

const searchResponse = {
  data: [makeSearchResult()],
  meta: { query: 'AAPL', resultCount: 1, searchTimeMs: 50 },
}

/** Get the desktop container — the mobile sheet is a second copy we skip. */
function getDesktop() {
  return document.querySelector('.wcp2--desktop') as HTMLElement
}

/** Type into search and wait for debounce + results to appear. */
async function typeAndSearch(desktop: HTMLElement, query: string) {
  const input = within(desktop).getByPlaceholderText('Search by ticker or name...')
  fireEvent.change(input, { target: { value: query } })
  // Wait for debounce (250ms) + async search to resolve
  await waitFor(() => {
    expect(mockSearchInstruments).toHaveBeenCalled()
  })
}

function renderPanel(contextOverrides: Partial<ChainPanelContext> = {}, props: Record<string, unknown> = {}) {
  const context = makeContext(contextOverrides)
  return render(
    <WheelChainPanel
      context={context}
      spotPrice={0}
      onClose={vi.fn()}
      onStrikeSelect={vi.fn()}
      {...props}
    />,
  )
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('WheelChainPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetOptionExpirations.mockResolvedValue({ expirations: ['2026-07-17', '2026-07-24'] })
    mockGetOptionsChainForExpiry.mockResolvedValue({
      underlying: 'SOXL',
      spotPrice: 25,
      expirations: {},
    })
    mockGetQuote.mockResolvedValue({
      symbol: 'AAPL',
      bid: 190.5,
      ask: 191.0,
      last: 190.8,
      mid: 190.75,
      spread: 0.5,
      volume: 50000,
      timestamp: new Date().toISOString(),
    })
    mockSearchInstruments.mockResolvedValue(searchResponse)
  })

  describe('search-first mode (no ticker pre-selected)', () => {
    it('renders search bar when no ticker is selected', () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      expect(within(desktop).getByPlaceholderText('Search by ticker or name...')).toBeInTheDocument()
    })

    it('shows "Select Ticker" header when no ticker is selected', () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      expect(within(desktop).getByText('Select Ticker')).toBeInTheDocument()
    })

    it('does not render expiry selector or chain when in search mode', () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      expect(within(desktop).queryByText('Expiry')).not.toBeInTheDocument()
      expect(within(desktop).queryByText('Loading chain...')).not.toBeInTheDocument()
    })

    it('calls searchInstruments when user types in search bar', async () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')
      expect(mockSearchInstruments).toHaveBeenCalledWith('AAPL', undefined, 8)
    })

    it('shows search results with ticker and name', async () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')
      expect(within(desktop).getByText('AAPL')).toBeInTheDocument()
      expect(within(desktop).getByText('Apple Inc.')).toBeInTheDocument()
    })

    it('fetches and displays quote for search results', async () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')
      expect(mockGetQuote).toHaveBeenCalledWith('AAPL')
      expect(within(desktop).getByText('$190.80')).toBeInTheDocument()
      expect(within(desktop).getByText(/BID.*190\.50/)).toBeInTheDocument()
      expect(within(desktop).getByText(/ASK.*191\.00/)).toBeInTheDocument()
    })

    it('shows CSP and CC buttons after selecting a search result', async () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')

      // Click on search result
      fireEvent.click(within(desktop).getByText('Apple Inc.'))

      // Should show CSP and CC option-type selection
      expect(within(desktop).getByText('Cash-Secured Put')).toBeInTheDocument()
      expect(within(desktop).getByText('Covered Call')).toBeInTheDocument()
    })

    it('shows empty state when no results found', async () => {
      mockSearchInstruments.mockResolvedValue({ data: [], meta: { query: 'ZZZZZ', resultCount: 0, searchTimeMs: 10 } })
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'ZZZZZ')
      expect(within(desktop).getByText('No results found')).toBeInTheDocument()
    })
  })

  describe('CSP/CC button selection in search mode', () => {
    it('calls onTickerSelect with put option side when CSP button is clicked', async () => {
      const onTickerSelect = vi.fn()
      renderPanel({ ticker: '', searchMode: true }, { onTickerSelect })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')

      // Select search result then click CSP
      fireEvent.click(within(desktop).getByText('Apple Inc.'))
      const cspButton = within(desktop).getByText('Cash-Secured Put').closest('button')!
      fireEvent.click(cspButton)

      expect(onTickerSelect).toHaveBeenCalledWith('AAPL', 'put')
    })

    it('calls onTickerSelect with call option side when CC button is clicked', async () => {
      const onTickerSelect = vi.fn()
      renderPanel({ ticker: '', searchMode: true }, { onTickerSelect })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')

      // Select search result then click CC
      fireEvent.click(within(desktop).getByText('Apple Inc.'))
      const ccButton = within(desktop).getByText('Covered Call').closest('button')!
      fireEvent.click(ccButton)

      expect(onTickerSelect).toHaveBeenCalledWith('AAPL', 'call')
    })

    it('allows going back from CSP/CC selection', async () => {
      renderPanel({ ticker: '', searchMode: true })
      const desktop = getDesktop()
      await typeAndSearch(desktop, 'AAPL')

      // Select search result
      fireEvent.click(within(desktop).getByText('Apple Inc.'))
      expect(within(desktop).getByText('Back')).toBeInTheDocument()

      // Click back
      fireEvent.click(within(desktop).getByText('Back'))

      // Should show search results again
      expect(within(desktop).getByText('Apple Inc.')).toBeInTheDocument()
    })
  })

  describe('chain mode (ticker IS pre-selected)', () => {
    it('renders ticker in header when ticker is selected', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('SOXL')).toBeInTheDocument()
    })

    it('renders CSP label for put option side', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('CSP')).toBeInTheDocument()
    })

    it('renders CC label for call option side', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'call' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('CC')).toBeInTheDocument()
    })

    it('initializes chain data on mount', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      expect(mockGetOptionExpirations).toHaveBeenCalledWith('SOXL')
      expect(mockUseMarketDataWebSocket.subscribe).toHaveBeenCalledWith('SOXL')
    })

    it('renders expiry selector when ticker is selected', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('Expiry')).toBeInTheDocument()
    })

    it('renders strike table columns', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('Strike')).toBeInTheDocument()
      expect(within(desktop).getByText('Bid')).toBeInTheDocument()
      expect(within(desktop).getByText('Ask')).toBeInTheDocument()
    })

    it('always renders search bar (even in chain mode)', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByPlaceholderText('Search by ticker or name...')).toBeInTheDocument()
    })

    it('renders footer text', () => {
      renderPanel({ ticker: 'SOXL', optionSide: 'put' })
      const desktop = getDesktop()
      expect(within(desktop).getByText('Tap a strike to place order')).toBeInTheDocument()
    })
  })

  describe('close button', () => {
    it('calls onClose when close button is clicked', () => {
      const onClose = vi.fn()
      renderPanel({}, { onClose })
      const desktop = getDesktop()

      fireEvent.click(within(desktop).getByLabelText('Close'))
      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })
})
