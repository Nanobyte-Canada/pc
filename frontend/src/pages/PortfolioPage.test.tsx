import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi } from 'vitest'

vi.mock('@/hooks/useModelPortfolios', () => ({
  useModelPortfolios: () => ({
    data: {
      models: [
        { id: 1, name: 'Conservative Income', riskLevel: 'LOW', isSystem: true, allocationCount: 5, totalPercent: 100, description: null },
        { id: 2, name: 'Balanced Growth', riskLevel: 'MODERATE', isSystem: true, allocationCount: 5, totalPercent: 100, description: null },
        { id: 3, name: 'Growth', riskLevel: 'HIGH', isSystem: true, allocationCount: 5, totalPercent: 100, description: null },
        { id: 4, name: 'Aggressive Equity', riskLevel: 'EXTRA_HIGH', isSystem: true, allocationCount: 5, totalPercent: 100, description: null },
      ],
    },
    isLoading: false,
  }),
  useModelAnalysis: () => ({ data: null, isLoading: false }),
  useModelPortfolio: () => ({ data: null, isLoading: false }),
}))

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter>{children}</MemoryRouter>
  </QueryClientProvider>
)

describe('PortfolioPage', () => {
  it('renders 4 system model cards plus custom slot', async () => {
    const PortfolioPage = (await import('./PortfolioPage')).default
    render(<PortfolioPage />, { wrapper })
    expect(screen.getByText('Conservative Income')).toBeInTheDocument()
    expect(screen.getByText('Balanced Growth')).toBeInTheDocument()
    expect(screen.getByText('Growth')).toBeInTheDocument()
    expect(screen.getByText('Aggressive Equity')).toBeInTheDocument()
    expect(screen.getByText('Build Your Own')).toBeInTheDocument()
  })
})
