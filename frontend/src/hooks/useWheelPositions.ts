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

function isOptionPosition(p: BrokerPosition): boolean {
  return (
    p.instrumentType === 'OPTION' &&
    p.optionType != null &&
    p.strikePrice != null &&
    p.expirationDate != null &&
    p.underlyingSymbol != null
  )
}

function toWheelPosition(
  p: BrokerPosition,
  accountName: string | null,
  accountNumber: string | null,
  connectionId: number,
): WheelPosition {
  return {
    id: p.id,
    type: p.optionType === 'CALL' ? 'CC' : 'CSP',
    strike: p.strikePrice!,
    premium: p.averageCost != null ? Math.abs(p.averageCost) * 100 : null,
    currentPrice: p.currentPrice,
    pnl: p.totalPnl,
    otmPercent: null,
    quantity: Math.abs(p.quantity),
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
  const optionPositions = positions.filter(
    p => isOptionPosition(p) && tickers.includes(p.underlyingSymbol!)
  )

  const expirySet = new Set<string>()
  availableExpiries.forEach(e => {
    const dte = diffDays(today, new Date(e + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(e)
  })
  optionPositions.forEach(p => {
    const dte = diffDays(today, new Date(p.expirationDate! + 'T00:00:00'))
    if (dte >= 0 && dte <= MAX_DTE) expirySet.add(p.expirationDate!)
  })

  const sortedExpiries = Array.from(expirySet).sort()

  const expiryRows: WheelExpiryRow[] = sortedExpiries.map(expiry => {
    const d = new Date(expiry + 'T00:00:00')
    const dte = diffDays(today, d)

    const cells: Record<string, WheelCell> = {}
    tickers.forEach(ticker => {
      const matching = optionPositions
        .filter(p => p.underlyingSymbol === ticker && p.expirationDate === expiry)
        .map(p => {
          const wp = toWheelPosition(p, accountName, accountNumber, connectionId)
          const underlyingPrice = underlyingPrices[ticker]
          if (underlyingPrice && underlyingPrice > 0) {
            wp.otmPercent = Math.abs(wp.strike - underlyingPrice) / underlyingPrice * 100
          }
          return wp
        })
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
    if (!isOptionPosition(p) || !tickers.includes(p.underlyingSymbol!)) return
    const strike = p.strikePrice!
    const qty = Math.abs(p.quantity)
    const cost = Math.abs(p.averageCost ?? 0)

    if (p.optionType === 'PUT') {
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
