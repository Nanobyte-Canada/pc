import { useDashboardIrr } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './IrrWidget.css'

function formatIrr(irr: number | null): string {
  if (irr === null) return '—'
  return `${irr >= 0 ? '+' : ''}${irr.toFixed(2)}%`
}

function irrClass(irr: number | null): string {
  if (irr === null) return 'neutral'
  return irr >= 0 ? 'positive' : 'negative'
}

export default function IrrWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardIrr(connectionId)

  if (isLoading || !data) return <Skeleton style={{ height: '8rem', width: '100%' }} />

  const showAccounts = !connectionId && data.accounts.length > 0

  if (data.portfolioIrr === null && data.accounts.every(a => a.irr === null)) {
    return <div className="irr-empty">No return data available yet</div>
  }

  return (
    <div>
      <div className="irr-headline">
        <span className={`irr-value ${irrClass(data.portfolioIrr)}`}>
          {formatIrr(data.portfolioIrr)}
        </span>
        <span className="irr-label">annualized</span>
      </div>

      {showAccounts && (
        <div className="irr-accounts">
          {data.accounts.map(acct => (
            <div key={acct.connectionId} className="irr-account-row">
              <span className="irr-account-name">
                {acct.accountName || acct.brokerName || `Account #${acct.connectionId}`}
              </span>
              <span className={`irr-account-value ${irrClass(acct.irr)}`}>
                {formatIrr(acct.irr)}
              </span>
            </div>
          ))}
        </div>
      )}

      {data.accounts.length > 0 && data.accounts[0].startDate && (
        <div className="irr-period">
          Since {data.accounts[0].startDate}
        </div>
      )}
    </div>
  )
}
