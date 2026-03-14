import { useState } from 'react'
import type { TargetAllocation } from '../../types/portfolioGroup'

interface TargetAllocationsEditorProps {
  targets: TargetAllocation[]
  onSave: (targets: { symbol: string; targetPercent: number }[]) => void
  isSaving: boolean
}

export function TargetAllocationsEditor({ targets, onSave, isSaving }: TargetAllocationsEditorProps) {
  const [editableTargets, setEditableTargets] = useState(
    targets.map(t => ({ symbol: t.symbol, targetPercent: t.targetPercent }))
  )
  const [newSymbol, setNewSymbol] = useState('')
  const [newPercent, setNewPercent] = useState('')

  const totalPercent = editableTargets.reduce((sum, t) => sum + t.targetPercent, 0)
  const isValid = totalPercent <= 100

  const handleAdd = () => {
    if (!newSymbol.trim() || !newPercent) return
    const percent = parseFloat(newPercent)
    if (isNaN(percent) || percent <= 0 || percent > 100) return
    if (editableTargets.some(t => t.symbol.toUpperCase() === newSymbol.trim().toUpperCase())) return

    setEditableTargets([...editableTargets, { symbol: newSymbol.trim().toUpperCase(), targetPercent: percent }])
    setNewSymbol('')
    setNewPercent('')
  }

  const handleRemove = (index: number) => {
    setEditableTargets(editableTargets.filter((_, i) => i !== index))
  }

  const handlePercentChange = (index: number, value: string) => {
    const percent = parseFloat(value)
    if (isNaN(percent)) return
    const updated = [...editableTargets]
    updated[index] = { ...updated[index], targetPercent: percent }
    setEditableTargets(updated)
  }

  const handleSave = () => {
    if (!isValid) return
    onSave(editableTargets)
  }

  return (
    <div className="target-allocations-editor">
      <table className="targets-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Target %</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {editableTargets.map((target, index) => (
            <tr key={target.symbol}>
              <td className="target-symbol">{target.symbol}</td>
              <td>
                <input
                  type="number"
                  value={target.targetPercent}
                  onChange={e => handlePercentChange(index, e.target.value)}
                  min={0}
                  max={100}
                  step={0.01}
                  className="target-percent-input"
                />
              </td>
              <td>
                <button className="target-remove-btn" onClick={() => handleRemove(index)}>&times;</button>
              </td>
            </tr>
          ))}
          <tr className="add-target-row">
            <td>
              <input
                type="text"
                value={newSymbol}
                onChange={e => setNewSymbol(e.target.value)}
                placeholder="Symbol"
                className="target-symbol-input"
              />
            </td>
            <td>
              <input
                type="number"
                value={newPercent}
                onChange={e => setNewPercent(e.target.value)}
                placeholder="%"
                min={0}
                max={100}
                step={0.01}
                className="target-percent-input"
              />
            </td>
            <td>
              <button className="target-add-btn" onClick={handleAdd} disabled={!newSymbol.trim() || !newPercent}>
                +
              </button>
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td><strong>Total</strong></td>
            <td>
              <strong style={{ color: totalPercent > 100 ? '#dc2626' : totalPercent === 100 ? '#059669' : 'inherit' }}>
                {totalPercent.toFixed(2)}%
              </strong>
            </td>
            <td></td>
          </tr>
        </tfoot>
      </table>
      {totalPercent > 100 && (
        <div className="target-error">Total allocation exceeds 100%</div>
      )}
      <div className="target-actions">
        <button className="btn-primary" onClick={handleSave} disabled={!isValid || isSaving}>
          {isSaving ? 'Saving...' : 'Save Targets'}
        </button>
      </div>
    </div>
  )
}
