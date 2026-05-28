import { useState } from 'react'
import { Search, Bell, Settings, ChevronDown, X } from 'lucide-react'
import { TrendingUp, Wallet, DollarSign, ShoppingCart, PieChart } from 'lucide-react'
import { KpiCard } from '../components/dashboard/KpiCard'
import { PositionsTable } from '../components/dashboard/PositionsTable'
import { AccountActivitiesGrid } from '../components/broker/AccountActivitiesGrid'
import {
  useDashboardSummary,
  useDashboardCash,
  useSectorExposure,
  useDashboardIrr,
  useDashboardAccounts,
  useDividendCalendar,
} from '../hooks/useDashboardWidgets'
import { useBrokerConnections } from '../hooks/useBrokerConnections'
import { useAuthStore } from '../stores/authStore'
import type { DashboardAccount } from '../types/dashboard'
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

function maskAccountNumber(num: string | null | undefined): string {
  if (!num) return ''
  const last4 = num.slice(-4)
  return `••${last4}`
}

function getBrokerBadge(brokerName: string): { letter: string; className: string } {
  const lower = brokerName.toLowerCase()
  if (lower.includes('questrade')) return { letter: 'Q', className: 'acct-switcher__broker-icon--questrade' }
  if (lower.includes('wealthsimple')) return { letter: 'W', className: 'acct-switcher__broker-icon--wealthsimple' }
  if (lower.includes('ibkr') || lower.includes('interactive')) return { letter: 'IB', className: 'acct-switcher__broker-icon--ibkr' }
  return { letter: brokerName.charAt(0).toUpperCase(), className: 'acct-switcher__broker-icon--questrade' }
}

type ContentTab = 'positions' | 'activities' | 'dividends'

