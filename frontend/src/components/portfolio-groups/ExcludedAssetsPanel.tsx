import { useState } from 'react'
import type { ExcludedAsset } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'

interface ExcludedAssetsPanelProps {
  excludedAssets: ExcludedAsset[]
  onAdd: (symbol: string) => void
  onRemove: (symbol: string) => void
  isUpdating: boolean
}

export function ExcludedAssetsPanel({ excludedAssets, onAdd, onRemove, isUpdating }: ExcludedAssetsPanelProps) {
  const [newSymbol, setNewSymbol] = useState('')

  const handleAdd = () => {
    if (!newSymbol.trim()) return
    onAdd(newSymbol.trim().toUpperCase())
    setNewSymbol('')
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleAdd()
  }

  return (
    <div className="excluded-assets-panel">
      <div className="excluded-add-row">
        <input
          type="text"
          value={newSymbol}
          onChange={e => setNewSymbol(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Enter symbol to exclude..."
          className="excluded-symbol-input"
          disabled={isUpdating}
        />
        <button
          className="btn-primary"
          onClick={handleAdd}
          disabled={!newSymbol.trim() || isUpdating}
        >
          Exclude
        </button>
      </div>

      {excludedAssets.length === 0 ? (
        <p className="text-muted" style={{ marginTop: '0.75rem' }}>No excluded assets.</p>
      ) : (
        <div className="excluded-list">
          {excludedAssets.map(asset => (
            <div key={asset.symbol} className="excluded-item">
              <div className="excluded-info">
                <span className="excluded-symbol">{asset.symbol}</span>
                {asset.securityName && <span className="excluded-name">{asset.securityName}</span>}
                {asset.currentValue != null && (
                  <span className="excluded-value">{formatCurrency(asset.currentValue, asset.currency || 'CAD')}</span>
                )}
              </div>
              <button
                className="excluded-remove-btn"
                onClick={() => onRemove(asset.symbol)}
                disabled={isUpdating}
              >
                &times;
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
