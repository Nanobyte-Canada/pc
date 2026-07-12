import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { WheelCalendarGrid } from './WheelCalendarGrid'
import type { TickerRowData } from '@/types/wheel'

vi.mock('@/services/brokerService', () => ({
  formatCurrency: (val: number | null, currency: string) =>
    val != null ? `${currency} ${val.toFixed(2)}` : '—',
}))

const baseExpiries = [
  { date: '2026-07-17', dte: 7, dayOfWeek: 'Friday', isMonthly: false },
  { date: '2026-07-24', dte: 14, dayOfWeek: 'Friday', isMonthly: false },
]

const emptyTickerRows: TickerRowData[] = [
  {
    symbol: 'SOXL',
    currentPrice: 25.5,
    currency: 'USD',
    totalExposure: 0,
    ccInfo: null,
    cells: {
      '2026-07-17': { positions: [] },
      '2026-07-24': { positions: [] },
    },
  },
]

const tickerRowsWithPosition: TickerRowData[] = [
  {
    symbol: 'SOXL',
    currentPrice: 25.5,
    currency: 'USD',
    totalExposure: 2500,
    ccInfo: null,
    cells: {
      '2026-07-17': {
        positions: [
          {
            id: 1,
            type: 'CSP',
            strike: 25,
            premium: 1.2,
            currentPrice: 25.5,
            pnl: 50,
            otmPercent: -2,
            quantity: 1,
            currency: 'USD',
            accountName: null,
            accountNumber: null,
            connectionId: 1,
          },
        ],
      },
      '2026-07-24': { positions: [] },
    },
  },
]

function renderGrid(
  overrides: Partial<React.ComponentProps<typeof WheelCalendarGrid>> = {},
) {
  const defaults = {
    tickerRows: emptyTickerRows,
    expiries: baseExpiries,
    dateRange: 'Jul 17 — Jul 24, 2026',
    onPrev: vi.fn(),
    onNext: vi.fn(),
    onToday: vi.fn(),
    onPositionClick: vi.fn(),
    onEmptySlotClick: vi.fn(),
    onAddTicker: vi.fn(),
  }
  return render(<WheelCalendarGrid {...defaults} {...overrides} />)
}

describe('WheelCalendarGrid', () => {
  describe('empty cell click', () => {
    it('calls onEmptySlotClick with correct ticker and expiry when empty cell is clicked', () => {
      const onEmptySlotClick = vi.fn()
      renderGrid({ onEmptySlotClick })

      const buttons = screen.getAllByText('+ CSP / CC')
      fireEvent.click(buttons[0])

      expect(onEmptySlotClick).toHaveBeenCalledWith('SOXL', '2026-07-17')
    })

    it('renders a clickable button for each empty cell', () => {
      renderGrid()
      const buttons = screen.getAllByText('+ CSP / CC')
      // Two expiries × one ticker = 2 empty cells
      expect(buttons).toHaveLength(2)
    })
  })

  describe('cell with existing positions', () => {
    it('does not show empty cell button when positions exist', () => {
      renderGrid({ tickerRows: tickerRowsWithPosition })
      const buttons = screen.getAllByText('+ CSP / CC')
      // Only 1 empty cell (2026-07-24 has no positions)
      expect(buttons).toHaveLength(1)
    })
  })

  describe('deprecated onCCSlotClick prop removed', () => {
    it('WheelCalendarGrid does NOT accept onCCSlotClick in its props', () => {
      // The component interface should not have onCCSlotClick
      // Verify it compiles without it and works correctly
      const onEmptySlotClick = vi.fn()
      const { container } = renderGrid({ onEmptySlotClick })

      // Should not contain the old "+ CSP" and "+ CC" separate buttons
      expect(container.querySelector('.wcg-slot-row')).toBeNull()
      expect(screen.queryByText('+ CSP')).toBeNull()
      expect(screen.queryByText('+ CC')).toBeNull()
    })
  })

  describe('onAddTicker', () => {
    it('calls onAddTicker when Add Ticker button is clicked', () => {
      const onAddTicker = vi.fn()
      renderGrid({ onAddTicker })

      fireEvent.click(screen.getByText('+ Add Ticker'))
      expect(onAddTicker).toHaveBeenCalledTimes(1)
    })
  })

  describe('navigation', () => {
    it('calls onPrev when previous button clicked', () => {
      const onPrev = vi.fn()
      renderGrid({ onPrev })

      fireEvent.click(screen.getByLabelText('Previous weeks'))
      expect(onPrev).toHaveBeenCalledTimes(1)
    })

    it('calls onNext when next button clicked', () => {
      const onNext = vi.fn()
      renderGrid({ onNext })

      fireEvent.click(screen.getByLabelText('Next weeks'))
      expect(onNext).toHaveBeenCalledTimes(1)
    })

    it('calls onToday when Today button clicked', () => {
      const onToday = vi.fn()
      renderGrid({ onToday })

      fireEvent.click(screen.getByText('Today'))
      expect(onToday).toHaveBeenCalledTimes(1)
    })
  })

  describe('rendering', () => {
    it('renders ticker symbols', () => {
      renderGrid()
      expect(screen.getByText('SOXL')).toBeInTheDocument()
    })

    it('renders expiry column headers', () => {
      renderGrid()
      // Use container to find expiry-date elements specifically
      const expiryDates = document.querySelectorAll('.wcg-expiry-date')
      expect(expiryDates.length).toBeGreaterThanOrEqual(2)
      expect(expiryDates[0]).toHaveTextContent('Jul 17')
      expect(expiryDates[1]).toHaveTextContent('Jul 24')
    })

    it('renders DTE values', () => {
      renderGrid()
      expect(screen.getByText('7d')).toBeInTheDocument()
      expect(screen.getByText('14d')).toBeInTheDocument()
    })
  })
})
