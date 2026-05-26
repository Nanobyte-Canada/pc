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

function formatGainLoss(value: number | null, pct: number | null): { text: string; isPositive: boolean } {
  if (value === null) return { text: '--', isPositive: true }
  const sign = value >= 0 ? '+' : ''
  const valStr = `${sign}$${Math.abs(value).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
  const pctStr = pct != null ? ` (${sign}${pct.toFixed(1)}%)` : ''
  return { text: `${valStr}${pctStr}`, isPositive: value >= 0 }
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

  return (
    <div className="ca-strip">
      {data.accounts.map(account => {
        const brand = getBrokerBrand(account.brokerName)
        const masked = maskAccount(account.accountNumber)
        const accountType = account.accountType || ''
        const gainLoss = formatGainLoss(
          account.investmentValue != null && account.totalValue != null
            ? account.totalValue - account.investmentValue
            : null,
          null
        )

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
                  {gainLoss.text}
                </span>
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
