import { useParams, Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { DashboardGrid } from '../components/dashboard/DashboardGrid'
import { useBrokerConnections } from '../hooks/useBrokerConnections'
import './AccountDetailPage.css'

export function AccountDetailPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const connId = connectionId ? parseInt(connectionId, 10) : undefined
  const { data: connectionsData } = useBrokerConnections()

  /* Find the connection to display breadcrumb label */
  const connection = connectionsData?.connections?.find(c => c.id === connId)
  const accountLabel = connection?.accountMetaType
    || connection?.accountType
    || connection?.accountName
    || `Account ${connectionId}`

  return (
    <div className="account-detail-page">
      {/* Breadcrumb */}
      <nav className="account-breadcrumb">
        <Link to="/brokers/connections" className="breadcrumb-link">Accounts</Link>
        <ChevronRight size={14} className="breadcrumb-separator" />
        <span className="breadcrumb-current">{accountLabel}</span>
      </nav>

      <DashboardGrid connectionId={connId} contextType="ACCOUNT" />
    </div>
  )
}
