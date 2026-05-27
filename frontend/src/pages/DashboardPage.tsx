import { Search, Bell, Settings } from 'lucide-react'
import { TrendingUp, Wallet, DollarSign, ShoppingCart, PieChart } from 'lucide-react'
import { AccountsStrip } from '../components/dashboard/AccountsStrip'
import { KpiCard } from '../components/dashboard/KpiCard'
import { PositionsTable } from '../components/dashboard/PositionsTable'
import {
  useDashboardSummary,
  useDashboardCash,
  useSectorExposure,
  useDashboardIrr,
  useDashboardAccounts,
} from '../hooks/useDashboardWidgets'
import { useAuthStore } from '../stores/authStore'
import './DashboardPage.css'

const SECTOR_COLORS = ['#10b981', '#059669', '#6ee7b7', '#34d399', '#047857', '#065f46', '#a7f3d0', '#d1fae5']

function fmtCad(value: number | null | undefined): string {
  if (value == null) return 'C$ 0'
  return `C$ ${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function fmtCadSigned(value: number | null | undefined): string {
  if (value == null) return '+C$ 0'
  const prefix = value >= 0 ? '+' : '-'
  return `${prefix}C$ ${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function fmtPct(value: number | null | undefined): string {
  if (value == null) return '0.0%'
  const prefix = value >= 0 ? '+' : ''
  return `${prefix}${value.toFixed(1)}%`
}

function fmtBreakdownAmount(value: number | null | undefined): string {
  if (value == null) return '0.00'
  return value.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function getGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

function getInitials(name: string | null | undefined): string {
  if (!name) return '?'
  return name
    .split(' ')
    .map(p => p[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()
}

export function DashboardPage() {
  const user = useAuthStore(s => s.user)
  const { data: summaryData } = useDashboardSummary()
  const { data: cashData } = useDashboardCash()
  const { data: sectorData } = useSectorExposure()
  const { data: irrData } = useDashboardIrr()
  const { data: accountsData } = useDashboardAccounts()

  const pv = summaryData?.portfolioValue
  const totalValue = pv?.totalValue ?? 0
  const investmentValue = pv?.investmentValue ?? 0
  const totalGain = pv?.totalChange ?? (totalValue - investmentValue)
  const totalGainPct = pv?.totalChangePercent ?? (investmentValue > 0 ? (totalGain / investmentValue) * 100 : 0)
  const isPositive = totalGain >= 0

  /* Cash breakdown by currency */
  const cashBreakdown = (cashData?.availableCash ?? []).map(c => ({
    label: c.currency === 'CAD' ? 'C$' : c.currency === 'USD' ? 'US$' : c.currency,
    value: fmtBreakdownAmount(c.amount),
  }))

  /* Buying power breakdown */
  const bpBreakdown = (cashData?.buyingPower ?? []).map(c => ({
    label: c.currency === 'CAD' ? 'C$' : c.currency === 'USD' ? 'US$' : c.currency,
    value: fmtBreakdownAmount(c.amount),
  }))

  /* Investment breakdown from accounts */
  const accounts = accountsData?.accounts ?? []
  const cadInvestment = accounts.reduce((sum, a) => sum + (a.investmentValue ?? 0), 0)
  const investmentBreakdown = [
    { label: 'C$', value: fmtBreakdownAmount(cadInvestment) },
  ]

  /* Returns breakdown */
  const roi = irrData?.portfolioTotalReturnPct
  const irr = irrData?.portfolioIrr
  const divYield = irrData?.portfolioDividendYield
  const returnsBreakdown: Array<{ label: string; value: string; variant: 'positive' | 'negative' | 'neutral' }> = [
    { label: 'ROI', value: fmtPct(roi), variant: (roi ?? 0) >= 0 ? 'positive' : 'negative' },
    { label: 'IRR', value: fmtPct(irr), variant: (irr ?? 0) >= 0 ? 'positive' : 'negative' },
    { label: 'Div Yield', value: fmtPct(divYield), variant: (divYield ?? 0) >= 0 ? 'positive' : 'negative' },
  ]

  /* Sectors - top 6 */
  const topSectors = (sectorData?.sectors ?? [])
    .slice(0, 6)
    .map((s, i) => ({
      name: s.sectorName,
      weight: s.weight,
      color: SECTOR_COLORS[i % SECTOR_COLORS.length],
    }))

  return (
    <div className="dashboard-page">
      {/* Desktop header */}
      <div className="dashboard-header">
        <h1 className="dashboard-header__title">Dashboard</h1>
        <div className="dashboard-header__actions">
          <div className="dashboard-header__search">
            <Search className="dashboard-header__search-icon" />
            <input
              type="text"
              placeholder="Search..."
              className="dashboard-header__search-input"
            />
          </div>
          <button className="dashboard-header__icon-btn" title="Notifications">
            <Bell size={14} />
          </button>
          <button className="dashboard-header__icon-btn" title="Settings">
            <Settings size={14} />
          </button>
        </div>
      </div>

      {/* Mobile hero */}
      <div className="dashboard-mobile-hero">
        <div className="dashboard-mobile-hero__left">
          <p className="dashboard-mobile-hero__greeting">{getGreeting()}</p>
          <p className="dashboard-mobile-hero__value">{fmtCad(totalValue)}</p>
          <div className="dashboard-mobile-hero__gain-row">
            <span className={`dashboard-mobile-hero__gain ${isPositive ? 'dashboard-mobile-hero__gain--positive' : 'dashboard-mobile-hero__gain--negative'}`}>
              {fmtCadSigned(totalGain)}
            </span>
            <span className={`dashboard-mobile-hero__gain-pill ${isPositive ? 'dashboard-mobile-hero__gain-pill--positive' : 'dashboard-mobile-hero__gain-pill--negative'}`}>
              {fmtPct(totalGainPct)}
            </span>
          </div>
        </div>
        <div className="dashboard-mobile-hero__avatar">
          {getInitials(user?.name)}
        </div>
      </div>

      {/* Accounts strip */}
      <div className="dashboard-section">
        <AccountsStrip />
      </div>

      {/* KPI row */}
      <div className="dashboard-section">
        <div className="dashboard-kpi-row">
          <KpiCard
            label="Investment"
            icon={<TrendingUp size={14} />}
            value={fmtCad(investmentValue)}
            breakdown={investmentBreakdown}
          />
          <KpiCard
            label="Cash"
            icon={<Wallet size={14} />}
            value={fmtCad(cashData?.totalCashCAD)}
            breakdown={cashBreakdown}
          />
          <KpiCard
            label="Buying Power"
            icon={<ShoppingCart size={14} />}
            value={fmtCad(cashData?.totalBuyingPowerCAD)}
            breakdown={bpBreakdown}
          />
          <KpiCard
            label="Returns"
            icon={<DollarSign size={14} />}
            value={fmtCadSigned(irrData?.portfolioTotalReturn)}
            variant="returns"
            breakdown={returnsBreakdown}
          />
          <KpiCard
            label="Sectors"
            icon={<PieChart size={14} />}
            value=""
            sectors={topSectors}
          />
        </div>
      </div>

      {/* Positions table */}
      <div className="dashboard-section">
        <PositionsTable />
      </div>
    </div>
  )
}
