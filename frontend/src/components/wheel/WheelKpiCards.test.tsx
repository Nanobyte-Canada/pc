import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WheelKpiCards } from './WheelKpiCards'
import type { CapitalMetrics } from '@/types/wheel'
import type { CurrencyAmount } from '@/types/dashboard'

function makeMetrics(overrides: Partial<CapitalMetrics> = {}): CapitalMetrics {
  return {
    cashUsd: 10000,
    cashCad: 5000,
    cashTotalUsd: 13500,
    cashTotalCad: 18630,
    deployedCsp: { usd: 5000, cad: 6900 },
    ccsWritten: { usd: 3000, cad: 4140 },
    totalPremium: { usd: 450, cad: 621 },
    unrealizedPnl: { usd: 200, cad: 276 },
    ...overrides,
  }
}

function makeBuyingPower(amounts: Array<{ currency: string; amount: number }> = []): CurrencyAmount[] {
  return amounts.map(a => a as CurrencyAmount)
}

const defaultCcEligible = new Map<string, { sharesOwned: number; contractsAvailable: number }>()

const defaultPositionCounts = { csp: 2, cc: 1, total: 3 }

describe('WheelKpiCards', () => {
  describe('Capital Available', () => {
    it('renders single USD total (CAD converted + USD)', () => {
      const buyingPower = makeBuyingPower([
        { currency: 'CAD', amount: 5500 },
        { currency: 'USD', amount: 8000 },
      ])
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={buyingPower}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      // 5500 / 1.38 + 8000 ≈ 11985.51
      const expectedValue = 5500 / 1.38 + 8000
      const formatted = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'USD' }).format(expectedValue)
      expect(screen.getByText(formatted)).toBeInTheDocument()
    })

    it('does not render C$/US$ labels', () => {
      const buyingPower = makeBuyingPower([
        { currency: 'CAD', amount: 5500 },
        { currency: 'USD', amount: 8000 },
      ])
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={buyingPower}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.queryByText('C$')).not.toBeInTheDocument()
      expect(screen.queryByText('US$')).not.toBeInTheDocument()
    })

    it('does not render divider or breakdown rows', () => {
      const buyingPower = makeBuyingPower([
        { currency: 'CAD', amount: 5500 },
        { currency: 'USD', amount: 8000 },
      ])
      const { container } = render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={buyingPower}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      // Should have no divider rows at all in the KPI cards
      const dividers = container.querySelectorAll('.wheel-kpi__divider')
      // Only the Positions card has a divider now (for CSP/CC breakdown)
      expect(dividers.length).toBe(1)
    })
  })

  describe('Capital Deployed', () => {
    it('renders total deployed (CSP + CC) without breakdown', () => {
      const metrics = makeMetrics({
        deployedCsp: { usd: 5000, cad: 6900 },
        ccsWritten: { usd: 3000, cad: 4140 },
      })
      render(
        <WheelKpiCards
          metrics={metrics}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      // Total deployed = 5000 + 3000 = 8000
      const formatted = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'USD' }).format(8000)
      expect(screen.getByText(formatted)).toBeInTheDocument()
    })

    it('does not render CSP/CC breakdown labels outside Positions card', () => {
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      // CSP and CC should only appear once each (in the Positions card)
      const cspElements = screen.getAllByText('CSP')
      const ccElements = screen.getAllByText('CC')
      expect(cspElements.length).toBe(1)
      expect(ccElements.length).toBe(1)
    })
  })

  describe('CC Available', () => {
    it('renders total contract count without per-ticker breakdown', () => {
      const ccEligible = new Map([
        ['SOXL', { sharesOwned: 200, contractsAvailable: 2 }],
        ['TECL', { sharesOwned: 100, contractsAvailable: 1 }],
      ])
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={ccEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.getByText('3 contracts')).toBeInTheDocument()
      // Should not show per-ticker labels
      expect(screen.queryByText('SOXL')).not.toBeInTheDocument()
      expect(screen.queryByText('TECL')).not.toBeInTheDocument()
    })

    it('shows 0 contracts when no CC-eligible tickers', () => {
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.getByText('0 contracts')).toBeInTheDocument()
    })

    it('uses singular "contract" for 1', () => {
      const ccEligible = new Map([
        ['SOXL', { sharesOwned: 100, contractsAvailable: 1 }],
      ])
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={ccEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.getByText('1 contract')).toBeInTheDocument()
    })
  })

  describe('Premium & P&L', () => {
    it('renders only main P&L number without Premium/Unrealized breakdown', () => {
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      const formatted = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'USD' }).format(200)
      expect(screen.getByText(formatted)).toBeInTheDocument()
      expect(screen.queryByText('Premium')).not.toBeInTheDocument()
      expect(screen.queryByText('Unrealized')).not.toBeInTheDocument()
    })
  })

  describe('Positions', () => {
    it('renders total with CSP and CC counts above the total', () => {
      const positionCounts = { csp: 2, cc: 3, total: 5 }
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={positionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.getByText('5')).toBeInTheDocument()
      expect(screen.getByText('2')).toBeInTheDocument() // CSP count
      expect(screen.getByText('3')).toBeInTheDocument() // CC count
    })

    it('does not render Expiring breakdown', () => {
      const positionCounts = { csp: 2, cc: 1, total: 3 }
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={positionCounts}
          fxRate={1.38}
        />
      )
      expect(screen.queryByText('Expiring')).not.toBeInTheDocument()
    })

    it('renders CSP and CC labels', () => {
      render(
        <WheelKpiCards
          metrics={makeMetrics()}
          buyingPower={makeBuyingPower()}
          ccEligible={defaultCcEligible}
          positionCounts={defaultPositionCounts}
          fxRate={1.38}
        />
      )
      // CSP and CC should appear only in Positions card
      const cspElements = screen.getAllByText('CSP')
      const ccElements = screen.getAllByText('CC')
      expect(cspElements.length).toBe(1)
      expect(ccElements.length).toBe(1)
    })
  })
})
