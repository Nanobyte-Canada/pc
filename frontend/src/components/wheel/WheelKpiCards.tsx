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
}

export function WheelKpiCards({ metrics, buyingPower, ccEligible, positionCounts }: WheelKpiCardsProps) {
  const bpCad = buyingPower.find(bp => bp.currency === 'CAD')?.amount ?? 0
  const bpUsd = buyingPower.find(bp => bp.currency === 'USD')?.amount ?? 0
  const totalCC = Array.from(ccEligible.values()).reduce((sum, cc) => sum + cc.contractsAvailable, 0)
  const ccEntries = Array.from(ccEligible.entries()).slice(0, 3)

  return (
    <div className="wheel-kpi-row">
      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Available</span>
          <TrendingUp size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          C$ {bpCad.toLocaleString('en-US', { maximumFractionDigits: 0 })}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">C$</span>
          <span className="wheel-kpi__breakdown-value">{bpCad.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">US$</span>
          <span className="wheel-kpi__breakdown-value">{bpUsd.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
        </div>
      </div>

      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Capital Deployed</span>
          <Shield size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">
          {formatCurrency(metrics?.deployedCsp.usd ?? 0, 'USD')}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CSP</span>
          <span className="wheel-kpi__breakdown-value">{formatCurrency(metrics?.deployedCsp.usd ?? 0, 'USD')}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CC</span>
          <span className="wheel-kpi__breakdown-value">{formatCurrency(metrics?.ccsWritten.usd ?? 0, 'USD')}</span>
        </div>
      </div>

      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">CC Available</span>
          <TriangleAlert size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value wheel-kpi__value--cc">
          {totalCC} contract{totalCC !== 1 ? 's' : ''}
        </div>
        <div className="wheel-kpi__divider" />
        {ccEntries.map(([ticker, info]) => (
          <div key={ticker} className="wheel-kpi__breakdown-row">
            <span className="wheel-kpi__breakdown-label">{ticker}</span>
            <span className="wheel-kpi__breakdown-value">{info.contractsAvailable}</span>
          </div>
        ))}
        {ccEntries.length === 0 && (
          <div className="wheel-kpi__breakdown-row">
            <span className="wheel-kpi__breakdown-label">None</span>
            <span className="wheel-kpi__breakdown-value">--</span>
          </div>
        )}
      </div>

      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Premium & P&L</span>
          <DollarSign size={14} className="wheel-kpi__icon" />
        </div>
        <div className={`wheel-kpi__value ${(metrics?.unrealizedPnl.usd ?? 0) >= 0 ? 'wheel-kpi__value--pos' : 'wheel-kpi__value--neg'}`}>
          {formatCurrency(metrics?.unrealizedPnl.usd ?? 0, 'USD')}
        </div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Premium</span>
          <span className="wheel-kpi__breakdown-value wheel-kpi__breakdown-value--pos">
            +{formatCurrency(metrics?.totalPremium.usd ?? 0, 'USD')}
          </span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Unrealized</span>
          <span className={`wheel-kpi__breakdown-value ${(metrics?.unrealizedPnl.usd ?? 0) >= 0 ? 'wheel-kpi__breakdown-value--pos' : 'wheel-kpi__breakdown-value--neg'}`}>
            {formatCurrency(metrics?.unrealizedPnl.usd ?? 0, 'USD')}
          </span>
        </div>
      </div>

      <div className="wheel-kpi-card">
        <div className="wheel-kpi__header">
          <span className="wheel-kpi__label">Positions</span>
          <Target size={14} className="wheel-kpi__icon" />
        </div>
        <div className="wheel-kpi__value">{positionCounts.total}</div>
        <div className="wheel-kpi__divider" />
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CSP</span>
          <span className="wheel-kpi__breakdown-value">{positionCounts.csp}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">CC</span>
          <span className="wheel-kpi__breakdown-value">{positionCounts.cc}</span>
        </div>
        <div className="wheel-kpi__breakdown-row">
          <span className="wheel-kpi__breakdown-label">Expiring</span>
          <span className="wheel-kpi__breakdown-value wheel-kpi__breakdown-value--neg">{positionCounts.expiring}</span>
        </div>
      </div>
    </div>
  )
}
