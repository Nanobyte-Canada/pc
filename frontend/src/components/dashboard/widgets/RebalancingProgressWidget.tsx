import { useRebalanceProgress } from '@/hooks/useModelPortfolios'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp } from 'lucide-react'
import './RebalancingProgressWidget.css'

export default function RebalancingProgressWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useRebalanceProgress(connectionId ?? 0, !!connectionId)

  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (!connectionId || data.entries.length === 0) {
    return (
      <div className="rpw-empty">
        <TrendingUp style={{ height: '2rem', width: '2rem' }} />
        <span>No model portfolio applied</span>
      </div>
    )
  }

  return (
    <div>
      <div className="rpw-header">
        <span className="rpw-header-label">REBALANCING PROGRESS</span>
        <div className="rpw-legend">
          <div className="rpw-legend-item">
            <span className="rpw-legend-dot rpw-legend-dot-target"></span>
            <span className="rpw-legend-text">Target</span>
          </div>
          <div className="rpw-legend-item">
            <span className="rpw-legend-dot rpw-legend-dot-actual"></span>
            <span className="rpw-legend-text">Actual</span>
          </div>
        </div>
      </div>

      <div className="rpw-list">
        {data.entries.map(entry => {
          const abbrevName = entry.securityName
            ? entry.securityName.length > 25
              ? entry.securityName.substring(0, 25) + '...'
              : entry.securityName
            : null

          return (
            <div key={entry.symbol} className={`rpw-entry${entry.isNonModel ? ' rpw-entry-nonmodel' : ''}`}>
              <div className="rpw-entry-header">
                <div className="rpw-entry-info">
                  <span className="rpw-symbol">{entry.symbol}</span>
                  {abbrevName && <span className="rpw-name">{abbrevName}</span>}
                  {entry.isNonModel && <span className="rpw-sell-badge">SELL</span>}
                </div>
                <div className="rpw-entry-values">
                  <span className="rpw-value rpw-value-target">{entry.targetPercent.toFixed(1)}%</span>
                  <span className={`rpw-value ${entry.isNonModel ? 'rpw-value-sell' : 'rpw-value-actual'}`}>{entry.actualPercent.toFixed(1)}%</span>
                </div>
              </div>
              <div className="rpw-bars">
                <div className="rpw-bar-row">
                  <div className="rpw-bar rpw-bar-target" style={{ width: `${entry.targetPercent}%` }}></div>
                </div>
                <div className="rpw-bar-row">
                  <div className={`rpw-bar ${entry.isNonModel ? 'rpw-bar-sell' : 'rpw-bar-actual'}`} style={{ width: `${entry.actualPercent}%` }}></div>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
