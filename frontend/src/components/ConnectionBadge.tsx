import { useQuoteStore } from '@/stores/quoteStore'
import './ConnectionBadge.css'

export function ConnectionBadge({ compact = false }: { compact?: boolean }) {
  const providerConnected = useQuoteStore((state) => state.providerConnected)

  const status = providerConnected === true
    ? 'connected'
    : providerConnected === false
      ? 'disconnected'
      : 'connecting'

  const label = status === 'connected'
    ? 'MD'
    : status === 'connecting'
      ? 'MD...'
      : 'MD'

  return (
    <div
      className={`connection-badge connection-badge--${status}${compact ? ' connection-badge--compact' : ''}`}
      title={`Market Data: ${status}`}
    >
      <span className="connection-badge__dot" />
      <span>{label}</span>
    </div>
  )
}
