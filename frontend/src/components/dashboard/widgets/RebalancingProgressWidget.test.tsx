import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'

vi.mock('@/hooks/useModelPortfolios', () => ({
  useRebalanceProgress: () => ({
    data: {
      connectionId: 1,
      modelName: 'Growth',
      accuracy: 11,
      entries: [
        { symbol: 'SOXL', securityName: 'Semiconductor Bull 3X', targetPercent: 10, actualPercent: 1.6, isNonModel: false },
        { symbol: 'TECL', securityName: 'Technology Bull 3X', targetPercent: 10, actualPercent: 4.3, isNonModel: false },
        { symbol: 'AAPL', securityName: 'Apple Inc.', targetPercent: 0, actualPercent: 5.2, isNonModel: true },
      ],
    },
    isLoading: false,
  }),
}))

describe('RebalancingProgressWidget', () => {
  it('renders target vs actual entries', async () => {
    const RebalancingProgressWidget = (await import('./RebalancingProgressWidget')).default
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <RebalancingProgressWidget connectionId={1} />
      </QueryClientProvider>
    )
    expect(screen.getByText('SOXL')).toBeInTheDocument()
    expect(screen.getByText('TECL')).toBeInTheDocument()
  })

  it('renders non-model entries with SELL badge', async () => {
    const RebalancingProgressWidget = (await import('./RebalancingProgressWidget')).default
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <RebalancingProgressWidget connectionId={1} />
      </QueryClientProvider>
    )
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('SELL')).toBeInTheDocument()
  })
})
