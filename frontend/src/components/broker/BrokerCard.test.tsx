import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerCard } from './BrokerCard'
import type { Broker } from '../../types/broker'
import { scenario } from '../../test/scenario'

describe('BrokerCard', () => {
  const mockBroker: Broker = {
    name: 'Questrade',
    slug: 'questrade',
    description: 'Canadian discount brokerage',
    status: 'ACTIVE',
    logoUrl: null
  }

  it(scenario('BROKER-CARD-001', 'renders broker name'), () => {
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

  it(scenario('BROKER-CARD-002', 'calls onConnect with broker slug when card clicked'), () => {
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

  it(scenario('BROKER-CARD-003', 'does not call onConnect when disabled'), () => {
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

  it(scenario('BROKER-CARD-004', 'renders broker initial as icon when no logoUrl'), () => {
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

  it(scenario('BROKER-CARD-005', 'shows connected pill when connection exists'), () => {
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
      lastActivitiesFetchedAt: null,
      lastActivitiesSyncStatus: null,
      lastBalanceFetchedAt: null,
      lastBalanceSyncStatus: null,
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

  it(scenario('BROKER-CARD-006', 'shows maintenance badge when broker is in maintenance'), () => {
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
