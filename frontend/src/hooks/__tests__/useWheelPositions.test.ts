// frontend/src/hooks/__tests__/useWheelPositions.test.ts
import { describe, it, expect } from 'vitest'
import { buildWheelGrid, computeCapitalMetrics, computeTickerTotals } from '../useWheelPositions'
import type { BrokerPosition } from '@/types/broker'

function makePosition(overrides: Partial<BrokerPosition> = {}): BrokerPosition {
  return {
    id: 1,
    symbol: 'SOXL 250530P00020000',
    securityName: null,
    instrumentType: 'OPTION',
    quantity: -1,
    averageCost: 0.85,
    currentPrice: 0.23,
    currentValue: -23,
    totalPnl: 62,
    totalPnlPercent: 72.9,
    currency: 'USD',
    strikePrice: 20,
    expirationDate: '2026-05-30',
    optionType: 'PUT',
    underlyingSymbol: 'SOXL',
    ...overrides,
  }
}

describe('buildWheelGrid', () => {
  const tickers = ['SOXL', 'TECL']
  const today = new Date('2026-05-23')

  it('places a CSP position in the correct cell', () => {
    const positions = [makePosition()]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row).toBeDefined()
    expect(row!.cells['SOXL'].positions).toHaveLength(1)
    expect(row!.cells['SOXL'].positions[0].type).toBe('CSP')
    expect(row!.cells['SOXL'].positions[0].strike).toBe(20)
  })

  it('places a CC position in the correct cell', () => {
    const positions = [
      makePosition({
        id: 2,
        symbol: 'TECL 250530C00085000',
        optionType: 'CALL',
        strikePrice: 85,
        underlyingSymbol: 'TECL',
        quantity: -1,
        averageCost: 3.40,
        totalPnl: 210,
      }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['TECL'].positions).toHaveLength(1)
    expect(row!.cells['TECL'].positions[0].type).toBe('CC')
  })

  it('filters out non-option positions', () => {
    const positions = [
      makePosition({ instrumentType: 'STOCK', strikePrice: null, optionType: null }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const totalPositions = grid.expiryRows.reduce(
      (sum, row) => sum + Object.values(row.cells).reduce(
        (s, cell) => s + cell.positions.length, 0
      ), 0
    )
    expect(totalPositions).toBe(0)
  })

  it('filters out positions with expiry beyond 90 days', () => {
    const positions = [
      makePosition({ expirationDate: '2026-09-18' }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const totalPositions = grid.expiryRows.reduce(
      (sum, row) => sum + Object.values(row.cells).reduce(
        (s, cell) => s + cell.positions.length, 0
      ), 0
    )
    expect(totalPositions).toBe(0)
  })

  it('stacks multiple positions in the same cell', () => {
    const positions = [
      makePosition({ id: 1, strikePrice: 20 }),
      makePosition({ id: 2, strikePrice: 18, averageCost: 0.60, totalPnl: 40 }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['SOXL'].positions).toHaveLength(2)
  })

  it('computes DTE correctly', () => {
    const positions = [makePosition()]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    // DTE is 8 because 2026-05-30 minus 2026-05-23 = 7 days, but the calculation is inclusive
    // (from May 23 to May 30 inclusive is 8 days in the calendar)
    expect(row!.dte).toBe(8)
  })

  it('includes expiry dates from available expirations even without positions', () => {
    const availableExpiries = ['2026-06-06', '2026-06-20']
    const grid = buildWheelGrid([], tickers, availableExpiries, today)

    expect(grid.expiryRows.length).toBeGreaterThanOrEqual(2)
    const jun6 = grid.expiryRows.find(r => r.expiryDate === '2026-06-06')
    expect(jun6).toBeDefined()
    expect(jun6!.cells['SOXL'].positions).toHaveLength(0)
  })
})

describe('computeTickerTotals', () => {
  it('computes position count, CSP exposure, and total P&L', () => {
    const positions = [
      makePosition(),
      makePosition({
        id: 2,
        optionType: 'CALL',
        strikePrice: 26,
        totalPnl: 28,
        quantity: -1,
      }),
    ]
    const tickers = ['SOXL']
    const today = new Date('2026-05-23')
    const grid = buildWheelGrid(positions, tickers, [], today)
    const totals = computeTickerTotals(grid)

    expect(totals['SOXL'].positionCount).toBe(2)
    expect(totals['SOXL'].cspExposure).toBe(2000)
    expect(totals['SOXL'].totalPnl).toBe(90)
  })
})

describe('buildWheelGrid with symbol-parsed positions', () => {
  const tickers = ['SOXL', 'TQQQ']
  const today = new Date('2026-05-23')

  it('parses option data from symbol when fields are null', () => {
    const positions = [
      makePosition({
        symbol: 'SOXL29May26P70.00',
        instrumentType: 'OTHER',
        optionType: null,
        strikePrice: null,
        expirationDate: null,
        underlyingSymbol: null,
      }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-29')
    expect(row).toBeDefined()
    expect(row!.cells['SOXL'].positions).toHaveLength(1)
    expect(row!.cells['SOXL'].positions[0].type).toBe('CSP')
    expect(row!.cells['SOXL'].positions[0].strike).toBe(70)
  })

  it('parses call options from symbol', () => {
    const positions = [
      makePosition({
        symbol: 'TQQQ18Jun26C48.00',
        instrumentType: 'OTHER',
        optionType: null,
        strikePrice: null,
        expirationDate: null,
        underlyingSymbol: null,
      }),
    ]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-06-18')
    expect(row).toBeDefined()
    expect(row!.cells['TQQQ'].positions).toHaveLength(1)
    expect(row!.cells['TQQQ'].positions[0].type).toBe('CC')
    expect(row!.cells['TQQQ'].positions[0].strike).toBe(48)
  })
})

describe('computeCapitalMetrics', () => {
  it('computes capital metrics from positions and cash', () => {
    const optionPositions = [
      makePosition({ optionType: 'PUT', strikePrice: 20, quantity: -1, averageCost: 0.85, totalPnl: 62 }),
      makePosition({
        id: 2,
        optionType: 'CALL',
        strikePrice: 26,
        underlyingSymbol: 'SOXL',
        quantity: -1,
        averageCost: 0.65,
        totalPnl: 28,
      }),
    ]
    const stockPositions = [
      makePosition({
        id: 3,
        instrumentType: 'STOCK',
        symbol: 'SOXL',
        underlyingSymbol: null,
        optionType: null,
        strikePrice: null,
        currentValue: 2245,
        quantity: 100,
      }),
    ]
    const tickers = ['SOXL']
    const metrics = computeCapitalMetrics(optionPositions, stockPositions, tickers, 5000)

    expect(metrics.availableCash).toBe(5000)
    expect(metrics.deployedCsp).toBe(2000)
    expect(metrics.sharesHeld).toBe(2245)
    expect(metrics.ccsWritten).toBe(2600)
    expect(metrics.totalPremium).toBe(150)
    expect(metrics.unrealizedPnl).toBe(90)
  })
})
