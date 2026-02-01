import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrokerConnectionCard } from './BrokerConnectionCard'
import type { BrokerConnection } from '../../types/broker'

describe('BrokerConnectionCard', () => {
  const mockConnection: BrokerConnection = {
    id: 1,
    broker: {
      id: 1,
      code: 'QUESTRADE',
      name: 'Questrade',
      authType: 'OAUTH2',
      status: 'ACTIVE',
      logoUrl: null,
      description: null
    },
    accountNumber: '51234567',
    accountType: 'TFSA',
    accountName: 'My TFSA',
    status: 'ACTIVE',
    positionsCount: 5,
    totalValue: 25000.50,
    lastPositionsFetchedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    errorMessage: null,
    createdAt: new Date().toISOString()
  }

  it('renders broker name', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Questrade')).toBeInTheDocument()
  })

  it('renders account type', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText(/TFSA/)).toBeInTheDocument()
  })

  it('renders account number in parentheses', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('(51234567)')).toBeInTheDocument()
  })

  it('renders total value', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('$25,000.50')).toBeInTheDocument()
  })

  it('renders positions count', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('5 positions')).toBeInTheDocument()
  })

  it('renders singular position for count of 1', () => {
    const singlePositionConnection = { ...mockConnection, positionsCount: 1 }
    render(
      <BrokerConnectionCard
        connection={singlePositionConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('1 position')).toBeInTheDocument()
  })

  it('shows Fetch Now button for active connections', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Fetch Now')).toBeInTheDocument()
  })

  it('shows Fetching... when isFetching is true', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={true}
      />
    )
    expect(screen.getByText('Fetching...')).toBeInTheDocument()
  })

  it('calls onFetch with connection id when Fetch Now clicked', () => {
    const mockOnFetch = vi.fn()
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={mockOnFetch}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )

    fireEvent.click(screen.getByText('Fetch Now'))
    expect(mockOnFetch).toHaveBeenCalledWith(1)
  })

  it('shows Reconnect button for EXPIRED status', () => {
    const expiredConnection = { ...mockConnection, status: 'EXPIRED' as const }
    render(
      <BrokerConnectionCard
        connection={expiredConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it('shows Reconnect button for ERROR status', () => {
    const errorConnection = { ...mockConnection, status: 'ERROR' as const }
    render(
      <BrokerConnectionCard
        connection={errorConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Reconnect')).toBeInTheDocument()
  })

  it('shows Disconnect button', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Disconnect')).toBeInTheDocument()
  })

  it('shows confirmation buttons when Disconnect clicked', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )

    fireEvent.click(screen.getByText('Disconnect'))

    expect(screen.getByText('Confirm')).toBeInTheDocument()
    expect(screen.getByText('Cancel')).toBeInTheDocument()
  })

  it('calls onDisconnect when Confirm clicked', () => {
    const mockOnDisconnect = vi.fn()
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={mockOnDisconnect}
        isFetching={false}
      />
    )

    fireEvent.click(screen.getByText('Disconnect'))
    fireEvent.click(screen.getByText('Confirm'))

    expect(mockOnDisconnect).toHaveBeenCalledWith(1)
  })

  it('hides confirmation when Cancel clicked', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )

    fireEvent.click(screen.getByText('Disconnect'))
    expect(screen.getByText('Confirm')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.queryByText('Confirm')).not.toBeInTheDocument()
    expect(screen.getByText('Disconnect')).toBeInTheDocument()
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
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Token refresh failed')).toBeInTheDocument()
  })

  it('renders broker initial as avatar', () => {
    render(
      <BrokerConnectionCard
        connection={mockConnection}
        onFetch={vi.fn()}
        onDisconnect={vi.fn()}
        isFetching={false}
      />
    )
    expect(screen.getByText('Q')).toBeInTheDocument()
  })
})
