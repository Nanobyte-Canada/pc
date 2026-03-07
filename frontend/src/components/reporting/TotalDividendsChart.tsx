import { AgCharts } from 'ag-charts-react'
import type { SymbolDividend } from '../../types/broker'

interface TotalDividendsChartProps {
  data: SymbolDividend[]
}

export function TotalDividendsChart({ data }: TotalDividendsChartProps) {
  const chartData = data.slice(0, 15) // Top 15 symbols

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar' as const,
        xKey: 'symbol',
        yKey: 'total',
        yName: 'Total Dividends',
        fill: '#7c3aed',
        direction: 'horizontal' as const
      }
    ],
    axes: [
      {
        type: 'category' as const,
        position: 'left' as const,
        label: { fontSize: 11 }
      },
      {
        type: 'number' as const,
        position: 'bottom' as const,
        label: {
          formatter: ({ value }: { value: number }) =>
            `$${value >= 1000 ? `${(value / 1000).toFixed(0)}k` : value.toFixed(0)}`
        }
      }
    ],
    legend: { enabled: false }
  }

  return (
    <div className="reporting-chart-section reporting-chart-full">
      <div className="chart-main">
        <h3>Total Dividends by Symbol</h3>
        <div className="chart-container chart-container-tall">
          <AgCharts options={options} />
        </div>
      </div>
    </div>
  )
}
