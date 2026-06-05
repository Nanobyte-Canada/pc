import type { Quote } from '@/types/options'
import './QuoteBar.css'

interface QuoteBarProps {
  quote: Quote
}

export function QuoteBar({ quote }: QuoteBarProps) {
  const change = quote.last - quote.bid // approximate change
  const isPositive = change >= 0

  return (
    <div className="quote-bar">
      <span className="quote-bar__symbol">{quote.symbol}</span>
      <span className="quote-bar__price">${quote.last.toFixed(2)}</span>

      {change !== 0 && (
        <span className={`quote-bar__change ${isPositive ? 'quote-bar__change--positive' : 'quote-bar__change--negative'}`}>
          {isPositive ? '+' : ''}{change.toFixed(2)}
        </span>
      )}

      <div className="quote-bar__divider" />

      <div className="quote-bar__fields">
        <div className="quote-bar__field">
          <span className="quote-bar__label">Bid</span>
          <span className="quote-bar__value">${quote.bid.toFixed(2)}</span>
        </div>
        <div className="quote-bar__field">
          <span className="quote-bar__label">Ask</span>
          <span className="quote-bar__value">${quote.ask.toFixed(2)}</span>
        </div>
        <div className="quote-bar__field">
          <span className="quote-bar__label">Spread</span>
          <span className="quote-bar__value quote-bar__value--muted">${quote.spread.toFixed(2)}</span>
        </div>
        <div className="quote-bar__field">
          <span className="quote-bar__label">Volume</span>
          <span className="quote-bar__value">{quote.volume.toLocaleString()}</span>
        </div>
      </div>
    </div>
  )
}
