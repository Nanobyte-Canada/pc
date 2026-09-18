import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ConnectionStatus } from './ConnectionStatus'
import { scenario } from '../../test/scenario'

describe('ConnectionStatus', () => {
  it(scenario('BROKER-STATUS-001', 'renders ACTIVE status with correct label'), () => {
    render(<ConnectionStatus status="ACTIVE" />)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it(scenario('BROKER-STATUS-002', 'renders PENDING status with correct label'), () => {
    render(<ConnectionStatus status="PENDING" />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })

  it(scenario('BROKER-STATUS-003', 'renders EXPIRED status with correct label'), () => {
    render(<ConnectionStatus status="EXPIRED" />)
    expect(screen.getByText('Token Expired')).toBeInTheDocument()
  })

  it(scenario('BROKER-STATUS-004', 'renders ERROR status with correct label'), () => {
    render(<ConnectionStatus status="ERROR" />)
    expect(screen.getByText('Error')).toBeInTheDocument()
  })

  it(scenario('BROKER-STATUS-005', 'renders DISCONNECTED status with correct label'), () => {
    render(<ConnectionStatus status="DISCONNECTED" />)
    expect(screen.getByText('Disconnected')).toBeInTheDocument()
  })

  it(scenario('BROKER-STATUS-006', 'applies correct color styling for ACTIVE status'), () => {
    const { container } = render(<ConnectionStatus status="ACTIVE" />)
    const span = container.querySelector('span')
    expect(span).toHaveStyle({ color: '#059669' })
  })

  it(scenario('BROKER-STATUS-007', 'applies correct color styling for ERROR status'), () => {
    const { container } = render(<ConnectionStatus status="ERROR" />)
    const span = container.querySelector('span')
    expect(span).toHaveStyle({ color: '#dc2626' })
  })

  it(scenario('BROKER-STATUS-008', 'renders with status indicator dot'), () => {
    const { container } = render(<ConnectionStatus status="ACTIVE" />)
    const dots = container.querySelectorAll('span')
    // Should have outer span and inner dot span
    expect(dots.length).toBeGreaterThan(1)
  })
})
