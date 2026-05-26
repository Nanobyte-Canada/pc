import { useSectorExposure } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { PieChart } from 'lucide-react'
import './SectorExposureWidget.css'

const EMERALD_SHADES = ['#10b981', '#059669', '#34d399', '#6ee7b7', '#a7f3d0']

export default function SectorExposureWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useSectorExposure(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '20rem', width: '100%' }} />

  if (data.sectors.length === 0) {
    return (
      <div className="se-empty">
        <PieChart style={{ height: '2.5rem', width: '2.5rem' }} />
        <span>No sector data available</span>
      </div>
    )
  }

  const sectorData = data.sectors.map((s, i) => ({
    label: s.sectorName,
    value: Number((s.weight * 100).toFixed(1)),
    color: EMERALD_SHADES[i % EMERALD_SHADES.length],
  }))

  return (
    <div className="se-bars">
      {sectorData.map(s => (
        <div key={s.label} className="se-bar-row">
          <div className="se-bar-header">
            <span className="se-bar-label">{s.label}</span>
            <span className="se-bar-pct">{s.value}%</span>
          </div>
          <div className="se-bar-track">
            <div
              className="se-bar-fill"
              style={{ width: `${s.value}%`, backgroundColor: s.color }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}
