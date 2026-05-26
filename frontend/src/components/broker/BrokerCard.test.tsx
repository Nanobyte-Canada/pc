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

  it('calls onConnect with broker slug when card clicked', () => {
    const mockOnConnect = vi.fn()
    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={mockOnConnect}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    fireEvent.click(screen.getByRole('button'))
    expect(mockOnConnect).toHaveBeenCalledWith('questrade')
  })

  it('does not call onConnect when disabled', () => {
    const mockOnConnect = vi.fn()
    render(
      <BrokerCard
        broker={{ ...mockBroker, maintenanceMode: true }}
        onConnect={mockOnConnect}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    fireEvent.click(screen.getByRole('button'))
    expect(mockOnConnect).not.toHaveBeenCalled()
  })

  it('renders broker initial as icon when no logoUrl', () => {
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

  it('shows connected pill when connection exists', () => {
    const connections = [{
      id: 1,
      broker: mockBroker,
      gatewayConnectionId: 'gw-1',
      accountNumber: '123456',
      accountType: 'TFSA',
      accountName: null,
      accountNumberActual: null,
      accountMetaType: null,
      status: 'ACTIVE' as const,
      lastPositionsFetchedAt: null,
      positionsCount: 5,
      totalValue: 10000,
      errorMessage: null,
      createdAt: '2024-01-01',
      modelPortfolioId: null,
      modelPortfolioName: null,
    }]

    render(
      <BrokerCard
        broker={mockBroker}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={true}
        connections={connections}
      />
    )

    expect(screen.getByText('1 Account Connected')).toBeInTheDocument()
  })

  it('shows maintenance badge when broker is in maintenance', () => {
    render(
      <BrokerCard
        broker={{ ...mockBroker, maintenanceMode: true }}
        onConnect={vi.fn()}
        isConnecting={false}
        hasExistingConnection={false}
      />
    )

    expect(screen.getByText('Maintenance')).toBeInTheDocument()
  })
})
