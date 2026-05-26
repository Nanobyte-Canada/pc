import { useStrategyStore } from '@/stores/strategyStore'
import type { StrategyInfo } from '@/types/options'
import './StrategySelector.css'

interface StrategySelectorProps {
  strategies: StrategyInfo[]
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
            {s.name}
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
              {s.name}
            </option>
          ))}
        </select>
      </div>
    </>
  )
}
