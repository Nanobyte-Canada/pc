import type { PerformanceSummary } from '../../types/performance'

interface RiskMetricsCardsProps {
  summary: PerformanceSummary
}

export function RiskMetricsCards({ summary }: RiskMetricsCardsProps) {
  const metrics = [
    {
      label: 'Total Return',
      value: `${summary.totalReturn >= 0 ? '+' : ''}${summary.totalReturn.toFixed(2)}%`,
      color: summary.totalReturn >= 0 ? 'var(--success)' : 'var(--error)'
    },
    {
      label: 'TWR',
      value: `${summary.twr >= 0 ? '+' : ''}${summary.twr.toFixed(2)}%`,
      color: summary.twr >= 0 ? 'var(--success)' : 'var(--error)'
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      color: summary.sharpeRatio >= 1 ? 'var(--success)' : summary.sharpeRatio >= 0 ? 'var(--warning)' : 'var(--error)'
    },
    {
      label: 'Sortino Ratio',
      value: summary.sortinoRatio.toFixed(2),
      color: summary.sortinoRatio >= 1 ? 'var(--success)' : 'var(--warning)'
    },
    {
      label: 'Max Drawdown',
      value: `-${summary.maxDrawdown.toFixed(2)}%`,
      color: summary.maxDrawdown < 10 ? 'var(--success)' : summary.maxDrawdown < 20 ? 'var(--warning)' : 'var(--error)'
    },
    {
      label: 'Volatility',
      value: `${summary.volatility.toFixed(2)}%`,
      color: summary.volatility < 15 ? 'var(--success)' : summary.volatility < 25 ? 'var(--warning)' : 'var(--error)'
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
