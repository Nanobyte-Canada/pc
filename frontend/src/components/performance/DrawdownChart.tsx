import { AgCharts } from 'ag-charts-react'
import type { DrawdownPoint } from '../../types/performance'

interface DrawdownChartProps {
  drawdowns: DrawdownPoint[]
}

export function DrawdownChart({ drawdowns }: DrawdownChartProps) {
  if (drawdowns.length === 0) {
    return null
  }

  const data = drawdowns.map(d => ({
    date: d.date,
    drawdown: Number(d.drawdown)
  }))

  const options = {
    background: { fill: 'transparent' },
    data,
    series: [
      {
        type: 'area' as const,
        xKey: 'date',
        yKey: 'drawdown',
        yName: 'Drawdown',
        fill: 'rgba(220, 38, 38, 0.15)',
        stroke: '#dc2626',
        strokeWidth: 1.5,
        marker: { enabled: false }
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
          formatter: ({ value }: { value: number }) => `${value.toFixed(1)}%`
        }
      }
    ],
    legend: { enabled: false }
  }

  return (
    <div className="performance-chart-container">
      <h4>Drawdown</h4>
      <div style={{ height: 200 }}>
        <AgCharts options={options} />
      </div>
    </div>
  )
}
