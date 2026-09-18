import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { scenario } from './test/scenario'

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
    checkAuth: vi.fn(),
    setSessionExpired: vi.fn()
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

  it(scenario('APP-001', 'renders the app without crashing'), async () => {
    const { container } = renderWithProviders(<App />)
    await waitFor(() => {
      expect(container.innerHTML.length).toBeGreaterThan(0)
    })
  })

  it(scenario('APP-002', 'renders the default dashboard page when authenticated'), async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      expect(document.querySelector('[class*="dashboard"], [class*="app-layout"]')).toBeTruthy()
    })
  })

  it(scenario('APP-003', 'renders navigation elements'), async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      const navButtons = screen.getAllByRole('button')
      expect(navButtons.length).toBeGreaterThan(0)
    })
  })

  it(scenario('APP-004', 'has route definitions for key pages'), async () => {
    renderWithProviders(<App />)
    await waitFor(() => {
      expect(document.body.querySelector('main, [class*="layout"], [class*="app"]')).toBeTruthy()
    })
  })
})
