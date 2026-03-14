import type { PortfolioGroupSettings, UpdateSettingsRequest } from '../../types/portfolioGroup'

interface SettingsPanelProps {
  settings: PortfolioGroupSettings
  onUpdate: (request: UpdateSettingsRequest) => void
  isUpdating: boolean
}

const SETTINGS_CONFIG = [
  {
    key: 'sellToRebalance' as const,
    label: 'Sell to Rebalance',
    description: 'Allow selling overweight positions to rebalance. When disabled, only uses available cash for buys.'
  },
  {
    key: 'keepCurrenciesSeparate' as const,
    label: 'Keep Currencies Separate',
    description: 'Only use cash in the same currency as the security being purchased.'
  },
  {
    key: 'preventNonTradableTrades' as const,
    label: 'Prevent Non-Tradable Trades',
    description: 'Skip trade recommendations for securities that cannot be traded in the linked accounts.'
  },
  {
    key: 'notifyNewAssets' as const,
    label: 'Notify New Assets',
    description: 'Show alerts when new positions are detected that are not in the target allocation.'
  },
  {
    key: 'retainCashForExchange' as const,
    label: 'Retain Cash for Exchange',
    description: 'Reserve a portion of cash for currency exchange fees.'
  }
]

export function SettingsPanel({ settings, onUpdate, isUpdating }: SettingsPanelProps) {
  const handleToggle = (key: keyof PortfolioGroupSettings) => {
    onUpdate({ [key]: !settings[key] })
  }

  return (
    <div className="settings-panel">
      {SETTINGS_CONFIG.map(setting => (
        <div key={setting.key} className="setting-item">
          <div className="setting-info">
            <label className="setting-label">{setting.label}</label>
            <p className="setting-description">{setting.description}</p>
          </div>
          <label className="toggle-switch">
            <input
              type="checkbox"
              checked={settings[setting.key]}
              onChange={() => handleToggle(setting.key)}
              disabled={isUpdating}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
      ))}
    </div>
  )
}
