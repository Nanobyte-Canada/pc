import { useEffect, useCallback, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { SessionTimeoutWarning } from './components/auth/SessionTimeoutWarning'
import { useAuthStore } from './stores/authStore'
import { useSessionManager } from './hooks/useSessionManager'

// Auth pages (lazy loaded)
const LoginPage = lazy(() => import('./pages/auth/LoginPage').then(m => ({ default: m.LoginPage })))
const SignupPage = lazy(() => import('./pages/auth/SignupPage').then(m => ({ default: m.SignupPage })))
const ForgotPasswordPage = lazy(() => import('./pages/auth/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/auth/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const VerifyEmailPage = lazy(() => import('./pages/auth/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))

// Protected pages (lazy loaded)
const ScreenerPage = lazy(() => import('./pages/ScreenerPage').then(m => ({ default: m.ScreenerPage })))
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage').then(m => ({ default: m.AnalyticsPage })))
const ProfilePage = lazy(() => import('./pages/ProfilePage').then(m => ({ default: m.ProfilePage })))
const AdminPage = lazy(() => import('./pages/admin/AdminPage').then(m => ({ default: m.AdminPage })))
const InstrumentDetailPage = lazy(() => import('./pages/InstrumentDetailPage').then(m => ({ default: m.InstrumentDetailPage })))
const UnauthorizedPage = lazy(() => import('./pages/UnauthorizedPage').then(m => ({ default: m.UnauthorizedPage })))

// Broker pages (lazy loaded)
const BrokerConnectionsPage = lazy(() => import('./pages/BrokerConnectionsPage').then(m => ({ default: m.BrokerConnectionsPage })))
const BrokerPositionsPage = lazy(() => import('./pages/BrokerPositionsPage').then(m => ({ default: m.BrokerPositionsPage })))
const PositionDetailsPage = lazy(() => import('./pages/PositionDetailsPage').then(m => ({ default: m.PositionDetailsPage })))
const ReportingPage = lazy(() => import('./pages/ReportingPage').then(m => ({ default: m.ReportingPage })))

// Portfolio pages (lazy loaded)
const PortfolioPage = lazy(() => import('./pages/PortfolioPage'))

// Dashboard (lazy loaded)
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })))
const AccountDetailPage = lazy(() => import('./pages/AccountDetailPage').then(m => ({ default: m.AccountDetailPage })))

// Options Trading (lazy loaded)
const OptionsPage = lazy(() => import('./pages/OptionsPage').then(m => ({ default: m.OptionsPage })))

import './App.css'

function App() {
  const { checkAuth, isLoading, isAuthenticated, logout, setSessionExpired } = useAuthStore()
  const { extendSession } = useSessionManager()

  useEffect(() => {
    checkAuth()
  }, [checkAuth])

  const handleSessionLogout = useCallback(() => {
    logout()
    setSessionExpired(true)
    window.location.href = '/login'
  }, [logout, setSessionExpired])

  if (isLoading) {
    return (
      <div className="app-loading">
        <div className="loading-spinner" />
        <p>Loading...</p>
      </div>
    )
  }

  return (
    <>
    {isAuthenticated && (
      <SessionTimeoutWarning
        onExtendSession={extendSession}
        onLogout={handleSessionLogout}
      />
    )}
    <Suspense fallback={<div className="page-loading">Loading...</div>}>
      <Routes>
        {/* Public auth routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />

        {/* Protected routes */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="portfolios" element={<PortfolioPage />} />
          <Route path="screener/:type" element={<ScreenerPage />} />
          <Route path="instruments/:type/:ticker" element={<InstrumentDetailPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="brokers/connections" element={<BrokerConnectionsPage />} />
          <Route path="brokers/positions" element={<BrokerPositionsPage />} />
          <Route path="brokers/positions/:connectionId" element={<PositionDetailsPage />} />
          <Route path="brokers/accounts/:connectionId" element={<AccountDetailPage />} />
          <Route path="brokers/reporting" element={<ReportingPage />} />
          <Route path="options" element={<OptionsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route
            path="admin"
            element={
              <ProtectedRoute requiredRoles={['ADMIN']}>
                <AdminPage />
              </ProtectedRoute>
            }
          />
        </Route>

        {/* Unauthorized page */}
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        {/* Catch-all redirect */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
    </>
  )
}

export default App
