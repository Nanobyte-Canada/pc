import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminPage } from './AdminPage'
import * as adminService from '../../services/adminService'

vi.mock('../../services/adminService', () => ({
  getIngestionStats: vi.fn(),
  getActiveRun: vi.fn(),
  getIngestionRuns: vi.fn(),
  getRunSteps: vi.fn(),
  getRunErrors: vi.fn(),
  triggerExchangeSync: vi.fn(),
  triggerFullIngestion: vi.fn(),
  cancelIngestion: vi.fn(),
}))

const mockStats: adminService.IngestionStats = {
  totalInstruments: 12847,
  enrichedInstruments: 8231,
  pendingInstruments: 4616,
  remainingDailyQuota: 72450,
  totalDailyQuota: 100000,
  exchangeCount: 5,
  exchanges: ['US', 'TO', 'V', 'INDX', 'GBOND'],
  lastRunStatus: 'COMPLETED',
  lastRunCompletedAt: new Date(Date.now() - 7200000).toISOString(),
  instrumentsByType: {
    STOCK: { total: 8421, enriched: 5102 },
    ETF: { total: 2847, enriched: 2103 },
    MUTUAL_FUND: { total: 1234, enriched: 891 },
    PREFERRED_STOCK: { total: 189, enriched: 72 },
    INDEX: { total: 124, enriched: 48 },
    BOND: { total: 32, enriched: 15 },
  },
}

const mockActiveRun: adminService.ActiveRun = {
  isRunning: false,
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>
  )
}

describe('AdminPage', () => {
  beforeEach(() => {
    vi.mocked(adminService.getIngestionStats).mockResolvedValue(mockStats)
    vi.mocked(adminService.getActiveRun).mockResolvedValue(mockActiveRun)
    vi.mocked(adminService.getIngestionRuns).mockResolvedValue([])
  })

  it('renders the page title', () => {
    renderWithProviders(<AdminPage />)
    expect(screen.getByText('Admin Panel')).toBeTruthy()
  })

  it('renders both workflow cards', () => {
    renderWithProviders(<AdminPage />)
    expect(screen.getByText('Exchange Sync')).toBeTruthy()
    expect(screen.getByText('Full Ingestion')).toBeTruthy()
  })

  it('displays summary stats after loading', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText('12,847')).toBeTruthy()
      expect(screen.getByText('8,231')).toBeTruthy()
      expect(screen.getByText('4,616')).toBeTruthy()
    })
  })

  it('displays instrument type stats', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText('8,421')).toBeTruthy()
      expect(screen.getByText('2,847')).toBeTruthy()
    })
  })

  it('shows empty state when no runs are available', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText(/No ingestion runs found/i)).toBeTruthy()
    })
  })
})
