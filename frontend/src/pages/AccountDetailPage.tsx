import { useParams } from 'react-router-dom'
import { DashboardGrid } from '../components/dashboard/DashboardGrid'
import './AccountDetailPage.css'

export function AccountDetailPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const connId = connectionId ? parseInt(connectionId, 10) : undefined

  return (
    <div className="account-detail-page">
      <DashboardGrid connectionId={connId} contextType="ACCOUNT" />
    </div>
  )
}
