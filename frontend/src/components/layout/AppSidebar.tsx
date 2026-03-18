import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  BarChart3,
  Briefcase,
  Hammer,
  TrendingUp,
  PieChart,
  Link2,
  Wallet,
  FileText,
  Shield,
  LogOut,
  User,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react'
import { useUser } from '@/stores/authStore'
import { logout } from '@/services/authService'
import { useSidebarStore } from '@/stores/sidebarStore'
import { ThemeToggle } from './ThemeToggle'
import { NotificationBell } from './NotificationBell'
import { Tooltip } from '@/components/ui/tooltip'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'
import './AppSidebar.css'

interface NavItem {
  to: string
  icon: React.ElementType
  label: string
  end?: boolean
}

interface NavSection {
  title: string
  items: NavItem[]
}

const navSections: NavSection[] = [
  {
    title: 'Main',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Dashboard', end: true },
      { to: '/analytics', icon: BarChart3, label: 'Analytics' },
    ],
  },
  {
    title: 'Portfolios',
    items: [
      { to: '/portfolios', icon: Briefcase, label: 'Model Portfolios' },
      { to: '/builder', icon: Hammer, label: 'Portfolio Builder' },
    ],
  },
  {
    title: 'Screeners',
    items: [
      { to: '/screener/stocks', icon: TrendingUp, label: 'Stocks' },
      { to: '/screener/etfs', icon: PieChart, label: 'ETFs' },
    ],
  },
  {
    title: 'Brokers',
    items: [
      { to: '/brokers/connections', icon: Link2, label: 'Connections' },
      { to: '/brokers/positions', icon: Wallet, label: 'Positions' },
      { to: '/brokers/reporting', icon: FileText, label: 'Reporting' },
    ],
  },
]

interface AppSidebarProps {
  onNavigate?: () => void
}

export function AppSidebar({ onNavigate }: AppSidebarProps) {
  const user = useUser()
  const navigate = useNavigate()
  const displayName = user?.name || user?.email?.split('@')[0] || 'User'
  const { collapsed, toggleSidebar } = useSidebarStore()
  // When onNavigate is set (mobile sheet), always show expanded
  const isCollapsed = onNavigate ? false : collapsed

  const handleLogout = async () => {
    try {
      await logout()
      navigate('/login')
    } catch {
      navigate('/login')
    }
  }

  const renderNavLink = (item: NavItem) => {
    const link = (
      <NavLink
        key={item.to}
        to={item.to}
        end={item.end}
        onClick={onNavigate}
        className={({ isActive }) =>
          cn('sidebar-nav-link', isActive && 'sidebar-nav-link-active')
        }
      >
        <item.icon className="sidebar-nav-icon" />
        {!isCollapsed && <span>{item.label}</span>}
      </NavLink>
    )

    if (isCollapsed) {
      return (
        <Tooltip key={item.to} content={item.label} side="right">
          {link}
        </Tooltip>
      )
    }

    return link
  }

  return (
    <div className={cn('sidebar', isCollapsed && 'sidebar-collapsed')}>
      {/* Brand */}
      <div className="sidebar-brand">
        <div className="sidebar-brand-icon">P</div>
        {!isCollapsed && <span className="sidebar-brand-text">Portfolio Builder</span>}
        {!onNavigate && (
          <button
            className="sidebar-collapse-btn"
            onClick={toggleSidebar}
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {isCollapsed ? (
              <ChevronsRight style={{ height: '1rem', width: '1rem' }} />
            ) : (
              <ChevronsLeft style={{ height: '1rem', width: '1rem' }} />
            )}
          </button>
        )}
      </div>

      <Separator />

      {/* Nav sections */}
      <nav className="sidebar-nav">
        {navSections.map((section) => (
          <div key={section.title}>
            {!isCollapsed && <p className="sidebar-section-title">{section.title}</p>}
            <div className="sidebar-section-items">
              {section.items.map(renderNavLink)}
            </div>
          </div>
        ))}

        {/* Admin section - conditional */}
        {user?.roles.includes('ADMIN') && (
          <div>
            {!isCollapsed && <p className="sidebar-section-title">Admin</p>}
            {isCollapsed ? (
              <Tooltip content="Admin Panel" side="right">
                <NavLink
                  to="/admin"
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn('sidebar-nav-link', isActive && 'sidebar-nav-link-active')
                  }
                >
                  <Shield className="sidebar-nav-icon" />
                </NavLink>
              </Tooltip>
            ) : (
              <NavLink
                to="/admin"
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn('sidebar-nav-link', isActive && 'sidebar-nav-link-active')
                }
              >
                <Shield className="sidebar-nav-icon" />
                <span>Admin Panel</span>
              </NavLink>
            )}
          </div>
        )}
      </nav>

      {/* Footer */}
      <div className="sidebar-footer">
        {/* Theme toggle + Notification */}
        <div className={cn('sidebar-footer-actions', isCollapsed && 'sidebar-footer-actions-collapsed')}>
          <ThemeToggle />
          <NotificationBell />
        </div>

        <Separator />

        {/* User info */}
        <div className={cn('sidebar-user', isCollapsed && 'sidebar-user-collapsed')}>
          <div className="sidebar-user-avatar">
            {displayName.charAt(0).toUpperCase()}
          </div>
          {!isCollapsed && (
            <>
              <div className="sidebar-user-info">
                <p className="sidebar-user-name">{displayName}</p>
                {user?.email && (
                  <p className="sidebar-user-email">{user.email}</p>
                )}
              </div>
              <div className="sidebar-user-actions">
                <NavLink
                  to="/profile"
                  onClick={onNavigate}
                  className="sidebar-icon-btn"
                  title="Profile"
                >
                  <User style={{ height: '1rem', width: '1rem' }} />
                </NavLink>
                <button
                  onClick={handleLogout}
                  className="sidebar-icon-btn sidebar-icon-btn-danger"
                  title="Logout"
                >
                  <LogOut style={{ height: '1rem', width: '1rem' }} />
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
