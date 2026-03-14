interface BenchmarkSelectorProps {
  selected: string | undefined
  onSelect: (benchmark: string | undefined) => void
}

const benchmarks = [
  { value: undefined, label: 'None' },
  { value: 'SPY', label: 'S&P 500 (SPY)' },
  { value: 'XIU', label: 'TSX Composite (XIU)' }
]

export function BenchmarkSelector({ selected, onSelect }: BenchmarkSelectorProps) {
  return (
    <div className="benchmark-selector">
      <label className="benchmark-label">Benchmark:</label>
      <select
        className="benchmark-select"
        value={selected || ''}
        onChange={e => onSelect(e.target.value || undefined)}
      >
        {benchmarks.map(b => (
          <option key={b.label} value={b.value || ''}>{b.label}</option>
        ))}
      </select>
    </div>
  )
}
