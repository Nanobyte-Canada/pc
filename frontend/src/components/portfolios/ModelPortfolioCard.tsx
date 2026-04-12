import { Plus } from 'lucide-react'
import type { ModelPortfolioSummary, RiskLevel } from '@/types/modelPortfolio'
import './ModelPortfolioCard.css'

const RISK_CONFIG: Record<RiskLevel, { label: string; color: string; widthPercent: number; modifier: string }> = {
  LOW:        { label: 'Low Risk',   color: 'var(--success)', widthPercent: 25,  modifier: 'low' },
  MODERATE:   { label: 'Moderate',   color: 'var(--info)',    widthPercent: 50,  modifier: 'moderate' },
  HIGH:       { label: 'High Risk',  color: 'var(--warning)', widthPercent: 75,  modifier: 'high' },
  EXTRA_HIGH: { label: 'Aggressive', color: 'var(--error)',   widthPercent: 100, modifier: 'extra-high' },
}

interface Props {
  model: ModelPortfolioSummary | null
  isSelected: boolean
  onClick: () => void
}

export function ModelPortfolioCard({ model, isSelected, onClick }: Props) {
  // Custom slot card
  if (!model) {
    return (
      <div
        className={`portfolio-card portfolio-card--custom${isSelected ? ' portfolio-card--selected' : ''}`}
        onClick={onClick}
        role="button"
        tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') onClick() }}
      >
        <div className="portfolio-card__custom-icon">
          <Plus size={20} />
        </div>
        <span className="portfolio-card__custom-label">Build Your Own</span>
      </div>
    )
  }

  // System/regular model card
  const risk = RISK_CONFIG[model.riskLevel]

  return (
    <div
      className={`portfolio-card portfolio-card--${risk.modifier}${isSelected ? ` portfolio-card--selected portfolio-card--${risk.modifier}` : ''}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') onClick() }}
    >
      <h3 className="portfolio-card__name">{model.name}</h3>

      <span className={`portfolio-card__risk-label portfolio-card__risk-label--${risk.modifier}`}>
        {risk.label}
      </span>

      {model.description && (
        <p className="portfolio-card__description">{model.description}</p>
      )}

      <div className="portfolio-card__stats">
        <div>
          <span className="portfolio-card__stat-value">{model.allocationCount}</span>
          <span className="portfolio-card__stat-label"> Holdings</span>
        </div>
        <div>
          <span className="portfolio-card__stat-value">{model.totalPercent.toFixed(0)}%</span>
          <span className="portfolio-card__stat-label"> Allocated</span>
        </div>
      </div>

      <div className="portfolio-card__risk-bar">
        <div
          className="portfolio-card__risk-fill"
          style={{
            width: `${risk.widthPercent}%`,
            backgroundColor: risk.color,
          }}
        />
      </div>
    </div>
  )
}
