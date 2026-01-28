import { Link } from 'react-router-dom';
import './UnauthorizedPage.css';

export function UnauthorizedPage() {
  return (
    <div className="unauthorized-container">
      <div className="unauthorized-card">
        <div className="unauthorized-icon">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="64"
            height="64"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
          </svg>
        </div>
        <h1 className="unauthorized-title">Access Denied</h1>
        <p className="unauthorized-message">
          You don't have permission to access this page.
        </p>
        <Link to="/" className="unauthorized-link">
          Go to Home
        </Link>
      </div>
    </div>
  );
}
