import { useState, useEffect, useCallback } from 'react';

interface SessionTimeoutWarningProps {
  onExtendSession: () => void;
  onLogout: () => void;
}

export function SessionTimeoutWarning({ onExtendSession, onLogout }: SessionTimeoutWarningProps) {
  const [visible, setVisible] = useState(false);

  const handleSessionExpiring = useCallback(() => {
    setVisible(true);
  }, []);

  useEffect(() => {
    window.addEventListener('session-expiring', handleSessionExpiring);
    return () => {
      window.removeEventListener('session-expiring', handleSessionExpiring);
    };
  }, [handleSessionExpiring]);

  if (!visible) return null;

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <h3 style={{ margin: '0 0 8px 0' }}>Session Expiring</h3>
        <p style={{ margin: '0 0 20px 0', color: 'var(--text-muted)' }}>
          Your session is about to expire due to inactivity. Would you like to stay logged in?
        </p>
        <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
          <button
            onClick={() => {
              setVisible(false);
              onLogout();
            }}
            style={secondaryButtonStyle}
          >
            Log out
          </button>
          <button
            onClick={() => {
              setVisible(false);
              onExtendSession();
            }}
            style={primaryButtonStyle}
          >
            Stay logged in
          </button>
        </div>
      </div>
    </div>
  );
}

const overlayStyle: React.CSSProperties = {
  position: 'fixed',
  inset: 0,
  backgroundColor: 'rgba(0, 0, 0, 0.5)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 10000,
};

const modalStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)',
  borderRadius: '8px',
  padding: '24px',
  maxWidth: '420px',
  width: '90%',
  boxShadow: '0 4px 24px rgba(0, 0, 0, 0.2)',
  color: 'var(--text-primary)',
};

const primaryButtonStyle: React.CSSProperties = {
  padding: '8px 20px',
  backgroundColor: 'var(--accent)',
  color: 'var(--nav-text)',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '14px',
};

const secondaryButtonStyle: React.CSSProperties = {
  padding: '8px 20px',
  backgroundColor: 'transparent',
  color: 'var(--text-muted)',
  border: '1px solid var(--border)',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '14px',
};
