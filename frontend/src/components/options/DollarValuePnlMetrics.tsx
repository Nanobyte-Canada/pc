import './DollarValuePnlMetrics.css'

interface DollarValuePnlMetricsProps {
  maxProfit: number
  maxLoss: number
  maxProfitDollars: number
  maxLossDollars: number
  netDebitCreditDollars: number
  quantity: number
}

export function DollarValuePnlMetrics({
  maxProfit,
  maxLoss,
  maxProfitDollars,
  maxLossDollars,
  netDebitCreditDollars,
  quantity,
}: DollarValuePnlMetricsProps) {
  return (
    <div className="dollar-metrics">
      <div className="dollar-metrics__row">
        <div className="dollar-metrics__cell dollar-metrics__cell--profit">
          <span className="dollar-metrics__label">Max Profit</span>
          <div className="dollar-metrics__values">
            <span className="dollar-metrics__dollar">
              {maxProfitDollars >= 0 ? '+' : ''}{maxProfitDollars.toFixed(2)}
            </span>
            <span className="dollar-metrics__percent">
              {maxProfit > 0 ? '+' : ''}{(maxProfit * 100).toFixed(0)}%
            </span>
          </div>
          <span className="dollar-metrics__per-contract">
            per contract ({quantity}x)
          </span>
        </div>

        <div className="dollar-metrics__cell dollar-metrics__cell--loss">
          <span className="dollar-metrics__label">Max Loss</span>
          <div className="dollar-metrics__values">
            <span className="dollar-metrics__dollar">
              {maxLossDollars > 0 ? '-' : ''}{Math.abs(maxLossDollars).toFixed(2)}
            </span>
            <span className="dollar-metrics__percent">
              {maxLoss > 0 ? '-' : ''}{(Math.abs(maxLoss) * 100).toFixed(0)}%
            </span>
          </div>
          <span className="dollar-metrics__per-contract">
            per contract ({quantity}x)
          </span>
        </div>
      </div>

      <div className="dollar-metrics__net-row">
        <span className="dollar-metrics__label">Net Debit/Credit</span>
        <span className={`dollar-metrics__net-value ${netDebitCreditDollars >= 0 ? 'dollar-metrics__net-value--positive' : 'dollar-metrics__net-value--negative'}`}>
          {netDebitCreditDollars >= 0 ? '+' : ''}{netDebitCreditDollars.toFixed(2)}
        </span>
      </div>
    </div>
  )
}
