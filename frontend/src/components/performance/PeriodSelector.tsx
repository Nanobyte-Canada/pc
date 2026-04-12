import type { PerformancePeriod } from '../../types/performance'

interface PeriodSelectorProps {
  selected: PerformancePeriod
  onSelect: (period: PerformancePeriod) => void
}

const periods: { key: PerformancePeriod; label: string }[] = [
  { key: '1M', label: '1M' },
  { key: '3M', label: '3M' },
  { key: '6M', label: '6M' },
  { key: 'YTD', label: 'YTD' },
  { key: '1Y', label: '1Y' },
  { key: 'ALL', label: 'ALL' }
]

export function PeriodSelector({ selected, onSelect }: PeriodSelectorProps) {
  return (
    <div className="period-selector">
      {periods.map(p => (
        <button
          key={p.key}
          className={`period-btn ${selected === p.key ? 'active' : ''}`}
          onClick={() => onSelect(p.key)}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}
