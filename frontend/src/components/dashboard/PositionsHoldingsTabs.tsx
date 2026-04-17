import { Suspense, lazy } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import './PositionsHoldingsTabs.css'

const PositionsTableWidget = lazy(() => import('./widgets/PositionsTableWidget'))

interface PositionsHoldingsTabsProps {
  connectionId?: number
}

export function PositionsHoldingsTabs({ connectionId }: PositionsHoldingsTabsProps) {
  return (
    <div className="ph-tabs-card">
      <div className="ph-tabs-content">
        <Suspense fallback={<Skeleton style={{ height: '12rem', width: '100%' }} />}>
          <PositionsTableWidget connectionId={connectionId} />
        </Suspense>
      </div>
    </div>
  )
}
