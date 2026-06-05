import { useState } from 'react'
import type { ConnectBrokerRequest } from '../../types/broker'
import './ConnectBrokerDialog.css'

interface ConnectBrokerDialogProps {
  brokerType: string
  onConnect: (request: ConnectBrokerRequest) => void
  onCancel: () => void
  isConnecting: boolean
  error: string | null
}

export function ConnectBrokerDialog({
  brokerType,
  onConnect,
  onCancel,
  isConnecting,
  error
}: ConnectBrokerDialogProps) {
  const [refreshToken, setRefreshToken] = useState('')
  const [usePractice, setUsePractice] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!refreshToken.trim()) return

    onConnect({
      brokerType: brokerType.toUpperCase(),
      credentials: {
        refreshToken: refreshToken.trim(),
        usePractice
      }
    })
  }

  const brokerName = brokerType.charAt(0).toUpperCase() + brokerType.slice(1).toLowerCase()

  return (
    <div className="connect-dialog-overlay" onClick={onCancel}>
      <div className="connect-dialog" onClick={e => e.stopPropagation()}>
        <div className="connect-dialog-header">
          <h2>Connect {brokerName}</h2>
          <button className="connect-dialog-close" onClick={onCancel}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="connect-dialog-body">
            <div className="connect-dialog-instructions">
              <p>To connect your {brokerName} account:</p>
              <ol>
                <li>
                  Log in to{' '}
                  <a
                    href="https://login.questrade.com/APIAccess/UserApps.aspx"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Questrade API Hub
                  </a>
                </li>
                <li>Click &quot;Register a personal app&quot; (if not already done)</li>
                <li>Generate a new API token</li>
                <li>Paste the token below</li>
              </ol>
            </div>

            <div className="connect-dialog-field">
              <label htmlFor="refresh-token">API Token (Refresh Token)</label>
              <input
                id="refresh-token"
                type="password"
                value={refreshToken}
                onChange={e => setRefreshToken(e.target.value)}
                placeholder="Paste your Questrade API token"
                autoFocus
                disabled={isConnecting}
              />
            </div>

            <div className="connect-dialog-field connect-dialog-checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={usePractice}
                  onChange={e => setUsePractice(e.target.checked)}
                  disabled={isConnecting}
                />
                Use practice (demo) account
              </label>
            </div>

            {error && (
              <div className="connect-dialog-error">{error}</div>
            )}
          </div>

          <div className="connect-dialog-footer">
            <button
              type="button"
              className="connect-dialog-btn cancel"
              onClick={onCancel}
              disabled={isConnecting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="connect-dialog-btn connect"
              disabled={isConnecting || !refreshToken.trim()}
            >
              {isConnecting ? 'Connecting...' : 'Connect'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
