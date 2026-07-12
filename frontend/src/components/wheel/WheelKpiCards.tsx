import type { CapitalMetrics } from '@/types/wheel'
import type { CurrencyAmount } from '@/types/dashboard'
import { formatCurrency } from '@/services/brokerService'
import { TrendingUp, Shield, TriangleAlert, DollarSign, Target } from 'lucide-react'
import './WheelKpiCards.css'

interface WheelKpiCardsProps {
  metrics: CapitalMetrics | null
  buyingPower: CurrencyAmount[]
  ccEligible: Map<string, { sharesOwned: number; contractsAvailable: number }>
  positionCounts: { csp: number; cc: number; expiring: number; total: number }
  /** Exchange rate from USD to CAD. Used to convert CAD buying power to USD. */
  fxRateToCAD?: number
}

export function WheelKpiCards({ metrics, buyingPower, ccEligible, positionCounts, fxRateToCAD = 1.38 }: WheelKpiCardsProps) {
  const bpCad = buyingPower.find(bp => bp.currency === 'CAD')?.amount ?? 0
  const bpUsd = buyingPower.find(bp => bp.currency === 'USD')?.amount ?? 0
  const totalCC = Array.from(ccEligible.values()).reduce((sum, cc) => sum + cc.contractsAvailable, 0)

  // Convert CAD buying power to USD and sum with existing USD
  const totalUsd = bpCad / fxRateToCAD + bpUsd

  const deployedTotalUsd = (metrics?.deployedCsp.usd ?? 0) + (metrics?.ccsWritten.usd ?? 0)

  return (
    <div className="wheel-kpi-row">
      {/* Card 1: Capital Available — single USD total */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Available</span>
          <TrendingUp size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          {formatCurrency(totalUsd, 'USD')}
        </div>
      </div>

      {/* Card 2: Capital Deployed — total only */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Deployed</span>
          <Shield size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          {formatCurrency(deployedTotalUsd, 'USD')}
        </div>
      </div>

      {/* Card 3: CC Available — total count only */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">CC Available</span>
          <TriangleAlert size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value wheel-kpi__value--cc">
          {totalCC} contract{totalCC !== 1 ? 's' : ''}
        </div>
      </div>

      {/* Card 4: Premium & P&L — main number only */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Premium & P&L</span>
          <DollarSign size={14} className="wheel-kpi__icon" />
        </div>
        <div className={`wheel-kpi__value ${(metrics?.unrealizedPnl.usd ?? 0) >= 0 ? 'wheel-kpi__value--pos' : 'wheel-kpi__value--neg'}`}>
          {formatCurrency(metrics?.unrealizedPnl.usd ?? 0, 'USD')}
        </div>
      </div>

      {/* Card 5: Positions — CSP and CC counts above total */}
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Positions</span>
          <Target size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__positions-top">
          <span className="wheel-kpi__positions-csp">{positionCounts.csp} CSP</span>
          <span className="wheel-kpi__positions-sep">/</span>
          <span className="wheel-kpi__positions-cc">{positionCounts.cc} CC</span>
        </div>
        <div className="wheel-kpi__value">{positionCounts.total}</div>
      </div>
    </div>
  )
}
