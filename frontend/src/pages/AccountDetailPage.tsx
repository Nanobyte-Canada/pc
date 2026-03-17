import { useParams } from 'react-router-dom'
import { DashboardGrid } from '../components/dashboard/DashboardGrid'
import { useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './AccountDetailPage.css'

function maskAccountNumber(num: string | null) {
  if (!num || num.length < 4) return num ?? ''
  return '••••' + num.slice(-4)
}

function accuracyColor(accuracy: number) {
  if (accuracy >= 90) return '#059669'
  if (accuracy >= 75) return '#d97706'
  return '#dc2626'
}

export function AccountDetailPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const connId = connectionId ? parseInt(connectionId, 10) : undefined
  const { data, isLoading } = useDashboardAccounts()

  const account = data?.accounts.find(a => a.connectionId === connId)

  return (
    <div className="account-detail-page">
      {isLoading ? (
        <Skeleton style={{ height: '4.5rem', width: '100%', borderRadius: '0.75rem', marginBottom: '1.5rem' }} />
      ) : account ? (
        <div className="account-header">
          <div className="account-header-info">
            <h1 className="account-header-broker">{account.brokerName}</h1>
            <span className="account-header-subtitle">
              {account.accountName}{account.accountNumber ? ` · ${maskAccountNumber(account.accountNumber)}` : ''}
            </span>
            {account.linkedGroup && (
              <span className="account-header-model">Model: {account.linkedGroup.name}</span>
            )}
          </div>
          <div
            className="account-header-accuracy"
            style={{
              borderColor: accuracyColor(account.linkedGroup?.accuracy ?? 0),
              color: accuracyColor(account.linkedGroup?.accuracy ?? 0),
            }}
          >
            {(account.linkedGroup?.accuracy ?? 0).toFixed(0)}%
          </div>
        </div>
      ) : (
        <div className="account-header">
          <div className="account-header-info">
            <h1 className="account-header-broker">Account Dashboard</h1>
          </div>
        </div>
      )}
      <DashboardGrid connectionId={connId} contextType="ACCOUNT" />
    </div>
  )
}
