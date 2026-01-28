import { NavLink, useNavigate } from 'react-router-dom';
import { useUser } from '../../stores/authStore';
import { logout } from '../../services/authService';
import './Navigation.css';

export function Navigation() {
  const user = useUser();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logout();
      navigate('/login');
    } catch (error) {
      console.error('Logout failed:', error);
      // Still navigate to login even if logout fails
      navigate('/login');
    }
  };

  const displayName = user?.name || user?.email?.split('@')[0] || 'User';

  return (
    <nav className="navigation">
      <div className="nav-brand">
        <span className="brand-text">Portfolio Builder</span>
      </div>
      <div className="nav-links">
        <NavLink
          to="/"
          className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
        >
          Portfolio Builder
        </NavLink>
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
