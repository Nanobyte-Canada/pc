// frontend/src/hooks/__tests__/useWheelPositions.test.ts
import { describe, it, expect } from 'vitest'
import { buildWheelGrid, computeTickerTotals, discoverTickers, detectCCEligible, generateWeeklyExpiries } from '../useWheelPositions'
import { isMarketHoliday, isMonthlyExpiry, getWeeklyExpiryDate } from '../marketHolidays'
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

  it('adds currency field to positions', () => {
    const positions = [makePosition({ currency: 'USD' })]
    const grid = buildWheelGrid(positions, tickers, [], today)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['SOXL'].positions[0].currency).toBe('USD')
  })

  it('fills premium from premiumMap when averageCost is null', () => {
    const positions = [makePosition({ averageCost: null })]
    const premiumMap = new Map([['SOXL 250530P00020000', { premium: 125, currency: 'USD' }]])
    const grid = buildWheelGrid(positions, tickers, [], today, null, null, 0, {}, premiumMap)

    const row = grid.expiryRows.find(r => r.expiryDate === '2026-05-30')
    expect(row!.cells['SOXL'].positions[0].premium).toBe(125)
  })
})

describe('computeTickerTotals', () => {
  it('computes position count, CSP exposure (dual currency), and total P&L (dual currency)', () => {
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
    const fxRate = 1.40
    const grid = buildWheelGrid(positions, tickers, [], today, null, null, 0, {}, new Map(), fxRate)
    const totals = computeTickerTotals(grid, fxRate)

    expect(totals['SOXL'].positionCount).toBe(2)
    expect(totals['SOXL'].cspExposure.usd).toBe(2000)
    expect(totals['SOXL'].cspExposure.cad).toBe(2800)
    expect(totals['SOXL'].totalPnl.usd).toBe(90)
    expect(totals['SOXL'].totalPnl.cad).toBeCloseTo(126, 2)
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

describe('discoverTickers', () => {
  it('returns unique underlying symbols from option positions', () => {
    const positions: BrokerPosition[] = [
      makePosition({ underlyingSymbol: 'SOXL', optionType: 'PUT' }),
      makePosition({ id: 2, underlyingSymbol: 'SOXL', optionType: 'PUT' }),
      makePosition({ id: 3, underlyingSymbol: 'TQQQ', optionType: 'CALL', symbol: 'TQQQ30May26C500', strikePrice: 500 }),
    ]
    const tickers = discoverTickers(positions)
    expect(tickers).toEqual(['SOXL', 'TQQQ'])
  })

  it('ignores positions without option fields', () => {
    const positions: BrokerPosition[] = [
      makePosition({ underlyingSymbol: null, optionType: null, symbol: 'AAPL', instrumentType: 'STOCK', strikePrice: null, expirationDate: null }),
    ]
    const tickers = discoverTickers(positions)
    expect(tickers).toEqual([])
  })
})

describe('detectCCEligible', () => {
  it('detects tickers with 100+ shares', () => {
    const positions: BrokerPosition[] = [
      { id: 10, symbol: 'TQQQ', instrumentType: 'STOCK', quantity: 100, currency: 'USD',
        securityName: null, averageCost: 40, currentPrice: 827, currentValue: 82700,
        totalPnl: 42700, totalPnlPercent: 107, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
      { id: 11, symbol: 'QQU.TO', instrumentType: 'STOCK', quantity: 300, currency: 'CAD',
        securityName: null, averageCost: 26, currentPrice: 41.38, currentValue: 12414,
        totalPnl: 4614, totalPnlPercent: 59, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
      { id: 12, symbol: 'TECL', instrumentType: 'STOCK', quantity: 38, currency: 'USD',
        securityName: null, averageCost: 86, currentPrice: 256, currentValue: 9728,
        totalPnl: 6460, totalPnlPercent: 197, strikePrice: null, expirationDate: null,
        optionType: null, underlyingSymbol: null },
    ]
    const ccMap = detectCCEligible(positions)
    expect(ccMap.get('TQQQ')).toEqual({ sharesOwned: 100, contractsAvailable: 1 })
    expect(ccMap.get('QQU.TO')).toEqual({ sharesOwned: 300, contractsAvailable: 3 })
    expect(ccMap.has('TECL')).toBe(false)
  })
})

describe('marketHolidays', () => {
  it('identifies known market holidays', () => {
    expect(isMarketHoliday('2026-07-03')).toBe(true)   // Independence Day (observed)
    expect(isMarketHoliday('2026-12-25')).toBe(true)   // Christmas
    expect(isMarketHoliday('2026-04-03')).toBe(true)   // Good Friday
  })

  it('returns false for regular trading days', () => {
    expect(isMarketHoliday('2026-06-05')).toBe(false)  // regular Friday
    expect(isMarketHoliday('2026-06-12')).toBe(false)  // regular Friday
  })

  it('shifts Friday expiry to Thursday when Friday is a holiday', () => {
    // 2026-07-03 is a holiday (Independence Day observed) — should shift to 2026-07-02 (Thursday)
    const friday = new Date(2026, 6, 3) // July 3
    const actual = getWeeklyExpiryDate(friday)
    expect(actual.getDate()).toBe(2)
    expect(actual.getDay()).toBe(4) // Thursday
  })

  it('keeps Friday when it is not a holiday', () => {
    const friday = new Date(2026, 5, 5) // June 5
    const actual = getWeeklyExpiryDate(friday)
    expect(actual.getDate()).toBe(5)
    expect(actual.getDay()).toBe(5) // Friday
  })

  it('recognizes holiday-adjusted monthly expiry (3rd Friday shifted to Thursday)', () => {
    // 2027-03-26 is Good Friday AND the 4th Friday — not a monthly
    // Need a case where 3rd Friday IS a holiday. Check Juneteenth 2027:
    // 2027-06-18 is Juneteenth (observed, Friday) AND the 3rd Friday of June 2027
    expect(isMonthlyExpiry('2027-06-17')).toBe(true)  // Thursday before holiday 3rd Friday
    expect(isMonthlyExpiry('2027-06-18')).toBe(false)  // holiday itself
  })

  it('recognizes normal 3rd Friday as monthly expiry', () => {
    // June 2026: 3rd Friday (Jun 19) is Juneteenth holiday, so monthly shifts to Jun 18 (Thu)
    expect(isMonthlyExpiry('2026-06-18')).toBe(true)
    expect(isMonthlyExpiry('2026-06-19')).toBe(false)  // holiday — not a valid expiry
    // May 2026: 3rd Friday (May 15) is not a holiday
    expect(isMonthlyExpiry('2026-05-15')).toBe(true)
    expect(isMonthlyExpiry('2026-05-08')).toBe(false)  // 2nd Friday
  })
})

describe('generateWeeklyExpiries with holidays', () => {
  it('generates Thursday expiry when Friday is a holiday', () => {
    // Start from a date just before July 3, 2026 (holiday Friday)
    const start = new Date(2026, 5, 29) // June 29 (Monday)
    const expiries = generateWeeklyExpiries(start, 2)

    // First expiry: July 2 (Thursday, shifted from July 3 holiday)
    expect(expiries[0].date).toBe('2026-07-02')
    expect(expiries[0].dayOfWeek).toBe('Thursday')

    // Second expiry: July 10 (regular Friday)
    expect(expiries[1].date).toBe('2026-07-10')
    expect(expiries[1].dayOfWeek).toBe('Friday')
  })
})
