import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/services/api'

interface ExchangeRateResponse {
  currency: string
  rateToCAD: number
  date: string
}

export function useExchangeRate(currency: string) {
  return useQuery({
    queryKey: ['exchange-rate', currency],
    queryFn: async (): Promise<ExchangeRateResponse> => {
      const response = await apiFetch(`/api/v1/exchange-rates/rate/${currency}`)
      if (!response.ok) throw new Error('Failed to fetch exchange rate')
      return response.json()
    },
    staleTime: 3600_000,
  })
}
