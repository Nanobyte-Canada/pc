export interface WheelTicker {
  symbol: string
  currentPrice: number | null
}

export interface WheelPosition {
  id: number
  type: 'CSP' | 'CC'
  strike: number
  premium: number | null
  currentPrice: number | null
  pnl: number | null
  otmPercent: number | null
  quantity: number
  currency: string
  accountName: string | null
  accountNumber: string | null
  connectionId: number
}

export interface WheelCell {
  positions: WheelPosition[]
}

export interface WheelExpiryRow {
  expiryDate: string
  dte: number
  dayOfWeek: string
  isMonthly: boolean
  cells: Record<string, WheelCell>
}

export interface DualCurrency {
  usd: number
  cad: number
}

export interface TickerTotals {
  positionCount: number
  cspExposure: DualCurrency
  totalPnl: DualCurrency
}

export interface WheelGridData {
  tickers: WheelTicker[]
  expiryRows: WheelExpiryRow[]
  totals: Record<string, TickerTotals>
}

export interface CapitalMetrics {
  cashUsd: number
  cashCad: number
  cashTotalUsd: number
  cashTotalCad: number
  deployedCsp: DualCurrency
  ccsWritten: DualCurrency
  totalPremium: DualCurrency
  unrealizedPnl: DualCurrency
}

export type DteUrgency = 'critical' | 'warning' | 'safe'

export function getDteUrgency(dte: number): DteUrgency {
  if (dte <= 5) return 'critical'
  if (dte <= 20) return 'warning'
  return 'safe'
}

export { isMonthlyExpiry } from '@/hooks/marketHolidays'

export interface WheelChainStrike {
  strike: number
  bid: number | null
  ask: number | null
  delta: number | null
  discount: number | null
  bidYield: number | null
  askYield: number | null
  isATM: boolean
  isITM: boolean
}

export interface CCInfo {
  sharesOwned: number
  contractsAvailable: number
}

export interface TickerRowData {
  symbol: string
  currentPrice: number | null
  currency: string
  totalExposure: number
  ccInfo: CCInfo | null
  cells: Record<string, WheelCell>
}

export interface CalendarWindow {
  startDate: string
  endDate: string
  expiries: Array<{
    date: string
    dte: number
    dayOfWeek: string
    isMonthly: boolean
  }>
}

export interface CalendarGridData {
  tickerRows: TickerRowData[]
  calendarWindow: CalendarWindow
  manualTickers: string[]
}

export interface ChainPanelContext {
  ticker: string
  expiryDate: string
  optionSide: 'put' | 'call'
  /** When true the panel is opened in search mode with no ticker pre-selected. */
  searchMode?: boolean
}
