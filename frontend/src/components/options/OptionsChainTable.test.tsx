import { describe, it, expect, vi, beforeAll } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { OptionsChainTable } from './OptionsChainTable'
import type { OptionsChain } from '@/types/options'
import { scenario } from '../../test/scenario'

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
})

vi.mock('@/stores/strategyStore', () => ({
  useStrategyStore: () => ({
    addLeg: vi.fn(),
  }),
}))

function makeChain(overrides?: { expirations?: Record<string, Record<string, unknown>>; spotPrice?: number }): OptionsChain {
  return {
    underlying: 'AAPL',
    spotPrice: overrides?.spotPrice ?? 195,
    expirations: overrides?.expirations ?? {
      '2026-09-19': {
        '195.00': {
          call: {
            underlying: 'AAPL', optionType: 'CALL', strike: 195, expiry: '2026-09-19',
            bid: 2.50, ask: 2.70, last: 2.60, mid: 2.60, spread: 0.20,
            spreadQuality: 0.5, volume: 1000, openInterest: 5000,
            greeks: { delta: 0.5, gamma: 0.02, theta: -0.05, vega: 0.15, rho: 0.01, source: 'BLACK_SCHOLES' },
            timestamp: new Date().toISOString(),
          },
          put: {
            underlying: 'AAPL', optionType: 'PUT', strike: 195, expiry: '2026-09-19',
            bid: 2.30, ask: 2.50, last: 2.40, mid: 2.40, spread: 0.20,
            spreadQuality: 0.5, volume: 800, openInterest: 4000,
            greeks: { delta: -0.5, gamma: 0.02, theta: -0.05, vega: 0.15, rho: -0.01, source: 'BLACK_SCHOLES' },
            timestamp: new Date().toISOString(),
          },
        },
        '200.00': {
          call: {
            underlying: 'AAPL', optionType: 'CALL', strike: 200, expiry: '2026-09-19',
            bid: 0.80, ask: 1.00, last: 0.90, mid: 0.90, spread: 0.20,
            spreadQuality: 0.5, volume: 500, openInterest: 3000,
            greeks: { delta: 0.3, gamma: 0.01, theta: -0.04, vega: 0.12, rho: 0.005, source: 'BLACK_SCHOLES' },
            timestamp: new Date().toISOString(),
          },
          put: {
            underlying: 'AAPL', optionType: 'PUT', strike: 200, expiry: '2026-09-19',
            bid: 4.50, ask: 4.70, last: 4.60, mid: 4.60, spread: 0.20,
            spreadQuality: 0.5, volume: 600, openInterest: 2500,
            greeks: { delta: -0.7, gamma: 0.01, theta: -0.04, vega: 0.12, rho: -0.005, source: 'BLACK_SCHOLES' },
            timestamp: new Date().toISOString(),
          },
        },
      },
    },
  }
}

describe('OptionsChainTable', () => {
  it(scenario('OPT-TABLE-001', 'renders empty state when no expirations'), () => {
    render(
      <OptionsChainTable
        chain={makeChain({ expirations: {} })}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    expect(screen.getByText('No options chain data available')).toBeInTheDocument()
  })

  it(scenario('OPT-TABLE-002', 'renders expiry tabs with date'), () => {
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    const tabs = screen.getAllByText('2026-09-19')
    expect(tabs.length).toBeGreaterThanOrEqual(1)
  })

  it(scenario('OPT-TABLE-003', 'renders desktop column headers in table'), () => {
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    const thElements = document.querySelectorAll('th')
    const headerTexts = Array.from(thElements).map(th => th.textContent)
    expect(headerTexts).toContain('Bid')
    expect(headerTexts).toContain('Ask')
    expect(headerTexts).toContain('Delta')
    expect(headerTexts).toContain('Strike')
  })

  it(scenario('OPT-TABLE-004', 'renders strike prices in table cells'), () => {
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    const strikeCells = document.querySelectorAll('.chain-table__strike-cell')
    const strikes = Array.from(strikeCells).map(td => td.textContent)
    expect(strikes).toContain('195')
    expect(strikes).toContain('200')
  })

  it(scenario('OPT-TABLE-005', 'renders strikes per side selector with active state'), () => {
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    const activeBtn = document.querySelector('.chain-table__strikes-btn--active')
    expect(activeBtn?.textContent).toBe('25')
  })

  it(scenario('OPT-TABLE-006', 'calls onStrikesPerSideChange when strikes button clicked'), () => {
    const mockOnChange = vi.fn()
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={mockOnChange}
      />
    )
    const strikeBtns = document.querySelectorAll('.chain-table__strikes-btn')
    const btn50 = Array.from(strikeBtns).find(btn => btn.textContent === '50')!
    fireEvent.click(btn50)
    expect(mockOnChange).toHaveBeenCalledWith(50)
  })

  it(scenario('OPT-TABLE-007', 'calls onExpiryChange when expiry tab clicked'), () => {
    const mockOnExpiry = vi.fn()
    const chain = makeChain({
      expirations: {
        '2026-09-19': { '195.00': { call: null, put: null } },
        '2026-09-26': { '195.00': { call: null, put: null } },
      },
    })
    render(
      <OptionsChainTable
        chain={chain}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
        onExpiryChange={mockOnExpiry}
      />
    )
    const expiryTabs = document.querySelectorAll('.chain-table__expiry-tab')
    const tab26 = Array.from(expiryTabs).find(btn => btn.textContent === '2026-09-26')!
    fireEvent.click(tab26)
    expect(mockOnExpiry).toHaveBeenCalledWith('2026-09-26')
  })

  it(scenario('OPT-TABLE-008', 'highlights active expiry tab'), () => {
    render(
      <OptionsChainTable
        chain={makeChain()}
        strikesPerSide={25}
        onStrikesPerSideChange={vi.fn()}
      />
    )
    const activeTab = document.querySelector('.chain-table__expiry-tab--active')
    expect(activeTab?.textContent).toBe('2026-09-19')
  })
})
