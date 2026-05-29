import { useState, useMemo, useEffect, useCallback } from 'react'
import type { WheelPosition } from '@/types/wheel'
import type { CurrencyAmount } from '@/types/dashboard'
import { formatCurrency } from '@/services/brokerService'
import { X, Minus, Plus, ChevronDown } from 'lucide-react'
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import { useQuoteStore } from '@/stores/quoteStore'
import { getOptionsChain } from '@/services/marketDataService'
import { submitOptionsOrder } from '@/services/tradingService'
import { useToast } from '@/stores/toastStore'
import './OrderPanel.css'

interface OrderPanelAccount {
  connectionId: number
  accountType: string
  accountNumber: string
  brokerName: string
}

interface OrderPanelProps {
  position?: WheelPosition | null
  ticker: string
  currentPrice?: number
  onClose: () => void
  accounts: OrderPanelAccount[]
  buyingPower: CurrencyAmount[]
}

function getBrokerIcon(brokerName: string) {
  const lower = brokerName.toLowerCase()
  if (lower.includes('questrade')) return { letter: 'Q', bg: '#1a5c3a', color: '#4ade80' }
  if (lower.includes('wealthsimple')) return { letter: 'W', bg: '#1a1a3a', color: '#a78bfa' }
  if (lower.includes('interactive') || lower.includes('ibkr'))
    return { letter: 'IB', bg: '#3a1a1a', color: '#f87171' }
  return { letter: brokerName[0]?.toUpperCase() ?? '?', bg: '#1a2332', color: '#94a3b8' }
}

function getCurrencyLabel(ticker: string): string {
  // Canadian-listed ETFs typically end with .TO or .TSX — these are US ETFs
  const canadianSuffixes = ['.TO', '.TSX', '.V', '.CN']
  if (canadianSuffixes.some(s => ticker.toUpperCase().endsWith(s))) return 'C$'
  return 'US$'
}

function getOptionCurrency(ticker: string): string {
  return getCurrencyLabel(ticker) === 'C$' ? 'CAD' : 'USD'
}

