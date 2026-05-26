import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { LayoutGrid, Layers, Search, Clock, Menu, X, Target, FileText, Shield, User } from 'lucide-react'
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
]

const moreItems: TabItem[] = [
  { icon: Target, path: '/wheel', label: 'Wheel Strategy' },
  { icon: FileText, path: '/brokers/reporting', label: 'Reporting' },
  { icon: Shield, path: '/admin', label: 'Admin' },
  { icon: User, path: '/profile', label: 'Profile' },
]

export function BottomTabBar() {
  const location = useLocation()
  const navigate = useNavigate()
  const [moreOpen, setMoreOpen] = useState(false)

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    if (path === '/brokers/connections') return location.pathname.startsWith('/brokers')
    if (path === '/screener/stocks') return location.pathname.startsWith('/screener')
    return location.pathname.startsWith(path)
  }

  const isMoreActive = moreItems.some((item) => location.pathname.startsWith(item.path))

  return (
    <>
      {moreOpen && (
        <div className="more-sheet-overlay" onClick={() => setMoreOpen(false)}>
          <div className="more-sheet" onClick={(e) => e.stopPropagation()}>
            <div className="more-sheet__handle" />
            <div className="more-sheet__header">
              <span className="more-sheet__title">More</span>
              <button className="more-sheet__close" onClick={() => setMoreOpen(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <div className="more-sheet__items">
              {moreItems.map((item) => (
                <button
                  key={item.path}
                  className={`more-sheet__item${location.pathname.startsWith(item.path) ? ' more-sheet__item--active' : ''}`}
                  onClick={() => { navigate(item.path); setMoreOpen(false) }}
                >
                  <item.icon size={20} />
                  <span>{item.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      <nav className="bottom-tab-bar" aria-label="Tab navigation">
        {tabs.map((tab) => (
          <button
            key={tab.path}
            className={`bottom-tab-bar__item${isActive(tab.path) ? ' bottom-tab-bar__item--active' : ''}`}
            onClick={() => { navigate(tab.path); setMoreOpen(false) }}
            aria-label={tab.label}
            aria-current={isActive(tab.path) ? 'page' : undefined}
          >
            <tab.icon size={20} />
            <span className="bottom-tab-bar__label">{tab.label}</span>
          </button>
        ))}
        <button
          className={`bottom-tab-bar__item${isMoreActive ? ' bottom-tab-bar__item--active' : ''}`}
          onClick={() => setMoreOpen(!moreOpen)}
          aria-label="More"
        >
          <Menu size={20} />
          <span className="bottom-tab-bar__label">More</span>
        </button>
      </nav>
    </>
  )
}
