import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { useNavigate } from 'react-router-dom'
import './AccountTabs.css'

interface AccountTabsProps {
  selectedConnectionId?: number
  onSelect: (connectionId: number) => void
}

const STATUS_DOT_CLASS: Record<string, string> = {
  ACTIVE: 'at-dot-active',
  NEEDS_RECONNECTION: 'at-dot-warning',
  ERROR: 'at-dot-error',
}

export function AccountTabs({ selectedConnectionId, onSelect }: AccountTabsProps) {
  const { data, isLoading } = useDashboardAccounts()
  const navigate = useNavigate()

  if (isLoading || !data) {
    return (
      <div className="at-row">
        {[1, 2, 3].map(i => (
          <Skeleton key={i} style={{ height: '5rem', width: '14rem', borderRadius: '0.75rem' }} />
        ))}
      </div>
    )
  }

  if (data.accounts.length === 0) return null

  return (
    <div className="at-row">
      {data.accounts.map(account => {
        const isSelected = account.connectionId === selectedConnectionId
        return (
          <button
            key={account.connectionId}
            className={`at-card ${isSelected ? 'at-card-selected' : ''}`}
            onClick={() => onSelect(account.connectionId)}
            onDoubleClick={() => navigate(`/brokers/accounts/${account.connectionId}`)}
          >
            <div className="at-card-top">
              <span className={`at-status-dot ${STATUS_DOT_CLASS[account.status] || 'at-dot-default'}`} />
              <span className="at-account-name">{account.accountName}</span>
            </div>
            <div className="at-broker-name">{account.brokerName}</div>
            <div className="at-account-number">{account.accountNumber || '-'}</div>
          </button>
        )
      })}
    </div>
  )
}
