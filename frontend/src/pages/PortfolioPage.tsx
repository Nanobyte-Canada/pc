import { useState } from 'react'
import { useModelPortfolios } from '@/hooks/useModelPortfolios'
import { ModelPortfolioCard } from '@/components/portfolios/ModelPortfolioCard'
import { ModelAnalysisPanel } from '@/components/portfolios/ModelAnalysisPanel'
import { CustomPortfolioBuilder } from '@/components/portfolios/CustomPortfolioBuilder'
import { Skeleton } from '@/components/ui/skeleton'
import type { ModelPortfolioSummary } from '@/types/modelPortfolio'
import './PortfolioPage.css'

const RISK_ORDER = { LOW: 0, MODERATE: 1, HIGH: 2, EXTRA_HIGH: 3 } as const

export function PortfolioPage() {
  const { data, isLoading } = useModelPortfolios()
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null)
  const [customSlotSelected, setCustomSlotSelected] = useState(false)
  const [editingCustomModel, setEditingCustomModel] = useState(false)

  if (isLoading) {
    return (
      <div className="portfolio-page">
        <div className="portfolio-page__header">
          <h1>Portfolio</h1>
        </div>
        <div className="portfolio-page__loading">
          {[1, 2, 3, 4, 5].map(i => (
            <Skeleton key={i} style={{ height: '140px', borderRadius: '8px' }} />
          ))}
        </div>
      </div>
    )
  }

  const allModels = data?.models ?? []

  // Separate and sort system models by risk level
  const systemModels = allModels
    .filter(m => m.isSystem)
    .sort((a, b) => RISK_ORDER[a.riskLevel] - RISK_ORDER[b.riskLevel])

  // First custom model (if any) for the 5th card slot
  const customModels = allModels.filter(m => !m.isSystem)
  const firstCustomModel: ModelPortfolioSummary | null = customModels.length > 0 ? customModels[0] : null

  const handleCardClick = (model: ModelPortfolioSummary) => {
    setCustomSlotSelected(false)
    setEditingCustomModel(false)
    setSelectedModelId(prev => prev === model.id ? null : model.id)
  }

  return (
    <div className="portfolio-page">
      <div className="portfolio-page__header">
        <h1>Portfolio</h1>
      </div>

      <div className="portfolio-page__cards">
        {systemModels.map(model => (
          <ModelPortfolioCard
            key={model.id}
            model={model}
            isSelected={selectedModelId === model.id}
            onClick={() => handleCardClick(model)}
          />
        ))}
        <ModelPortfolioCard
          model={firstCustomModel}
          isSelected={customSlotSelected}
          onClick={() => {
            setSelectedModelId(null)
            setEditingCustomModel(false)
            setCustomSlotSelected(prev => !prev)
          }}
        />
      </div>

      {selectedModelId && !customSlotSelected && (
        <ModelAnalysisPanel modelId={selectedModelId} />
      )}

      {customSlotSelected && !firstCustomModel && (
        <CustomPortfolioBuilder
          onSaved={() => setCustomSlotSelected(false)}
        />
      )}

      {customSlotSelected && firstCustomModel && !editingCustomModel && (
        <>
          <ModelAnalysisPanel modelId={firstCustomModel.id} />
          <div className="portfolio-page__edit-bar">
            <button
              className="portfolio-page__edit-btn"
              onClick={() => setEditingCustomModel(true)}
            >
              Edit Portfolio
            </button>
          </div>
        </>
      )}

      {customSlotSelected && firstCustomModel && editingCustomModel && (
        <CustomPortfolioBuilder
          existingModelId={firstCustomModel.id}
          onSaved={() => setEditingCustomModel(false)}
        />
      )}
    </div>
  )
}

export default PortfolioPage
