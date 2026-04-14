import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  BarChart3,
  Briefcase,
  Link2,
  Wallet,
  FileText,
  Shield,
  LogOut,
  User,
  ChevronsLeft,
  ChevronsRight,
  Landmark,
  RefreshCw,
  TrendingUp,
  PieChart,
  Building2,
  Star,
  Activity,
  Banknote,
} from 'lucide-react'
import { useDashboardAccounts, useRefreshAll } from '@/hooks/useDashboardWidgets'
import { useTypeCounts } from '@/hooks/useNewScreener'
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
  countKey?: string
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
      { to: '/portfolios', icon: Briefcase, label: 'Portfolio' },
    ],
  },
  {
    title: 'Screeners',
    items: [
      { to: '/screener/stocks', icon: TrendingUp, label: 'Stocks', countKey: 'STOCK' },
      { to: '/screener/etfs', icon: PieChart, label: 'ETFs', countKey: 'ETF' },
      { to: '/screener/mutual-funds', icon: Building2, label: 'Mutual Funds', countKey: 'MUTUAL_FUND' },
      { to: '/screener/preferred-stocks', icon: Star, label: 'Preferred Stocks', countKey: 'PREFERRED_STOCK' },
      { to: '/screener/indices', icon: Activity, label: 'Indices', countKey: 'INDEX' },
      { to: '/screener/bonds', icon: Banknote, label: 'Bonds', countKey: 'BOND' },
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
  const { data: accountsData } = useDashboardAccounts()
  const { data: typeCounts } = useTypeCounts()
  const { mutate: refresh, isPending: isRefreshing } = useRefreshAll()
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
        {!isCollapsed && item.countKey && typeCounts?.[item.countKey] != null && (
          <span className="sidebar-link-count">
            {typeCounts[item.countKey].toLocaleString()}
          </span>
        )}
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
        {navSections.map((section) => {
          // Inject individual account links into Main section after Dashboard
          let items = section.items
          if (section.title === 'Main' && accountsData && accountsData.accounts.length > 0) {
            const accountLinks = accountsData.accounts.map((acc) => ({
              to: `/brokers/accounts/${acc.connectionId}`,
              icon: Landmark,
              label: acc.accountName || acc.brokerName || 'Account',
            }))
            items = [
              section.items[0], // Dashboard
              ...accountLinks,
              ...section.items.slice(1), // Analytics
            ]
          }

          return (
            <div key={section.title}>
              {!isCollapsed && <p className="sidebar-section-title">{section.title}</p>}
              <div className="sidebar-section-items">
                {items.map(renderNavLink)}
              </div>
            </div>
          )
        })}

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
        {/* Refresh + Theme + Notification */}
        <div className={cn('sidebar-footer-actions', isCollapsed && 'sidebar-footer-actions-collapsed')}>
          <Tooltip content={isRefreshing ? 'Refreshing...' : 'Refresh Data'} side="right">
            <button
              className={cn('sidebar-icon-btn', isRefreshing && 'sidebar-icon-btn-spin')}
              onClick={() => refresh()}
              disabled={isRefreshing}
              title="Refresh Data"
            >
              <RefreshCw style={{ height: '1rem', width: '1rem' }} />
            </button>
          </Tooltip>
          <ThemeToggle />
          <NotificationBell />
        </div>
        {!isCollapsed && accountsData?.accounts?.[0]?.lastFetchedAt && (
          <p className="sidebar-refresh-time">
            Last refreshed: {new Date(accountsData.accounts[0].lastFetchedAt).toLocaleTimeString('en-CA', { hour: '2-digit', minute: '2-digit' })}
          </p>
        )}

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
