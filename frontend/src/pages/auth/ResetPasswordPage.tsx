import { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { resetPassword, AuthError } from '../../services/authService';
import './AuthPages.css';

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const validatePassword = (pwd: string): string[] => {
    const errors: string[] = [];
    if (pwd.length < 12) errors.push('At least 12 characters');
    if (!/[A-Z]/.test(pwd)) errors.push('One uppercase letter');
    if (!/[a-z]/.test(pwd)) errors.push('One lowercase letter');
    if (!/[0-9]/.test(pwd)) errors.push('One number');
    if (!/[^A-Za-z0-9]/.test(pwd)) errors.push('One special character');
    return errors;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!token) {
      setError('Invalid reset link. Please request a new one.');
      return;
    }

    const passwordErrors = validatePassword(password);
    if (passwordErrors.length > 0) {
      setError(`Password requires: ${passwordErrors.join(', ')}`);
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setIsLoading(true);

    try {
      await resetPassword({ token, newPassword: password });
      setSuccess(true);
    } catch (err) {
      if (err instanceof AuthError) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred. Please try again.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h1 className="auth-title">Invalid Link</h1>
          <p className="auth-subtitle">
            This password reset link is invalid or has expired.
          </p>
          <div className="auth-footer">
            <p>
              <Link to="/forgot-password" className="auth-link">
                Request a new reset link
              </Link>
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h1 className="auth-title">Password Reset</h1>
          <p className="auth-subtitle">
            Your password has been reset successfully. You can now sign in with your new password.
          </p>
          <button
            className="auth-button"
            onClick={() => navigate('/login')}
          >
            Sign In
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1 className="auth-title">Reset Password</h1>
        <p className="auth-subtitle">Enter your new password below.</p>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label htmlFor="password">New Password</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Min 12 characters"
              required
              autoComplete="new-password"
            />
            <small className="form-hint">
              Must include uppercase, lowercase, number, and special character
            </small>
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword">Confirm Password</label>
            <input
              type="password"
              id="confirmPassword"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Confirm your password"
              required
              autoComplete="new-password"
            />
          </div>

          <button type="submit" className="auth-button" disabled={isLoading}>
            {isLoading ? 'Resetting...' : 'Reset Password'}
          </button>
        </form>
      </div>
    </div>
  );
}
