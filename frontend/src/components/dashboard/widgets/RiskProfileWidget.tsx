import { useRiskProfile } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './RiskProfileWidget.css'

const LEVEL_LABELS: Record<string, string> = {
  LOW: 'Low Risk',
  MODERATE_LOW: 'Moderate Low',
  MODERATE: 'Moderate',
  MODERATE_HIGH: 'Moderate High',
  HIGH: 'High Risk',
}

/** Score-based color thresholds: low risk (0-35) = green, moderate (36-65) = amber, high (66-100) = red */
function getRiskColors(score: number): { badgeBg: string; badgeText: string; gaugeColor: string } {
  if (score <= 35) {
    // Green — low risk is good
    return { badgeBg: 'var(--green-light)', badgeText: 'var(--green-text)', gaugeColor: '#059669' }
  }
  if (score <= 65) {
    // Amber — moderate
    return { badgeBg: 'var(--yellow-light)', badgeText: 'var(--yellow-text)', gaugeColor: '#d97706' }
  }
  // Red — high risk is bad
  return { badgeBg: 'var(--red-light)', badgeText: 'var(--red-text)', gaugeColor: '#dc2626' }
}

const FACTOR_LABELS: Record<string, string> = {
  concentrationHHI: 'Concentration',
  top10Concentration: 'Top 10 Weight',
  sectorConcentrationHHI: 'Sector Conc.',
  geographicConcentration: 'Geo Conc.',
}

export default function RiskProfileWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useRiskProfile(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '8rem', width: '100%' }} />

  const { riskScore, riskLevel, factors } = data
  const config = {
    ...getRiskColors(riskScore),
    label: LEVEL_LABELS[riskLevel] || 'Moderate',
  }

  const radius = 45
  const strokeWidth = 10
  const circumference = Math.PI * radius
  const fillLength = (riskScore / 100) * circumference

  return (
    <div>
      <div className="rp-top">
        <div className="rp-gauge" style={{ width: 110, height: 60 }}>
          <svg width="110" height="60" viewBox="0 0 110 65">
            <path
              d="M 10 55 A 45 45 0 0 1 100 55"
              fill="none"
              className="rp-gauge-bg"
              strokeWidth={strokeWidth}
              strokeLinecap="round"
            />
            <path
              d="M 10 55 A 45 45 0 0 1 100 55"
              fill="none"
              stroke={config.gaugeColor}
              strokeWidth={strokeWidth}
              strokeLinecap="round"
              strokeDasharray={`${fillLength} ${circumference}`}
            />
          </svg>
          <div className="rp-gauge-score">
            <span>{riskScore}</span>
          </div>
        </div>
        <div>
          <span
            className="rp-risk-badge"
            style={{ backgroundColor: config.badgeBg, color: config.badgeText }}
          >
            {config.label}
          </span>
        </div>
      </div>

      <div className="rp-factors">
        {Object.entries(FACTOR_LABELS).map(([key, label]) => {
          const value = (factors as any)[key] as number
          const pct = Math.min(100, value * 100)
          return (
            <div key={key}>
              <div className="rp-factor-header">
                <span className="rp-factor-label">{label}</span>
                <span className="rp-factor-value">{(value * 100).toFixed(0)}%</span>
              </div>
              <div className="rp-factor-bar-bg">
                <div
                  className="rp-factor-bar-fill"
                  style={{ width: `${pct}%`, backgroundColor: config.gaugeColor }}
                />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
