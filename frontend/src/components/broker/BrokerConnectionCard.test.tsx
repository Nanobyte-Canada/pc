import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerConnectionCard } from './BrokerConnectionCard'
import type { BrokerConnection } from '../../types/broker'

describe('BrokerConnectionCard', () => {
  const mockConnection: BrokerConnection = {
    id: 1,
    broker: {
      name: 'Questrade',
      slug: 'questrade',
      status: 'ACTIVE',
      logoUrl: null,
      description: null
    },
    gatewayConnectionId: 'gw-conn-uuid-123',
    accountNumber: '51234567',
    accountType: 'TFSA',
    accountName: 'My TFSA',
    accountNumberActual: '53105513',
    accountMetaType: 'TFSA',
    status: 'ACTIVE',
    positionsCount: 5,
    totalValue: 25000.50,
    lastPositionsFetchedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    errorMessage: null,
    createdAt: new Date().toISOString(),
    modelPortfolioId: null,
    modelPortfolioName: null
  }

  it('renders account type as heading', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('TFSA')).toBeInTheDocument()
  })

  it('renders masked account number', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    // Shows last 4 digits masked
    expect(screen.getByText(/5513/)).toBeInTheDocument()
  })

  it('renders total value', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('C$ 25,001')).toBeInTheDocument()
  })

  it('renders positions count', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('5 positions')).toBeInTheDocument()
  })

  it('renders singular position for count of 1', () => {
    const singlePositionConnection = { ...mockConnection, positionsCount: 1 }
    render(
      <BrokerConnectionCard
        connection={singlePositionConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('1 position')).toBeInTheDocument()
  })

  it('shows Sync button for active connections', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Sync')).toBeInTheDocument()
  })

  it('shows Syncing... when isSyncing is true', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={true}
      />
    )
    expect(screen.getByText('Syncing...')).toBeInTheDocument()
  })

  it('calls onSyncAll with connection id when Sync clicked', () => {
    const mockOnSyncAll = vi.fn()
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={mockOnSyncAll}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )

    fireEvent.click(screen.getByText('Sync'))
    expect(mockOnSyncAll).toHaveBeenCalledWith(1)
  })

  it('shows Reconnect button for EXPIRED status', () => {
    const expiredConnection = { ...mockConnection, status: 'EXPIRED' as const }
    render(
      <BrokerConnectionCard
        connection={expiredConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it('shows Reconnect button for ERROR status', () => {
    const errorConnection = { ...mockConnection, status: 'ERROR' as const }
    render(
      <BrokerConnectionCard
        connection={errorConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it('displays error message when present', () => {
    const errorConnection = {
      ...mockConnection,
      status: 'ERROR' as const,
      errorMessage: 'Token refresh failed'
    }
    render(
      <BrokerConnectionCard
        connection={errorConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Token refresh failed')).toBeInTheDocument()
  })

  it('renders broker brand icon for Questrade', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Q')).toBeInTheDocument()
  })
})
