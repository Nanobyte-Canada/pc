import type { ReactNode } from 'react'
import './KpiCard.css'

interface BreakdownItem {
  label: string
  value: string
  variant?: 'positive' | 'negative' | 'neutral'
}

interface SectorItem {
  name: string
  weight: number
  color: string
}

interface CurrencyValue {
  cad: string
  usd?: string
}

interface KpiCardProps {
  label: string
  icon?: ReactNode
  value: string
  breakdown?: BreakdownItem[]
  sectors?: SectorItem[]
  variant?: 'default' | 'emerald' | 'returns' | 'combined'
  /** Combined card data — Total Value / Investment / Cash */
  combined?: {
    totalValue: CurrencyValue
    investment: CurrencyValue
    cash: CurrencyValue
  }
}

export function KpiCard({ label, icon, value, breakdown, sectors, variant = 'default', combined }: KpiCardProps) {
  const cardClass = [
    'kpi-card',
    variant === 'emerald' && 'kpi-card--emerald',
    variant === 'combined' && 'kpi-card--combined',
  ].filter(Boolean).join(' ')

  /* Combined variant: Total Value / Investment / Cash */
  if (variant === 'combined' && combined) {
    return (
      <div className={cardClass}>
        <div className="kpi-card__header">
          <span className="kpi-card__label">Total Value</span>
          {icon && <span className="kpi-card__icon">{icon}</span>}
        </div>
        <div className="kpi-card__combined-primary">
          <span className="kpi-card__combined-cad">{combined.totalValue.cad}</span>
          {combined.totalValue.usd && (
            <span className="kpi-card__combined-usd">{combined.totalValue.usd}</span>
          )}
        </div>
        <div className="kpi-card__divider" />
        <div className="kpi-card__combined-columns">
          <div className="kpi-card__combined-col">
            <span className="kpi-card__combined-col-label">Investment</span>
            <span className="kpi-card__combined-col-cad">{combined.investment.cad}</span>
            {combined.investment.usd && (
              <span className="kpi-card__combined-col-usd">{combined.investment.usd}</span>
            )}
          </div>
          <div className="kpi-card__combined-col">
            <span className="kpi-card__combined-col-label">Cash</span>
            <span className="kpi-card__combined-col-cad">{combined.cash.cad}</span>
            {combined.cash.usd && (
              <span className="kpi-card__combined-col-usd">{combined.cash.usd}</span>
            )}
          </div>
        </div>
      </div>
    )
  }

  const valueClass = [
    'kpi-card__value',
    variant === 'returns' && value.startsWith('+') && 'kpi-card__value--positive',
    variant === 'returns' && value.startsWith('-') && 'kpi-card__value--negative',
  ].filter(Boolean).join(' ')

  return (
    <div className={cardClass}>
      <div className="kpi-card__header">
        <span className="kpi-card__label">{label}</span>
        {icon && <span className="kpi-card__icon">{icon}</span>}
      </div>
      <div className={valueClass}>{value}</div>

      {/* Currency / Returns breakdown */}
      {breakdown && breakdown.length > 0 && (
        <>
          <div className="kpi-card__divider" />
          {breakdown.map((item, i) => (
            <div key={i} className="kpi-card__breakdown-row">
              <span className="kpi-card__breakdown-label">{item.label}</span>
              <span className={`kpi-card__breakdown-value ${
                item.variant === 'positive' ? 'kpi-card__breakdown-value--positive' :
                item.variant === 'negative' ? 'kpi-card__breakdown-value--negative' : ''
              }`}>
                {item.value}
              </span>
            </div>
          ))}
        </>
      )}

      {/* Sectors variant */}
      {sectors && sectors.length > 0 && (
        <>
          <div className="kpi-card__divider" />
          <div className="kpi-card__sectors">
            {sectors.map((sector, i) => (
              <div key={i} className="kpi-card__sector-row">
                <div className="kpi-card__sector-info">
                  <span className="kpi-card__sector-name">{sector.name}</span>
                  <span className="kpi-card__sector-pct">{(sector.weight * 100).toFixed(1)}%</span>
                </div>
                <div className="kpi-card__sector-bar-track">
                  <div
                    className="kpi-card__sector-bar-fill"
                    style={{ width: `${Math.min(sector.weight * 100, 100)}%`, background: sector.color }}
                  />
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
