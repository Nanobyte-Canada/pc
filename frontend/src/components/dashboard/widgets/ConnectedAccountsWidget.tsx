import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Link2 } from 'lucide-react'
import './ConnectedAccountsWidget.css'

const CIRCLE_RADIUS = 17
const CIRCUMFERENCE = 2 * Math.PI * CIRCLE_RADIUS // ~106.81

function getAccuracyColor(accuracy: number | null): string {
  if (accuracy == null) return 'var(--text-muted)'
  if (accuracy >= 85) return 'var(--success)'
  if (accuracy >= 65) return 'var(--warning)'
  return 'var(--error)'
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'var(--success)'
    case 'NEEDS_RECONNECTION': return 'var(--warning)'
    case 'ERROR': return 'var(--error)'
    default: return 'var(--text-muted)'
  }
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
    <div className="ca-row">
      {data.accounts.map(account => {
        const accuracy = account.linkedGroup?.accuracy ?? null
        const fgColor = getAccuracyColor(accuracy)
        const dashOffset = accuracy != null
          ? CIRCUMFERENCE * (1 - accuracy / 100)
          : CIRCUMFERENCE

        const modelLabel = account.linkedGroup?.name
          ?? account.modelPortfolioName
          ?? null

        return (
          <div
            key={account.connectionId}
            className="ca-card"
            onClick={() => window.location.href = `/brokers/accounts/${account.connectionId}`}
          >
            <div className="ca-card-info">
              <div className="ca-card-top">
                <span
                  className="ca-dot"
                  style={{ backgroundColor: getStatusColor(account.status) }}
                />
                <span className="ca-card-name">
                  {account.accountName || account.brokerName}
                </span>
              </div>
              <div className="ca-card-broker">{account.brokerName}</div>
              {modelLabel ? (
                <span className="ca-card-model">{modelLabel}</span>
              ) : (
                <span className="ca-card-no-model">No model</span>
              )}
            </div>

            <div className="ca-accuracy">
              <svg viewBox="0 0 40 40" width="40" height="40">
                <circle
                  className="ca-bg-ring"
                  cx="20"
                  cy="20"
                  r={CIRCLE_RADIUS}
                />
                <circle
                  className="ca-fg-ring"
                  cx="20"
                  cy="20"
                  r={CIRCLE_RADIUS}
                  style={{
                    stroke: fgColor,
                    strokeDasharray: CIRCUMFERENCE,
                    strokeDashoffset: dashOffset,
                  }}
                />
              </svg>
              <span className="ca-accuracy-text" style={{ color: fgColor }}>
                {accuracy != null ? `${accuracy.toFixed(0)}%` : '\u2014'}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}
