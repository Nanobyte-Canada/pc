import { useState, useCallback, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuoteStore } from '@/stores/quoteStore'
import { useStrategyStore } from '@/stores/strategyStore'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { getQuote, getOptionsChainWithGreeks } from '@/services/marketDataService'
import { getStrategies, calculateStrategy } from '@/services/optionsStrategyService'
import { UnderlyingSearch } from '@/components/options/UnderlyingSearch'
import { QuoteBar } from '@/components/options/QuoteBar'
import { StrategySelector } from '@/components/options/StrategySelector'
import { OptionsChainTable } from '@/components/options/OptionsChainTable'
import { LegBuilder } from '@/components/options/LegBuilder'
import { PnlChart } from '@/components/options/PnlChart'
import type { CalculationResult } from '@/types/options'
import './OptionsPage.css'

export function OptionsPage() {
  const { selectedUnderlying, setSelectedUnderlying, setQuote, setChain, quotes, chains } = useQuoteStore()
  const { legs, setIsCalculating, isCalculating, setStrategies, strategies, selectedStrategy } = useStrategyStore()
  const { isConnected, subscribe, subscribeChain, unsubscribeChain } = useMarketDataWebSocket()
  const [searchParams, setSearchParams] = useSearchParams()

  const [isLoadingChain, setIsLoadingChain] = useState(false)
  const [calcResult, setCalcResult] = useState<CalculationResult | null>(null)
  const [calcWarnings, setCalcWarnings] = useState<string[]>([])
  const [strategiesLoaded, setStrategiesLoaded] = useState(false)

  useEffect(() => {
    const ticker = searchParams.get('ticker')
    if (ticker && !selectedUnderlying) {
      handleSearch(ticker)
      setSearchParams({}, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSearch = useCallback(async (symbol: string) => {
    setIsLoadingChain(true)
    setSelectedUnderlying(symbol)
    try {
      const [quoteData, chainData] = await Promise.all([
        getQuote(symbol),
        getOptionsChainWithGreeks(symbol),
      ])
      setQuote(symbol, quoteData)
      setChain(symbol, chainData)
      subscribe(symbol)

      if (selectedUnderlying && selectedUnderlying !== symbol) {
        unsubscribeChain(selectedUnderlying)
      }
      subscribeChain(symbol)

      if (!strategiesLoaded) {
        const strats = await getStrategies()
        setStrategies(strats)
        setStrategiesLoaded(true)
      }
    } catch (err) {
      console.error('Failed to load options data:', err)
    } finally {
      setIsLoadingChain(false)
    }
  }, [setSelectedUnderlying, setQuote, setChain, subscribe, subscribeChain, unsubscribeChain, selectedUnderlying, setStrategies, strategiesLoaded])

  const handleCalculate = useCallback(async () => {
    if (!selectedUnderlying || legs.length === 0) return
    const quote = quotes[selectedUnderlying]
    if (!quote) return

    setIsCalculating(true)
    try {
      const result = await calculateStrategy(
        selectedStrategy ?? 'BULL_CALL_SPREAD',
        selectedUnderlying,
        quote.last,
        legs.map((l) => ({
          action: l.action,
          optionType: l.optionType,
          strike: l.strike,
          expiry: l.expiry,
          quantity: l.quantity ?? 1,
          price: l.price,
        }))
      )
      setCalcResult(result)
      setCalcWarnings(result.probabilityOfProfit !== undefined ? [] : [])
    } catch (err) {
      console.error('Calculation failed:', err)
    } finally {
      setIsCalculating(false)
    }
  }, [selectedUnderlying, legs, quotes, selectedStrategy, setIsCalculating])

  const quote = selectedUnderlying ? quotes[selectedUnderlying] : null
  const chain = selectedUnderlying ? chains[selectedUnderlying] : null

  return (
    <div className="options-page">
      <div className="options-page__header">
        <h1 className="options-page__title">Options Trading</h1>
      </div>

      <div className="options-page__toolbar">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <UnderlyingSearch onSearch={handleSearch} isLoading={isLoadingChain} />
          <div className="options-page__ws-status">
            <div className={`options-page__ws-dot ${isConnected ? 'options-page__ws-dot--connected' : 'options-page__ws-dot--disconnected'}`} />
            {isConnected ? 'Live' : 'Disconnected'}
          </div>
        </div>

        {strategies.length > 0 && <StrategySelector strategies={strategies} />}
      </div>

      {quote && <QuoteBar quote={quote} />}

      {!selectedUnderlying && (
        <div className="options-page__empty">
          <p>Enter a symbol above to load the options chain</p>
          <p>Try SPY, QQQ, AAPL, MSFT, or NVDA</p>
        </div>
      )}

      {isLoadingChain && <div className="options-page__loading">Loading options chain...</div>}

      {chain && !isLoadingChain && (
        <div className="options-page__content">
          <div className="options-page__chain-section">
            <OptionsChainTable chain={chain} />
          </div>
          <div className="options-page__sidebar">
            <LegBuilder
              onCalculate={handleCalculate}
              isCalculating={isCalculating}
            />
            {calcResult && <PnlChart result={calcResult} warnings={calcWarnings} />}
          </div>
        </div>
      )}
    </div>
  )
}
