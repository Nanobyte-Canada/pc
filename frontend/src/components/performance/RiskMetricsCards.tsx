import type { PerformanceSummary } from '../../types/performance'

interface RiskMetricsCardsProps {
  summary: PerformanceSummary
}

export function RiskMetricsCards({ summary }: RiskMetricsCardsProps) {
  const metrics = [
    {
      label: 'Total Return',
      value: `${summary.totalReturn >= 0 ? '+' : ''}${summary.totalReturn.toFixed(2)}%`,
      color: summary.totalReturn >= 0 ? '#059669' : '#dc2626'
    },
    {
      label: 'TWR',
      value: `${summary.twr >= 0 ? '+' : ''}${summary.twr.toFixed(2)}%`,
      color: summary.twr >= 0 ? '#059669' : '#dc2626'
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      color: summary.sharpeRatio >= 1 ? '#059669' : summary.sharpeRatio >= 0 ? '#d97706' : '#dc2626'
    },
    {
      label: 'Sortino Ratio',
      value: summary.sortinoRatio.toFixed(2),
      color: summary.sortinoRatio >= 1 ? '#059669' : '#d97706'
    },
    {
      label: 'Max Drawdown',
      value: `-${summary.maxDrawdown.toFixed(2)}%`,
      color: summary.maxDrawdown < 10 ? '#059669' : summary.maxDrawdown < 20 ? '#d97706' : '#dc2626'
    },
    {
      label: 'Volatility',
      value: `${summary.volatility.toFixed(2)}%`,
      color: summary.volatility < 15 ? '#059669' : summary.volatility < 25 ? '#d97706' : '#dc2626'
    }
  ]

  return (
    <div className="risk-metrics-grid">
      {metrics.map(m => (
        <div key={m.label} className="risk-metric-card">
          <div className="risk-metric-label">{m.label}</div>
          <div className="risk-metric-value" style={{ color: m.color }}>{m.value}</div>
        </div>
      ))}
    </div>
  )
}