function formatExpiryForTitle(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Generate a list of Friday expiry dates from now out to ~6 months */
function generateExpiryDates(): string[] {
  const dates: string[] = []
  const now = new Date()
  const end = new Date(now)
  end.setMonth(end.getMonth() + 6)

  const d = new Date(now)
  // Advance to the next Friday
  d.setDate(d.getDate() + ((5 - d.getDay() + 7) % 7 || 7))

  while (d <= end) {
    const iso = d.toISOString().split('T')[0]
    dates.push(iso)
    d.setDate(d.getDate() + 7)
  }
  return dates
}

export function OrderPanel({ position, ticker, currentPrice, onClose, accounts, buyingPower }: OrderPanelProps) {
  const isExistingPosition = !!position
  const { success, error: showError } = useToast()

  // Form state
  const [optionType, setOptionType] = useState<'Call' | 'Put'>(
    position?.type === 'CC' ? 'Call' : 'Put'
  )
  const [expiration, setExpiration] = useState(position?.id ? '' : '')
  const [strike, setStrike] = useState(position?.strike?.toString() ?? '')
  const [orderType, setOrderType] = useState<string>('Limit')
  const [quantity, setQuantity] = useState(position?.quantity ?? 1)
  const [limitPrice, setLimitPrice] = useState(
    position?.currentPrice != null ? position.currentPrice.toFixed(2) : ''
  )
  const [stopPrice, setStopPrice] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [duration, setDuration] = useState<'Day' | 'GTC'>('Day')
  const [selectedAccountId, setSelectedAccountId] = useState<number>(
    position?.connectionId ?? accounts[0]?.connectionId ?? 0
  )

  // Initialize expiration from grid context (from position's expiry)
  // The WheelPage passes expiryDate via the position-click flow
  useEffect(() => {
    if (position) {
      setOptionType(position.type === 'CC' ? 'Call' : 'Put')
      setStrike(position.strike?.toString() ?? '')
      setQuantity(position.quantity ?? 1)
      setLimitPrice(position.currentPrice != null ? position.currentPrice.toFixed(2) : '')
      setSelectedAccountId(position.connectionId ?? accounts[0]?.connectionId ?? 0)
    }
  }, [position, accounts])

  const setChain = useQuoteStore(s => s.setChain)

  useEffect(() => {
    let cancelled = false
    getOptionsChain(ticker).then(chainData => {
      if (!cancelled) setChain(ticker, chainData)
    }).catch(() => {})
    return () => { cancelled = true }
  }, [ticker, setChain])

  const { subscribeOption, unsubscribeOption } = useMarketDataWebSocket()

  useEffect(() => {
    if (!expiration || !strike) return

    const ot = optionType === 'Call' ? 'CALL' : 'PUT'
    subscribeOption(ticker, expiration, strike, ot)

    return () => {
      unsubscribeOption(ticker, expiration, strike, ot)
    }
  }, [ticker, expiration, strike, optionType, subscribeOption, unsubscribeOption])

  const chainData = useQuoteStore(s => s.chains[ticker])
  const optionQuote = useMemo(() => {
    if (!chainData?.expirations || !expiration || !strike) return null
    const strikeKey = strike.includes('.') ? strike : strike + '.0'
    const side = optionType === 'Call' ? 'call' : 'put'
    return chainData.expirations[expiration]?.[strikeKey]?.[side] ?? null
  }, [chainData, expiration, strike, optionType])

  const currencyLabel = getCurrencyLabel(ticker)
  const optionCurrency = getOptionCurrency(ticker)
  const buyingPowerAmount = buyingPower.find(bp => bp.currency === optionCurrency)?.amount ?? null
  const price = currentPrice ?? position?.currentPrice ?? 0

  // Generate expirations and strikes for dropdowns
  const expiryOptions = useMemo(() => generateExpiryDates(), [])
  const strikeOptions = useMemo(() => {
    // Generate strikes around the current price
    const center = price > 0 ? Math.round(price) : 100
    const step = center > 200 ? 5 : center > 50 ? 2.5 : 1
    const strikes: number[] = []
    for (let i = -20; i <= 20; i++) {
      const s = Math.round((center + i * step) * 100) / 100
      if (s > 0) strikes.push(s)
    }
    return strikes
  }, [price])

  // Estimated total
  const estimatedTotal = useMemo(() => {
    const lp = parseFloat(limitPrice)
    if (isNaN(lp)) return null
    return quantity * lp * 100
  }, [quantity, limitPrice])

  // Selected account info
  const selectedAccount = accounts.find(a => a.connectionId === selectedAccountId)
  const selectedBrokerIcon = selectedAccount ? getBrokerIcon(selectedAccount.brokerName) : null

  // Derive supported order types from broker
  const supportedOrderTypes = useMemo(() => {
    const account = accounts.find(a => a.connectionId === selectedAccountId)
    if (!account) return ['Limit', 'Market']
    const broker = account.brokerName.toLowerCase()
    if (broker.includes('questrade') || broker.includes('ibkr') || broker.includes('interactive'))
      return ['Limit', 'Market', 'Stop', 'Stop Limit']
    return ['Limit', 'Market']
  }, [accounts, selectedAccountId])

  // Contract title line
  const contractTitle = useMemo(() => {
    const exp = expiration ? formatExpiryForTitle(expiration) : '---'
    const str = strike || '---'
    return `${ticker} ${exp} ${str} ${optionType}`
  }, [ticker, expiration, strike, optionType])

  const handleSubmitOrder = useCallback(async (action: 'BUY' | 'SELL') => {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const lp = parseFloat(limitPrice)
      const sp = parseFloat(stopPrice)
      const strikeNum = parseFloat(strike)
      await submitOptionsOrder({
        symbol: ticker,
        action,
        units: quantity,
        price: lp || 0,
        amount: quantity * (lp || 0) * 100,
        currency: getCurrencyLabel(ticker) === 'C$' ? 'CAD' : 'USD',
        connectionId: selectedAccountId,
        limitPrice: (orderType === 'Limit' || orderType === 'Stop Limit') && !isNaN(lp) ? lp : undefined,
        stopPrice: (orderType === 'Stop' || orderType === 'Stop Limit') && !isNaN(sp) ? sp : undefined,
        optionType: optionType === 'Call' ? 'CALL' : 'PUT',
        strikePrice: !isNaN(strikeNum) ? strikeNum : undefined,
        expirationDate: expiration || undefined,
      })
      success(`${action} order submitted for ${ticker}`)
      onClose()
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Order submission failed'
      setSubmitError(msg)
      showError(msg)
    } finally {
      setSubmitting(false)
    }
  }, [ticker, optionType, expiration, strike, orderType, quantity, limitPrice, stopPrice, selectedAccountId, onClose, success, showError])

  const panelContent = (
    <div className="order-panel__inner">
      {/* 1. Header */}
      <div className="order-panel__header">
        <div className="order-panel__header-text">
          <span className="order-panel__symbol">{ticker}</span>
          <span className="order-panel__description">
            {isExistingPosition ? `${position.type === 'CSP' ? 'Cash-Secured Put' : 'Covered Call'}` : 'New Order'}
          </span>
        </div>
        <button className="order-panel__close" onClick={onClose} aria-label="Close order panel">
          <X size={18} />
        </button>
      </div>

      {/* 2. Live Quote */}
      <div className="order-panel__quote">
        <div className="order-panel__quote-main">
          <span className="order-panel__quote-price">
            {price > 0 ? formatCurrency(price, 'USD') : '--'}
          </span>
          {position?.pnl != null && (
            <span className={`order-panel__quote-change ${position.pnl >= 0 ? 'order-panel__quote-change--up' : 'order-panel__quote-change--down'}`}>
              {position.pnl >= 0 ? '+' : ''}{formatCurrency(position.pnl, 'USD')}
            </span>
          )}
        </div>
        <div className="order-panel__quote-cards">
          <div className="order-panel__quote-card">
            <span className="order-panel__quote-card-label">Bid</span>
            <span className="order-panel__quote-card-value">
              {optionQuote?.bid != null ? optionQuote.bid.toFixed(2) : '--'}
            </span>
          </div>
          <div className="order-panel__quote-card">
            <span className="order-panel__quote-card-label">Mid</span>
            <span className="order-panel__quote-card-value">
              {optionQuote?.mid != null
                ? optionQuote.mid.toFixed(2)
                : position?.currentPrice != null
                  ? position.currentPrice.toFixed(2)
                  : '--'}
            </span>
          </div>
          <div className="order-panel__quote-card">
            <span className="order-panel__quote-card-label">Ask</span>
            <span className="order-panel__quote-card-value">
              {optionQuote?.ask != null ? optionQuote.ask.toFixed(2) : '--'}
            </span>
          </div>
        </div>
      </div>

      {/* 3. Contract Section */}
      <div className="order-panel__section">
        <div className="order-panel__contract-title">{contractTitle}</div>
        <div className="order-panel__contract-last">
          Last: {optionQuote?.last != null
            ? formatCurrency(optionQuote.last, 'USD')
            : position?.premium != null
              ? formatCurrency(position.premium, 'USD')
              : '--'}
        </div>
        <div className="order-panel__contract-grid">
          <div className="order-panel__field">
            <label className="order-panel__label">Option Type</label>
            <div className="order-panel__select-wrap">
              <select
                className="order-panel__select"
                value={optionType}
                onChange={e => setOptionType(e.target.value as 'Call' | 'Put')}
              >
                <option value="Put">Put</option>
                <option value="Call">Call</option>
              </select>
              <ChevronDown size={14} className="order-panel__select-chevron" />
            </div>
          </div>
          <div className="order-panel__field">
            <label className="order-panel__label">Expiration</label>
            <div className="order-panel__select-wrap">
              <select
                className="order-panel__select"
                value={expiration}
                onChange={e => setExpiration(e.target.value)}
              >
                <option value="">Select...</option>
                {expiryOptions.map(d => (
                  <option key={d} value={d}>{formatExpiryForTitle(d)}</option>
                ))}
              </select>
              <ChevronDown size={14} className="order-panel__select-chevron" />
            </div>
          </div>
          <div className="order-panel__field">
            <label className="order-panel__label">Strike</label>
            <div className="order-panel__select-wrap">
              <select
                className="order-panel__select"
                value={strike}
                onChange={e => setStrike(e.target.value)}
              >
                <option value="">Select...</option>
                {strikeOptions.map(s => (
                  <option key={s} value={s.toString()}>{s.toFixed(2)}</option>
                ))}
              </select>
              <ChevronDown size={14} className="order-panel__select-chevron" />
            </div>
          </div>
          <div className="order-panel__field">
            <label className="order-panel__label">Order Type</label>
            <div className="order-panel__select-wrap">
              <select
                className="order-panel__select"
                value={orderType}
                onChange={e => setOrderType(e.target.value)}
              >
                {supportedOrderTypes.map(ot => (
                  <option key={ot} value={ot}>{ot}</option>
                ))}
              </select>
              <ChevronDown size={14} className="order-panel__select-chevron" />
            </div>
          </div>
        </div>
      </div>

      {/* 4. Quantity Stepper */}
      <div className="order-panel__section">
        <label className="order-panel__label">Quantity</label>
        <div className="order-panel__stepper">
          <button
            className="order-panel__stepper-btn"
            onClick={() => setQuantity(q => Math.max(1, q - 1))}
            aria-label="Decrease quantity"
          >
            <Minus size={14} />
          </button>
          <span className="order-panel__stepper-value">{quantity}</span>
          <button
            className="order-panel__stepper-btn"
            onClick={() => setQuantity(q => q + 1)}
            aria-label="Increase quantity"
          >
            <Plus size={14} />
          </button>
        </div>
      </div>

      {/* 5. Limit Price */}
      {(orderType === 'Limit' || orderType === 'Stop Limit') && (
        <div className="order-panel__section">
          <label className="order-panel__label">Limit Price</label>
          <div className="order-panel__price-input-wrap">
            <span className="order-panel__price-currency">{currencyLabel}</span>
            <input
              type="text"
              inputMode="decimal"
              className="order-panel__price-input"
              value={limitPrice}
              onChange={e => {
                const v = e.target.value
                if (/^\d*\.?\d{0,2}$/.test(v) || v === '') setLimitPrice(v)
              }}
              placeholder="0.00"
            />
          </div>
        </div>
      )}

      {/* 5b. Stop Price */}
      {(orderType === 'Stop' || orderType === 'Stop Limit') && (
        <div className="order-panel__section">
          <label className="order-panel__label">Stop Price</label>
          <div className="order-panel__price-input-wrap">
            <span className="order-panel__price-currency">{currencyLabel}</span>
            <input
              type="text"
              inputMode="decimal"
              className="order-panel__price-input"
              value={stopPrice}
              onChange={e => {
                const v = e.target.value
                if (/^\d*\.?\d{0,2}$/.test(v) || v === '') setStopPrice(v)
              }}
              placeholder="0.00"
            />
          </div>
        </div>
      )}

      {/* 6. Duration */}
      <div className="order-panel__section">
        <label className="order-panel__label">Duration</label>
        <div className="order-panel__select-wrap">
          <select
            className="order-panel__select"
            value={duration}
            onChange={e => setDuration(e.target.value as 'Day' | 'GTC')}
          >
            <option value="Day">Day</option>
            <option value="GTC">GTC</option>
          </select>
          <ChevronDown size={14} className="order-panel__select-chevron" />
        </div>
      </div>

      {/* 7. Account Selector */}
      {accounts.length > 0 && (
        <div className="order-panel__section">
          <label className="order-panel__label">Account</label>
          <div className="order-panel__account-selector">
            <div className="order-panel__account-info">
              {selectedBrokerIcon && (
                <span
                  className="order-panel__broker-icon"
                  style={{ background: selectedBrokerIcon.bg, color: selectedBrokerIcon.color }}
                >
                  {selectedBrokerIcon.letter}
                </span>
              )}
              <div className="order-panel__select-wrap order-panel__account-select-wrap">
                <select
                  className="order-panel__select"
                  value={selectedAccountId}
                  onChange={e => setSelectedAccountId(Number(e.target.value))}
                >
                  {accounts.map(a => (
                    <option key={a.connectionId} value={a.connectionId}>
                      {a.brokerName} {a.accountType ? `${a.accountType} ` : ''}
                      {a.accountNumber ? `••${a.accountNumber.slice(-4)}` : ''}
                    </option>
                  ))}
                </select>
                <ChevronDown size={14} className="order-panel__select-chevron" />
              </div>
            </div>
            <div className="order-panel__buying-power">
              <span className="order-panel__buying-power-value">
                {buyingPowerAmount != null
                  ? `${currencyLabel} ${buyingPowerAmount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
                  : '--'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* 8. Estimated Total */}
      <div className="order-panel__estimated">
        <span className="order-panel__estimated-label">Estimated Total</span>
        <span className="order-panel__estimated-value">
          {estimatedTotal != null ? formatCurrency(estimatedTotal, 'USD') : '--'}
        </span>
      </div>

      {/* 9. Buy / Sell Buttons */}
      <div className="order-panel__actions">
        <button
          className="order-panel__action-btn order-panel__action-btn--buy"
          onClick={() => handleSubmitOrder('BUY')}
          disabled={submitting}
        >
          {submitting ? 'Submitting...' : 'Buy'}
        </button>
        <button
          className="order-panel__action-btn order-panel__action-btn--sell"
          onClick={() => handleSubmitOrder('SELL')}
          disabled={submitting}
        >
          {submitting ? 'Submitting...' : 'Sell'}
        </button>
      </div>
      {submitError && (
        <div className="order-panel__error">{submitError}</div>
      )}
    </div>
  )

  return (
    <>
      {/* Desktop: inline panel */}
      <div className="order-panel order-panel--desktop">
        {panelContent}
      </div>

      {/* Mobile: bottom sheet overlay */}
      <div className="order-sheet-overlay" onClick={onClose}>
        <div className="order-sheet" onClick={e => e.stopPropagation()}>
          <div className="order-sheet__handle" />
          {panelContent}
        </div>
      </div>
    </>
  )
}
