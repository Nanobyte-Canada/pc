import type { Leg, OptionsChain } from '@/types/options'

function strikeKeyCandidates(strike: number): string[] {
  return [
    String(strike),
    strike.toFixed(1),
    strike.toFixed(2),
    strike.toFixed(4),
    `${strike}.0`,
    `${strike}.00`,
  ]
}

export function derivePriceFor(
  chains: Record<string, OptionsChain>,
  underlying: string | null
): (leg: Leg) => number | undefined {
  return (leg) => {
    if (!underlying || !leg.symbol) return undefined
    if (leg.symbol !== underlying) return undefined
    const expiryData = chains[underlying]?.expirations[leg.expiry]
    if (!expiryData) return undefined

    let strikeData = expiryData[String(leg.strike)]
    if (!strikeData) {
      for (const candidate of strikeKeyCandidates(leg.strike)) {
        if (expiryData[candidate]) {
          strikeData = expiryData[candidate]
          break
        }
      }
    }
    if (!strikeData) return undefined

    const quote = leg.optionType === 'PUT' ? strikeData.put : strikeData.call
    if (!quote) return undefined
    if (typeof quote.mid === 'number' && quote.mid > 0) return quote.mid
    if (quote.bid > 0 && quote.ask > 0) return (quote.bid + quote.ask) / 2
    return undefined
  }
}
