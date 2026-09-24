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

  it('hides the net debit/credit readout when there are no legs', () => {
    useStrategyStore.setState({ legs: [] })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    expect(screen.queryByText(/Net debit/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Net credit/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Waiting for mid prices/i)).not.toBeInTheDocument()
  })

  it('shows a live net debit for buy legs', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    // BUY 1 @ 2.50 -> pay $250.00
    expect(screen.getByText('Net debit')).toBeInTheDocument()
    expect(screen.getByText('$250.00')).toBeInTheDocument()
  })

  it('shows a live net credit for sell legs', () => {
    useStrategyStore.setState({
      legs: [{ ...leg, action: 'SELL', price: 4 }],
    })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    // SELL 1 @ 4.00 -> receive $400.00
    expect(screen.getByText('Net credit')).toBeInTheDocument()
    expect(screen.getByText('$400.00')).toBeInTheDocument()
  })

  it('nets multiple legs together', () => {
    useStrategyStore.setState({
      legs: [
        leg,
        { ...leg, action: 'SELL' as const, strike: 110, price: 1.0 },
      ],
    })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    // -2.50 + 1.00 = -1.50 per share -> $150.00 debit
    expect(screen.getByText('Net debit')).toBeInTheDocument()
    expect(screen.getByText('$150.00')).toBeInTheDocument()
  })

  it('recomputes the net when quantity changes', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    expect(screen.getByText('$250.00')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Increase quantity for leg 1'))
    expect(screen.getByText('$500.00')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Decrease quantity for leg 1'))
    expect(screen.getByText('$250.00')).toBeInTheDocument()
  })

  it('prefers the live mid over the stored leg price', () => {
    render(
      <LegBuilder onCalculate={() => {}} isCalculating={false} liveMid={() => 3.25} />
    )
    // BUY 1 @ live 3.25 -> $325.00 debit (not the stored 2.50)
    expect(screen.getByText('$325.00')).toBeInTheDocument()
  })

  it('waits for prices instead of showing $0.00 when a leg has no price', () => {
    useStrategyStore.setState({
      legs: [{ ...leg, price: undefined }],
    })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    expect(screen.getByText('Waiting for mid prices')).toBeInTheDocument()
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument()
  })

  it('treats a live mid lookup miss as pending when the leg has no price either', () => {
    useStrategyStore.setState({
      legs: [{ ...leg, price: undefined }],
    })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} liveMid={() => undefined} />)
    expect(screen.getByText('Waiting for mid prices')).toBeInTheDocument()
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument()
  })
})
