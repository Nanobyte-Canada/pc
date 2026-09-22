import { useCallback } from 'react'
import { useStrategyStore } from '@/stores/strategyStore'
import { getStrategyInfo } from '@/services/optionsStrategyService'
import type { StrategyInfo } from '@/types/options'
import './StrategySelector.css'

interface StrategySelectorProps {
  strategies: StrategyInfo[]
}

function formatStrategyName(name: string): string {
  if (!name.includes('_') && name !== name.toUpperCase()) return name
  return name
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

function outlookBadgeClass(outlook: string): string {
  const lower = outlook.toLowerCase()
  if (lower.includes('bullish')) return 'strategy-card__outlook--bullish'
  if (lower.includes('bearish')) return 'strategy-card__outlook--bearish'
  return 'strategy-card__outlook--neutral'
}

function riskBadgeClass(risk: string): string {
  const lower = risk.toLowerCase()
  if (lower.includes('high')) return 'strategy-card__risk--high'
  if (lower.includes('limited')) return 'strategy-card__risk--low'
  return 'strategy-card__risk--medium'
}

export function StrategySelector({ strategies }: StrategySelectorProps) {
  const { selectedStrategy, setSelectedStrategy, setSelectedStrategyEducation } = useStrategyStore()

  const handleSelect = useCallback(async (strategy: StrategyInfo) => {
    const isAlreadySelected = selectedStrategy === strategy.type
    if (isAlreadySelected) {
      setSelectedStrategy(null)
      setSelectedStrategyEducation(null)
      return
    }

    setSelectedStrategy(strategy.type)

    try {
      const data = await getStrategyInfo(strategy.type)
      setSelectedStrategyEducation(data.education)
    } catch {
      // Education fetch failed - card still shows basic info
    }
  }, [selectedStrategy, setSelectedStrategy, setSelectedStrategyEducation])

  return (
    <div className="strategy-selector">
      {strategies.map((s) => (
        <button
          key={s.type}
          className={`strategy-card ${selectedStrategy === s.type ? 'strategy-card--selected' : ''}`}
          onClick={() => handleSelect(s)}
          title={s.description}
        >
          <div className="strategy-card__top-row">
            <span className="strategy-card__name">{formatStrategyName(s.name)}</span>
            <span className={`strategy-card__outlook ${outlookBadgeClass(s.marketOutlook)}`}>
              {s.marketOutlook.split(' ')[0]}
            </span>
          </div>
          <div className="strategy-card__bottom-row">
            <span className={`strategy-card__risk ${riskBadgeClass(s.riskLevel)}`}>
              {s.riskLevel}
            </span>
            <span className="strategy-card__legs">{s.legs} legs</span>
          </div>
        </button>
      ))}
    </div>
  )
}