export function DashboardPage() {
  const user = useAuthStore(s => s.user)
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false)
  const [contentTab, setContentTab] = useState<ContentTab>('positions')

  // Data hooks — pass selectedAccountId for per-account filtering
  const { data: accountsData } = useDashboardAccounts()
  const { data: summaryData } = useDashboardSummary(selectedAccountId ?? undefined)
  const { data: cashData } = useDashboardCash(selectedAccountId ?? undefined)
  const { data: sectorData } = useSectorExposure(selectedAccountId ?? undefined)
  const { data: irrData } = useDashboardIrr(selectedAccountId ?? undefined)
  const { data: dividendData } = useDividendCalendar(undefined, selectedAccountId ?? undefined)
  const { data: connectionsData } = useBrokerConnections()

  const accounts = accountsData?.accounts ?? []

  // Reset content tab to positions when switching accounts
  const handleAccountSelect = (connectionId: number | null) => {
    setSelectedAccountId(connectionId)
    setContentTab('positions')
    setMobileSheetOpen(false)
  }

  // Get selected account data for mobile hero
  const selectedAccount = selectedAccountId
    ? accounts.find(a => a.connectionId === selectedAccountId)
    : null

  // Connection status for activities grid
  const connection = selectedAccountId
    ? connectionsData?.connections?.find(c => c.id === selectedAccountId)
    : null
  const connectionActive = connection?.status === 'ACTIVE'

  // Mobile label for switcher
  const mobileLabel = selectedAccount
    ? `${getBrokerBadge(selectedAccount.brokerName).letter} ${selectedAccount.accountType ?? 'Account'} ${maskAccountNumber(selectedAccount.accountNumber)}`
    : 'ALL ACCOUNTS'

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
      {/* Desktop header with account switcher pills */}
      <div className="dashboard-header">
        <div className="dashboard-header__left">
          <h1 className="dashboard-header__title">Portfolio</h1>
          <div className="acct-switcher">
            <div className="acct-switcher__pills">
              <button
                className={`acct-switcher__pill ${selectedAccountId === null ? 'acct-switcher__pill--active' : ''}`}
                onClick={() => handleAccountSelect(null)}
              >
                All Accounts
              </button>
              {accounts.map((account: DashboardAccount) => {
                const badge = getBrokerBadge(account.brokerName)
                return (
                  <button
                    key={account.connectionId}
                    className={`acct-switcher__pill ${selectedAccountId === account.connectionId ? 'acct-switcher__pill--active' : ''}`}
                    onClick={() => handleAccountSelect(account.connectionId)}
                  >
                    <span className={`acct-switcher__broker-icon ${badge.className}`}>
                      {badge.letter}
                    </span>
                    <span className="acct-switcher__pill-type">
                      {account.accountType ?? 'Account'}
                    </span>
                    <span className="acct-switcher__pill-number">
                      {maskAccountNumber(account.accountNumber)}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
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

      {/* Mobile account switcher dropdown + dots */}
      <div className="mobile-acct-switcher">
        <button
          className="mobile-acct-switcher__trigger"
          onClick={() => setMobileSheetOpen(true)}
        >
          <span className="mobile-acct-switcher__label">{mobileLabel}</span>
          <ChevronDown size={14} className="mobile-acct-switcher__chevron" />
        </button>
        <div className="mobile-acct-switcher__dots">
          <span
            className={`mobile-acct-switcher__dot ${selectedAccountId === null ? 'mobile-acct-switcher__dot--active' : ''}`}
            onClick={() => handleAccountSelect(null)}
          />
          {accounts.map(account => (
            <span
              key={account.connectionId}
              className={`mobile-acct-switcher__dot ${selectedAccountId === account.connectionId ? 'mobile-acct-switcher__dot--active' : ''}`}
              onClick={() => handleAccountSelect(account.connectionId)}
            />
          ))}
        </div>
      </div>

      {/* Mobile account switcher bottom sheet */}
      {mobileSheetOpen && (
        <div className="acct-sheet-overlay" onClick={() => setMobileSheetOpen(false)}>
          <div className="acct-sheet" onClick={(e) => e.stopPropagation()}>
            <div className="acct-sheet__handle" />
            <div className="acct-sheet__header">
              <span className="acct-sheet__title">Select Account</span>
              <button className="acct-sheet__close" onClick={() => setMobileSheetOpen(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <div className="acct-sheet__items">
              <button
                className={`acct-sheet__item ${selectedAccountId === null ? 'acct-sheet__item--active' : ''}`}
                onClick={() => handleAccountSelect(null)}
              >
                <span className="acct-sheet__item-label">All Accounts</span>
              </button>
              {accounts.map(account => {
                const badge = getBrokerBadge(account.brokerName)
                return (
                  <button
                    key={account.connectionId}
                    className={`acct-sheet__item ${selectedAccountId === account.connectionId ? 'acct-sheet__item--active' : ''}`}
                    onClick={() => handleAccountSelect(account.connectionId)}
                  >
                    <span className={`acct-switcher__broker-icon ${badge.className}`}>
                      {badge.letter}
                    </span>
                    <span className="acct-sheet__item-label">
                      {account.accountType ?? 'Account'} {maskAccountNumber(account.accountNumber)}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      )}

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

      {/* Content area — conditional based on account selection */}
      {selectedAccountId === null ? (
        /* All Accounts: PositionsTable with Holdings/Orders tabs */
        <div className="dashboard-section">
          <PositionsTable autoFit />
        </div>
      ) : (
        /* Individual account: Positions/Activities/Dividends tabs */
        <>
          <div className="dashboard-content-tabs">
            <button
              className={`dashboard-content-tab ${contentTab === 'positions' ? 'dashboard-content-tab--active' : 'dashboard-content-tab--inactive'}`}
              onClick={() => setContentTab('positions')}
            >
              Positions
            </button>
            <button
              className={`dashboard-content-tab ${contentTab === 'activities' ? 'dashboard-content-tab--active' : 'dashboard-content-tab--inactive'}`}
              onClick={() => setContentTab('activities')}
            >
              Activities
            </button>
            <button
              className={`dashboard-content-tab ${contentTab === 'dividends' ? 'dashboard-content-tab--active' : 'dashboard-content-tab--inactive'}`}
              onClick={() => setContentTab('dividends')}
            >
              Dividends
            </button>
          </div>

          <div className="dashboard-section">
            {contentTab === 'positions' && (
              <PositionsTable connectionId={selectedAccountId} autoFit />
            )}
            {contentTab === 'activities' && (
              <AccountActivitiesGrid
                connectionId={selectedAccountId}
                connectionActive={connectionActive}
                autoFit
              />
            )}
            {contentTab === 'dividends' && (
              <DividendsList data={dividendData} />
            )}
          </div>
        </>
      )}
    </div>
  )
}

/* Simple dividends list — adapted from AccountDetailPage */
function DividendsList({ data }: { data: ReturnType<typeof useDividendCalendar>['data'] }) {
  if (!data || data.entries.length === 0) {
    return (
      <div className="dashboard-dividends__empty">
        No dividend entries found for this period.
      </div>
    )
  }

  return (
    <div className="dashboard-dividends">
      {/* Desktop: table */}
      <div className="dashboard-dividends__desktop">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: '8px 12px', fontSize: 11, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)' }}>Date</th>
              <th style={{ textAlign: 'left', padding: '8px 12px', fontSize: 11, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)' }}>Symbol</th>
              <th style={{ textAlign: 'right', padding: '8px 12px', fontSize: 11, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)' }}>Amount</th>
              <th style={{ textAlign: 'left', padding: '8px 12px', fontSize: 11, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)' }}>Currency</th>
            </tr>
          </thead>
          <tbody>
            {data.entries.map((entry, i) => (
              <tr key={i}>
                <td style={{ padding: '8px 12px', fontSize: 12, color: 'var(--text-secondary)', borderBottom: '1px solid var(--border)' }}>
                  {new Date(entry.date).toLocaleDateString()}
                </td>
                <td style={{ padding: '8px 12px', fontSize: 12, fontWeight: 600, color: 'var(--text-primary)', borderBottom: '1px solid var(--border)' }}>
                  {entry.symbol ?? '-'}
                </td>
                <td style={{ padding: '8px 12px', fontSize: 12, fontFamily: 'var(--font-mono)', color: '#6ee7b7', textAlign: 'right', borderBottom: '1px solid var(--border)' }}>
                  {entry.amount.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </td>
                <td style={{ padding: '8px 12px', fontSize: 12, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)' }}>
                  {entry.currency}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile: card list */}
      <div className="dashboard-dividends__mobile">
        {data.entries.map((entry, i) => (
          <div key={i} className="dashboard-dividend-card">
            <div className="dashboard-dividend-card__left">
              <span className="dashboard-dividend-card__date">
                {new Date(entry.date).toLocaleDateString()}
              </span>
              <span className="dashboard-dividend-card__symbol">{entry.symbol ?? '-'}</span>
            </div>
            <div className="dashboard-dividend-card__right">
              <span className="dashboard-dividend-card__amount">
                {entry.amount.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
              <span className="dashboard-dividend-card__currency">{entry.currency}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
