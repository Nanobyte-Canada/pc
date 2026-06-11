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

export async function getOptionsChainForExpiry(
  underlying: string,
  expiry: string,
  opts?: { strikesPerSide?: number; side?: 'put' | 'call' }
): Promise<OptionsChain> {
  const params = new URLSearchParams()
  if (opts?.strikesPerSide) params.set('strikesPerSide', String(opts.strikesPerSide))
  if (opts?.side) params.set('side', opts.side)
  const qs = params.toString()
  const url = `/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}/expiry/${encodeURIComponent(expiry)}${qs ? '?' + qs : ''}`
  const response = await proxyFetch(url)
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
