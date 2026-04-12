import { useState } from 'react'
import { useModelAnalysis, useModelPortfolio } from '@/hooks/useModelPortfolios'
import { ApplyToAccountModal } from './ApplyToAccountModal'
import { Button } from '@/components/ui/button'
import { PieChart, Globe } from 'lucide-react'
import type { ExposureEntry, ModelAllocation } from '@/types/modelPortfolio'
import './ModelAnalysisPanel.css'

const DONUT_COLORS = [
  '#546d84', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1',
  '#14b8a6', '#a855f7', '#e11d48', '#0ea5e9', '#eab308',
]

function getRiskColor(score: number): string {
  if (score < 30) return '#22c55e'
  if (score <= 60) return '#f59e0b'
  return '#ef4444'
}

function getRiskDescription(level: string): string {
  const l = level.toUpperCase()
  if (l === 'LOW') return 'This portfolio has a conservative risk profile with lower volatility expectations.'
  if (l === 'MODERATE' || l === 'MODERATE_LOW') return 'This portfolio balances growth and stability with moderate risk exposure.'
  if (l === 'HIGH' || l === 'MODERATE_HIGH') return 'This portfolio targets higher growth with elevated volatility.'
  return 'This portfolio carries significant risk with potential for large swings.'
}

function formatRiskLabel(level: string): string {
  const l = level.toUpperCase()
  if (l === 'LOW') return 'Low Risk'
  if (l === 'MODERATE_LOW') return 'Moderate Low'
  if (l === 'MODERATE') return 'Moderate'
  if (l === 'MODERATE_HIGH') return 'Moderate High'
  if (l === 'HIGH') return 'High Risk'
  if (l === 'EXTRA_HIGH') return 'Aggressive'
  return level
}

/** Build a CSS conic-gradient string from exposure entries */
function buildConicGradient(entries: ExposureEntry[]): string {
  if (entries.length === 0) return 'none'

  const segments: string[] = []
  let cumulative = 0

  entries.forEach((entry, i) => {
    const color = DONUT_COLORS[i % DONUT_COLORS.length]
    const start = cumulative
    cumulative += entry.percentage
    segments.push(`${color} ${start}% ${cumulative}%`)
  })

  // Fill remainder with muted if total < 100
  if (cumulative < 100) {
    segments.push(`var(--muted-bg, #18181b) ${cumulative}% 100%`)
  }

  return `conic-gradient(${segments.join(', ')})`
}

// ---------- Sub-components ----------

