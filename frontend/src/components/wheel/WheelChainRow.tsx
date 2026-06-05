import { memo } from 'react'
import type { WheelChainStrike } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'

interface WheelChainRowProps {
  strike: WheelChainStrike
  onClick: (strike: WheelChainStrike) => void
}

export const WheelChainRow = memo(function WheelChainRow({ strike, onClick }: WheelChainRowProps) {
  const rowClass = strike.isATM
    ? 'wcp-row wcp-row-atm'
    : strike.isITM
      ? 'wcp-row wcp-row-itm'
      : 'wcp-row'

  const discountClass = strike.discount != null
    ? strike.discount > 0 ? 'wcp-discount--green' : strike.discount < 0 ? 'wcp-discount--red' : ''
    : ''

  return (
    <tr className={rowClass} onClick={() => onClick(strike)} role="button" tabIndex={0}>
      <td className="wcp-cell wcp-cell-strike">
        <div className="wcp-primary">{formatCurrency(strike.strike, 'USD')}</div>
        <div className="wcp-secondary">
          {strike.delta != null ? `δ ${strike.delta.toFixed(2)}` : ''}
        </div>
      </td>
      <td className="wcp-cell wcp-cell-bid">
        <div className="wcp-primary">{strike.bid != null ? formatCurrency(strike.bid, 'USD') : '—'}</div>
        <div className={`wcp-secondary ${discountClass}`}>
          {strike.discount != null ? `${(strike.discount * 100).toFixed(1)}%` : ''}
        </div>
        <div className="wcp-secondary wcp-yield">
          {strike.bidYield != null ? `${(strike.bidYield * 100).toFixed(1)}%` : ''}
        </div>
      </td>
      <td className="wcp-cell wcp-cell-ask">
        <div className="wcp-primary">{strike.ask != null ? formatCurrency(strike.ask, 'USD') : '—'}</div>
        <div className={`wcp-secondary ${discountClass}`}>
          {strike.discount != null ? `${(strike.discount * 100).toFixed(1)}%` : ''}
        </div>
        <div className="wcp-secondary wcp-yield">
          {strike.askYield != null ? `${(strike.askYield * 100).toFixed(1)}%` : ''}
        </div>
      </td>
    </tr>
  )
})
