import { useState, useCallback, useMemo } from 'react'
import { useQuery, useQueries } from '@tanstack/react-query'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
import { useDashboardCash, useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { useExchangeRate } from '@/hooks/useExchangeRate'
import { useWheelActivities } from '@/hooks/useWheelActivities'
import {
  discoverTickers, detectCCEligible, generateWeeklyExpiries,
  buildWheelGrid,
} from '@/hooks/useWheelPositions'
import { getConnectionPositions, getAggregatedPositions } from '@/services/brokerService'
import { getQuote } from '@/services/marketDataService'
import { AccountNavBar } from '@/components/layout/AccountNavBar'
import { WheelKpiCards } from '@/components/wheel/WheelKpiCards'
import { WheelCalendarGrid } from '@/components/wheel/WheelCalendarGrid'
import { WheelTopTickers } from '@/components/wheel/WheelTopTickers'
import { OrderPanel } from '@/components/wheel/OrderPanel'
import { WheelChainPanel } from '@/components/wheel/WheelChainPanel'
import type { WheelPosition, CapitalMetrics, TickerRowData, ChainPanelContext } from '@/types/wheel'
import type { BrokerPosition, ConnectionPositionsResponse, AggregatedPositionsResponse } from '@/types/broker'
import './WheelPage.css'

const DESKTOP_COLUMNS = 4

interface SelectedPositionState {
  position?: WheelPosition | null
  ticker: string
  expiryDate: string
}

export function WheelPage() {
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | undefined>(undefined)
  const [selectedPosition, setSelectedPosition] = useState<SelectedPositionState | null>(null)
  const [chainPanel, setChainPanel] = useState<ChainPanelContext | null>(null)
  const [calendarOffset, setCalendarOffset] = useState(0)

  const { data: connectionsData } = useBrokerConnections()
  const connections = connectionsData?.connections ?? []
  const activeConnections = connections.filter(c => c.status === 'ACTIVE')
  const connectionIds = activeConnections.map(c => c.id)

  const { data: accountsData } = useDashboardAccounts()
  const accounts = accountsData?.accounts ?? []

  const { data: fxData } = useExchangeRate('USD')
  const fxRate = fxData?.rateToCAD ?? 1.38

  const { premiumMap, isLoading: activitiesLoading } = useWheelActivities(connectionIds)

  const { data: cashData } = useDashboardCash(selectedConnectionId)

  // 1. Fetch raw positions
  const positionsQuery = useQuery<ConnectionPositionsResponse | AggregatedPositionsResponse>({
    queryKey: ['wheel-positions', selectedConnectionId ?? 'all'],
    queryFn: () =>
      selectedConnectionId
        ? getConnectionPositions(selectedConnectionId)
        : getAggregatedPositions(),
    staleTime: 60_000,
  })

  // 2. Extract raw positions array
  const { rawPositions, accountName, accountNumber, connId } = useMemo(() => {
    if (!positionsQuery.data) return { rawPositions: [] as BrokerPosition[], accountName: null, accountNumber: null, connId: 0 }
    const data = positionsQuery.data
    if ('connectionId' in data && 'broker' in data) {
      const resp = data as ConnectionPositionsResponse
      return { rawPositions: resp.positions, accountName: resp.broker, accountNumber: resp.accountNumber, connId: resp.connectionId }
    }
    const resp = data as AggregatedPositionsResponse
    return { rawPositions: resp.positions as unknown as BrokerPosition[], accountName: null, accountNumber: null, connId: 0 }
  }, [positionsQuery.data])

  // 3. Discover tickers from option positions and detect CC-eligible stock holdings
  const optionTickers = useMemo(() => discoverTickers(rawPositions), [rawPositions])
  const ccEligible = useMemo(() => detectCCEligible(rawPositions), [rawPositions])
  // Only show tickers that have current option positions (CSP or CC) in the grid
  const allTickers = useMemo(() => {
    return Array.from(new Set(optionTickers)).sort()
  }, [optionTickers])

  // 4. Fetch quotes for discovered tickers
  const quotesQuery = useQueries({
    queries: allTickers.map(ticker => ({
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

  // 5. Generate calendar window based on calendarOffset
  const expiries = useMemo(() => {
    const start = new Date()
    start.setDate(start.getDate() + calendarOffset * 7)
    return generateWeeklyExpiries(start, DESKTOP_COLUMNS)
  }, [calendarOffset])

  const dateRange = useMemo(() => {
    if (expiries.length === 0) return ''
    const fmt = (iso: string) => {
      const d = new Date(iso + 'T00:00:00')
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    }
    return `${fmt(expiries[0].date)} — ${fmt(expiries[expiries.length - 1].date)}, ${new Date(expiries[0].date + 'T00:00:00').getFullYear()}`
  }, [expiries])

  // 6. Build grid data using buildWheelGrid, then transpose to ticker rows
  const tickerRows: TickerRowData[] = useMemo(() => {
    if (allTickers.length === 0) return []

    const expiryDates = expiries.map(e => e.date)
    const today = new Date()

    const grid = buildWheelGrid(
      rawPositions, allTickers, expiryDates, today,
      accountName, accountNumber, connId,
      underlyingPrices, premiumMap, fxRate,
    )

    // Transpose: from expiry-rows to ticker-rows
    return allTickers.map(symbol => {
      const price = underlyingPrices[symbol] ?? null
      const isCanadian = symbol.endsWith('.TO') || symbol.endsWith('.TSX') || symbol.endsWith('.V') || symbol.endsWith('.CN')
      const currency = isCanadian ? 'CAD' : 'USD'

      const cells: Record<string, { positions: WheelPosition[] }> = {}
      let totalExposure = 0

      grid.expiryRows.forEach(row => {
        const cell = row.cells[symbol]
        const positions = cell?.positions ?? []
        cells[row.expiryDate] = { positions }
        positions.forEach(pos => {
          if (pos.type === 'CSP') {
            totalExposure += pos.strike * 100 * pos.quantity
          }
        })
      })

      return {
        symbol,
        currentPrice: price,
        currency,
        totalExposure,
        ccInfo: ccEligible.get(symbol) ?? null,
        cells,
      }
    })
  }, [allTickers, rawPositions, expiries, underlyingPrices, premiumMap, fxRate, ccEligible, accountName, accountNumber, connId])

  // 7. Compute capital metrics
  const capitalMetrics: CapitalMetrics | null = useMemo(() => {
    if (allTickers.length === 0 && !positionsQuery.data) return null

    const cashItems = cashData?.availableCash ?? []
    const cashUsd = cashItems.find(c => c.currency === 'USD')?.amount ?? 0
    const cashCad = cashItems.find(c => c.currency === 'CAD')?.amount ?? 0
    const cashTotalCad = cashData?.totalCashCAD ?? (cashCad + cashUsd * fxRate)
    const cashTotalUsd = cashCad / fxRate + cashUsd

    let deployedCspUsd = 0
    let ccsWrittenUsd = 0
    let totalPremiumUsd = 0
    let unrealizedPnlUsd = 0

    tickerRows.forEach(row => {
      Object.values(row.cells).forEach(cell => {
        cell.positions.forEach(p => {
          if (p.type === 'CSP') deployedCspUsd += p.strike * 100 * p.quantity
          else ccsWrittenUsd += p.strike * 100 * p.quantity
          totalPremiumUsd += p.premium ?? 0
          unrealizedPnlUsd += p.pnl ?? 0
        })
      })
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
  }, [allTickers, positionsQuery.data, cashData, fxRate, tickerRows])

  // 8. Compute position counts for KPI
  const positionCounts = useMemo(() => {
    let csp = 0
    let cc = 0
    let expiring = 0
    const now = new Date()
    tickerRows.forEach(row => {
      Object.entries(row.cells).forEach(([expiryDate, cell]) => {
        cell.positions.forEach(pos => {
          if (pos.type === 'CSP') csp++
          else cc++
          const expDate = new Date(expiryDate + 'T00:00:00')
          const msPerDay = 86400000
          const dte = Math.round((expDate.getTime() - now.getTime()) / msPerDay)
          if (dte <= 5) expiring++
        })
      })
    })
    return { csp, cc, expiring, total: csp + cc }
  }, [tickerRows])

  const isLoading = positionsQuery.isLoading || activitiesLoading

  const handlePositionClick = useCallback((position: WheelPosition, ticker: string, expiryDate: string) => {
    setSelectedPosition({ position, ticker, expiryDate })
    setChainPanel(null)
  }, [])

  const handleEmptySlotClick = useCallback((ticker: string, expiryDate: string) => {
    setChainPanel({ ticker, expiryDate, optionSide: 'put' })
    setSelectedPosition(null)
  }, [])

  const handleStrikeSelect = useCallback((ticker: string, expiry: string, strike: number, optionSide: 'put' | 'call') => {
    setChainPanel(null)
    setSelectedPosition({
      ticker,
      expiryDate: expiry,
      position: {
        id: 0,
        type: optionSide === 'put' ? 'CSP' : 'CC',
        strike,
        premium: null,
        currentPrice: null,
        pnl: null,
        otmPercent: null,
        quantity: 1,
        currency: 'USD',
        accountName: null,
        accountNumber: null,
        connectionId: activeConnections[0]?.id ?? 0,
      },
    })
  }, [activeConnections])

  return (
    <div className="wheel-page">
      <AccountNavBar
        accounts={accounts}
        selectedId={selectedConnectionId ?? null}
        onSelect={(id) => setSelectedConnectionId(id ?? undefined)}
      />

      <div className="wheel-page__header">
        <h1 className="wheel-page__title">Wheel Strategy</h1>
      </div>

      <div className="wheel-page__content">
        <div className="wheel-page__main">
          <WheelKpiCards
            metrics={capitalMetrics}
            buyingPower={cashData?.buyingPower ?? []}
            ccEligible={ccEligible}
            positionCounts={positionCounts}
            fxRateToCAD={fxRate}
          />

          {isLoading && (
            <div className="wheel-loading">Loading positions...</div>
          )}

          {positionsQuery.error && (
            <div className="wheel-error">Failed to load positions. Please try again.</div>
          )}

          {!isLoading && !positionsQuery.error && (
            <>
              <WheelTopTickers
                onTickerClick={(ticker) => {
                  setChainPanel({ ticker, expiryDate: expiries[0]?.date ?? new Date().toISOString().split('T')[0], optionSide: 'put' })
                  setSelectedPosition(null)
                }}
                onAddTicker={() => {
                  setChainPanel({ ticker: '', expiryDate: expiries[0]?.date ?? new Date().toISOString().split('T')[0], optionSide: 'put', searchMode: true })
                  setSelectedPosition(null)
                }}
              />

              <WheelCalendarGrid
                tickerRows={tickerRows}
                expiries={expiries}
                dateRange={dateRange}
                onPrev={() => setCalendarOffset(o => o - DESKTOP_COLUMNS)}
                onNext={() => setCalendarOffset(o => o + DESKTOP_COLUMNS)}
                onToday={() => setCalendarOffset(0)}
                onPositionClick={handlePositionClick}
                onEmptySlotClick={handleEmptySlotClick}
              />
            </>
          )}
        </div>

        {(selectedPosition || chainPanel) && (
          <div className="wheel-page__panel">
            {chainPanel && !selectedPosition && (
              <WheelChainPanel
                context={chainPanel}
                spotPrice={underlyingPrices[chainPanel.ticker] ?? 0}
                onClose={() => setChainPanel(null)}
                onStrikeSelect={handleStrikeSelect}
                onTickerSelect={(ticker) => {
                  setChainPanel(prev => prev ? { ...prev, ticker, searchMode: false } : null)
                }}
              />
            )}
            {selectedPosition && (
              <OrderPanel
                position={selectedPosition.position}
                ticker={selectedPosition.ticker}
                currentPrice={underlyingPrices[selectedPosition.ticker]}
                expiryDate={selectedPosition.expiryDate}
                onClose={() => setSelectedPosition(null)}
                accounts={activeConnections.map(c => ({
                  connectionId: c.id,
                  accountType: c.accountType ?? '',
                  accountNumber: c.accountNumber ?? '',
                  brokerName: c.broker?.name ?? '',
                }))}
                buyingPower={cashData?.buyingPower ?? []}
              />
            )}
          </div>
        )}
      </div>
    </div>
  )
}
