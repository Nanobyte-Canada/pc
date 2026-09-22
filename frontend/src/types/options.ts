export interface Quote {
  symbol: string
  bid: number
  ask: number
  last: number
  mid: number
  spread: number
  volume: number
  timestamp: string
}

export type OptionTypeName = 'CALL' | 'PUT'

export interface Greeks {
  delta: number
  gamma: number
  theta: number
  vega: number
  rho: number
  source: 'QUESTRADE' | 'BLACK_SCHOLES'
}

export interface OptionQuoteData {
  underlying: string
  optionType: OptionTypeName
  strike: number
  expiry: string
  bid: number
  ask: number
  last: number
  mid: number
  spread: number
  spreadQuality: number
  volume: number
  openInterest: number
  greeks: Greeks | null
  timestamp: string
}

export interface StrikeData {
  call: OptionQuoteData | null
  put: OptionQuoteData | null
}

export interface OptionsChain {
  underlying: string
  spotPrice: number
  expirations: Record<string, Record<string, StrikeData>>
}

export interface IvRankData {
  ticker: string
  currentIv: number
  ivRank: number
  ivPercentile: number
  periodStart: string
  periodEnd: string
  observationCount: number
}

export type StrategyType =
  | 'BULL_CALL_SPREAD'
  | 'BEAR_PUT_SPREAD'
  | 'BULL_PUT_SPREAD'
  | 'BEAR_CALL_SPREAD'
  | 'IRON_CONDOR'
  | 'BUTTERFLY_SPREAD'

export type LegAction = 'BUY' | 'SELL'

export interface Leg {
  action: LegAction
  optionType: OptionTypeName
  strike: number
  expiry: string
  quantity: number
  price?: number
  bid?: number
  ask?: number
  mid?: number
  delta?: number
}

export interface NetGreeks {
  delta: number
  gamma: number
  theta: number
  vega: number
}

export interface PnlPoint {
  spotPrice: number
  pnl: number
}

export interface CalculationResult {
  maxProfit: number
  maxLoss: number
  breakEvens: number[]
  netDebit: number
  probabilityOfProfit: number
  roi: number
  pnlCurve: PnlPoint[]
  netGreeks: NetGreeks
  maxProfitDollars: number
  maxLossDollars: number
  netDebitCreditDollars: number
  warnings: string[]
}

export interface StrategyInfo {
  type: StrategyType
  name: string
  description: string
  legs: number
  marketOutlook: string
  riskLevel: string
}

export interface StrategyEducation {
  whenToUse: string
  riskExplanation: string
  keyCharacteristics: string[]
  warnings: string[]
}

export interface WheelAccount {
  id: number
  name: string
  accountId: number | null
  tickers: string
  active: boolean
}

export interface WheelConfig {
  id: number
  wheelAccountId: number
  dteMin: number
  dteMax: number
  cspDeltaTarget: number
  cspDeltaTolerance: number
  ccDeltaTarget: number
  ccDeltaTolerance: number
  ivRankThreshold: number
}

export interface WheelRecommendation {
  id: number
  wheelAccountId: number
  ticker: string
  optionType: OptionTypeName
  strike: number
  expiry: string
  dte: number
  delta: number | null
  bid: number | null
  ask: number | null
  mid: number | null
  annualizedYield: number | null
  score: number | null
  status: string
}

export interface OptionsOrderRequest {
  strategyType: StrategyType
  underlying: string
  legs: Leg[]
  quantity: number
  orderType: string
  netPrice?: number
}

export interface OptionsOrderResponse {
  id: number
  strategyType: StrategyType
  underlying: string
  status: string
  brokerOrderId: string | null
  createdAt: string
}

export interface TradeRequest {
  strategyType: string
  legs: Leg[]
  spotPrice: number
  connectionId: number
  accountId: string
  limitPrice: number
  orderType?: string
  timeInForce?: string
}

export interface TradeResponse {
  orderId: string | null
  status: 'SUBMITTED' | 'REJECTED' | 'ERROR'
  message: string
  legs?: Array<{
    action: string
    symbol: string
    quantity: number
    status: string
  }>
}