function ExposureWidget({
  title,
  entries,
  emptyIcon,
  emptyText,
}: {
  title: string
  entries: ExposureEntry[]
  emptyIcon: React.ReactNode
  emptyText: string
}) {
  if (!entries || entries.length === 0) {
    return (
      <div className="map-widget">
        <div className="map-widget__title">{title}</div>
        <div className="map-exposure__empty">
          {emptyIcon}
          <span>{emptyText}</span>
        </div>
      </div>
    )
  }

  const gradient = buildConicGradient(entries)
  const total = entries.reduce((sum, e) => sum + e.percentage, 0)

  return (
    <div className="map-widget">
      <div className="map-widget__title">{title}</div>
      <div className="map-exposure__chart-row">
        <div className="map-exposure__donut-wrapper" style={{ background: gradient }}>
          <div className="map-exposure__donut-center">
            <span
              style={{
                background: 'var(--bg-secondary, #0c0c0e)',
                width: '60%',
                height: '60%',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {total.toFixed(0)}%
            </span>
          </div>
        </div>
        <div className="map-exposure__legend">
          {entries.map((entry, i) => (
            <div key={entry.name} className="map-exposure__legend-item">
              <div
                className="map-exposure__legend-dot"
                style={{ backgroundColor: DONUT_COLORS[i % DONUT_COLORS.length] }}
              />
              <span className="map-exposure__legend-name">{entry.name}</span>
              <span className="map-exposure__legend-pct">{entry.percentage.toFixed(1)}%</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function RiskProfileWidget({ score, level }: { score: number; level: string }) {
  const color = getRiskColor(score)
  const label = formatRiskLabel(level)
  const description = getRiskDescription(level)

  return (
    <div className="map-widget">
      <div className="map-widget__title">Risk Profile</div>
      <div className="map-risk">
        <div className="map-risk__circle" style={{ borderColor: color }}>
          <span className="map-risk__score">{score}</span>
        </div>
        <div className="map-risk__info">
          <span className="map-risk__level" style={{ color }}>{label}</span>
          <span className="map-risk__desc">{description}</span>
          <div className="map-risk__bar-bg">
            <div
              className="map-risk__bar-fill"
              style={{ width: `${Math.min(100, score)}%`, backgroundColor: color }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

function HoldingsWidget({ holdings }: { holdings: ModelAllocation[] }) {
  return (
    <div className="map-widget">
      <div className="map-widget__title">Holdings</div>
      {holdings.length === 0 ? (
        <div className="map-holdings__empty">No holdings configured</div>
      ) : (
        <div className="map-holdings__scroll">
          <table className="map-holdings__table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Target %</th>
                <th>Asset Class</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map(h => (
                <tr key={h.id}>
                  <td className="map-holdings__symbol">{h.symbol}</td>
                  <td className="map-holdings__pct">{h.targetPercent.toFixed(1)}%</td>
                  <td className="map-holdings__asset-class">{h.assetClass ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div className="model-analysis-panel">
      <div className="map-loading">
        {[0, 1, 2, 3].map(i => (
          <div key={i} className="map-loading__card">
            <div className="map-loading__title" />
            <div className={`map-loading__block${i === 3 ? ' map-loading__block--tall' : ''}`} />
          </div>
        ))}
      </div>
    </div>
  )
}

// ---------- Main Component ----------

interface ModelAnalysisPanelProps {
  modelId: number
}

export function ModelAnalysisPanel({ modelId }: ModelAnalysisPanelProps) {
  const { data, isLoading, error } = useModelAnalysis(modelId)
  const { data: modelData } = useModelPortfolio(modelId)
  const [showApplyModal, setShowApplyModal] = useState(false)

  const modelName = modelData?.name ?? 'Portfolio'

  if (isLoading) {
    return <LoadingSkeleton />
  }

  if (error || !data) {
    return (
      <div className="model-analysis-panel">
        <p className="map-error">Unable to load analysis data. Please try again.</p>
        <div className="map-actions">
          <button className="map-apply-btn" onClick={() => setShowApplyModal(true)}>
            Apply to Account
          </button>
        </div>
        {showApplyModal && (
          <ApplyToAccountModal
            modelId={modelId}
            modelName={modelName}
            isOpen={showApplyModal}
            onClose={() => setShowApplyModal(false)}
          />
        )}
      </div>
    )
  }

  return (
    <div className="model-analysis-panel">
      <div className="map-grid">
        <ExposureWidget
          title="Sector & Industry Exposure"
          entries={data.sectorExposure}
          emptyIcon={<PieChart size={28} />}
          emptyText="No sector data available"
        />
        <ExposureWidget
          title="Geographic Exposure"
          entries={data.geographyExposure}
          emptyIcon={<Globe size={28} />}
          emptyText="No geography data available"
        />
        <RiskProfileWidget score={data.riskScore} level={data.riskLevel} />
        <HoldingsWidget holdings={data.holdings} />
      </div>

      <div className="map-actions">
        <Button onClick={() => setShowApplyModal(true)}>
          Apply to Account
        </Button>
      </div>

      {showApplyModal && (
        <ApplyToAccountModal
          modelId={modelId}
          modelName={modelName}
          isOpen={showApplyModal}
          onClose={() => setShowApplyModal(false)}
        />
      )}
    </div>
  )
}
