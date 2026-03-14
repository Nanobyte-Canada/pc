import { AgCharts } from 'ag-charts-react'
import type { ValuePoint, PerformanceKpis } from '../../types/broker'
import { KpiCard } from './KpiCard'

interface TotalValueChartProps {
  data: ValuePoint[]
  kpis: PerformanceKpis
}

export function TotalValueChart({ data, kpis }: TotalValueChartProps) {
  const options = {
    background: { fill: 'transparent' },
    data,
    series: [
      {
        type: 'line' as const,
        xKey: 'date',
        yKey: 'totalValue',
        yName: 'Total Value',
        stroke: '#0284c7',
        strokeWidth: 2,
        marker: { enabled: false }
      }
    ],
    axes: [
      {
        type: 'category' as const,
        position: 'bottom' as const,
        label: { rotation: 45, fontSize: 10 },
      },
      {
        type: 'number' as const,
        position: 'left' as const,
        label: {
          formatter: ({ value }: { value: number }) =>
            `$${value >= 1000 ? `${(value / 1000).toFixed(0)}k` : value.toFixed(0)}`
        }
      }
    ],
    legend: { enabled: false }
  }

  return (
    <div className="reporting-chart-section">
      <div className="chart-main">
        <h3>Total Value</h3>
        <div className="chart-container">
          <AgCharts options={options} />
        </div>
      </div>
      <div className="chart-sidebar">
        <KpiCard
          label="Net Change"
          value={kpis.netChange}
          valueColor={kpis.netChange >= 0 ? '#059669' : '#dc2626'}
        />
      </div>
    </div>
  )
}
