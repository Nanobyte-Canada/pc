import { useModelPortfolios } from '@/hooks/useModelPortfolios'

interface BenchmarkSelectorProps {
  selected: string | undefined
  onSelect: (benchmark: string | undefined) => void
}

const MARKET_BENCHMARKS = [
  { value: 'SPY', label: 'S&P 500 (SPY)' },
  { value: 'XIU', label: 'TSX Composite (XIU)' }
]

export function BenchmarkSelector({ selected, onSelect }: BenchmarkSelectorProps) {
  const { data: modelsData } = useModelPortfolios()
  const models = modelsData?.models ?? []

  return (
    <div className="benchmark-selector">
      <label className="benchmark-label">Benchmark:</label>
      <select
        className="benchmark-select"
        value={selected || ''}
        onChange={e => onSelect(e.target.value || undefined)}
      >
        <option value="">None</option>
        <optgroup label="Market Indices">
          {MARKET_BENCHMARKS.map(b => (
            <option key={b.value} value={b.value}>{b.label}</option>
          ))}
        </optgroup>
        {models.length > 0 && (
          <optgroup label="Model Portfolios">
            {models.map(m => (
              <option key={m.id} value={`MODEL:${m.id}`}>{m.name}</option>
            ))}
          </optgroup>
        )}
      </select>
    </div>
  )
}
