import { useStrategyStore } from '@/stores/strategyStore'
import './LegBuilder.css'

interface LegBuilderProps {
  onCalculate: () => void
  isCalculating: boolean
}

export function LegBuilder({ onCalculate, isCalculating }: LegBuilderProps) {
  const { legs, removeLeg, clearStrategy } = useStrategyStore()

  return (
    <div className="leg-builder">
      <div className="leg-builder__header">
        <span className="leg-builder__title">
          Legs ({legs.length})
        </span>
        {legs.length > 0 && (
          <button className="leg-builder__clear" onClick={clearStrategy}>
            Clear All
          </button>
        )}
      </div>

      {legs.length === 0 ? (
        <div className="leg-builder__empty">
          Click on bid/ask in the options chain to add legs
        </div>
      ) : (
        <div className="leg-builder__list">
          {legs.map((leg, i) => (
            <div key={i} className="leg-builder__row">
              <span className={`leg-builder__action leg-builder__action--${leg.action.toLowerCase()}`}>
                {leg.action}
              </span>
              <span>{leg.optionType}</span>
              <span>${leg.strike.toFixed(0)}</span>
              <span>{leg.expiry}</span>
              <span>${leg.price?.toFixed(2) ?? '-'}</span>
              <button className="leg-builder__remove" onClick={() => removeLeg(i)}>
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      <button
        className="leg-builder__calculate"
        onClick={onCalculate}
        disabled={legs.length === 0 || isCalculating}
      >
        {isCalculating ? 'Calculating...' : 'Calculate P&L'}
      </button>
    </div>
  )
}
