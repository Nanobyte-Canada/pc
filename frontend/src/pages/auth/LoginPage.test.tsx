import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from './LoginPage';

vi.mock('../../services/authService', () => ({
  initiateGoogleLogin: vi.fn(),
}));

vi.mock('../../stores/authStore', () => ({
  useIsAuthenticated: vi.fn(() => false),
  useAuthStore: vi.fn(() => ({
    sessionExpired: false,
    setSessionExpired: vi.fn(),
  })),
}));

function renderLoginPage(route = '/login') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LoginPage />
    </MemoryRouter>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the hero section with heading and features', () => {
    renderLoginPage();

    expect(screen.getByText(/your portfolio/i)).toBeInTheDocument();
    expect(screen.getByText(/one dashboard/i)).toBeInTheDocument();
    expect(screen.getByText(/multi-broker portfolio tracking/i)).toBeInTheDocument();
    expect(screen.getByText(/look-through etf decomposition/i)).toBeInTheDocument();
  });

  it('renders the sign-in card with Google button', () => {
    renderLoginPage();

    expect(screen.getByText('Get started')).toBeInTheDocument();
    expect(screen.getByText('Continue with Google')).toBeInTheDocument();
  });

  it('calls initiateGoogleLogin when Google button is clicked', async () => {
    const { initiateGoogleLogin } = await import('../../services/authService');
    renderLoginPage();

    const button = screen.getByText('Continue with Google');
    fireEvent.click(button);

    expect(initiateGoogleLogin).toHaveBeenCalledOnce();
  });

  it('displays error message from query params', () => {
    renderLoginPage('/login?error=auth_failed');

    expect(screen.getByText(/sign in was cancelled or failed/i)).toBeInTheDocument();
  });

  it('displays generic error for unknown error codes', () => {
    renderLoginPage('/login?error=unknown_code');

    expect(screen.getByText(/an error occurred/i)).toBeInTheDocument();
  });
});
