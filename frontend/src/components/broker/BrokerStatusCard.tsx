import { RefreshCw, Loader2, CheckCircle, XCircle, AlertTriangle } from 'lucide-react'
import type { GatewayHealthResponse } from '../../services/brokerService'
import './BrokerStatusCard.css'

interface BrokerStatusCardProps {
  health: GatewayHealthResponse | undefined
  isLoading: boolean
  error: Error | null
  onRefresh: () => void
}

const BROKER_LABELS: Record<string, string> = {
  IBKR: 'Interactive Brokers',
  QUESTRADE: 'Questrade',
  WEALTHSIMPLE: 'Wealthsimple'
}

export function BrokerStatusCard({ health, isLoading, error, onRefresh }: BrokerStatusCardProps) {
  return (
    <section className="broker-status-section">
      <div className="broker-status-header">
        <h2>Gateway Status</h2>
        <button
          className="broker-status-refresh-btn"
          onClick={onRefresh}
          disabled={isLoading}
          title="Refresh gateway status"
        >
          <RefreshCw size={14} className={isLoading ? 'spinning' : ''} />
          Refresh
        </button>
      </div>

      {isLoading ? (
        <div className="broker-status-loading">
          <Loader2 size={16} className="spinning" />
          <span>Checking gateway connection...</span>
        </div>
      ) : error ? (
        <div className="broker-status-error">
          <XCircle size={18} />
          <div className="broker-status-error-content">
            <span className="broker-status-error-title">Gateway Unreachable</span>
            <span className="broker-status-error-detail">
              Broker gateway is unreachable. Check your configuration and ensure the broker-gateway service is running.
            </span>
          </div>
        </div>
      ) : health ? (
        <>
          {/* Gateway connectivity status */}
          <div className={`broker-status-gateway ${health.status === 'UP' ? 'up' : 'down'}`}>
            {health.status === 'UP' ? (
              <CheckCircle size={16} />
            ) : (
              <AlertTriangle size={16} />
            )}
            <span>Gateway: {health.status === 'UP' ? 'Connected' : health.status}</span>
          </div>

          {/* Per-broker status list */}
          {health.brokers.length > 0 ? (
            <div className="broker-status-list">
              {health.brokers.map(broker => (
                <div
                  key={broker.brokerType}
                  className={`broker-status-item ${broker.enabled ? 'enabled' : 'disabled'}`}
                >
                  <div className="broker-status-item-left">
                    <span className={`broker-status-dot ${broker.enabled ? 'dot-enabled' : 'dot-disabled'}`} />
                    <span className="broker-status-item-name">
                      {BROKER_LABELS[broker.brokerType] || broker.brokerType}
                    </span>
                  </div>
                  <div className="broker-status-item-right">
                    <span className={`broker-status-item-badge ${broker.enabled ? 'badge-enabled' : 'badge-disabled'}`}>
                      {broker.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                    {!broker.enabled && (
                      <span className="broker-status-item-hint">
                        Set {broker.brokerType}_ENABLED=true
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="broker-status-empty">
              No brokers reported by gateway.
            </div>
          )}
        </>
      ) : null}
    </section>
  )
}
