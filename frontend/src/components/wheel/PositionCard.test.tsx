import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PositionCard } from './PositionCard'
import type { WheelPosition } from '@/types/wheel'

function makePosition(overrides: Partial<WheelPosition> = {}): WheelPosition {
  return {
    id: 1,
    type: 'CSP',
    strike: 20,
    premium: 85,
    collectedPremium: 85,
    currentPrice: 22,
    pnl: 62,
    otmPercent: 10.0,
    quantity: 1,
    currency: 'USD',
    accountName: null,
    accountNumber: null,
    connectionId: 1,
    ...overrides,
  }
}

describe('PositionCard', () => {
  it('renders strike and type label', () => {
    render(<PositionCard position={makePosition()} onClick={vi.fn()} />)
    expect(screen.getByText('$20 Put')).toBeInTheDocument()
  })

  it('renders CC type correctly', () => {
    const pos = makePosition({ type: 'CC' })
    render(<PositionCard position={pos} onClick={vi.fn()} />)
    expect(screen.getByText('$20 CC')).toBeInTheDocument()
  })

  it('renders OTM percentage', () => {
    render(<PositionCard position={makePosition({ otmPercent: 10.0 })} onClick={vi.fn()} />)
    expect(screen.getByText('10.0%')).toBeInTheDocument()
  })

  it('renders P&L with positive sign', () => {
    const { container } = render(<PositionCard position={makePosition({ pnl: 62 })} onClick={vi.fn()} />)
    const pnlEl = container.querySelector('.wpc__pnl--pos')
    expect(pnlEl).toBeTruthy()
    expect(pnlEl!.textContent).toContain('+')
    expect(pnlEl!.textContent).toContain('62')
  })

  it('renders P&L without positive sign for negative', () => {
    const { container } = render(<PositionCard position={makePosition({ pnl: -30 })} onClick={vi.fn()} />)
    const pnlEl = container.querySelector('.wpc__pnl--neg')
    expect(pnlEl).toBeTruthy()
    expect(pnlEl!.textContent).toContain('30')
  })

  it('renders "--" when pnl is null', () => {
    render(<PositionCard position={makePosition({ pnl: null })} onClick={vi.fn()} />)
    expect(screen.getByText('--')).toBeInTheDocument()
  })

  it('displays collected premium when present and positive', () => {
    const { container } = render(<PositionCard position={makePosition({ collectedPremium: 85 })} onClick={vi.fn()} />)
    const premEl = container.querySelector('.wpc__premium')
    expect(premEl).toBeTruthy()
    expect(premEl!.textContent).toContain('Prem:')
    expect(premEl!.textContent).toContain('85')
  })

  it('does not display collected premium when null', () => {
    const { container } = render(<PositionCard position={makePosition({ collectedPremium: null })} onClick={vi.fn()} />)
    expect(container.querySelector('.wpc__premium')).toBeNull()
  })

  it('does not display collected premium when 0', () => {
    const { container } = render(<PositionCard position={makePosition({ collectedPremium: 0 })} onClick={vi.fn()} />)
    expect(container.querySelector('.wpc__premium')).toBeNull()
  })

  it('calls onClick with position when clicked', () => {
    const onClick = vi.fn()
    const pos = makePosition()
    render(<PositionCard position={pos} onClick={onClick} />)
    screen.getByRole('button').click()
    expect(onClick).toHaveBeenCalledWith(pos)
  })
})
