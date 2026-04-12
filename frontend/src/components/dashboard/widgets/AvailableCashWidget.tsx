import { useDashboardCash } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { Wallet } from 'lucide-react'
import './AvailableCashWidget.css'

const CURRENCY_SYMBOLS: Record<string, string> = {
  CAD: 'C$', USD: 'US$', EUR: '\u20AC', GBP: '\u00A3', JPY: '\u00A5',
}

export default function AvailableCashWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardCash(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  if (data.availableCash.length === 0) {
    return (
      <div className="ac-empty">
        <Wallet style={{ height: '2rem', width: '2rem' }} />
        <span>No cash data available</span>
      </div>
    )
  }

  const total = data.availableCash.reduce((sum, c) => sum + c.amount, 0)

  return (
    <div>
      <div className="ac-total">
        {new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(total)}
      </div>
      <div className="ac-list">
        {data.availableCash.map(c => (
          <div key={c.currency} className="ac-item">
            <div className="ac-item-left">
              <span className="ac-currency-badge">
                {CURRENCY_SYMBOLS[c.currency] || c.currency}
              </span>
              <span className="ac-currency-name">{c.currency}</span>
            </div>
            <span className="ac-amount">
              {new Intl.NumberFormat('en-CA', { style: 'currency', currency: c.currency }).format(c.amount)}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
