import type { CalculationResult } from '@/types/options'
import './PnlChart.css'

interface PnlChartProps {
  result: CalculationResult
  warnings?: string[]
}

export function PnlChart({ result, warnings }: PnlChartProps) {
  const { pnlCurve, maxProfit, maxLoss, breakEvens, netDebit } = result

  const prices = pnlCurve.map((p) => p.spotPrice)
  const pnls = pnlCurve.map((p) => p.pnl)
  const minX = Math.min(...prices)
  const maxX = Math.max(...prices)
  const minY = Math.min(...pnls)
  const maxY = Math.max(...pnls)
  const yRange = maxY - minY || 1

  const width = 600
  const height = 200
  const pad = { top: 10, right: 10, bottom: 20, left: 50 }
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom

  const toX = (v: number) => pad.left + ((v - minX) / (maxX - minX)) * plotW
  const toY = (v: number) => pad.top + plotH - ((v - minY) / yRange) * plotH

  const zeroY = toY(0)
  const pathD = pnlCurve.map((p, i) => `${i === 0 ? 'M' : 'L'}${toX(p.spotPrice).toFixed(1)},${toY(p.pnl).toFixed(1)}`).join(' ')

  // Build fill areas: green above zero, red below zero
  const fillAbove = pnlCurve
    .filter((p) => p.pnl >= 0)
    .map((p) => `${toX(p.spotPrice).toFixed(1)},${toY(p.pnl).toFixed(1)}`)
  const fillBelow = pnlCurve
    .filter((p) => p.pnl < 0)
    .map((p) => `${toX(p.spotPrice).toFixed(1)},${toY(p.pnl).toFixed(1)}`)

  // Simple gradient fill polygons
  const abovePoints = fillAbove.length > 1
    ? `${toX(pnlCurve.find((p) => p.pnl >= 0)!.spotPrice).toFixed(1)},${zeroY.toFixed(1)} ${fillAbove.join(' ')} ${toX(pnlCurve.filter((p) => p.pnl >= 0).slice(-1)[0].spotPrice).toFixed(1)},${zeroY.toFixed(1)}`
    : ''
  const belowPoints = fillBelow.length > 1
    ? `${toX(pnlCurve.find((p) => p.pnl < 0)!.spotPrice).toFixed(1)},${zeroY.toFixed(1)} ${fillBelow.join(' ')} ${toX(pnlCurve.filter((p) => p.pnl < 0).slice(-1)[0].spotPrice).toFixed(1)},${zeroY.toFixed(1)}`
    : ''

  return (
    <div className="pnl-chart">
      <div className="pnl-chart__title">P&L at Expiration</div>

      <div className="pnl-chart__metrics">
        <div className="pnl-chart__metric">
          <span className="pnl-chart__metric-label">Max Profit</span>
          <span className="pnl-chart__metric-value pnl-chart__metric-value--positive">
            ${maxProfit.toFixed(2)}
          </span>
        </div>
        <div className="pnl-chart__metric">
          <span className="pnl-chart__metric-label">Max Loss</span>
          <span className="pnl-chart__metric-value pnl-chart__metric-value--negative">
            -${maxLoss.toFixed(2)}
          </span>
        </div>
        <div className="pnl-chart__metric">
          <span className="pnl-chart__metric-label">Net Debit/Credit</span>
          <span className={`pnl-chart__metric-value ${netDebit >= 0 ? 'pnl-chart__metric-value--positive' : 'pnl-chart__metric-value--negative'}`}>
            ${netDebit.toFixed(2)}
          </span>
        </div>
        <div className="pnl-chart__metric">
          <span className="pnl-chart__metric-label">Risk/Reward</span>
          <span className="pnl-chart__metric-value">
            {maxLoss > 0 ? (maxProfit / maxLoss).toFixed(2) : '∞'}
          </span>
        </div>
      </div>

      <div className="pnl-chart__canvas">
        <svg className="pnl-chart__svg" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
          {/* Profit fill */}
          {abovePoints && (
            <polygon points={abovePoints} fill="rgba(16, 185, 129, 0.1)" />
          )}
          {/* Loss fill */}
          {belowPoints && (
            <polygon points={belowPoints} fill="rgba(248, 113, 113, 0.1)" />
          )}
          {/* Zero line */}
          <line x1={pad.left} y1={zeroY} x2={width - pad.right} y2={zeroY} stroke="var(--text-muted, #64748b)" strokeWidth="1" strokeDasharray="4,4" opacity="0.5" />
          {/* P&L curve */}
          <path d={pathD} fill="none" stroke="var(--accent, #10b981)" strokeWidth="2" />
          {/* Break-even markers */}
          {breakEvens.map((be, i) => (
            <g key={i}>
              <circle cx={toX(be)} cy={zeroY} r="4" fill="var(--bg-secondary, #111827)" stroke="var(--accent, #10b981)" strokeWidth="2" />
            </g>
          ))}
        </svg>
      </div>

      {breakEvens.length > 0 && (
        <div className="pnl-chart__breakeven">
          <span className="pnl-chart__breakeven-label">Break-even</span>
          <span className="pnl-chart__breakeven-values">
            {breakEvens.map((b) => `$${b.toFixed(2)}`).join('  /  ')}
          </span>
        </div>
      )}

      {warnings && warnings.length > 0 && (
        <div className="pnl-chart__warnings">
          {warnings.map((w, i) => (
            <div key={i} className="pnl-chart__warning">{w}</div>
          ))}
        </div>
      )}
    </div>
  )
}
