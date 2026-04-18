import type { Quote } from '@/types/options'
import './QuoteBar.css'

interface QuoteBarProps {
  quote: Quote
}

export function QuoteBar({ quote }: QuoteBarProps) {
  return (
    <div className="quote-bar">
      <span className="quote-bar__symbol">{quote.symbol}</span>
      <span className="quote-bar__price">${quote.last.toFixed(2)}</span>
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
        <span className="quote-bar__value quote-bar__spread">${quote.spread.toFixed(2)}</span>
      </div>
      <div className="quote-bar__field">
        <span className="quote-bar__label">Volume</span>
        <span className="quote-bar__value">{quote.volume.toLocaleString()}</span>
      </div>
    </div>
  )
}
