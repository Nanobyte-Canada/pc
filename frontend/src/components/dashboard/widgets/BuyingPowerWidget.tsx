import { useDashboardCash } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { Zap } from 'lucide-react'
import './BuyingPowerWidget.css'

const CURRENCY_SYMBOLS: Record<string, string> = {
  CAD: 'C$', USD: 'US$', EUR: '\u20AC', GBP: '\u00A3', JPY: '\u00A5',
}

export default function BuyingPowerWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardCash(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (data.buyingPower.length === 0) {
    return (
      <div className="bp-empty">
        <Zap style={{ height: '2rem', width: '2rem' }} />
        <span>No buying power data available</span>
      </div>
    )
  }

  const total = data.buyingPower.reduce((sum, c) => sum + c.amount, 0)

  return (
    <div>
      <div className="bp-total">
        {new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(total)}
      </div>
      <div className="bp-list">
        {data.buyingPower.map(c => (
          <div key={c.currency} className="bp-item">
            <div className="bp-item-left">
              <span className="bp-currency-badge">
                {CURRENCY_SYMBOLS[c.currency] || c.currency}
              </span>
              <span className="bp-currency-name">{c.currency}</span>
            </div>
            <span className="bp-amount">
              {new Intl.NumberFormat('en-CA', { style: 'currency', currency: c.currency }).format(c.amount)}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
