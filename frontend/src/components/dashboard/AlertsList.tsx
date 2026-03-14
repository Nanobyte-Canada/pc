import { useNavigate } from 'react-router-dom'
import type { Notification } from '../../types/notification'
import { useMarkAsRead } from '../../hooks/useNotifications'

interface AlertsListProps {
  alerts: Notification[]
}

export function AlertsList({ alerts }: AlertsListProps) {
  const navigate = useNavigate()
  const markAsRead = useMarkAsRead()

  const handleAlertClick = (alert: Notification) => {
    markAsRead.mutate(alert.id)
    if (alert.link) {
      navigate(alert.link)
    }
  }

  if (alerts.length === 0) {
    return (
      <div className="dashboard-card">
        <h3>Alerts</h3>
        <p className="text-muted">No active alerts.</p>
      </div>
    )
  }

  return (
    <div className="dashboard-card">
      <h3>Alerts</h3>
      <div className="alerts-list">
        {alerts.map(alert => (
          <div
            key={alert.id}
            className="alert-item"
            onClick={() => handleAlertClick(alert)}
          >
            <div className="alert-item-content">
              <span className="alert-item-title">{alert.title}</span>
              <span className="alert-item-message">{alert.message}</span>
            </div>
            <span className="alert-item-time">
              {new Date(alert.createdAt).toLocaleDateString('en-CA', { month: 'short', day: 'numeric' })}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
