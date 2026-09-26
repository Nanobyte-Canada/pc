import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerConnectionCard } from './BrokerConnectionCard'
import type { BrokerConnection } from '../../types/broker'
import { scenario } from '../../test/scenario'

vi.mock('lucide-react', () => ({
  MoreVertical: () => <span data-testid="more-icon" />,
}))

function makeConnection(overrides?: Partial<BrokerConnection>): BrokerConnection {
  return {
    id: 1,
    broker: {
      name: 'Questrade',
      slug: 'questrade',
      status: 'ACTIVE',
      logoUrl: null,
      description: null,
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
    lastActivitiesFetchedAt: null,
    lastActivitiesSyncStatus: null,
    lastBalanceFetchedAt: null,
    lastBalanceSyncStatus: null,
    modelPortfolioId: null,
    modelPortfolioName: null,
    ...overrides,
  }
}

describe('BrokerConnectionCard', () => {
  it(scenario('BROKER-CONN-CARD-001', 'renders account type as heading'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('TFSA')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-002', 'renders masked account number'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText(/5513/)).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-003', 'renders total value with C$ prefix for CAD account'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('C$ 25,001')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-004', 'renders plural positions count'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('5 positions')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-005', 'renders singular position for count of 1'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ positionsCount: 1 })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('1 position')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-006', 'shows Sync button for active connections'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Sync')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-007', 'shows Syncing when isSyncing is true'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={true}
      />
    )
    expect(screen.getByText('Syncing...')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-008', 'calls onSyncAll with connection id when Sync clicked'), () => {
    const mockOnSyncAll = vi.fn()
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={mockOnSyncAll}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByText('Sync'))
    expect(mockOnSyncAll).toHaveBeenCalledWith(1)
  })

  it(scenario('BROKER-CONN-CARD-009', 'shows Reconnect button for EXPIRED status'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ status: 'EXPIRED' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-010', 'shows Reconnect button for ERROR status'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ status: 'ERROR' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-011', 'calls onReconnect with gatewayConnectionId when Reconnect clicked'), () => {
    const mockOnReconnect = vi.fn()
    render(
      <BrokerConnectionCard
        connection={makeConnection({ status: 'EXPIRED' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={mockOnReconnect}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByText('Reconnect'))
    expect(mockOnReconnect).toHaveBeenCalledWith('gw-conn-uuid-123')
  })

  it(scenario('BROKER-CONN-CARD-012', 'displays error message when present'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ status: 'ERROR', errorMessage: 'Token refresh failed' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Token refresh failed')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-013', 'renders broker brand icon for Questrade'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('Q')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-014', 'shows error-state class for ERROR connections'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ status: 'ERROR' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    const card = document.querySelector('.broker-connection-card')
    expect(card?.className).toContain('error-state')
  })

  it(scenario('BROKER-CONN-CARD-015', 'opens more menu on MoreVertical click'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByTestId('more-icon').closest('button')!)
    expect(screen.getByText('Disconnect')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-016', 'shows disconnect confirmation after clicking Disconnect'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByTestId('more-icon').closest('button')!)
    fireEvent.click(screen.getByText('Disconnect'))
    expect(screen.getByText('Confirm Disconnect')).toBeInTheDocument()
    expect(screen.getByText('Cancel')).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-017', 'calls onDisconnect on confirm'), () => {
    const mockOnDisconnect = vi.fn()
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={mockOnDisconnect}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByTestId('more-icon').closest('button')!)
    fireEvent.click(screen.getByText('Disconnect'))
    fireEvent.click(screen.getByText('Confirm Disconnect'))
    expect(mockOnDisconnect).toHaveBeenCalledWith('gw-conn-uuid-123')
  })

  it(scenario('BROKER-CONN-CARD-018', 'cancels disconnect on Cancel click'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection()}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    fireEvent.click(screen.getByTestId('more-icon').closest('button')!)
    fireEvent.click(screen.getByText('Disconnect'))
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.getByText('Disconnect')).toBeInTheDocument()
    expect(screen.queryByText('Confirm Disconnect')).not.toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-019', 'renders US$ prefix for USD account type'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({ accountMetaType: 'US Margin' })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText(/US\$/)).toBeInTheDocument()
  })

  it(scenario('BROKER-CONN-CARD-020', 'renders brand icon for Wealthsimple'), () => {
    render(
      <BrokerConnectionCard
        connection={makeConnection({
          broker: { name: 'Wealthsimple', slug: 'wealthsimple', status: 'ACTIVE', logoUrl: null, description: null },
        })}
        onSyncAll={vi.fn()}
        onDisconnect={vi.fn()}
        onReconnect={vi.fn()}
        isSyncing={false}
      />
    )
    expect(screen.getByText('W')).toBeInTheDocument()
  })
})
