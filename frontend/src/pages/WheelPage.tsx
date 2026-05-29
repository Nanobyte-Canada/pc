import { useState, useCallback, useMemo } from 'react'
import { useQueries } from '@tanstack/react-query'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
import { useWheelPositions } from '@/hooks/useWheelPositions'
import { useDashboardCash, useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { useExchangeRate } from '@/hooks/useExchangeRate'
import { useWheelActivities } from '@/hooks/useWheelActivities'
import { computeTickerTotals } from '@/hooks/useWheelPositions'
import { getQuote } from '@/services/marketDataService'
import { AccountNavBar } from '@/components/layout/AccountNavBar'
import { CapitalSummary } from '@/components/wheel/CapitalSummary'
import { WheelGrid } from '@/components/wheel/WheelGrid'
import { OrderPanel } from '@/components/wheel/OrderPanel'
import { WheelChainPanel } from '@/components/wheel/WheelChainPanel'
import type { WheelPosition, CapitalMetrics } from '@/types/wheel'
import { Plus } from 'lucide-react'
import './WheelPage.css'

const WHEEL_TICKERS = ['SOXL', 'TECL', 'TQQQ', 'UPRO']

interface SelectedPositionState {
  position: WheelPosition
  ticker: string
  expiryDate: string
}

export function WheelPage() {
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | undefined>(undefined)
  const [selectedPosition, setSelectedPosition] = useState<SelectedPositionState | null>(null)
  const [chainPanel, setChainPanel] = useState<{ ticker: string; expiryDate: string } | null>(null)

  const { data: connectionsData } = useBrokerConnections()
  const connections = connectionsData?.connections ?? []
  const activeConnections = connections.filter(c => c.status === 'ACTIVE')
  const connectionIds = activeConnections.map(c => c.id)

  const { data: accountsData } = useDashboardAccounts()
  const accounts = accountsData?.accounts ?? []

  const { data: fxData } = useExchangeRate('USD')
  const fxRate = fxData?.rateToCAD ?? 1.38

  const { premiumMap, isLoading: activitiesLoading } = useWheelActivities(connectionIds)

  const { gridData: rawGridData, isLoading: positionsLoading, error } = useWheelPositions(WHEEL_TICKERS, selectedConnectionId)
  const { data: cashData } = useDashboardCash(selectedConnectionId)

  const quotesQuery = useQueries({
    queries: WHEEL_TICKERS.map(ticker => ({
      queryKey: ['wheel-quote', ticker],
      queryFn: async () => {
        const response = await getQuote(ticker)
        return { ticker, price: response.last ?? response.mid ?? 0 }
      },
      staleTime: 30_000,
    })),
  })

  const underlyingPrices = useMemo(() => {
    const prices: Record<string, number> = {}
    quotesQuery.forEach(q => {
      if (q.data) prices[q.data.ticker] = q.data.price
    })
    return prices
  }, [quotesQuery])

  // Re-build grid with premium map, underlying prices, and FX rate
  const gridData = useMemo(() => {
    if (!rawGridData) return null

    const enrichedGrid = { ...rawGridData }

    // Update ticker prices from quotes
    enrichedGrid.tickers = enrichedGrid.tickers.map(t => ({
      ...t,
      currentPrice: underlyingPrices[t.symbol] ?? t.currentPrice,
    }))

    // Update positions with premium from activities and OTM from quotes
    enrichedGrid.expiryRows = enrichedGrid.expiryRows.map(row => ({
      ...row,
      cells: Object.fromEntries(
        Object.entries(row.cells).map(([ticker, cell]) => [
          ticker,
          {
            positions: cell.positions.map(pos => {
              let premium = pos.premium
              if (premium == null) {
                for (const [sym, info] of premiumMap.entries()) {
                  if (sym.includes(ticker) && sym.includes(String(pos.strike))) {
                    premium = info.premium
                    break
                  }
                }
              }

              const underlyingPrice = underlyingPrices[ticker]
              let otmPercent = pos.otmPercent
              if (underlyingPrice && underlyingPrice > 0) {
                otmPercent = Math.abs(pos.strike - underlyingPrice) / underlyingPrice * 100
              }

              return { ...pos, premium, otmPercent }
            }),
          },
        ])
      ),
    }))

    // Recompute totals with FX rate
    enrichedGrid.totals = computeTickerTotals(enrichedGrid, fxRate)

    return enrichedGrid
  }, [rawGridData, underlyingPrices, premiumMap, fxRate])

  const capitalMetrics: CapitalMetrics | null = useMemo(() => {
    if (!gridData) return null

    const cashItems = cashData?.availableCash ?? []
    const cashUsd = cashItems.find(c => c.currency === 'USD')?.amount ?? 0
    const cashCad = cashItems.find(c => c.currency === 'CAD')?.amount ?? 0
    const cashTotalCad = cashData?.totalCashCAD ?? (cashCad + cashUsd * fxRate)
    const cashTotalUsd = cashCad / fxRate + cashUsd

    const allPositions = gridData.expiryRows.flatMap(row =>
      Object.values(row.cells).flatMap(cell => cell.positions)
    )

    let deployedCspUsd = 0
    let ccsWrittenUsd = 0
    let totalPremiumUsd = 0
    let unrealizedPnlUsd = 0

    allPositions.forEach(p => {
      if (p.type === 'CSP') deployedCspUsd += p.strike * 100 * p.quantity
      else ccsWrittenUsd += p.strike * 100 * p.quantity
      totalPremiumUsd += p.premium ?? 0
      unrealizedPnlUsd += p.pnl ?? 0
    })

    return {
      cashUsd,
      cashCad,
      cashTotalUsd,
      cashTotalCad,
      deployedCsp: { usd: deployedCspUsd, cad: deployedCspUsd * fxRate },
      ccsWritten: { usd: ccsWrittenUsd, cad: ccsWrittenUsd * fxRate },
      totalPremium: { usd: totalPremiumUsd, cad: totalPremiumUsd * fxRate },
      unrealizedPnl: { usd: unrealizedPnlUsd, cad: unrealizedPnlUsd * fxRate },
    }
  }, [gridData, cashData, fxRate])

  const isLoading = positionsLoading || activitiesLoading
  const showAccount = selectedConnectionId === undefined

  const handlePositionClick = useCallback((position: WheelPosition, ticker: string, expiryDate: string) => {
    setSelectedPosition({ position, ticker, expiryDate })
  }, [])

  const handleEmptySlotClick = useCallback((ticker: string, expiryDate: string) => {
    setChainPanel({ ticker, expiryDate })
  }, [])

  const handleMobileAdd = useCallback(() => {
    // Open chain panel for first ticker with a reasonable expiry
    if (WHEEL_TICKERS.length > 0) {
      const expiry = gridData?.expiryRows[0]?.expiryDate
      if (expiry) {
        setChainPanel({ ticker: WHEEL_TICKERS[0], expiryDate: expiry })
      }
    }
  }, [gridData])

  return (
    <div className="wheel-page">
      {/* Account switcher — above header */}
      <AccountNavBar
        accounts={accounts}
        selectedId={selectedConnectionId ?? null}
        onSelect={(id) => setSelectedConnectionId(id ?? undefined)}
      />

      <div className="wheel-page-header">
        <div className="wheel-header-left">
          <h1 className="wheel-page-title">Wheel Strategy</h1>
          {/* Mobile: abbreviated legend below title */}
          <div className="wheel-legend-mobile">
            <span className="wheel-legend-mobile-item">
              <span className="wheel-legend-dot wheel-legend-dot-csp" />
              CSP
            </span>
            <span className="wheel-legend-mobile-item">
              <span className="wheel-legend-dot wheel-legend-dot-cc" />
              CC
            </span>
            <span className="wheel-legend-mobile-item">
              <span className="wheel-legend-dot wheel-legend-dot-open" />
              Open
            </span>
          </div>
        </div>
        <div className="wheel-header-right">
          {/* Desktop: full legend */}
          <div className="wheel-legend-desktop">
            <span className="wheel-legend-item">
              <span className="wheel-legend-swatch wheel-legend-swatch-csp" />
              Cash-Secured Put
            </span>
            <span className="wheel-legend-item">
              <span className="wheel-legend-swatch wheel-legend-swatch-cc" />
              Covered Call
            </span>
            <span className="wheel-legend-item">
              <span className="wheel-legend-swatch wheel-legend-swatch-open" />
              Open Slot
            </span>
          </div>
          {/* Mobile: "+" button */}
          <button className="wheel-mobile-add" onClick={handleMobileAdd} aria-label="Add position">
            <Plus size={20} />
          </button>
        </div>
      </div>

      <CapitalSummary metrics={capitalMetrics} />

      <div className="wheel-page__content">
        <div className="wheel-page__grid-area">
          {isLoading && (
            <div className="wheel-loading">Loading positions...</div>
          )}

          {error && (
            <div className="wheel-error">Failed to load positions. Please try again.</div>
          )}

          {gridData && !isLoading && (
            <WheelGrid
              data={gridData}
              showAccount={showAccount}
              onPositionClick={handlePositionClick}
              onEmptySlotClick={handleEmptySlotClick}
            />
          )}
        </div>

        {selectedPosition && (
          <div className="wheel-page__panel-area">
            <OrderPanel
              position={selectedPosition.position}
              ticker={selectedPosition.ticker}
              currentPrice={underlyingPrices[selectedPosition.ticker]}
              onClose={() => setSelectedPosition(null)}
              accounts={activeConnections.map(c => ({
                connectionId: c.id,
                accountType: c.accountType ?? '',
                accountNumber: c.accountNumber ?? '',
                brokerName: c.broker?.name ?? '',
              }))}
              buyingPower={cashData?.buyingPower ?? []}
            />
          </div>
        )}
      </div>

      {chainPanel && (
        <WheelChainPanel
          ticker={chainPanel.ticker}
          expiryDate={chainPanel.expiryDate}
          spotPrice={underlyingPrices[chainPanel.ticker] ?? 0}
          onClose={() => setChainPanel(null)}
        />
      )}
    </div>
  )
}
