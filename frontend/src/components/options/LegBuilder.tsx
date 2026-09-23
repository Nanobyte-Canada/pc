import { useStrategyStore } from '@/stores/strategyStore'
import type { Leg } from '@/types/options'
import './LegBuilder.css'

interface LegBuilderProps {
  onCalculate: () => void
  isCalculating: boolean
  liveMid?: (leg: Leg) => number | undefined
}

export function LegBuilder({ onCalculate, isCalculating, liveMid }: LegBuilderProps) {
  const { legs, removeLeg, updateLeg, clearStrategy } = useStrategyStore()

  const adjustQuantity = (index: number, delta: number) => {
    const current = legs[index]
    const next = Math.max(1, current.quantity + delta)
    if (next === current.quantity) return
    updateLeg(index, { ...current, quantity: next })
  }

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
                  <span className="leg-builder__card-label">Qty</span>
                  <span className="leg-builder__card-value">
                    <button
                      className="leg-builder__qty-btn"
                      aria-label={`Decrease quantity for leg ${i + 1}`}
                      onClick={() => adjustQuantity(i, -1)}
                    >
                      &minus;
                    </button>
                    <span className="leg-builder__qty-value">{leg.quantity}</span>
                    <button
                      className="leg-builder__qty-btn"
                      aria-label={`Increase quantity for leg ${i + 1}`}
                      onClick={() => adjustQuantity(i, 1)}
                    >
                      +
                    </button>
                  </span>
                </div>
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Mid</span>
                  <span className="leg-builder__card-value">
                    ${(liveMid?.(leg) ?? leg.price)?.toFixed(2) ?? '-'}
                  </span>
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
