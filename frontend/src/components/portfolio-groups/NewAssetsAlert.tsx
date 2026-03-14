import type { NewAsset } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'

interface NewAssetsAlertProps {
  newAssets: NewAsset[]
  onExclude: (symbol: string) => void
}

export function NewAssetsAlert({ newAssets, onExclude }: NewAssetsAlertProps) {
  if (newAssets.length === 0) return null

  return (
    <div className="new-assets-alert">
      <div className="alert-header">
        <strong>New Assets Detected</strong>
        <span className="alert-count">{newAssets.length}</span>
      </div>
      <p className="alert-description">
        The following positions were found in your linked accounts but are not in your target allocation.
        Add them to your targets or exclude them.
      </p>
      <div className="new-assets-list">
        {newAssets.map(asset => (
          <div key={asset.symbol} className="new-asset-item">
            <div className="new-asset-info">
              <span className="new-asset-symbol">{asset.symbol}</span>
              {asset.securityName && <span className="new-asset-name">{asset.securityName}</span>}
              {asset.currentValue != null && (
                <span className="new-asset-value">{formatCurrency(asset.currentValue, asset.currency || 'CAD')}</span>
              )}
            </div>
            <button className="exclude-btn" onClick={() => onExclude(asset.symbol)}>
              Exclude
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
