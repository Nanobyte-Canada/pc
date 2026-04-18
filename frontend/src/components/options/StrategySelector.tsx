import { useStrategyStore } from '@/stores/strategyStore'
import type { StrategyInfo } from '@/types/options'
import './StrategySelector.css'

interface StrategySelectorProps {
  strategies: StrategyInfo[]
}

export function StrategySelector({ strategies }: StrategySelectorProps) {
  const { selectedStrategy, setSelectedStrategy } = useStrategyStore()

  return (
    <div className="strategy-selector">
      {strategies.map((s) => (
        <button
          key={s.type}
          className={`strategy-selector__button ${selectedStrategy === s.type ? 'strategy-selector__button--active' : ''}`}
          onClick={() => setSelectedStrategy(selectedStrategy === s.type ? null : s.type)}
          title={s.description}
        >
          {s.name}
        </button>
      ))}
    </div>
  )
}
