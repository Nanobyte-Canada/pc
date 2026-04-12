import { useGeographyExposure } from '@/hooks/useDashboardWidgets'
import { useChartTheme } from '@/hooks/useChartTheme'
import { Skeleton } from '@/components/ui/skeleton'
import { AgCharts } from 'ag-charts-react'
import type { AgChartOptions } from 'ag-charts-community'
import { Globe } from 'lucide-react'
import './GeographyExposureWidget.css'

const REGION_COLORS = ['#546d84', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4']

export default function GeographyExposureWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useGeographyExposure(connectionId)
  const chartTheme = useChartTheme()
  if (isLoading || !data) return <Skeleton style={{ height: '20rem', width: '100%' }} />

  if (data.regions.length === 0) {
    return (
      <div className="ge-empty">
        <Globe style={{ height: '2.5rem', width: '2.5rem' }} />
        <span>No geography data available</span>
      </div>
    )
  }

  const regionData = data.regions.map((r, i) => ({
    label: r.name,
    value: Number((r.weight * 100).toFixed(1)),
    color: REGION_COLORS[i % REGION_COLORS.length],
  }))

  const countryData = data.regions.flatMap((r, ri) =>
    r.countries.map((c, ci) => ({
      label: c.name,
      value: Number((c.weight * 100).toFixed(1)),
      color: REGION_COLORS[ri % REGION_COLORS.length] + (ci === 0 ? '' : 'cc'),
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
        data: regionData,
        fills: regionData.map(d => d.color),
        tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
      },
      {
        type: 'donut',
        calloutLabelKey: 'label',
        angleKey: 'value',
        innerRadiusRatio: 0.6,
        outerRadiusRatio: 1,
        data: countryData,
        fills: countryData.map(d => d.color),
        tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
      },
    ] as any,
    legend: { enabled: false },
  }

  const top5 = regionData.slice(0, 5)

  return (
    <div>
      <AgCharts options={options} />
      <div className="ge-legend">
        {top5.map(r => (
          <div key={r.label} className="ge-legend-item">
            <div className="ge-legend-dot" style={{ backgroundColor: r.color }} />
            <span className="ge-legend-label">{r.label}</span>
            <span className="ge-legend-value">{r.value}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}
