import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerCard } from './BrokerCard'
import type { Broker } from '../../types/broker'

describe('BrokerCard', () => {
  const mockBroker: Broker = {
    id: 1,
    code: 'QUESTRADE',
    name: 'Questrade',
    description: 'Canadian discount brokerage',
    authType: 'OAUTH2',
    status: 'ACTIVE'
  }

  it('renders broker name', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )
    expect(screen.getByText('Questrade')).toBeInTheDocument()
  })

  it('renders broker description when provided', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )
    expect(screen.getByText('Canadian discount brokerage')).toBeInTheDocument()
  })

  it('shows Connect button for new connection', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )
    expect(screen.getByText('Connect')).toBeInTheDocument()
  })

  it('shows Add Account button when already connected', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={true}
      />
    )
    expect(screen.getByText('Add Account')).toBeInTheDocument()
  })

  it('shows Connecting... when connecting', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={true}
        hasExistingConnection={false}
      />
    )
    expect(screen.getByText('Connecting...')).toBeInTheDocument()
  })

  it('calls onConnect with broker code when button clicked', () => {
    const mockOnConnect = vi.fn()
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={mockOnConnect}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    fireEvent.click(screen.getByText('Connect'))
    expect(mockOnConnect).toHaveBeenCalledWith('QUESTRADE')
  })

  it('disables button when broker status is not ACTIVE', () => {
    const inactiveBroker = { ...mockBroker, status: 'MAINTENANCE' as const }
    render(
      <BrokerCard
        broker={inactiveBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
  })

  it('disables button when isConnecting is true', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={true}
        hasExistingConnection={false}
      />
    )

    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
  })

  it('shows Under Maintenance message for MAINTENANCE status', () => {
    const maintenanceBroker = { ...mockBroker, status: 'MAINTENANCE' as const }
    render(
      <BrokerCard
        broker={maintenanceBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    expect(screen.getByText('Under Maintenance')).toBeInTheDocument()
  })

  it('shows Unavailable message for INACTIVE status', () => {
    const inactiveBroker = { ...mockBroker, status: 'INACTIVE' as const }
    render(
      <BrokerCard
        broker={inactiveBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    expect(screen.getByText('Unavailable')).toBeInTheDocument()
  })

  it('renders broker initial as avatar', () => {
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    expect(screen.getByText('Q')).toBeInTheDocument()
  })
})
