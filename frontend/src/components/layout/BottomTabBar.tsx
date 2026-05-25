import { useLocation, useNavigate } from 'react-router-dom'
import { LayoutGrid, Layers, Search, Clock, Menu } from 'lucide-react'
import './BottomTabBar.css'

interface TabItem {
  icon: React.ElementType
  path: string
  label: string
}

const tabs: TabItem[] = [
  { icon: LayoutGrid, path: '/', label: 'Home' },
  { icon: Layers, path: '/brokers/connections', label: 'Accounts' },
  { icon: Search, path: '/screener/stocks', label: 'Screener' },
  { icon: Clock, path: '/options', label: 'Options' },
  { icon: Menu, path: '/more', label: 'More' },
]

export function BottomTabBar() {
  const location = useLocation()
  const navigate = useNavigate()

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    if (path === '/more') {
      return ['/wheel', '/brokers/reporting', '/admin', '/profile'].some(
        (p) => location.pathname.startsWith(p)
      )
    }
    return location.pathname.startsWith(path)
  }

  return (
    <nav className="bottom-tab-bar" aria-label="Tab navigation">
      {tabs.map((tab) => (
        <button
          key={tab.path}
          className={`bottom-tab-bar__item${isActive(tab.path) ? ' bottom-tab-bar__item--active' : ''}`}
          onClick={() => navigate(tab.path)}
          aria-label={tab.label}
          aria-current={isActive(tab.path) ? 'page' : undefined}
        >
          <tab.icon size={20} />
          <span className="bottom-tab-bar__label">{tab.label}</span>
        </button>
      ))}
    </nav>
  )
}
