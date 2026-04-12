import { Suspense, useState, lazy } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import './PositionsHoldingsTabs.css'

const PositionsTableWidget = lazy(() => import('./widgets/PositionsTableWidget'))
const HoldingsTableWidget = lazy(() => import('./widgets/HoldingsTableWidget'))

type Tab = 'positions' | 'holdings'

interface PositionsHoldingsTabsProps {
  connectionId?: number
}

export function PositionsHoldingsTabs({ connectionId }: PositionsHoldingsTabsProps) {
  const [activeTab, setActiveTab] = useState<Tab>('positions')

  return (
    <div className="ph-tabs-card">
      <div className="ph-tabs-bar">
        <button
          className={`ph-tab ${activeTab === 'positions' ? 'ph-tab-active' : ''}`}
          onClick={() => setActiveTab('positions')}
        >
          Positions
        </button>
        <button
          className={`ph-tab ${activeTab === 'holdings' ? 'ph-tab-active' : ''}`}
          onClick={() => setActiveTab('holdings')}
        >
          Holdings
        </button>
      </div>
      <div className="ph-tabs-content">
        <Suspense fallback={<Skeleton style={{ height: '12rem', width: '100%' }} />}>
          {activeTab === 'positions' ? (
            <PositionsTableWidget connectionId={connectionId} />
          ) : (
            <HoldingsTableWidget connectionId={connectionId} />
          )}
        </Suspense>
      </div>
    </div>
  )
}
