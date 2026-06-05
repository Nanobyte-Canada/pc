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
          Legs<span className="leg-builder__count">({legs.length})</span>
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
            <div key={i} className="leg-builder__card">
              <div className="leg-builder__card-badges">
                <span className={`leg-builder__badge leg-builder__badge--${leg.action.toLowerCase()}`}>
                  {leg.action}
                </span>
                <span className={`leg-builder__badge leg-builder__badge--${leg.optionType.toLowerCase()}`}>
                  {leg.optionType}
                </span>
              </div>

              <div className="leg-builder__card-fields">
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Strike</span>
                  <span className="leg-builder__card-value">${leg.strike.toFixed(0)}</span>
                </div>
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Mid</span>
                  <span className="leg-builder__card-value">${leg.price?.toFixed(2) ?? '-'}</span>
                </div>
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Expiry</span>
                  <span className="leg-builder__card-value">{leg.expiry}</span>
                </div>
              </div>

              <button className="leg-builder__remove" onClick={() => removeLeg(i)}>
                &times;
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
