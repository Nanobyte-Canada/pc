import { AgCharts } from 'ag-charts-react'
import type { DividendPeriod, PerformanceKpis } from '../../types/broker'
import { KpiCard } from './KpiCard'

interface DividendHistoryChartProps {
  data: DividendPeriod[]
  kpis: PerformanceKpis
}

export function DividendHistoryChart({ data, kpis }: DividendHistoryChartProps) {
  // Collect all unique symbols across all periods
  const allSymbols = new Set<string>()
  data.forEach(d => Object.keys(d.bySymbol).forEach(s => allSymbols.add(s)))
  const symbols = Array.from(allSymbols).sort()

  // Build chart data with a column per symbol
  const chartData = data.map(d => {
    const row: Record<string, string | number> = { period: d.period }
    symbols.forEach(s => {
      row[s] = d.bySymbol[s] || 0
    })
    return row
  })

  const colors = ['#7c3aed', '#059669', '#0284c7', '#d97706', '#dc2626', '#16a34a', '#ea580c', '#e11d48']

  const series = symbols.slice(0, 8).map((symbol, i) => ({
    type: 'bar' as const,
    xKey: 'period',
    yKey: symbol,
    yName: symbol,
    stacked: true,
    fill: colors[i % colors.length]
  }))

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series,
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
            `$${value >= 1000 ? `${(value / 1000).toFixed(0)}k` : value.toFixed(0)}`
        }
      }
    ],
    legend: { enabled: true, position: 'bottom' as const }
  }

  return (
    <div className="reporting-chart-section">
      <div className="chart-main">
        <h3>Dividend History</h3>
        <div className="chart-container">
          <AgCharts options={options} />
        </div>
      </div>
      <div className="chart-sidebar">
        <KpiCard label="Total Dividend Income" value={kpis.totalDividendIncome} />
        <KpiCard label="Avg Monthly Dividends" value={kpis.avgMonthlyDividends} />
        <KpiCard label="Fees & Commissions" value={kpis.feesAndCommissions} />
      </div>
    </div>
  )
}
