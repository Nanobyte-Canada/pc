export interface PerformanceSummary {
  twr: number
  mwr: number
  totalReturn: number
  volatility: number
  sharpeRatio: number
  sortinoRatio: number
  maxDrawdown: number
  startingValue: number
  endingValue: number
  startDate: string
  endDate: string
}

export interface ReturnPoint {
  date: string
  cumulativeReturn: number
  portfolioValue: number
}

export interface BenchmarkComparison {
  portfolioReturns: ReturnPoint[]
  benchmarkReturns: ReturnPoint[]
  alpha: number
}

export interface DrawdownPoint {
  date: string
  drawdown: number
}

export interface PerformanceChartData {
  summary: PerformanceSummary
  cumulativeReturns: ReturnPoint[]
  drawdowns: DrawdownPoint[]
  benchmarkComparison: BenchmarkComparison | null
}

export type PerformancePeriod = '1M' | '3M' | '6M' | 'YTD' | '1Y' | 'ALL'
