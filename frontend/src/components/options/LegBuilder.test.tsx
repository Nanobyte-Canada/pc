import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LegBuilder } from './LegBuilder'
import { useStrategyStore } from '@/stores/strategyStore'

const leg = {
  action: 'BUY' as const,
  optionType: 'CALL' as const,
  strike: 100,
  expiry: '2026-12-18',
  quantity: 1,
  price: 2.5,
  symbol: 'SPY',
}

describe('LegBuilder', () => {
  beforeEach(() => {
    useStrategyStore.setState({ legs: [leg] })
  })

  it('increments a leg quantity through the store', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Increase quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(2)
  })

  it('decrements a leg quantity through the store', () => {
    useStrategyStore.setState({ legs: [{ ...leg, quantity: 2 }] })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Decrease quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(1)
  })

  it('never decrements below one', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Decrease quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(1)
  })

  it('renders a live mid price when supplied', () => {
    render(
      <LegBuilder onCalculate={() => {}} isCalculating={false} liveMid={() => 3.75} />
    )
    expect(screen.getByText('$3.75')).toBeInTheDocument()
  })
})
