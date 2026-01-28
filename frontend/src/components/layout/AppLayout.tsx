import { Outlet } from 'react-router-dom';
import { Navigation } from './Navigation';
import './AppLayout.css';

export function AppLayout() {
  return (
    <div className="app-layout">
      <Navigation />
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
