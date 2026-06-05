import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

const mockUser = { id: '1', email: 'test@example.com', name: 'Test User', roles: ['USER'] }

// Mock the auth store with all exports
vi.mock('./stores/authStore', () => ({
  useAuthStore: vi.fn(() => ({
    user: mockUser,
    isAuthenticated: true,
    isLoading: false,
    csrfToken: null,
    setUser: vi.fn(),
    setCsrfToken: vi.fn(),
    logout: vi.fn(),
    setLoading: vi.fn(),
    checkAuth: vi.fn()
  })),
  useUser: vi.fn(() => mockUser),
  useIsAuthenticated: vi.fn(() => true),
  useIsLoading: vi.fn(() => false)
}))

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {ui}
      </BrowserRouter>
    </QueryClientProvider>
  )
}

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the navigation', async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      // Navigation now uses icon-only rail with aria-labels
      expect(screen.getByLabelText('Portfolio')).toBeInTheDocument()
    })
  })

  it('renders the portfolio builder page by default', async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      expect(screen.getByText('Build and analyze your portfolio with stocks and ETFs')).toBeInTheDocument()
    })
  })

  it('renders the screeners dropdown', async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      expect(screen.getByText('Screeners')).toBeInTheDocument()
    })
  })

  it('renders the analytics link', async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      expect(screen.getByText('Analytics')).toBeInTheDocument()
    })
  })
})
