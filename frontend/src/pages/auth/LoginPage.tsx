import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { initiateGoogleLogin, login } from '../../services/authService';
import { useIsAuthenticated } from '../../stores/authStore';
import './AuthPages.css';

const ERROR_MESSAGES: Record<string, string> = {
  auth_failed: 'Sign in was cancelled or failed. Please try again.',
  provider_unavailable: 'Google sign-in is temporarily unavailable. Please try again later.',
};

const AUTH_METHOD = import.meta.env.VITE_AUTH_METHOD || 'google';

export function LoginPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    const errorCode = searchParams.get('error');
    if (errorCode) {
      setError(ERROR_MESSAGES[errorCode] || 'An error occurred. Please try again.');
    }
  }, [searchParams]);

  const handleGoogleLogin = () => {
    initiateGoogleLogin();
  };

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError(null);
    setIsLoggingIn(true);

    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Login failed. Please try again.';
      setLoginError(message);
    } finally {
      setIsLoggingIn(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-glow login-glow--top" />
      <div className="login-glow login-glow--bottom" />

      <div className="login-content">
        <div className="login-hero">
          <div className="login-logo-mark">P</div>
          <h1 className="login-heading">
            Your portfolio,<br />one dashboard.
          </h1>
          <p className="login-subtext">
            Track positions, analyze drift, and monitor performance across all
            your brokerage accounts.
          </p>
          <ul className="login-features">
            <li>
              <span className="login-check">&#10003;</span>
              Multi-broker portfolio tracking
            </li>
            <li>
              <span className="login-check">&#10003;</span>
              Look-through ETF decomposition
            </li>
            <li>
              <span className="login-check">&#10003;</span>
              Drift &amp; rebalancing analysis
            </li>
            <li>
              <span className="login-check">&#10003;</span>
              Options strategy builder
            </li>
          </ul>
        </div>

        <div className="login-card-wrapper">
          <div className="login-card">
            <div className="login-card-logo">P</div>
            <h2 className="login-card-title">Get started</h2>
            <p className="login-card-subtitle">Sign in or create an account</p>

            {error && <div className="login-error">{error}</div>}

            <button className="login-google-btn" onClick={handleGoogleLogin}>
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" />
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
              </svg>
              Continue with Google
            </button>

            {AUTH_METHOD !== 'google' && (
              <>
                <div className="login-divider">
                  <span>or</span>
                </div>
                <form onSubmit={handleEmailLogin} className="login-form">
                  <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    className="login-input"
                  />
                  <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    className="login-input"
                  />
                  {loginError && <div className="login-error">{loginError}</div>}
                  <button type="submit" disabled={isLoggingIn} className="login-email-btn">
                    {isLoggingIn ? 'Signing in...' : 'Sign in with Email'}
                  </button>
                </form>
              </>
            )}

            <div className="login-footer">
              By continuing, you agree to our{' '}
              <a href="/terms">Terms</a> and <a href="/privacy">Privacy Policy</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
