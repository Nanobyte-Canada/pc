import { useParams, useNavigate } from 'react-router-dom'
import { DashboardGrid } from '../components/dashboard/DashboardGrid'
import { AccountTabs } from '@/components/dashboard/AccountTabs'
import './AccountDetailPage.css'

export function AccountDetailPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const connId = connectionId ? parseInt(connectionId, 10) : undefined
  const navigate = useNavigate()

  return (
    <div className="account-detail-page">
      {/* Account Tabs */}
      <AccountTabs
        selectedConnectionId={connId}
        onSelect={(id) => navigate(`/brokers/accounts/${id}`)}
      />

      <DashboardGrid connectionId={connId} contextType="ACCOUNT" />
    </div>
  )
}
