import { useToastStore } from '@/stores/toastStore'
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react'
import './toast.css'

const ICONS = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
}

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore()

  if (toasts.length === 0) return null

  return (
    <div className="toast-container">
      {toasts.map((toast) => {
        const Icon = ICONS[toast.type]
        return (
          <div key={toast.id} className={`toast toast--${toast.type}`}>
            <Icon className="toast-icon" />
            <span className="toast-message">{toast.message}</span>
            <button className="toast-dismiss" onClick={() => removeToast(toast.id)}>
              <X style={{ width: '0.875rem', height: '0.875rem' }} />
            </button>
          </div>
        )
      })}
    </div>
  )
}
