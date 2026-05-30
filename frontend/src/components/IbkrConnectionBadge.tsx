import { useQuoteStore } from '@/stores/quoteStore'
import './IbkrConnectionBadge.css'

export function IbkrConnectionBadge({ compact = false }: { compact?: boolean }) {
  const ibkrConnected = useQuoteStore((state) => state.ibkrConnected)

  const status = ibkrConnected === true
    ? 'connected'
    : ibkrConnected === false
      ? 'disconnected'
      : 'connecting'

  const label = status === 'connected'
    ? 'IBKR'
    : status === 'connecting'
      ? 'IBKR...'
      : 'IBKR'

  return (
    <div
      className={`ibkr-badge ibkr-badge--${status}${compact ? ' ibkr-badge--compact' : ''}`}
      title={`IBKR Gateway: ${status}`}
    >
      <span className="ibkr-badge__dot" />
      <span>{label}</span>
    </div>
  )
}
