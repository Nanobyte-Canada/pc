import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminPage } from './AdminPage'
import * as adminService from '../../services/adminService'

vi.mock('../../services/adminService', () => ({
  getIngestionStats: vi.fn(),
  getIngestionRuns: vi.fn(),
  getErrorSummary: vi.fn(),
  getRecentErrors: vi.fn(),
  triggerFullIngestion: vi.fn(),
  triggerStockIngestion: vi.fn(),
  triggerStockEnrichment: vi.fn(),
  triggerEtfComUniverse: vi.fn(),
  triggerEtfComEnrichment: vi.fn(),
}))

const mockStats: adminService.IngestionStats = {
  totalStocks: 5000,
  stocksWithRawData: 4500,
  stocksPendingIngestion: 500,
  totalEtfs: 3000,
  etfsEnriched: 2800,
  etfsPendingEnrichment: 200,
  errorsLast24h: 12,
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
    vi.mocked(adminService.getIngestionRuns).mockResolvedValue([])
    vi.mocked(adminService.getErrorSummary).mockResolvedValue([])
    vi.mocked(adminService.getRecentErrors).mockResolvedValue([])
  })

  it('renders the page title', () => {
    renderWithProviders(<AdminPage />)
    expect(screen.getByText(/Admin — Ingestion Pipeline/i)).toBeTruthy()
  })

  it('renders all 5 workflow cards', () => {
    renderWithProviders(<AdminPage />)
    expect(screen.getByText('Full Pipeline')).toBeTruthy()
    expect(screen.getByText('Stock Ingestion')).toBeTruthy()
    expect(screen.getByText('AV Stock Ingestion')).toBeTruthy()
    expect(screen.getByText('ETF Ingestion')).toBeTruthy()
    expect(screen.getByText('ETF Enrichment')).toBeTruthy()
  })

  it('displays ingestion stats after loading', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText(/4,500 \/ 5,000/)).toBeTruthy()
    })
  })

  it('shows empty state when no runs are available', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText(/No ingestion runs found/i)).toBeTruthy()
    })
  })

  it('shows no errors message when error summary is empty', async () => {
    renderWithProviders(<AdminPage />)
    await waitFor(() => {
      expect(screen.getByText(/No errors in the last 24 hours/i)).toBeTruthy()
    })
  })

  it('displays error summary badges when errors exist', async () => {
    vi.mocked(adminService.getErrorSummary).mockResolvedValue([
      { errorType: 'API_ERROR', count: 5, lastOccurredAt: null },
      { errorType: 'PARSE_ERROR', count: 7, lastOccurredAt: null },
    ])

    renderWithProviders(<AdminPage />)

    await waitFor(() => {
      expect(screen.getByText('API_ERROR: 5')).toBeTruthy()
      expect(screen.getByText('PARSE_ERROR: 7')).toBeTruthy()
    })
  })
})
