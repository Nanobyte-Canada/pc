import { useSectorExposure } from '@/hooks/useDashboardWidgets'
import { useChartTheme } from '@/hooks/useChartTheme'
import { Skeleton } from '@/components/ui/skeleton'
import { AgCharts } from 'ag-charts-react'
import type { AgChartOptions } from 'ag-charts-community'
import { PieChart } from 'lucide-react'
import './SectorExposureWidget.css'

const SECTOR_COLORS = ['#546d84', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1', '#14b8a6']

export default function SectorExposureWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useSectorExposure(connectionId)
  const chartTheme = useChartTheme()
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
    color: SECTOR_COLORS[i % SECTOR_COLORS.length],
  }))

  const industryData = data.sectors.flatMap((s, si) =>
    s.industryGroups.map((ig, ii) => ({
      label: ig.name,
      value: Number((ig.weight * 100).toFixed(1)),
      color: SECTOR_COLORS[si % SECTOR_COLORS.length] + (ii === 0 ? '' : 'cc'),
    }))
  )

  const options: AgChartOptions = {
    theme: chartTheme.theme,
    height: 340,
    background: { fill: 'transparent' },
    series: [
      {
        type: 'donut',
        calloutLabelKey: 'label',
        angleKey: 'value',
        innerRadiusRatio: 0,
        outerRadiusRatio: 0.55,
        data: sectorData,
        fills: sectorData.map(d => d.color),
        tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
      },
      {
        type: 'donut',
        calloutLabelKey: 'label',
        angleKey: 'value',
        innerRadiusRatio: 0.6,
        outerRadiusRatio: 1,
        data: industryData,
        fills: industryData.map(d => d.color),
        tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
      },
    ] as any,
    legend: { enabled: false },
  }

  const top5 = sectorData.slice(0, 5)

  return (
    <div>
      <AgCharts options={options} />
      <div className="se-legend">
        {top5.map(s => (
          <div key={s.label} className="se-legend-item">
            <div className="se-legend-dot" style={{ backgroundColor: s.color }} />
            <span className="se-legend-label">{s.label}</span>
            <span className="se-legend-value">{s.value}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}
