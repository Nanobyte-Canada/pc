import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { X, Check, AlertCircle, Search } from 'lucide-react'
import { useNewInstrumentSearch } from '@/hooks/useNewScreener'
import {
  useModelPortfolio,
  useCreateModelPortfolio,
  useUpdateModelPortfolio,
} from '@/hooks/useModelPortfolios'
import { ModelAnalysisPanel } from '@/components/portfolios/ModelAnalysisPanel'
import type { RiskLevel, ModelAllocationInput } from '@/types/modelPortfolio'
import type { SearchResult, InstrumentType } from '@/types/screener'
import './CustomPortfolioBuilder.css'

// ---------- Constants ----------

const RISK_OPTIONS: { value: RiskLevel; label: string; modifier: string }[] = [
  { value: 'LOW', label: 'Low', modifier: 'low' },
  { value: 'MODERATE', label: 'Moderate', modifier: 'moderate' },
  { value: 'HIGH', label: 'High', modifier: 'high' },
  { value: 'EXTRA_HIGH', label: 'Aggressive', modifier: 'extra-high' },
]

interface Allocation {
  symbol: string
  name: string
  targetPercent: number
  assetClass?: string
}

// ---------- Props ----------

interface CustomPortfolioBuilderProps {
  existingModelId?: number
  onSaved?: () => void
}

// ---------- Component ----------

