import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUnreadCount, useNotifications, useMarkAsRead, useMarkAllAsRead } from '../../hooks/useNotifications'
import './NotificationBell.css'

export function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const { data: countData } = useUnreadCount()
  const { data: notificationsData } = useNotifications(false, 0)
  const markAsRead = useMarkAsRead()
  const markAllAsRead = useMarkAllAsRead()

  const unreadCount = countData?.unreadCount ?? 0
  const notifications = notificationsData?.notifications ?? []

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div className="notification-bell-container" ref={dropdownRef}>
      <button className="notification-bell-btn" onClick={() => setIsOpen(!isOpen)}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        {unreadCount > 0 && (
          <span className="notification-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      {isOpen && (
        <div className="notification-dropdown">
          <div className="notification-dropdown-header">
            <span>Notifications</span>
            {unreadCount > 0 && (
              <button className="mark-all-btn" onClick={() => markAllAsRead.mutate()}>
                Mark all read
              </button>
            )}
          </div>
          <div className="notification-dropdown-list">
            {notifications.length === 0 ? (
              <div className="notification-empty">No notifications</div>
            ) : (
              notifications.slice(0, 8).map(n => (
                <div
                  key={n.id}
                  className={`notification-item ${!n.isRead ? 'unread' : ''}`}
                  onClick={() => {
                    if (!n.isRead) markAsRead.mutate(n.id)
                    if (n.link) { navigate(n.link); setIsOpen(false) }
                  }}
                >
                  <div className="notification-item-content">
                    <span className="notification-item-title">{n.title}</span>
                    <span className="notification-item-message">{n.message}</span>
                  </div>
                  <span className="notification-item-time">
                    {new Date(n.createdAt).toLocaleDateString('en-CA', { month: 'short', day: 'numeric' })}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
