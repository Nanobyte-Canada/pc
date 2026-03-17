import { Outlet } from 'react-router-dom'
import { AppSidebar } from './AppSidebar'
import { MobileHeader } from './MobileHeader'
import { useSidebarStore } from '@/stores/sidebarStore'
import './AppLayout.css'

export function AppLayout() {
  const collapsed = useSidebarStore((s) => s.collapsed)

  return (
    <div className={`app-layout${collapsed ? ' sidebar-is-collapsed' : ''}`}>
      {/* Desktop sidebar */}
      <aside className="sidebar-container">
        <AppSidebar />
      </aside>

      <div className="main-wrapper">
        {/* Mobile-only header */}
        <MobileHeader />

        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
