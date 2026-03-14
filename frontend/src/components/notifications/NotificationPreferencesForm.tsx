import { useNotificationPreferences, useUpdateNotificationPreferences } from '../../hooks/useNotifications'

export function NotificationPreferencesForm() {
  const { data: prefs, isLoading } = useNotificationPreferences()
  const updatePrefs = useUpdateNotificationPreferences()

  if (isLoading || !prefs) {
    return <p className="text-muted">Loading preferences...</p>
  }

  const toggle = (key: string, value: boolean) => {
    updatePrefs.mutate({ [key]: !value })
  }

  const items = [
    { key: 'driftAlerts', label: 'Drift Alerts', description: 'Get notified when portfolio accuracy drops', value: prefs.driftAlerts },
    { key: 'orderAlerts', label: 'Order Alerts', description: 'Notifications for order fills, rejections, and failures', value: prefs.orderAlerts },
    { key: 'syncFailureAlerts', label: 'Sync Failure Alerts', description: 'Alert when broker data sync fails', value: prefs.syncFailureAlerts },
    { key: 'newAssetAlerts', label: 'New Asset Alerts', description: 'Alert when new assets appear in linked accounts', value: prefs.newAssetAlerts },
    { key: 'rebalanceReminder', label: 'Rebalance Reminders', description: 'Periodic reminders to check portfolio balance', value: prefs.rebalanceReminder },
    { key: 'emailEnabled', label: 'Email Notifications', description: 'Receive notifications via email', value: prefs.emailEnabled },
    { key: 'inAppEnabled', label: 'In-App Notifications', description: 'Show notifications in the app', value: prefs.inAppEnabled }
  ]

  return (
    <div className="settings-panel">
      {items.map(item => (
        <div key={item.key} className="setting-item">
          <div className="setting-info">
            <label className="setting-label" onClick={() => toggle(item.key, item.value)}>
              {item.label}
            </label>
            <p className="setting-description">{item.description}</p>
          </div>
          <label className="toggle-switch">
            <input
              type="checkbox"
              checked={item.value}
              onChange={() => toggle(item.key, item.value)}
            />
            <span className="toggle-slider" />
          </label>
        </div>
      ))}
    </div>
  )
}
