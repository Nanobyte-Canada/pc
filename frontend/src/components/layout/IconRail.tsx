import { useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutGrid, Layers, ClipboardList, Clock,
  Sun, Moon, Settings
} from 'lucide-react'
import { useThemeStore } from '@/stores/themeStore'
import { useAuthStore } from '@/stores/authStore'
import './IconRail.css'

interface NavItem {
  icon: React.ElementType
  path: string
  label: string
}

const navItems: NavItem[] = [
  { icon: LayoutGrid, path: '/', label: 'Dashboard' },
  { icon: Layers, path: '/brokers', label: 'Accounts' },
  { icon: ClipboardList, path: '/screener', label: 'Screener' },
  { icon: Clock, path: '/options', label: 'Options' },
]

function getInitials(name: string | null | undefined): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
  }
  return parts[0][0]?.toUpperCase() ?? '?'
}

export function IconRail() {
  const location = useLocation()
  const navigate = useNavigate()
  const { theme, toggleTheme } = useThemeStore()
  const user = useAuthStore((s) => s.user)

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname.startsWith(path)
  }

  const initials = getInitials(user?.name)

  return (
    <nav className="icon-rail" aria-label="Main navigation">
      <div className="icon-rail__logo">P</div>

      {navItems.map((item) => (
        <button
          key={item.path}
          className={`icon-rail__item${isActive(item.path) ? ' icon-rail__item--active' : ''}`}
          onClick={() => navigate(item.path)}
          title={item.label}
          aria-label={item.label}
          aria-current={isActive(item.path) ? 'page' : undefined}
        >
          <item.icon size={18} />
        </button>
      ))}

      <div className="icon-rail__spacer" />

      <button
        className="icon-rail__item"
        onClick={toggleTheme}
        title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        aria-label="Toggle theme"
      >
        {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
      </button>

      <button
        className="icon-rail__item"
        onClick={() => navigate('/admin')}
        title="Admin"
        aria-label="Admin settings"
      >
        <Settings size={18} />
      </button>

      <button
        className="icon-rail__avatar"
        onClick={() => navigate('/profile')}
        title="Profile"
        aria-label="User profile"
      >
        {initials}
      </button>
    </nav>
  )
}
