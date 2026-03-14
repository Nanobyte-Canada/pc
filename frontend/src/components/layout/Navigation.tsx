import { useState, useEffect, useRef } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useUser } from '../../stores/authStore';
import { logout } from '../../services/authService';
import { NotificationBell } from './NotificationBell';
import './Navigation.css';

export function Navigation() {
  const user = useUser();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const navRef = useRef<HTMLElement>(null);

  const handleLogout = async () => {
    try {
      await logout();
      navigate('/login');
    } catch (error) {
      console.error('Logout failed:', error);
      navigate('/login');
    }
  };

  // Close menu on route change
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  // Close menu on click outside
  useEffect(() => {
    if (!menuOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (navRef.current && !navRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  const displayName = user?.name || user?.email?.split('@')[0] || 'User';

  return (
    <nav className="navigation" ref={navRef}>
      <div className="nav-brand">
        <span className="brand-text">Portfolio Builder</span>
      </div>

      <button
        className="hamburger-btn"
        onClick={() => setMenuOpen(prev => !prev)}
        aria-label="Toggle navigation menu"
        aria-expanded={menuOpen}
      >
        <span className={`hamburger-icon${menuOpen ? ' open' : ''}`}>
          <span></span>
          <span></span>
          <span></span>
        </span>
      </button>

      <div className={`nav-links${menuOpen ? ' open' : ''}`}>
        <NavLink
          to="/"
          end
          className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
        >
          Dashboard
        </NavLink>
        <div className="nav-dropdown">
          <span className="nav-link dropdown-trigger">Portfolios</span>
          <div className="dropdown-menu">
            <NavLink to="/portfolios" className="dropdown-item">Model Portfolios</NavLink>
            <NavLink to="/builder" className="dropdown-item">Portfolio Builder</NavLink>
          </div>
        </div>
        <div className="nav-dropdown">
          <span className="nav-link dropdown-trigger">Screeners</span>
          <div className="dropdown-menu">
            <NavLink to="/screener/stocks" className="dropdown-item">Stocks</NavLink>
            <NavLink to="/screener/etfs" className="dropdown-item">ETFs</NavLink>
            <NavLink to="/screener/mutual-funds" className="dropdown-item">Mutual Funds</NavLink>
          </div>
        </div>
        <NavLink
          to="/analytics"
          className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
        >
          Analytics
        </NavLink>
        <div className="nav-dropdown">
          <span className="nav-link dropdown-trigger">Brokers</span>
          <div className="dropdown-menu">
            <NavLink to="/brokers/connections" className="dropdown-item">Connections</NavLink>
            <NavLink to="/brokers/positions" className="dropdown-item">Positions</NavLink>
            <NavLink to="/brokers/reporting" className="dropdown-item">Reporting</NavLink>
          </div>
        </div>
        {user?.roles.includes('ADMIN') && (
          <NavLink
            to="/admin"
            className={({ isActive }) => isActive ? 'nav-link admin-link active' : 'nav-link admin-link'}
          >
            Admin
          </NavLink>
        )}
      </div>
      <div className="nav-user">
        <NotificationBell />
        <div className="nav-dropdown user-dropdown">
          <span className="nav-link dropdown-trigger user-trigger">
            <span className="user-avatar">
              {displayName.charAt(0).toUpperCase()}
            </span>
            <span className="user-name">{displayName}</span>
          </span>
          <div className="dropdown-menu user-menu">
            <NavLink to="/profile" className="dropdown-item">Profile</NavLink>
            <div className="dropdown-divider"></div>
            <button onClick={handleLogout} className="dropdown-item logout-item">
              Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
