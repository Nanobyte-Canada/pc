// frontend/src/hooks/useWheelPositions.ts
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getConnectionPositions, getAggregatedPositions } from '@/services/brokerService'
import type { BrokerPosition, ConnectionPositionsResponse, AggregatedPositionsResponse } from '@/types/broker'
import type {
  WheelGridData, WheelExpiryRow, WheelCell, WheelPosition,
  WheelTicker, TickerTotals, CapitalMetrics,
} from '@/types/wheel'

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const MAX_DTE = 90

function diffDays(from: Date, to: Date): number {
  const msPerDay = 86400000
  const utcFrom = Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())
  const utcTo = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate())
  return Math.round((utcTo - utcFrom) / msPerDay)
}

function getThirdFriday(year: number, month: number): Date {
  const first = new Date(year, month, 1)
  const dayOfWeek = first.getDay()
  const firstFriday = dayOfWeek <= 5 ? (5 - dayOfWeek + 1) : (5 + 7 - dayOfWeek + 1)
  return new Date(year, month, firstFriday + 14)
}

function isMonthlyExpiry(dateStr: string): boolean {
  const d = new Date(dateStr + 'T00:00:00')
  const thirdFriday = getThirdFriday(d.getFullYear(), d.getMonth())
  return d.getDate() === thirdFriday.getDate()
}

interface ParsedOption {
  underlying: string
  expiry: string
  optionType: 'CALL' | 'PUT'
  strike: number
}

const MONTH_MAP: Record<string, string> = {
  Jan: '01', Feb: '02', Mar: '03', Apr: '04', May: '05', Jun: '06',
  Jul: '07', Aug: '08', Sep: '09', Oct: '10', Nov: '11', Dec: '12',
}

const OPTION_SYMBOL_RE = /^([A-Z]+)(\d{2})([A-Z][a-z]{2})(\d{2})([CP])(\d+(?:\.\d+)?)$/

function parseOptionSymbol(symbol: string): ParsedOption | null {
  const m = symbol.match(OPTION_SYMBOL_RE)
  if (!m) return null
  const [, underlying, day, monthStr, year, cp, strike] = m
  const month = MONTH_MAP[monthStr]
  if (!month) return null
  return {
    underlying,
    expiry: `20${year}-${month}-${day}`,
    optionType: cp === 'C' ? 'CALL' : 'PUT',
    strike: parseFloat(strike),
  }
}

function isOptionPosition(p: BrokerPosition): boolean {
  if (p.instrumentType === 'OPTION' && p.optionType != null && p.strikePrice != null && p.expirationDate != null && p.underlyingSymbol != null) {
    return true
  }
  return parseOptionSymbol(p.symbol) != null
}

function resolveOptionFields(p: BrokerPosition): { optionType: string, strikePrice: number, expirationDate: string, underlyingSymbol: string } | null {
  if (p.optionType != null && p.strikePrice != null && p.expirationDate != null && p.underlyingSymbol != null) {
    return { optionType: p.optionType, strikePrice: p.strikePrice, expirationDate: p.expirationDate, underlyingSymbol: p.underlyingSymbol }
  }
  const parsed = parseOptionSymbol(p.symbol)
  if (!parsed) return null
  return { optionType: parsed.optionType, strikePrice: parsed.strike, expirationDate: parsed.expiry, underlyingSymbol: parsed.underlying }
}

function toWheelPosition(
  p: BrokerPosition,
  accountName: string | null,
  accountNumber: string | null,
  connectionId: number,
): WheelPosition | null {
  const fields = resolveOptionFields(p)
  if (!fields) return null
  return {
    id: p.id,
    type: fields.optionType === 'CALL' ? 'CC' : 'CSP',
    strike: fields.strikePrice,
    premium: p.averageCost != null ? Math.abs(p.averageCost) * 100 : null,
    currentPrice: p.currentPrice,
    pnl: p.totalPnl,
    otmPercent: null,
    quantity: Math.abs(p.quantity ?? (p as Record<string, unknown>).totalQuantity as number ?? 1),
    accountName,
    accountNumber,
    connectionId,
  }
}

