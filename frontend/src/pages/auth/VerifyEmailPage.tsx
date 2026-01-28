import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { apiFetch } from '../../services/api';
import './AuthPages.css';

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('Invalid verification link.');
      return;
    }

    const verifyEmail = async () => {
      try {
        const response = await apiFetch(`/auth/verify-email?token=${token}`);

        if (response.ok) {
          const data = await response.json();
          setStatus('success');
          setMessage(data.message || 'Email verified successfully!');
        } else {
          const error = await response.json();
          setStatus('error');
          setMessage(error.message || 'Verification failed. The link may have expired.');
        }
      } catch {
        setStatus('error');
        setMessage('An error occurred. Please try again.');
      }
    };

    verifyEmail();
  }, [token]);

  return (
    <div className="auth-container">
      <div className="auth-card">
        {status === 'loading' && (
          <>
            <h1 className="auth-title">Verifying Email</h1>
            <p className="auth-subtitle">Please wait...</p>
            <div className="loading-spinner" />
          </>
        )}

        {status === 'success' && (
          <>
            <h1 className="auth-title">Email Verified</h1>
            <p className="auth-subtitle">{message}</p>
            <Link to="/login" className="auth-button" style={{ textAlign: 'center', display: 'block' }}>
              Sign In
            </Link>
          </>
        )}

        {status === 'error' && (
          <>
            <h1 className="auth-title">Verification Failed</h1>
            <p className="auth-subtitle auth-error">{message}</p>
            <div className="auth-footer">
              <p>
                <Link to="/login" className="auth-link">
                  Back to sign in
                </Link>
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
