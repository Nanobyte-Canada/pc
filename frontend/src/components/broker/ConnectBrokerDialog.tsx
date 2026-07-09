import { useState } from 'react'
import type { ConnectBrokerRequest } from '../../types/broker'
import './ConnectBrokerDialog.css'

interface BrokerCredentialField {
  key: string
  label: string
  type: 'password' | 'text' | 'number' | 'checkbox'
  placeholder?: string
}

interface BrokerConfig {
  name: string
  instructions: string[]
  links?: { label: string; url: string }[]
  fields: BrokerCredentialField[]
}

const BROKER_CONFIGS: Record<string, BrokerConfig> = {
  questrade: {
    name: 'Questrade',
    instructions: [
      'Log in to your Questrade account',
      'Go to the API Access page in your account settings',
      'Register a personal app (if not already done)',
      'Generate a new API token',
      'Paste the token below'
    ],
    links: [
      { label: 'Questrade API Hub', url: 'https://login.questrade.com/APIAccess/UserApps.aspx' }
    ],
    fields: [
      { key: 'refreshToken', label: 'API Token (Refresh Token)', type: 'password', placeholder: 'Paste your Questrade API token' }
    ]
  },
  wealthsimple: {
    name: 'Wealthsimple',
    instructions: [
      'Enter your Wealthsimple account email and password',
      'An authorization code will be sent to your email',
      'Enter the code to complete the connection'
    ],
    fields: [
      { key: 'email', label: 'Email', type: 'text', placeholder: 'your-email@example.com' },
      { key: 'password', label: 'Password', type: 'password', placeholder: 'Enter your Wealthsimple password' }
    ]
  },
  ibkr: {
    name: 'Interactive Brokers',
    instructions: [
      'Ensure your IBKR TWS or IB Gateway is running and configured for API access',
      'Enter the connection details below',
      'Make sure the "Enable ActiveX and Socket Clients" setting is enabled in TWS'
    ],
    fields: [
      { key: 'host', label: 'Host', type: 'text', placeholder: 'localhost' },
      { key: 'port', label: 'Port', type: 'number', placeholder: '7497' },
      { key: 'clientId', label: 'Client ID', type: 'number', placeholder: '1' }
    ]
  }
}

const DEFAULT_CONFIG: BrokerConfig = {
  name: 'Broker',
  instructions: ['Configure your broker connection using the fields below.'],
  fields: [
    { key: 'apiKey', label: 'API Key', type: 'password', placeholder: 'Enter your API key' }
  ]
}

interface ConnectBrokerDialogProps {
  brokerType: string
  onConnect: (request: ConnectBrokerRequest) => void
  onReconnect?: (request: { connectionId: string; credentials: Record<string, unknown> }) => void
  onCancel: () => void
  isConnecting: boolean
  error: string | null
  connectionId?: string
}

export function ConnectBrokerDialog({
  brokerType,
  onConnect,
  onReconnect,
  onCancel,
  isConnecting,
  error,
  connectionId
}: ConnectBrokerDialogProps) {
  const brokerSlug = brokerType.toLowerCase()
  const config = BROKER_CONFIGS[brokerSlug] || DEFAULT_CONFIG
  const isReconnect = !!connectionId

  const [fieldValues, setFieldValues] = useState<Record<string, string | boolean>>(() => {
    const initial: Record<string, string | boolean> = {}
    config.fields.forEach(f => {
      initial[f.key] = f.type === 'checkbox' ? false : ''
    })
    return initial
  })

  const handleFieldChange = (key: string, value: string | boolean) => {
    setFieldValues(prev => ({ ...prev, [key]: value }))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const credentials: Record<string, unknown> = {}
    for (const field of config.fields) {
      if (field.type === 'checkbox') {
        credentials[field.key] = fieldValues[field.key] as boolean
      } else if (fieldValues[field.key]) {
        credentials[field.key] = fieldValues[field.key]
      }
    }

    if (isReconnect && onReconnect && connectionId) {
      onReconnect({ connectionId, credentials })
    } else {
      onConnect({
        brokerType: brokerType.toUpperCase(),
        credentials
      })
    }
  }

  const isFormValid = () => {
    return config.fields
      .filter(f => f.type !== 'checkbox')
      .every(f => {
        const val = fieldValues[f.key]
        return typeof val === 'string' && val.trim().length > 0
      })
  }

  const brokerName = config.name
  const buttonLabel = isConnecting
    ? (isReconnect ? 'Reconnecting...' : 'Connecting...')
    : (isReconnect ? 'Reconnect' : 'Connect')

  return (
    <div className="connect-dialog-overlay" data-testid="dialog-overlay" onClick={onCancel}>
      <div className="connect-dialog" onClick={e => e.stopPropagation()}>
        <div className="connect-dialog-header">
          <h2>{isReconnect ? 'Reconnect' : 'Connect'} {brokerName}</h2>
          <button className="connect-dialog-close" onClick={onCancel}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="connect-dialog-body">
            <div className="connect-dialog-instructions">
              <p>To {isReconnect ? 'reconnect' : 'connect'} your {brokerName} account:</p>
              <ol>
                {config.instructions.map((instruction, i) => (
                  <li key={i}>{instruction}</li>
                ))}
                {config.links?.map((link, i) => (
                  <li key={`link-${i}`}>
                    Visit{' '}
                    <a
                      href={link.url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ol>
            </div>

            {config.fields.map(field => (
              <div
                key={field.key}
                className={`connect-dialog-field${field.type === 'checkbox' ? ' connect-dialog-checkbox' : ''}`}
              >
                {field.type === 'checkbox' ? (
                  <label>
                    <input
                      type="checkbox"
                      checked={!!fieldValues[field.key]}
                      onChange={e => handleFieldChange(field.key, e.target.checked)}
                      disabled={isConnecting}
                    />
                    {field.label}
                  </label>
                ) : (
                  <>
                    <label htmlFor={`field-${field.key}`}>{field.label}</label>
                    <input
                      id={`field-${field.key}`}
                      type={field.type}
                      value={fieldValues[field.key] as string}
                      onChange={e => handleFieldChange(field.key, e.target.value)}
                      placeholder={field.placeholder}
                      autoFocus={field === config.fields[0]}
                      disabled={isConnecting}
                    />
                  </>
                )}
              </div>
            ))}

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
              disabled={isConnecting || !isFormValid()}
            >
              {buttonLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
