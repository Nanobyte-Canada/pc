import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ChevronRight, TrendingUp, Wallet, DollarSign } from 'lucide-react'
import { KpiCard } from '../components/dashboard/KpiCard'
import { PositionsTable } from '../components/dashboard/PositionsTable'
import { AccountActivitiesGrid } from '../components/broker/AccountActivitiesGrid'
import { useBrokerConnections } from '../hooks/useBrokerConnections'
import {
  useDashboardSummary,
  useDashboardCash,
  useDashboardIrr,
  useDividendCalendar,
} from '../hooks/useDashboardWidgets'
import './AccountDetailPage.css'

type TabType = 'positions' | 'activities' | 'dividends'

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

function maskAccountNumber(num: string | null | undefined): string {
  if (!num) return ''
  const last4 = num.slice(-4)
  return `••${last4}`
}

function getBrokerBadge(brokerName: string): { letter: string; className: string } {
  const lower = brokerName.toLowerCase()
  if (lower.includes('questrade')) return { letter: 'Q', className: 'account-detail-header__icon--questrade' }
  if (lower.includes('wealthsimple')) return { letter: 'W', className: 'account-detail-header__icon--wealthsimple' }
  if (lower.includes('ibkr') || lower.includes('interactive')) return { letter: 'IB', className: 'account-detail-header__icon--ibkr' }
  return { letter: brokerName.charAt(0).toUpperCase(), className: 'account-detail-header__icon--questrade' }
}

function getStatusDot(status: string): string {
  switch (status.toUpperCase()) {
    case 'ACTIVE': return 'account-detail-header__status-dot--active'
    case 'ERROR':
    case 'DISCONNECTED':
    case 'EXPIRED': return 'account-detail-header__status-dot--error'
    default: return 'account-detail-header__status-dot--pending'
  }
}

function formatSyncTime(dateStr: string | null | undefined): string {
  if (!dateStr) return 'Never synced'
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return 'Just now'
  if (diffMin < 60) return `${diffMin}m ago`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}h ago`
  return d.toLocaleDateString()
}

export function AccountDetailPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const connId = connectionId ? parseInt(connectionId, 10) : undefined
  const [activeTab, setActiveTab] = useState<TabType>('positions')

  const { data: connectionsData, isLoading: connectionsLoading } = useBrokerConnections()
  const { data: summaryData } = useDashboardSummary(connId)
  const { data: cashData } = useDashboardCash(connId)
  const { data: irrData } = useDashboardIrr(connId)
  const { data: dividendData } = useDividendCalendar(undefined, connId)

  if (connectionsLoading) {
    return (
      <div className="account-detail-page">
        <div className="account-detail-loading">Loading account...</div>
      </div>
    )
  }

  const connection = connectionsData?.connections?.find(c => c.id === connId)
  const accountLabel = connection?.accountMetaType
    || connection?.accountType
    || connection?.accountName
    || `Account ${connectionId}`
  const brokerName = connection?.broker?.name ?? 'Unknown'
  const badge = getBrokerBadge(brokerName)
  const accountNumber = connection?.accountNumberActual || connection?.accountNumber
  const status = connection?.status ?? 'PENDING'

  /* KPI data */
  const pv = summaryData?.portfolioValue
  const totalValue = pv?.totalValue ?? 0
  const investmentValue = pv?.investmentValue ?? 0
  const totalGain = pv?.totalChange ?? (totalValue - investmentValue)

  /* Cash breakdown by currency */
  const cashBreakdown = (cashData?.availableCash ?? []).map(c => ({
    label: c.currency === 'CAD' ? 'C$' : c.currency === 'USD' ? 'US$' : c.currency,
    value: fmtBreakdownAmount(c.amount),
  }))

  /* Investment breakdown */
  const investmentBreakdown = [
    { label: 'C$', value: fmtBreakdownAmount(investmentValue) },
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

  return (
    <div className="account-detail-page">
      {/* Breadcrumb */}
      <nav className="account-breadcrumb">
        <Link to="/brokers/connections" className="breadcrumb-link">Accounts</Link>
        <ChevronRight size={14} className="breadcrumb-separator" />
        <span className="breadcrumb-current">{accountLabel}</span>
      </nav>

      {/* Account header */}
      <div className="account-detail-header">
        <div className={`account-detail-header__icon ${badge.className}`}>
          {badge.letter}
        </div>
        <div className="account-detail-header__info">
          <div className="account-detail-header__title-row">
            <h1 className="account-detail-header__type">{accountLabel}</h1>
            <span className="account-detail-header__number">
              {maskAccountNumber(accountNumber)}
            </span>
          </div>
          <div className="account-detail-header__meta-row">
            <span className={`account-detail-header__status-dot ${getStatusDot(status)}`} />
            <span className="account-detail-header__sync-time">
              {formatSyncTime(connection?.lastPositionsFetchedAt)}
            </span>
          </div>
        </div>
      </div>

      {/* 4-column KPI row */}
      <div className="account-detail-section">
        <div className="account-detail-kpi-row">
          <KpiCard
            label="Total Value"
            icon={<TrendingUp size={14} />}
            value={fmtCad(totalValue)}
            variant="emerald"
            breakdown={[{ label: 'Gain', value: fmtCadSigned(totalGain) }]}
          />
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
            label="Returns"
            icon={<DollarSign size={14} />}
            value={fmtCadSigned(irrData?.portfolioTotalReturn)}
            variant="returns"
            breakdown={returnsBreakdown}
          />
        </div>
      </div>

      {/* Tab bar */}
      <div className="account-detail-tabs">
        <button
          className={`account-detail-tab ${activeTab === 'positions' ? 'account-detail-tab--active' : 'account-detail-tab--inactive'}`}
          onClick={() => setActiveTab('positions')}
        >
          Positions
        </button>
        <button
          className={`account-detail-tab ${activeTab === 'activities' ? 'account-detail-tab--active' : 'account-detail-tab--inactive'}`}
          onClick={() => setActiveTab('activities')}
        >
          Activities
        </button>
        <button
          className={`account-detail-tab ${activeTab === 'dividends' ? 'account-detail-tab--active' : 'account-detail-tab--inactive'}`}
          onClick={() => setActiveTab('dividends')}
        >
          Dividends
        </button>
      </div>

      {/* Tab content */}
      <div className="account-detail-section">
        {activeTab === 'positions' && (
          <PositionsTable connectionId={connId} />
        )}
        {activeTab === 'activities' && connId && (
          <AccountActivitiesGrid
            connectionId={connId}
            connectionActive={status === 'ACTIVE'}
          />
        )}
        {activeTab === 'dividends' && (
          <DividendsList data={dividendData} />
        )}
      </div>
    </div>
  )
}

/* Simple dividends list */
function DividendsList({ data }: { data: ReturnType<typeof useDividendCalendar>['data'] }) {
  if (!data || data.entries.length === 0) {
    return (
      <div className="account-activities__empty">
        No dividend entries found for this period.
      </div>
    )
  }

  return (
    <div className="account-activities">
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
  )
}
