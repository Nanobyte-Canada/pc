import { useStrategyStore } from '@/stores/strategyStore'
import type { StrategyInfo } from '@/types/options'
import './StrategySelector.css'

interface StrategySelectorProps {
  strategies: StrategyInfo[]
}

/** Convert raw enum-style names like "BULL_CALL_SPREAD" to "Bull Call Spread" */
function formatStrategyName(name: string): string {
  if (!name.includes('_') && name !== name.toUpperCase()) return name
  return name
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

export function StrategySelector({ strategies }: StrategySelectorProps) {
  const { selectedStrategy, setSelectedStrategy } = useStrategyStore()

  return (
    <>
      {/* Desktop: pill buttons */}
      <div className="strategy-selector">
        {strategies.map((s) => (
          <button
            key={s.type}
            className={`strategy-selector__pill ${selectedStrategy === s.type ? 'strategy-selector__pill--active' : ''}`}
            onClick={() => setSelectedStrategy(selectedStrategy === s.type ? null : s.type)}
            title={s.description}
          >
            {formatStrategyName(s.name)}
          </button>
        ))}
      </div>

      {/* Mobile: dropdown */}
      <div className="strategy-selector__mobile">
        <select
          className="strategy-selector__dropdown"
          value={selectedStrategy ?? ''}
          onChange={(e) => setSelectedStrategy(e.target.value ? (e.target.value as StrategyInfo['type']) : null)}
        >
          <option value="">Strategy...</option>
          {strategies.map((s) => (
            <option key={s.type} value={s.type}>
              {formatStrategyName(s.name)}
            </option>
          ))}
        </select>
      </div>
    </>
  )
}
