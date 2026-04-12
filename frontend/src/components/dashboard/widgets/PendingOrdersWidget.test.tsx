import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'

vi.mock('@/hooks/useModelPortfolios', () => ({
  usePendingOrders: () => ({
    data: {
      connectionId: 1,
      orders: [
        { action: 'BUY', symbol: 'SOXL', securityName: 'Semiconductor 3X', units: 134, price: 48.89, amount: 6551.26, currency: 'CAD', accountName: 'TFSA', targetPercent: 10, targetValue: 6551.26, cashInsufficient: false },
      ],
      totalAmount: 6551.26,
      cashRemaining: 3448.74,
      cashWarning: null,
      totalSellAmount: 0,
      totalBuyAmount: 6551.26,
    },
    isLoading: false,
  }),
}))

describe('PendingOrdersWidget', () => {
  it('renders BUY orders', async () => {
    const PendingOrdersWidget = (await import('./PendingOrdersWidget')).default
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <PendingOrdersWidget connectionId={1} />
      </QueryClientProvider>
    )
    expect(screen.getByText('BUY')).toBeInTheDocument()
    expect(screen.getByText('SOXL')).toBeInTheDocument()
  })
})
