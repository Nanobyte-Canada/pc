import { useState } from 'react'
import { usePerformanceChart } from '../../hooks/usePerformance'
import { PeriodSelector } from './PeriodSelector'
import { BenchmarkSelector } from './BenchmarkSelector'
import { RiskMetricsCards } from './RiskMetricsCards'
import { CumulativeReturnChart } from './CumulativeReturnChart'
import { DrawdownChart } from './DrawdownChart'
import type { PerformancePeriod } from '../../types/performance'
import { formatCurrency } from '../../services/brokerService'

interface PerformanceTabProps {
  groupId: number
}

export function PerformanceTab({ groupId }: PerformanceTabProps) {
  const [period, setPeriod] = useState<PerformancePeriod>('1Y')
  const [benchmark, setBenchmark] = useState<string | undefined>(undefined)

  const { data: chartData, isLoading } = usePerformanceChart(groupId, period, benchmark)

  if (isLoading) {
    return <p className="text-muted">Loading performance data...</p>
  }

  if (!chartData) {
    return (
      <div className="empty-trades">
        <p>No performance data available. Portfolio snapshots are needed to calculate returns.</p>
      </div>
    )
  }

  return (
    <div className="performance-tab">
      <div className="performance-controls">
        <PeriodSelector selected={period} onSelect={setPeriod} />
        <BenchmarkSelector selected={benchmark} onSelect={setBenchmark} />
      </div>

      <div className="performance-value-summary">
        <span>
          Starting: <strong>{formatCurrency(chartData.summary.startingValue)}</strong>
        </span>
        <span>
          Ending: <strong>{formatCurrency(chartData.summary.endingValue)}</strong>
        </span>
        {chartData.benchmarkComparison && (
          <span>
            Alpha: <strong style={{
              color: chartData.benchmarkComparison.alpha >= 0 ? '#059669' : '#dc2626'
            }}>
              {chartData.benchmarkComparison.alpha >= 0 ? '+' : ''}{chartData.benchmarkComparison.alpha.toFixed(2)}%
            </strong>
          </span>
        )}
      </div>

      <RiskMetricsCards summary={chartData.summary} />

      <CumulativeReturnChart
        portfolioReturns={chartData.cumulativeReturns}
        benchmarkComparison={chartData.benchmarkComparison}
      />

      <DrawdownChart drawdowns={chartData.drawdowns} />
    </div>
  )
}
