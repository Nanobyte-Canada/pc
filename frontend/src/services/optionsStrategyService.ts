import { proxyFetch, parseErrorResponse } from '@/services/api'
import type {
  StrategyInfo,
  StrategyType,
  CalculationResult,
  EducationContent,
  Leg,
  OptionsOrderRequest,
  OptionsOrderResponse,
  WheelConfig,
  WheelRecommendation,
} from '@/types/options'

export async function getStrategies(): Promise<StrategyInfo[]> {
  const response = await proxyFetch('/strategy-api/api/v1/strategies')
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getStrategyInfo(type: StrategyType): Promise<StrategyInfo & { education: EducationContent }> {
  const response = await proxyFetch(`/strategy-api/api/v1/strategies/${type}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function calculateStrategy(
  strategyType: StrategyType,
  underlying: string,
  spotPrice: number,
  legs: Leg[]
): Promise<CalculationResult> {
  const response = await proxyFetch('/strategy-api/api/v1/strategies/calculate', {
    method: 'POST',
    body: JSON.stringify({ strategyType, underlying, spotPrice, legs }),
  })
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function suggestStrategy(
  outlook: string,
  underlying: string
): Promise<StrategyInfo[]> {
  const response = await proxyFetch(`/strategy-api/api/v1/strategies/suggest?outlook=${outlook}&underlying=${encodeURIComponent(underlying)}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
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
