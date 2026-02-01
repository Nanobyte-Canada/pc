import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ConnectionStatus } from './ConnectionStatus'
import type { ConnectionStatusType } from '../../types/broker'

describe('ConnectionStatus', () => {
  it('renders ACTIVE status with correct label', () => {
    render(<ConnectionStatus status="ACTIVE" />)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('renders PENDING status with correct label', () => {
    render(<ConnectionStatus status="PENDING" />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })

  it('renders EXPIRED status with correct label', () => {
    render(<ConnectionStatus status="EXPIRED" />)
    expect(screen.getByText('Expired')).toBeInTheDocument()
  })

  it('renders ERROR status with correct label', () => {
    render(<ConnectionStatus status="ERROR" />)
    expect(screen.getByText('Error')).toBeInTheDocument()
  })

  it('renders DISCONNECTED status with correct label', () => {
    render(<ConnectionStatus status="DISCONNECTED" />)
    expect(screen.getByText('Disconnected')).toBeInTheDocument()
  })

  it('applies correct color styling for ACTIVE status', () => {
    const { container } = render(<ConnectionStatus status="ACTIVE" />)
    const span = container.querySelector('span')
    expect(span).toHaveStyle({ color: '#10b981' })
  })

  it('applies correct color styling for ERROR status', () => {
    const { container } = render(<ConnectionStatus status="ERROR" />)
    const span = container.querySelector('span')
    expect(span).toHaveStyle({ color: '#ef4444' })
  })

  it('renders with status indicator dot', () => {
    const { container } = render(<ConnectionStatus status="ACTIVE" />)
    const dots = container.querySelectorAll('span')
    // Should have outer span and inner dot span
    expect(dots.length).toBeGreaterThan(1)
  })
})
