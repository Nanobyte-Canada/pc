import { Outlet } from 'react-router-dom'
import { IconRail } from './IconRail'
import { BottomTabBar } from './BottomTabBar'
import { ToastContainer } from '@/components/ui/toast'
import './AppLayout.css'

export function AppLayout() {
  return (
    <div className="app-layout">
      <aside className="sidebar-container">
        <IconRail />
      </aside>

      <div className="main-wrapper">
        <main className="main-content">
          <Outlet />
        </main>
        <BottomTabBar />
      </div>
      <ToastContainer />
    </div>
  )
}