export function CustomPortfolioBuilder({ existingModelId, onSaved }: CustomPortfolioBuilderProps) {
  // Form state
  const [name, setName] = useState('')
  const [riskLevel, setRiskLevel] = useState<RiskLevel>('MODERATE')
  const [allocations, setAllocations] = useState<Allocation[]>([])
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [savedModelId, setSavedModelId] = useState<number | null>(null)

  // Search state
  const [searchQuery, setSearchQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [searchOpen, setSearchOpen] = useState(false)
  const [selectedIndex, setSelectedIndex] = useState(-1)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Queries & mutations
  const { data: existingModel } = useModelPortfolio(existingModelId ?? 0, !!existingModelId)
  const createMutation = useCreateModelPortfolio()
  const updateMutation = useUpdateModelPortfolio()
  const { data: searchData, isLoading: searchLoading } = useNewInstrumentSearch(debouncedQuery, undefined, 10)

  const isEditing = !!existingModelId

  // Populate form when editing an existing model
  useEffect(() => {
    if (existingModel) {
      setName(existingModel.name)
      setRiskLevel(existingModel.riskLevel)
      setAllocations(
        existingModel.allocations.map(a => ({
          symbol: a.symbol,
          name: a.symbol, // Backend returns symbol; name not available in allocation
          targetPercent: a.targetPercent,
          assetClass: a.assetClass ?? undefined,
        }))
      )
    }
  }, [existingModel])

  // Debounce the search query
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(searchQuery), 200)
    return () => clearTimeout(timer)
  }, [searchQuery])

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        searchInputRef.current &&
        !searchInputRef.current.contains(event.target as Node)
      ) {
        setSearchOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Auto-dismiss success message
  useEffect(() => {
    if (successMsg) {
      const timer = setTimeout(() => setSuccessMsg(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [successMsg])

  // ---------- Derived State ----------

  const totalWeight = allocations.reduce((sum, a) => sum + a.targetPercent, 0)
  const isWeightValid = Math.abs(totalWeight - 100) < 0.05
  const isWeightOver = totalWeight > 100.05
  const canSave = name.trim().length > 0 && allocations.length > 0 && !isWeightOver
  const isSaving = createMutation.isPending || updateMutation.isPending
  const searchResults = searchData?.data ?? []

  const existingSymbols = useMemo(() => new Set(allocations.map(a => a.symbol.toUpperCase())), [allocations])

  // ---------- Handlers ----------

  const handleAddInstrument = useCallback((result: SearchResult) => {
    if (existingSymbols.has(result.ticker.toUpperCase())) return

    setAllocations(prev => [
      ...prev,
      {
        symbol: result.ticker,
        name: result.name,
        targetPercent: 0,
        assetClass: result.type === 'ETF' ? 'ETF' : 'EQUITY',
      },
    ])
    setSearchQuery('')
    setSearchOpen(false)
    searchInputRef.current?.focus()
  }, [existingSymbols])

  const handleRemoveAllocation = (symbol: string) => {
    setAllocations(prev => prev.filter(a => a.symbol !== symbol))
  }

  const handleWeightChange = (symbol: string, value: string) => {
    const num = parseFloat(value)
    if (isNaN(num) || num < 0) return
    setAllocations(prev =>
      prev.map(a => (a.symbol === symbol ? { ...a, targetPercent: num } : a))
    )
  }

  const handleNormalize = () => {
    if (allocations.length === 0 || totalWeight === 0) return
    const factor = 100 / totalWeight
    setAllocations(prev =>
      prev.map(a => ({
        ...a,
        targetPercent: Math.round(a.targetPercent * factor * 10) / 10,
      }))
    )
  }

  const handleClearAll = () => {
    setAllocations([])
    setErrorMsg(null)
    setSuccessMsg(null)
  }

  const handleSave = () => {
    if (!canSave) return
    setErrorMsg(null)
    setSuccessMsg(null)

    const allocationInputs: ModelAllocationInput[] = allocations.map(a => ({
      symbol: a.symbol,
      targetPercent: a.targetPercent,
      assetClass: a.assetClass,
    }))

    if (isEditing && existingModelId) {
      updateMutation.mutate(
        { id: existingModelId, request: { name: name.trim(), riskLevel, allocations: allocationInputs } },
        {
          onSuccess: () => {
            setSuccessMsg('Portfolio updated successfully')
            setSavedModelId(existingModelId)
            onSaved?.()
          },
          onError: (err) => {
            setErrorMsg(err instanceof Error ? err.message : 'Failed to update portfolio')
          },
        }
      )
    } else {
      createMutation.mutate(
        { name: name.trim(), riskLevel, allocations: allocationInputs },
        {
          onSuccess: (result) => {
            setSuccessMsg('Portfolio created successfully')
            setSavedModelId(result.id)
            onSaved?.()
          },
          onError: (err) => {
            setErrorMsg(err instanceof Error ? err.message : 'Failed to create portfolio')
          },
        }
      )
    }
  }

  const handleSearchKeyDown = (event: React.KeyboardEvent) => {
    if (!searchOpen || searchResults.length === 0) return

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setSelectedIndex(prev => Math.min(prev + 1, searchResults.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setSelectedIndex(prev => Math.max(prev - 1, 0))
        break
      case 'Enter':
        event.preventDefault()
        if (selectedIndex >= 0 && selectedIndex < searchResults.length) {
          handleAddInstrument(searchResults[selectedIndex])
        }
        break
      case 'Escape':
        setSearchOpen(false)
        setSelectedIndex(-1)
        break
    }
  }

  const getTypeClass = (type: InstrumentType) =>
    type === 'STOCK' ? 'type-stock' : type === 'ETF' ? 'type-etf' : 'type-other'

  const getTypeLabel = (type: InstrumentType) =>
    type === 'STOCK' ? 'Stock' : type === 'ETF' ? 'ETF' : type

  // ---------- Weight status ----------

  const weightStatusClass = isWeightValid
    ? 'cpb__total-weight--valid'
    : isWeightOver
      ? 'cpb__total-weight--over'
      : totalWeight > 0
        ? 'cpb__total-weight--under'
        : ''

  // ---------- Render ----------

  return (
    <div className="cpb">
      <h2 className="cpb__title">{isEditing ? 'Edit Custom Portfolio' : 'Build Custom Portfolio'}</h2>

      {/* Success / Error Messages */}
      {successMsg && (
        <div className="cpb__success">
          <Check size={16} />
          {successMsg}
        </div>
      )}
      {errorMsg && (
        <div className="cpb__error">
          <AlertCircle size={16} />
          {errorMsg}
        </div>
      )}

      {/* Model Info: Name + Risk Level */}
      <div className="cpb__info">
        <div className="cpb__name-field">
          <label className="cpb__label">Portfolio Name</label>
          <input
            className="cpb__name-input"
            type="text"
            placeholder="e.g. My Growth Portfolio"
            value={name}
            onChange={e => setName(e.target.value.slice(0, 100))}
            maxLength={100}
          />
        </div>
        <div className="cpb__risk-field">
          <label className="cpb__label">Risk Level</label>
          <div className="cpb__risk-buttons">
            {RISK_OPTIONS.map(opt => (
              <button
                key={opt.value}
                className={`cpb__risk-btn${riskLevel === opt.value ? ` cpb__risk-btn--active cpb__risk-btn--${opt.modifier}` : ''}`}
                onClick={() => setRiskLevel(opt.value)}
                type="button"
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Instrument Search */}
      <div className="cpb__search">
        <label className="cpb__search-label">
          <Search size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
          Add Instruments
        </label>
        <div className="cpb__search-container">
          <input
            ref={searchInputRef}
            className="cpb__search-input"
            type="text"
            placeholder="Search by ticker or name..."
            value={searchQuery}
            onChange={e => {
              setSearchQuery(e.target.value)
              setSearchOpen(true)
              setSelectedIndex(-1)
            }}
            onFocus={() => setSearchOpen(true)}
            onKeyDown={handleSearchKeyDown}
          />

          {searchOpen && searchQuery.length >= 1 && (
            <div ref={dropdownRef} className="cpb__search-dropdown">
              {searchLoading && (
                <div className="cpb__search-loading">Searching...</div>
              )}
              {!searchLoading && searchResults.length === 0 && (
                <div className="cpb__search-empty">No results found</div>
              )}
              {!searchLoading &&
                searchResults.map((result, index) => {
                  const alreadyAdded = existingSymbols.has(result.ticker.toUpperCase())
                  return (
                    <div
                      key={result.id}
                      className={`cpb__search-item${index === selectedIndex ? ' cpb__search-item--selected' : ''}${alreadyAdded ? ' cpb__search-item--disabled' : ''}`}
                      onClick={() => !alreadyAdded && handleAddInstrument(result)}
                    >
                      <span className={`type-badge ${getTypeClass(result.type)}`}>
                        {getTypeLabel(result.type)}
                      </span>
                      <span className="cpb__search-ticker">{result.ticker}</span>
                      <span className="cpb__search-name">{result.name}</span>
                      {result.exchange && (
                        <span className="cpb__search-exchange">{result.exchange}</span>
                      )}
                      {alreadyAdded ? (
                        <span className="cpb__search-added">Added</span>
                      ) : (
                        <button
                          className="cpb__search-add-btn"
                          type="button"
                          onClick={e => {
                            e.stopPropagation()
                            handleAddInstrument(result)
                          }}
                        >
                          + Add
                        </button>
                      )}
                    </div>
                  )
                })}
            </div>
          )}
        </div>
      </div>

      <hr className="cpb__divider" />

      {/* Allocations Table */}
      <div className="cpb__allocations">
        <div className="cpb__alloc-header">
          <span className="cpb__alloc-title">Allocations</span>
          <span className={`cpb__total-weight ${weightStatusClass}`}>
            Total: {totalWeight.toFixed(1)}%
          </span>
        </div>

        {allocations.length === 0 ? (
          <div className="cpb__alloc-empty">
            No instruments added yet. Use the search above to add stocks or ETFs.
          </div>
        ) : (
          <table className="cpb__alloc-table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Name</th>
                <th>Weight (%)</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {allocations.map(alloc => (
                <tr key={alloc.symbol}>
                  <td className="cpb__alloc-symbol">{alloc.symbol}</td>
                  <td className="cpb__alloc-name">{alloc.name}</td>
                  <td style={{ textAlign: 'right' }}>
                    <input
                      className="cpb__weight-input"
                      type="number"
                      min="0"
                      max="100"
                      step="0.1"
                      value={alloc.targetPercent}
                      onChange={e => handleWeightChange(alloc.symbol, e.target.value)}
                    />
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <button
                      className="cpb__remove-btn"
                      type="button"
                      onClick={() => handleRemoveAllocation(alloc.symbol)}
                      title="Remove"
                    >
                      <X size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Action Buttons */}
      <div className="cpb__actions">
        {allocations.length > 0 && (
          <>
            <button className="cpb__btn cpb__btn--clear" type="button" onClick={handleClearAll}>
              Clear All
            </button>
            <button className="cpb__btn cpb__btn--normalize" type="button" onClick={handleNormalize}>
              Normalize to 100%
            </button>
          </>
        )}
        <button
          className="cpb__btn cpb__btn--save"
          type="button"
          disabled={!canSave || isSaving}
          onClick={handleSave}
        >
          {isSaving ? 'Saving...' : isEditing ? 'Update Portfolio' : 'Save Portfolio'}
        </button>
      </div>

      {/* Live Analysis Panel (shown after save) */}
      {savedModelId && (
        <div className="cpb__analysis">
          <ModelAnalysisPanel modelId={savedModelId} />
        </div>
      )}
    </div>
  )
}
