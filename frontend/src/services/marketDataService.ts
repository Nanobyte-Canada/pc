import { proxyFetch, parseErrorResponse } from '@/services/api'
import type { Quote, OptionsChain, IvRankData } from '@/types/options'

export async function getQuote(symbol: string): Promise<Quote> {
  const response = await proxyFetch(`/market-data-api/api/v1/quotes/${encodeURIComponent(symbol)}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getOptionsChain(underlying: string): Promise<OptionsChain> {
  const response = await proxyFetch(`/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getOptionsChainWithGreeks(underlying: string): Promise<OptionsChain> {
  const response = await proxyFetch(`/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}/greeks`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getOptionsChainForExpiry(underlying: string, expiry: string): Promise<OptionsChain> {
  const response = await proxyFetch(`/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}/expiry/${encodeURIComponent(expiry)}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getOptionExpirations(underlying: string): Promise<{
  underlying: string
  spotPrice: number
  expirations: string[]
}> {
  const response = await proxyFetch(`/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}/expirations`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}

export async function getIvRank(ticker: string): Promise<IvRankData> {
  const response = await proxyFetch(`/market-data-api/api/v1/iv/${encodeURIComponent(ticker)}`)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}
