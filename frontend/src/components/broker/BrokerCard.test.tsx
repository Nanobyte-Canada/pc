import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerCard } from './BrokerCard'
import type { Broker } from '../../types/broker'

describe('BrokerCard', () => {
  const mockBroker: Broker = {
    name: 'Questrade',
    slug: 'questrade',
    description: 'Canadian discount brokerage',
    status: 'ACTIVE',
    logoUrl: null
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

  it('calls onConnect with broker slug when button clicked', () => {
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
    expect(mockOnConnect).toHaveBeenCalledWith('questrade')
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
