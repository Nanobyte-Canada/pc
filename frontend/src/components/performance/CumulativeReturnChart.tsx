import { AgCharts } from 'ag-charts-react'
import type { ReturnPoint, BenchmarkComparison } from '../../types/performance'

interface CumulativeReturnChartProps {
  portfolioReturns: ReturnPoint[]
  benchmarkComparison: BenchmarkComparison | null
}

export function CumulativeReturnChart({ portfolioReturns, benchmarkComparison }: CumulativeReturnChartProps) {
  if (portfolioReturns.length === 0) {
    return <p className="text-muted">No performance data available. Snapshots are needed for charts.</p>
  }

  // Merge portfolio and benchmark data for chart
  const data = portfolioReturns.map(p => {
    const benchmarkPoint = benchmarkComparison?.benchmarkReturns.find(b => b.date === p.date)
    return {
      date: p.date,
      portfolio: Number(p.cumulativeReturn),
      benchmark: benchmarkPoint ? Number(benchmarkPoint.cumulativeReturn) : undefined
    }
  })

  const series = [
    {
      type: 'line' as const,
      xKey: 'date',
      yKey: 'portfolio',
      yName: 'Portfolio',
      stroke: '#0284c7',
      strokeWidth: 2,
      marker: { enabled: false }
    }
  ]

  if (benchmarkComparison && benchmarkComparison.benchmarkReturns.length > 0) {
    series.push({
      type: 'line' as const,
      xKey: 'date',
      yKey: 'benchmark',
      yName: 'Benchmark',
      stroke: '#9333ea',
      strokeWidth: 2,
      marker: { enabled: false }
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)
  }

  const options = {
    background: { fill: 'transparent' },
    data,
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
          formatter: ({ value }: { value: number }) => `${value.toFixed(1)}%`
        }
      }
    ],
    legend: {
      enabled: series.length > 1,
      position: 'bottom' as const
    }
  }

  return (
    <div className="performance-chart-container">
      <h4>Cumulative Returns</h4>
      <div style={{ height: 320 }}>
        <AgCharts options={options} />
      </div>
    </div>
  )
}
