import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Link2 } from 'lucide-react'
import './ConnectedAccountsWidget.css'

/** Broker brand configs for icon styling */
const BROKER_BRANDS: Record<string, { icon: string; bg: string; color: string }> = {
  questrade: { icon: 'Q', bg: '#1a5c3a', color: '#4ade80' },
  wealthsimple: { icon: 'W', bg: '#1a1a3a', color: '#a78bfa' },
  ibkr: { icon: 'IB', bg: '#3a1a1a', color: '#f87171' },
  'interactive brokers': { icon: 'IB', bg: '#3a1a1a', color: '#f87171' },
}

function getBrokerBrand(brokerName: string) {
  const key = brokerName.toLowerCase()
  for (const [k, v] of Object.entries(BROKER_BRANDS)) {
    if (key.includes(k)) return v
  }
  return { icon: brokerName.charAt(0).toUpperCase(), bg: 'var(--bg-tertiary)', color: 'var(--text-primary)' }
}

function maskAccount(accountNumber: string | null): string {
  if (!accountNumber) return ''
  const last4 = accountNumber.slice(-4)
  return `••${last4}`
}

function formatValue(value: number | null): string {
  if (value === null) return '--'
  return value.toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

function computeGainLoss(
  totalValue: number | null,
  investmentValue: number | null
): { gain: number | null; pct: number | null; isPositive: boolean } {
  if (totalValue == null || investmentValue == null) return { gain: null, pct: null, isPositive: true }
  const gain = totalValue - investmentValue
  const pct = investmentValue !== 0 ? (gain / investmentValue) * 100 : null
  return { gain, pct, isPositive: gain >= 0 }
}

function formatGainDollar(value: number | null): string {
  if (value === null) return '--'
  const sign = value >= 0 ? '+' : ''
  return `${sign}$${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function formatGainPct(pct: number | null): string | null {
  if (pct == null) return null
  const sign = pct >= 0 ? '+' : ''
  return `${sign}${pct.toFixed(2)}%`
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export default function ConnectedAccountsWidget(_props: { connectionId?: number }) {
  const { data, isLoading } = useDashboardAccounts()
  if (isLoading || !data) return <Skeleton style={{ height: '3.5rem', width: '100%' }} />

  if (data.accounts.length === 0) {
    return (
      <div className="ca-empty">
        <Link2 style={{ height: '1.5rem', width: '1.5rem' }} />
        <p>No connected accounts</p>
        <Button variant="outline" size="sm" onClick={() => window.location.href = '/brokers/connections'}>
          Connect Account
        </Button>
      </div>
    )
  }

  /* Aggregate totals for the "All Accounts" card */
  const aggTotalValue = data.accounts.reduce((sum, a) => sum + (a.totalValue ?? 0), 0)
  const aggInvestmentValue = data.accounts.reduce((sum, a) => sum + (a.investmentValue ?? 0), 0)
  const hasAnyValue = data.accounts.some(a => a.totalValue != null)
  const aggGainLoss = hasAnyValue
    ? computeGainLoss(aggTotalValue, aggInvestmentValue)
    : { gain: null, pct: null, isPositive: true }
  const aggPctStr = formatGainPct(aggGainLoss.pct)

  return (
    <div className="ca-strip">
      {/* All Accounts aggregate card */}
      <div className="ca-account ca-account-all">
        <div className="ca-broker-icon ca-all-icon">
          <span>$∑</span>
        </div>
        <div className="ca-account-info">
          <div className="ca-account-top">
            <span className="ca-account-type ca-all-label">All Accounts</span>
          </div>
          <div className="ca-account-bottom">
            <span className="ca-account-value">
              C$ {hasAnyValue ? formatValue(aggTotalValue) : '--'}
            </span>
            <span className={`ca-account-gain ${aggGainLoss.isPositive ? 'ca-gain-positive' : 'ca-gain-negative'}`}>
              {formatGainDollar(aggGainLoss.gain)}
            </span>
            {aggPctStr && (
              <span className={`ca-gain-pct ${aggGainLoss.isPositive ? 'ca-gain-pct-positive' : 'ca-gain-pct-negative'}`}>
                {aggPctStr}
              </span>
            )}
          </div>
        </div>
      </div>

      {data.accounts.map(account => {
        const brand = getBrokerBrand(account.brokerName)
        const masked = maskAccount(account.accountNumber)
        const accountType = account.accountType || ''
        const gainLoss = computeGainLoss(account.totalValue, account.investmentValue)
        const pctStr = formatGainPct(gainLoss.pct)

        return (
          <div
            key={account.connectionId}
            className="ca-account"
            onClick={() => window.location.href = `/brokers/accounts/${account.connectionId}`}
          >
            <div
              className="ca-broker-icon"
              style={{ backgroundColor: brand.bg, color: brand.color }}
            >
              {brand.icon}
            </div>
            <div className="ca-account-info">
              <div className="ca-account-top">
                <span className="ca-account-type">{accountType}</span>
                {masked && <span className="ca-account-number">{masked}</span>}
              </div>
              <div className="ca-account-bottom">
                <span className="ca-account-value">
                  C$ {formatValue(account.totalValue)}
                </span>
                <span className={`ca-account-gain ${gainLoss.isPositive ? 'ca-gain-positive' : 'ca-gain-negative'}`}>
                  {formatGainDollar(gainLoss.gain)}
                </span>
                {pctStr && (
                  <span className={`ca-gain-pct ${gainLoss.isPositive ? 'ca-gain-pct-positive' : 'ca-gain-pct-negative'}`}>
                    {pctStr}
                  </span>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
