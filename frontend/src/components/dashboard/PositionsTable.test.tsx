import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { UseQueryResult } from '@tanstack/react-query'
import { PositionsTable } from './PositionsTable'
import { scenario } from '../../test/scenario'
import type { AggregatedPositionsResponse } from '@/types/broker'
import type { OpenOrdersResponse } from '@/types/dashboard'

vi.mock('@/hooks/useDashboardWidgets', () => ({
  useDashboardPositions: vi.fn(),
  useOpenOrders: vi.fn(),
}))

vi.mock('@/hooks/useAgGridTheme', () => ({
  useAgGridTheme: () => 'ag-theme-quartz-dark',
}))

vi.mock('@/hooks/useAutoPageSize', () => ({
  useAutoPageSize: () => 15,
}))

vi.mock('ag-grid-react', () => ({
  AgGridReact: ({ rowData, columnDefs }: { rowData: unknown[]; columnDefs: unknown[] }) => (
    <div data-testid="ag-grid" data-row-count={rowData?.length} data-col-count={columnDefs?.length} />
  ),
}))

import { useDashboardPositions, useOpenOrders } from '@/hooks/useDashboardWidgets'

/**
 * Builds a complete, typed TanStack Query v5 result mock.
 * Defaults to a successful (settled, idle) result with no data; pass overrides
 * to shape the state under test (e.g. `{ isLoading: true }` or `{ data }`).
 */
function queryResult<T>(overrides: Partial<UseQueryResult<T, Error>> = {}): UseQueryResult<T, Error> {
  return {
    data: undefined as T | undefined,
    dataUpdatedAt: 0,
    error: null,
    errorUpdatedAt: 0,
    failureCount: 0,
    failureReason: null,
    errorUpdateCount: 0,
    fetchStatus: 'idle',
    isError: false,
    isFetched: true,
    isFetchedAfterMount: true,
    isFetching: false,
    isInitialLoading: false,
    isLoading: false,
    isLoadingError: false,
    isPaused: false,
    isPending: false,
    isPlaceholderData: false,
    isRefetchError: false,
    isRefetching: false,
    isStale: false,
    isSuccess: true,
    isEnabled: true,
    status: 'success',
    refetch: async () => ({}) as UseQueryResult<T, Error>,
    ...overrides,
  } as UseQueryResult<T, Error>
}

const mockPositionsResponse: AggregatedPositionsResponse = {
  asOfDate: '2026-09-15',
  positions: [
    {
      symbol: 'AAPL',
      securityName: 'Apple Inc.',
      instrumentType: 'EQUITY',
      totalQuantity: 50,
      totalValue: 9500,
      averageCost: 170.0,
      totalPnl: 1000,
      totalPnlPercent: 11.76,
      currency: 'USD',
      brokerBreakdown: [],
    },
    {
      symbol: 'VEQT',
      securityName: 'Vanguard All-Equity ETF',
      instrumentType: 'ETF',
      totalQuantity: 200,
      totalValue: 7200,
      averageCost: 33.0,
      totalPnl: 600,
      totalPnlPercent: 9.09,
      currency: 'CAD',
      brokerBreakdown: [],
    },
  ],
  aggregateSummary: {
    totalValue: 16700,
    totalCost: 15100,
    totalPnl: 1600,
    totalPnlPercent: 10.6,
    brokerCount: 1,
    accountCount: 1,
  },
}

describe('PositionsTable', () => {
  it(scenario('DASH-POS-001', 'renders loading skeleton when data is loading'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ isLoading: true }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>())

    const { container } = render(<PositionsTable />)
    expect(container.querySelector('.positions-table')).toBeInTheDocument()
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it(scenario('DASH-POS-002', 'renders empty state when no positions'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(
      queryResult<AggregatedPositionsResponse>({ data: { ...mockPositionsResponse, positions: [] } })
    )
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>())

    render(<PositionsTable />)
    expect(screen.getByText('No positions')).toBeInTheDocument()
  })

  it(scenario('DASH-POS-003', 'renders Positions title and tab buttons'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>())

    render(<PositionsTable />)
    expect(screen.getByText('Positions')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Holdings' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Orders' })).toBeInTheDocument()
  })

  it(scenario('DASH-POS-004', 'renders AG Grid with position data'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>())

    render(<PositionsTable />)
    const grid = screen.getByTestId('ag-grid')
    expect(grid).toBeInTheDocument()
    expect(grid.getAttribute('data-row-count')).toBe('2')
  })

  it(scenario('DASH-POS-005', 'renders search input'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>())

    render(<PositionsTable />)
    expect(screen.getByPlaceholderText('Search...')).toBeInTheDocument()
  })

  it(scenario('DASH-POS-006', 'switches to orders tab on click'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(
      queryResult<OpenOrdersResponse>({ data: { orders: [], totalCount: 0 } })
    )

    render(<PositionsTable />)
    fireEvent.click(screen.getByRole('button', { name: 'Orders' }))
    expect(screen.getByText('No open orders')).toBeInTheDocument()
  })

  it(scenario('DASH-POS-007', 'shows empty orders state when orders tab is active with no data'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>({ data: { orders: [], totalCount: 0 } }))

    render(<PositionsTable />)
    fireEvent.click(screen.getByRole('button', { name: 'Orders' }))
    expect(screen.getByText('No open orders')).toBeInTheDocument()
  })

  it(scenario('DASH-POS-008', 'shows loading skeleton for orders tab when loading'), () => {
    vi.mocked(useDashboardPositions).mockReturnValue(queryResult<AggregatedPositionsResponse>({ data: mockPositionsResponse }))
    vi.mocked(useOpenOrders).mockReturnValue(queryResult<OpenOrdersResponse>({ isLoading: true }))

    const { container } = render(<PositionsTable />)
    fireEvent.click(screen.getByRole('button', { name: 'Orders' }))
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })
})