export function buildWheelGrid(
  positions: BrokerPosition[],
  tickers: string[],
  availableExpiries: string[],
  today: Date,
  accountName: string | null = null,
  accountNumber: string | null = null,
  connectionId: number = 0,
  underlyingPrices: Record<string, number> = {},
): WheelGridData {
  const resolvedPositions = positions
    .map(p => ({ position: p, fields: resolveOptionFields(p) }))
    .filter((r): r is { position: BrokerPosition; fields: NonNullable<ReturnType<typeof resolveOptionFields>> } =>
      r.fields != null && tickers.includes(r.fields.underlyingSymbol)
    )

  const expirySet = new Set<string>()
  availableExpiries.forEach(e => {
    const dte = diffDays(today, new Date(e + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(e)
  })
  resolvedPositions.forEach(({ fields }) => {
    const dte = diffDays(today, new Date(fields.expirationDate + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(fields.expirationDate)
  })

  const sortedExpiries = Array.from(expirySet).sort()

  const expiryRows: WheelExpiryRow[] = sortedExpiries.map(expiry => {
    const d = new Date(expiry + 'T00:00:00')
    const dte = diffDays(today, d)

    const cells: Record<string, WheelCell> = {}
    tickers.forEach(ticker => {
      const matching = resolvedPositions
        .filter(({ fields }) => fields.underlyingSymbol === ticker && fields.expirationDate === expiry)
        .map(({ position }) => {
          const wp = toWheelPosition(position, accountName, accountNumber, connectionId)
          if (!wp) return null
          const underlyingPrice = underlyingPrices[ticker]
          if (underlyingPrice && underlyingPrice > 0) {
            wp.otmPercent = Math.abs(wp.strike - underlyingPrice) / underlyingPrice * 100
          }
          return wp
        })
        .filter((wp): wp is WheelPosition => wp != null)
      cells[ticker] = { positions: matching }
    })

    return {
      expiryDate: expiry,
      dte,
      dayOfWeek: DAY_NAMES[d.getDay()],
      isMonthly: isMonthlyExpiry(expiry),
      cells,
    }
  })

  const tickerList: WheelTicker[] = tickers.map(symbol => ({
    symbol,
    currentPrice: underlyingPrices[symbol] ?? null,
  }))

  return {
    tickers: tickerList,
    expiryRows,
    totals: computeTickerTotals({ tickers: tickerList, expiryRows, totals: {} }),
  }
}

export function computeTickerTotals(grid: WheelGridData): Record<string, TickerTotals> {
  const totals: Record<string, TickerTotals> = {}

  grid.tickers.forEach(({ symbol }) => {
    let positionCount = 0
    let cspExposure = 0
    let totalPnl = 0

    grid.expiryRows.forEach(row => {
      const cell = row.cells[symbol]
      if (!cell) return
      cell.positions.forEach(pos => {
        positionCount++
        if (pos.type === 'CSP') {
          cspExposure += pos.strike * 100 * pos.quantity
        }
        totalPnl += pos.pnl ?? 0
      })
    })

    totals[symbol] = { positionCount, cspExposure, totalPnl }
  })

  return totals
}

export function computeCapitalMetrics(
  optionPositions: BrokerPosition[],
  stockPositions: BrokerPosition[],
  tickers: string[],
  cashBalance: number,
): CapitalMetrics {
  let deployedCsp = 0
  let ccsWritten = 0
  let totalPremium = 0
  let unrealizedPnl = 0

  optionPositions.forEach(p => {
    const fields = resolveOptionFields(p)
    if (!fields || !tickers.includes(fields.underlyingSymbol)) return
    const strike = fields.strikePrice
    const qty = Math.abs(p.quantity)
    const cost = Math.abs(p.averageCost ?? 0)

    if (fields.optionType === 'PUT') {
      deployedCsp += strike * 100 * qty
    } else {
      ccsWritten += strike * 100 * qty
    }
    totalPremium += cost * 100 * qty
    unrealizedPnl += p.totalPnl ?? 0
  })

  const sharesHeld = stockPositions
    .filter(p => tickers.includes(p.symbol))
    .reduce((sum, p) => sum + (p.currentValue ?? 0), 0)

  return {
    availableCash: cashBalance,
    deployedCsp,
    sharesHeld,
    ccsWritten,
    totalPremium,
    unrealizedPnl,
  }
}

export function useWheelPositions(
  tickers: string[],
  connectionId?: number,
) {
  const positionsQuery = useQuery<ConnectionPositionsResponse | AggregatedPositionsResponse>({
    queryKey: ['wheel-positions', connectionId ?? 'all'],
    queryFn: () =>
      connectionId
        ? getConnectionPositions(connectionId)
        : getAggregatedPositions(),
    staleTime: 60_000,
    enabled: tickers.length > 0,
  })

  const gridData = useMemo(() => {
    if (!positionsQuery.data) return null

    const today = new Date()
    let positions: BrokerPosition[]
    let accountName: string | null = null
    let accountNumber: string | null = null
    let connId = 0

    const data = positionsQuery.data
    if ('connectionId' in data && 'broker' in data) {
      const resp = data as ConnectionPositionsResponse
      positions = resp.positions
      accountName = resp.broker
      accountNumber = resp.accountNumber
      connId = resp.connectionId
    } else {
      const resp = data as AggregatedPositionsResponse
      positions = resp.positions as unknown as BrokerPosition[]
    }

    return buildWheelGrid(positions, tickers, [], today, accountName, accountNumber, connId)
  }, [positionsQuery.data, tickers])

  return {
    gridData,
    isLoading: positionsQuery.isLoading,
    error: positionsQuery.error,
    refetch: positionsQuery.refetch,
  }
}
