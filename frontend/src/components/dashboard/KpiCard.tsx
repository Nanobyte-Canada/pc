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

interface KpiCardProps {
  label: string
  icon?: ReactNode
  value: string
  breakdown?: BreakdownItem[]
  sectors?: SectorItem[]
  variant?: 'default' | 'emerald' | 'returns'
}

export function KpiCard({ label, icon, value, breakdown, sectors, variant = 'default' }: KpiCardProps) {
  const cardClass = [
    'kpi-card',
    variant === 'emerald' && 'kpi-card--emerald',
  ].filter(Boolean).join(' ')

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
                  <span className="kpi-card__sector-pct">{sector.weight.toFixed(1)}%</span>
                </div>
                <div className="kpi-card__sector-bar-track">
                  <div
                    className="kpi-card__sector-bar-fill"
                    style={{ width: `${Math.min(sector.weight, 100)}%`, background: sector.color }}
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
