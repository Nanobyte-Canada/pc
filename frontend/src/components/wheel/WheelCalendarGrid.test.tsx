import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { WheelCalendarGrid } from './WheelCalendarGrid'
import type { WheelPosition, CCInfo } from '@/types/wheel'
import { scenario } from '../../test/scenario'

vi.mock('./PositionCard', () => ({
  PositionCard: ({ position, onClick }: { position: WheelPosition; onClick: (p: WheelPosition) => void }) => (
    <div
      data-testid={`position-card-${position.id}`}
      data-type={position.type}
      onClick={() => onClick(position)}
    >
      ${position.strike} {position.type}
    </div>
  ),
}))

vi.mock('lucide-react', () => ({
  ChevronLeft: () => <span data-testid="chevron-left" />,
  ChevronRight: () => <span data-testid="chevron-right" />,
}))

const mockExpiries = [
  { date: '2026-09-19', dte: 3, dayOfWeek: 'Friday', isMonthly: true },
  { date: '2026-09-26', dte: 10, dayOfWeek: 'Friday', isMonthly: false },
  { date: '2026-10-03', dte: 17, dayOfWeek: 'Friday', isMonthly: false },
]

const mockPosition: WheelPosition = {
  id: 1,
  type: 'CSP',
  strike: 190,
  premium: 2.50,
  currentPrice: 195,
  pnl: 100,
  otmPercent: 2.6,
  quantity: 1,
  currency: 'USD',
  accountName: 'Test',
  accountNumber: '1234',
  connectionId: 1,
}

const mockTickerRows = [
  {
    symbol: 'AAPL',
    currentPrice: 195,
    currency: 'USD',
    totalExposure: 19000,
    ccInfo: { sharesOwned: 100, contractsAvailable: 1 } as CCInfo,
    cells: {
      '2026-09-19': { positions: [mockPosition] },
      '2026-09-26': { positions: [] },
      '2026-10-03': { positions: [] },
    },
  },
]

describe('WheelCalendarGrid', () => {
  it(scenario('WHEEL-GRID-001', 'renders timeline navigation buttons'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByTestId('chevron-left')).toBeInTheDocument()
    expect(screen.getByTestId('chevron-right')).toBeInTheDocument()
    expect(screen.getByText('Today')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-002', 'renders date range text'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('Sep 19 — Oct 3, 2026')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-003', 'renders ticker symbols'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('AAPL')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-004', 'renders shares owned from ccInfo'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('100 shares')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-005', 'renders expiry column headers with DTE'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('3d')).toBeInTheDocument()
    expect(screen.getByText('10d')).toBeInTheDocument()
    expect(screen.getByText('17d')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-006', 'renders Monthly badge for monthly expiries'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('Monthly')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-007', 'renders PositionCard for occupied cells'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByTestId('position-card-1')).toBeInTheDocument()
  })

  it(scenario('WHEEL-GRID-008', 'renders + Add hint for empty cells'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    const addHints = screen.getAllByText('+ Add')
    expect(addHints.length).toBeGreaterThanOrEqual(1)
  })

  it(scenario('WHEEL-GRID-009', 'calls onPrev when previous button clicked'), () => {
    const mockOnPrev = vi.fn()
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={mockOnPrev}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('chevron-left').closest('button')!)
    expect(mockOnPrev).toHaveBeenCalled()
  })

  it(scenario('WHEEL-GRID-010', 'calls onNext when next button clicked'), () => {
    const mockOnNext = vi.fn()
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={mockOnNext}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('chevron-right').closest('button')!)
    expect(mockOnNext).toHaveBeenCalled()
  })

  it(scenario('WHEEL-GRID-011', 'calls onToday when Today button clicked'), () => {
    const mockOnToday = vi.fn()
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={mockOnToday}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    fireEvent.click(screen.getByText('Today'))
    expect(mockOnToday).toHaveBeenCalled()
  })

  it(scenario('WHEEL-GRID-012', 'calls onEmptySlotClick when empty cell clicked'), () => {
    const mockOnEmpty = vi.fn()
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={mockOnEmpty}
      />
    )
    const addHint = screen.getAllByText('+ Add')[0]
    const cell = addHint.closest('td')!
    fireEvent.click(cell)
    expect(mockOnEmpty).toHaveBeenCalledWith('AAPL', '2026-09-26')
  })

  it(scenario('WHEEL-GRID-013', 'renders legend with CSP and CC labels'), () => {
    render(
      <WheelCalendarGrid
        tickerRows={mockTickerRows}
        expiries={mockExpiries}
        dateRange="Sep 19 — Oct 3, 2026"
        onPrev={vi.fn()}
        onNext={vi.fn()}
        onToday={vi.fn()}
        onPositionClick={vi.fn()}
        onEmptySlotClick={vi.fn()}
      />
    )
    expect(screen.getByText('CSP')).toBeInTheDocument()
    expect(screen.getByText('CC')).toBeInTheDocument()
  })
})
