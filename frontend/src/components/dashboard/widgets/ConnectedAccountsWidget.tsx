import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Link2, ExternalLink } from 'lucide-react'
import './ConnectedAccountsWidget.css'

function fmtCurrency(value: number | null) {
  if (value == null) return '-'
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

const STATUS_CLASSES: Record<string, string> = {
  ACTIVE: 'ca-status-active',
  NEEDS_RECONNECTION: 'ca-status-needs-reconnection',
  ERROR: 'ca-status-error',
}

export default function ConnectedAccountsWidget(_props: { connectionId?: number }) {
  const { data, isLoading } = useDashboardAccounts()
  if (isLoading || !data) return <Skeleton style={{ height: '8rem', width: '100%' }} />

  if (data.accounts.length === 0) {
    return (
      <div className="ca-empty">
        <Link2 style={{ height: '2.5rem', width: '2.5rem' }} />
        <p>No connected accounts</p>
        <Button variant="outline" size="sm" onClick={() => window.location.href = '/brokers/connections'}>
          Connect Account
        </Button>
      </div>
    )
  }

  return (
    <div className="ca-list">
      {data.accounts.map(account => (
        <div
          key={account.connectionId}
          className={`ca-item ${STATUS_CLASSES[account.status] || 'ca-status-default'}`}
          onClick={() => window.location.href = `/brokers/accounts/${account.connectionId}`}
        >
          <div className="ca-item-left">
            <div className="ca-broker-icon">
              <span>
                {(account.brokerName || 'B').substring(0, 2).toUpperCase()}
              </span>
            </div>
            <div className="ca-account-info">
              <div className="ca-account-name">{account.accountName || account.brokerName}</div>
              <div className="ca-account-detail">
                {account.accountType}{account.accountNumber ? ` \u2022 ${account.accountNumber}` : ''}
              </div>
            </div>
          </div>
          <div className="ca-item-right">
            <div className="ca-value-info">
              <div className="ca-value">{fmtCurrency(account.totalValue)}</div>
              <div className="ca-positions-count">{account.positionsCount} positions</div>
            </div>
            {account.linkedGroup ? (
              <div>
                <div
                  className="ca-accuracy-circle"
                  style={{
                    borderColor: account.linkedGroup.accuracy >= 90 ? '#059669' : account.linkedGroup.accuracy >= 75 ? '#d97706' : '#dc2626',
                    color: account.linkedGroup.accuracy >= 90 ? '#059669' : account.linkedGroup.accuracy >= 75 ? '#d97706' : '#dc2626',
                  }}
                >
                  {account.linkedGroup.accuracy.toFixed(0)}%
                </div>
              </div>
            ) : (
              <Badge variant="warning" style={{ fontSize: '0.75rem' }}>Setup</Badge>
            )}
            <ExternalLink className="ca-external-icon" />
          </div>
        </div>
      ))}
    </div>
  )
}
