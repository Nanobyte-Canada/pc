import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
})

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {ui}
      </BrowserRouter>
    </QueryClientProvider>
  )
}

describe('App', () => {
  it('renders the navigation', () => {
    renderWithProviders(<App />)
    expect(screen.getAllByText('Portfolio Builder').length).toBeGreaterThan(0)
  })

  it('renders the portfolio builder page by default', () => {
    renderWithProviders(<App />)
    expect(screen.getByText('Build and analyze your portfolio with stocks, ETFs, and mutual funds')).toBeInTheDocument()
  })

  it('renders the screeners dropdown', () => {
    renderWithProviders(<App />)
    expect(screen.getByText('Screeners')).toBeInTheDocument()
  })

  it('renders the analytics link', () => {
    renderWithProviders(<App />)
    expect(screen.getByText('Analytics')).toBeInTheDocument()
  })
})
