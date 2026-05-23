// frontend/src/types/wheel.ts

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

export interface TickerTotals {
  positionCount: number
  cspExposure: number
  totalPnl: number
}

export interface WheelGridData {
  tickers: WheelTicker[]
  expiryRows: WheelExpiryRow[]
  totals: Record<string, TickerTotals>
}

export interface CapitalMetrics {
  availableCash: number
  deployedCsp: number
  sharesHeld: number
  ccsWritten: number
  totalPremium: number
  unrealizedPnl: number
}

export type DteUrgency = 'critical' | 'warning' | 'normal' | 'safe' | 'far'

export function getDteUrgency(dte: number): DteUrgency {
  if (dte <= 10) return 'critical'
  if (dte <= 21) return 'warning'
  if (dte <= 45) return 'normal'
  if (dte <= 70) return 'safe'
  return 'far'
}

export function isMonthlyExpiry(date: Date): boolean {
  const thirdFriday = getThirdFriday(date.getFullYear(), date.getMonth())
  return date.getDate() === thirdFriday.getDate()
}

function getThirdFriday(year: number, month: number): Date {
  const first = new Date(year, month, 1)
  const dayOfWeek = first.getDay()
  const firstFriday = dayOfWeek <= 5 ? (5 - dayOfWeek + 1) : (5 + 7 - dayOfWeek + 1)
  return new Date(year, month, firstFriday + 14)
}
