import { useMemo } from 'react'
import { useQueries } from '@tanstack/react-query'
import { getConnectionActivities } from '@/services/brokerService'
import type { BrokerActivityDto } from '@/types/broker'

export interface PremiumInfo {
  premium: number
  currency: string
}

export function useWheelActivities(connectionIds: number[]) {
  const activityQueries = useQueries({
    queries: connectionIds.map(connId => ({
      queryKey: ['wheel-activities', connId],
      queryFn: () => getConnectionActivities(connId, { size: 200, type: 'SELL' }),
      staleTime: 300_000,
      enabled: connectionIds.length > 0,
    })),
  })

  const premiumMap = useMemo(() => {
    const map = new Map<string, PremiumInfo>()
    activityQueries.forEach(query => {
      if (!query.data) return
      query.data.activities.forEach((act: BrokerActivityDto) => {
        if (!act.symbol || act.price == null) return
        map.set(act.symbol, {
          premium: Math.abs(act.price) * 100,
          currency: act.currency,
        })
      })
    })
    return map
  }, [activityQueries])

  const isLoading = activityQueries.some(q => q.isLoading)

  return { premiumMap, isLoading }
}
