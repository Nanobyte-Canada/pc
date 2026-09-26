import { proxyFetch, parseErrorResponse } from '@/services/api'
import type {
  StrategyInfo,
  StrategyType,
  CalculationResult,
  StrategyEducation,
  Leg,
  OptionsOrderRequest,
  OptionsOrderResponse,
  WheelConfig,
  WheelRecommendation,
} from '@/types/options'

// --- Backend → Frontend Mappers ---

interface BackendStrategyListResponse {
  name: string        // enum name like "BULL_CALL_SPREAD"
  displayName: string // "Bull Call Spread"
  description: string
  outlook: string
  riskProfile: string
  legCount: number
}

interface BackendCalculateResponse {
  netDebitCredit: number
  maxProfit: number
  maxLoss: number
  breakEvenPrices: number[]
  riskRewardRatio: number
  probabilityOfProfit: number | null
  pnlCurve: Array<{ underlyingPrice: number; pnl: number }>
  netGreeks: { delta: number; gamma: number; theta: number; vega: number }
  warnings: string[]
  maxProfitDollars: number
  maxLossDollars: number
  netDebitCreditDollars: number
}

function mapStrategyInfo(raw: BackendStrategyListResponse): StrategyInfo {
  return {
    type: raw.name as StrategyType,
    name: raw.displayName,
    description: raw.description,
    legs: raw.legCount,
    marketOutlook: raw.outlook,
    riskLevel: raw.riskProfile,
  }
}

function mapCalculationResult(raw: BackendCalculateResponse): CalculationResult {
  return {
    maxProfit: raw.maxProfit,
    maxLoss: raw.maxLoss,
    breakEvens: raw.breakEvenPrices,
    netDebit: raw.netDebitCredit,
    probabilityOfProfit: raw.probabilityOfProfit ?? 0,
    roi: raw.riskRewardRatio,
    pnlCurve: raw.pnlCurve.map(p => ({ spotPrice: p.underlyingPrice, pnl: p.pnl })),
    netGreeks: raw.netGreeks,
    maxProfitDollars: raw.maxProfitDollars ?? 0,
    maxLossDollars: raw.maxLossDollars ?? 0,
    netDebitCreditDollars: raw.netDebitCreditDollars ?? 0,
    warnings: raw.warnings ?? [],
  }
}

// --- API Functions (with mapping) ---

export async function getStrategies(): Promise<StrategyInfo[]> {
  const response = await proxyFetch('/strategy-api/api/v1/strategies')
  if (!response.ok) throw await parseErrorResponse(response)
  const data: BackendStrategyListResponse[] = await response.json()
  return data.map(mapStrategyInfo)
}

export async function getStrategyInfo(type: StrategyType): Promise<StrategyInfo & { education: StrategyEducation }> {
  const response = await proxyFetch(`/strategy-api/api/v1/strategies/${type}`)
  if (!response.ok) throw await parseErrorResponse(response)
  const raw = await response.json()
  return {
    ...mapStrategyInfo({
      name: raw.name,
      displayName: raw.displayName,
      description: raw.description,
      outlook: raw.outlook,
      riskProfile: raw.riskProfile,
      legCount: raw.legCount,
    }),
    education: raw.education,
  }
}

export async function calculateStrategy(
  strategyType: StrategyType,
  underlying: string,
  spotPrice: number,
  legs: Leg[]
): Promise<CalculationResult> {
  // Map frontend Leg to backend LegRequest format
  const backendLegs = legs.map(l => ({
    action: l.action,
    optionType: l.optionType,
    strike: l.strike,
    expiry: l.expiry,
    quantity: l.quantity ?? 1,
    bid: l.bid ?? l.price ?? 0,
    ask: l.ask ?? l.price ?? 0,
    mid: l.mid ?? l.price ?? 0,
    delta: l.delta ?? 0,
    symbol: underlying,
  }))

  const response = await proxyFetch('/strategy-api/api/v1/strategies/calculate', {
    method: 'POST',
    body: JSON.stringify({ strategyType, spotPrice, legs: backendLegs }),
  })
  if (!response.ok) throw await parseErrorResponse(response)
  const data: BackendCalculateResponse = await response.json()
  return mapCalculationResult(data)
}

export async function suggestStrategy(
  outlook: string,
  underlying: string
): Promise<StrategyInfo[]> {
  // POST with JSON body per @PostMapping("/suggest") + @RequestBody SuggestRequest
  // (StrategyController.kt:87, docs/reference/api-endpoints.md:637). A GET falls
  // into GET /{name} and returns 400 "Invalid strategy name: suggest".
  const response = await proxyFetch('/strategy-api/api/v1/strategies/suggest', {
    method: 'POST',
    body: JSON.stringify({ outlook, underlying }),
  })
  if (!response.ok) throw await parseErrorResponse(response)
  const data: BackendStrategyListResponse[] = await response.json()
  return data.map(mapStrategyInfo)
}

export async function submitOptionsOrder(order: OptionsOrderRequest): Promise<OptionsOrderResponse> {
  const response = await proxyFetch('/strategy-api/api/v1/options/orders', {
    method: 'POST',
    body: JSON.stringify(order),
  })
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getOptionsOrders(): Promise<OptionsOrderResponse[]> {
  const response = await proxyFetch('/strategy-api/api/v1/options/orders')
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getWheelConfig(wheelAccountId: number): Promise<WheelConfig> {
  const response = await proxyFetch(`/strategy-api/api/v1/wheel/config?wheelAccountId=${wheelAccountId}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function generateWheelRecommendations(wheelAccountId: number): Promise<WheelRecommendation[]> {
  const response = await proxyFetch(`/strategy-api/api/v1/wheel/recommendations`, {
    method: 'POST',
    body: JSON.stringify({ wheelAccountId }),
  })
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}
