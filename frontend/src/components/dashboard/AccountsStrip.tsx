import { useNavigate } from 'react-router-dom'
import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import type { DashboardAccount } from '@/types/dashboard'
import './AccountsStrip.css'

function formatCurrency(value: number | null | undefined): string {
  if (value == null) return 'C$ 0'
  return `C$ ${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function formatGain(value: number | null | undefined): string {
  if (value == null) return '+C$ 0'
  const prefix = value >= 0 ? '+' : '-'
  return `${prefix}C$ ${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) return '0.0%'
  const prefix = value >= 0 ? '+' : ''
  return `${prefix}${value.toFixed(1)}%`
}

function maskAccountNumber(num: string | null | undefined): string {
  if (!num) return ''
  const last4 = num.slice(-4)
  return `••${last4}`
}

function getBrokerBadge(brokerName: string): { letter: string; className: string } {
  const lower = brokerName.toLowerCase()
  if (lower.includes('questrade')) return { letter: 'Q', className: 'accounts-strip__broker-icon--questrade' }
  if (lower.includes('wealthsimple')) return { letter: 'W', className: 'accounts-strip__broker-icon--wealthsimple' }
  if (lower.includes('ibkr') || lower.includes('interactive')) return { letter: 'IB', className: 'accounts-strip__broker-icon--ibkr' }
  return { letter: brokerName.charAt(0).toUpperCase(), className: 'accounts-strip__broker-icon--questrade' }
}

export function AccountsStrip() {
  const navigate = useNavigate()
  const { data, isLoading } = useDashboardAccounts()

  if (isLoading) {
    return (
      <div className="accounts-strip__skeleton">
        {[1, 2, 3, 4].map(i => (
          <Skeleton key={i} className="accounts-strip__skeleton-card" />
        ))}
      </div>
    )
  }

  const accounts = data?.accounts ?? []
  const totalValue = accounts.reduce((sum, a) => sum + (a.totalValue ?? 0), 0)
  const totalInvestment = accounts.reduce((sum, a) => sum + (a.investmentValue ?? 0), 0)
  const totalGain = totalValue - totalInvestment
  const totalGainPct = totalInvestment > 0 ? (totalGain / totalInvestment) * 100 : 0
  const isPositiveTotal = totalGain >= 0

  return (
    <div className="accounts-strip">
      {/* All Accounts card */}
      <div className="accounts-strip__card accounts-strip__card--all">
        <p className="accounts-strip__label">All Accounts</p>
        <div className="accounts-strip__value accounts-strip__value--all">
          {formatCurrency(totalValue)}
        </div>
        <div className="accounts-strip__gain-row">
          <span className={`accounts-strip__gain-amount ${isPositiveTotal ? 'accounts-strip__gain-amount--positive' : 'accounts-strip__gain-amount--negative'}`}>
            {formatGain(totalGain)}
          </span>
          <span className={`accounts-strip__gain-pill ${isPositiveTotal ? 'accounts-strip__gain-pill--positive' : 'accounts-strip__gain-pill--negative'}`}>
            {formatPercent(totalGainPct)}
          </span>
        </div>
      </div>

      {/* Individual account cards */}
      {accounts.map((account: DashboardAccount) => {
        const badge = getBrokerBadge(account.brokerName)
        const accountGain = (account.totalValue ?? 0) - (account.investmentValue ?? 0)
        const accountGainPct = (account.investmentValue ?? 0) > 0
          ? (accountGain / (account.investmentValue ?? 1)) * 100
          : 0
        const isPositive = accountGain >= 0

        return (
          <div
            key={account.connectionId}
            className="accounts-strip__card accounts-strip__card--account"
            onClick={() => navigate(`/brokers/accounts/${account.connectionId}`)}
          >
            <div className="accounts-strip__header-row">
              <span className={`accounts-strip__broker-icon ${badge.className}`}>
                {badge.letter}
              </span>
              <span className="accounts-strip__account-type">
                {account.accountType ?? account.accountName ?? 'Account'}
              </span>
              <span className="accounts-strip__account-number">
                {maskAccountNumber(account.accountNumber)}
              </span>
            </div>
            <div className="accounts-strip__value accounts-strip__value--account">
              {formatCurrency(account.totalValue)}
            </div>
            <div className="accounts-strip__gain-row">
              <span className={`accounts-strip__gain-amount ${isPositive ? 'accounts-strip__gain-amount--positive' : 'accounts-strip__gain-amount--negative'}`}>
                {formatGain(accountGain)}
              </span>
              <span className={`accounts-strip__gain-pill ${isPositive ? 'accounts-strip__gain-pill--positive' : 'accounts-strip__gain-pill--negative'}`}>
                {formatPercent(accountGainPct)}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}
