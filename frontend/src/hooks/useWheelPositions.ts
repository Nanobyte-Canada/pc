// frontend/src/hooks/useWheelPositions.ts
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getConnectionPositions, getAggregatedPositions } from '@/services/brokerService'
import type { BrokerPosition, ConnectionPositionsResponse, AggregatedPositionsResponse } from '@/types/broker'
import type {
  WheelGridData, WheelExpiryRow, WheelCell, WheelPosition,
  WheelTicker, TickerTotals,
} from '@/types/wheel'
import type { PremiumInfo } from '@/hooks/useWheelActivities'

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
  premiumMap: Map<string, PremiumInfo> = new Map(),
): WheelPosition | null {
  const fields = resolveOptionFields(p)
  if (!fields) return null
  let premium = p.averageCost != null ? Math.abs(p.averageCost) * 100 : null
  if (premium == null) {
    const lookup = premiumMap.get(p.symbol)
    if (lookup) {
      premium = lookup.premium
    }
  }
  return {
    id: p.id,
    type: fields.optionType === 'CALL' ? 'CC' : 'CSP',
    strike: fields.strikePrice,
    premium,
    currentPrice: p.currentPrice,
    pnl: p.totalPnl,
    otmPercent: null,
    quantity: Math.abs(p.quantity ?? ((p as unknown as Record<string, unknown>).totalQuantity as number) ?? 1),
    currency: p.currency ?? 'USD',
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
  premiumMap: Map<string, PremiumInfo> = new Map(),
  fxRate: number = 1.38,
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
          const wp = toWheelPosition(position, accountName, accountNumber, connectionId, premiumMap)
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

  const gridData: WheelGridData = {
    tickers: tickerList,
    expiryRows,
    totals: {},
  }
  gridData.totals = computeTickerTotals(gridData, fxRate)

  return gridData
}

export function computeTickerTotals(grid: WheelGridData, fxRate: number = 1.38): Record<string, TickerTotals> {
  const totals: Record<string, TickerTotals> = {}

  grid.tickers.forEach(({ symbol }) => {
    let positionCount = 0
    let cspExposureUsd = 0
    let totalPnlUsd = 0

    grid.expiryRows.forEach(row => {
      const cell = row.cells[symbol]
      if (!cell) return
      cell.positions.forEach(pos => {
        positionCount++
        if (pos.type === 'CSP') {
          cspExposureUsd += pos.strike * 100 * pos.quantity
        }
        totalPnlUsd += pos.pnl ?? 0
      })
    })

    totals[symbol] = {
      positionCount,
      cspExposure: { usd: cspExposureUsd, cad: cspExposureUsd * fxRate },
      totalPnl: { usd: totalPnlUsd, cad: totalPnlUsd * fxRate },
    }
  })

  return totals
}

export function discoverTickers(positions: BrokerPosition[]): string[] {
  const tickerSet = new Set<string>()
  for (const p of positions) {
    const fields = resolveOptionFields(p)
    if (fields) tickerSet.add(fields.underlyingSymbol)
  }
  return Array.from(tickerSet).sort()
}

export function detectCCEligible(positions: BrokerPosition[]): Map<string, { sharesOwned: number; contractsAvailable: number }> {
  const ccMap = new Map<string, { sharesOwned: number; contractsAvailable: number }>()
  for (const p of positions) {
    if (p.optionType != null || p.strikePrice != null) continue
    const qty = Math.abs(p.quantity ?? 0)
    if (qty >= 100) {
      ccMap.set(p.symbol, {
        sharesOwned: qty,
        contractsAvailable: Math.floor(qty / 100),
      })
    }
  }
  return ccMap
}

export function generateWeeklyExpiries(startDate: Date, count: number): Array<{ date: string; dte: number; dayOfWeek: string; isMonthly: boolean }> {
  const today = new Date()
  const expiries: Array<{ date: string; dte: number; dayOfWeek: string; isMonthly: boolean }> = []
  const d = new Date(startDate)
  const dow = d.getDay()
  if (dow !== 5) {
    d.setDate(d.getDate() + ((5 - dow + 7) % 7))
  }
  for (let i = 0; i < count; i++) {
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    expiries.push({
      date: iso,
      dte: Math.max(0, diffDays(today, d)),
      dayOfWeek: DAY_NAMES[d.getDay()],
      isMonthly: isMonthlyExpiry(iso),
    })
    d.setDate(d.getDate() + 7)
  }
  return expiries
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
