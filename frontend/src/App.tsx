import { useEffect, useCallback } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { SessionTimeoutWarning } from './components/auth/SessionTimeoutWarning'
import { useAuthStore } from './stores/authStore'
import { useSessionManager } from './hooks/useSessionManager'

// Auth pages
import { LoginPage } from './pages/auth/LoginPage'
import { SignupPage } from './pages/auth/SignupPage'
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage'
import { VerifyEmailPage } from './pages/auth/VerifyEmailPage'

// Protected pages
import { PortfolioBuilderPage } from './pages/PortfolioBuilderPage'
import { StockScreenerPage } from './pages/StockScreenerPage'
import { EtfScreenerPage } from './pages/EtfScreenerPage'
import { MutualFundScreenerPage } from './pages/MutualFundScreenerPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { ProfilePage } from './pages/ProfilePage'
import { AdminPage } from './pages/admin/AdminPage'
import { UnauthorizedPage } from './pages/UnauthorizedPage'

// Broker pages
import { BrokerConnectionsPage } from './pages/BrokerConnectionsPage'
import { BrokerPositionsPage } from './pages/BrokerPositionsPage'
import { PositionDetailsPage } from './pages/PositionDetailsPage'
import { ReportingPage } from './pages/ReportingPage'

// Portfolio Group pages
import { PortfolioGroupsPage } from './pages/PortfolioGroupsPage'
import { PortfolioGroupDetailPage } from './pages/PortfolioGroupDetailPage'

// Dashboard
import { DashboardPage } from './pages/DashboardPage'

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
        <Route path="builder" element={<PortfolioBuilderPage />} />
        <Route path="portfolios" element={<PortfolioGroupsPage />} />
        <Route path="portfolios/:groupId" element={<PortfolioGroupDetailPage />} />
        <Route path="screener/stocks" element={<StockScreenerPage />} />
        <Route path="screener/etfs" element={<EtfScreenerPage />} />
        <Route path="screener/mutual-funds" element={<MutualFundScreenerPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="brokers/connections" element={<BrokerConnectionsPage />} />
        <Route path="brokers/positions" element={<BrokerPositionsPage />} />
        <Route path="brokers/positions/:connectionId" element={<PositionDetailsPage />} />
        <Route path="brokers/reporting" element={<ReportingPage />} />
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
    </>
  )
}

export default App
