import { AgCharts } from 'ag-charts-react'
import type { PeriodSummary, PerformanceKpis } from '../../types/broker'
import { KpiCard } from './KpiCard'

interface ContributionsChartProps {
  data: PeriodSummary[]
  kpis: PerformanceKpis
}

export function ContributionsChart({ data, kpis }: ContributionsChartProps) {
  const chartData = data.map(d => ({
    period: d.period,
    contributions: d.contributions,
    withdrawals: -d.withdrawals
  }))

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar' as const,
        xKey: 'period',
        yKey: 'contributions',
        yName: 'Contributions',
        fill: '#059669'
      },
      {
        type: 'bar' as const,
        xKey: 'period',
        yKey: 'withdrawals',
        yName: 'Withdrawals',
        fill: '#dc2626'
      }
    ],
    axes: [
      {
        type: 'category' as const,
        position: 'bottom' as const,
        label: { rotation: 45, fontSize: 10 }
      },
      {
        type: 'number' as const,
        position: 'left' as const,
        label: {
          formatter: ({ value }: { value: number }) =>
            `$${Math.abs(value) >= 1000 ? `${(value / 1000).toFixed(0)}k` : value.toFixed(0)}`
        }
      }
    ],
    legend: { enabled: true, position: 'bottom' as const }
  }

  return (
    <div className="reporting-chart-section">
      <div className="chart-main">
        <h3>Contributions & Withdrawals</h3>
        <div className="chart-container">
          <AgCharts options={options} />
        </div>
      </div>
      <div className="chart-sidebar">
        <KpiCard label="Net Contributions" value={kpis.netContributions} />
        <KpiCard label="Monthly Average" value={kpis.monthlyAvgContributions} />
      </div>
    </div>
  )
}
