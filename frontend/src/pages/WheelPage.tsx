import { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
import { useWheelPositions } from '@/hooks/useWheelPositions'
import { useDashboardCash } from '@/hooks/useDashboardWidgets'
import { CapitalSummary } from '@/components/wheel/CapitalSummary'
import { WheelGrid } from '@/components/wheel/WheelGrid'
import { ClosePositionDialog } from '@/components/wheel/ClosePositionDialog'
import type { WheelPosition } from '@/types/wheel'
import './WheelPage.css'

const WHEEL_TICKERS = ['SOXL', 'TECL', 'TQQQ', 'UPRO']

interface CloseDialogState {
  position: WheelPosition
  ticker: string
  expiryDate: string
}

export function WheelPage() {
  const navigate = useNavigate()
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | undefined>(undefined)
  const [closeDialog, setCloseDialog] = useState<CloseDialogState | null>(null)

  const { data: connectionsData } = useBrokerConnections()
  const connections = connectionsData?.connections ?? []
  const activeConnections = connections.filter(c => c.status === 'ACTIVE')

  const { gridData, isLoading, error } = useWheelPositions(WHEEL_TICKERS, selectedConnectionId)
  const { data: cashData } = useDashboardCash(selectedConnectionId)

  const capitalMetrics = useMemo(() => {
    if (!gridData) return null
    const cashBalance = cashData?.totalCashCAD ?? 0
    const allPositions = gridData.expiryRows.flatMap(row =>
      Object.values(row.cells).flatMap(cell => cell.positions)
    )
    let deployedCsp = 0
    let ccsWritten = 0
    let totalPremium = 0
    let unrealizedPnl = 0

    allPositions.forEach(p => {
      if (p.type === 'CSP') {
        deployedCsp += p.strike * 100 * p.quantity
      } else {
        ccsWritten += p.strike * 100 * p.quantity
      }
      totalPremium += p.premium ?? 0
      unrealizedPnl += p.pnl ?? 0
    })

    return {
      availableCash: cashBalance,
      deployedCsp,
      sharesHeld: 0,
      ccsWritten,
      totalPremium,
      unrealizedPnl,
    }
  }, [gridData, cashData])

  const showAccount = selectedConnectionId === undefined

  const handlePositionClick = useCallback((position: WheelPosition, ticker: string, expiryDate: string) => {
    setCloseDialog({ position, ticker, expiryDate })
  }, [])

  const handleEmptySlotClick = useCallback((ticker: string) => {
    navigate(`/options?ticker=${encodeURIComponent(ticker)}`)
  }, [navigate])

  const handleCloseConfirm = useCallback(() => {
    setCloseDialog(null)
  }, [])

  return (
    <div className="wheel-page">
      <div className="wheel-page-header">
        <h1 className="wheel-page-title">Wheel Strategy</h1>
        <p className="wheel-page-subtitle">Manage CSP and CC positions across your accounts</p>
      </div>

      <div className="wheel-account-tabs">
        <button
          className={`wheel-account-tab ${selectedConnectionId === undefined ? 'wheel-account-tab-active' : ''}`}
          onClick={() => setSelectedConnectionId(undefined)}
        >
          All Accounts
        </button>
        {activeConnections.map(conn => (
          <button
            key={conn.id}
            className={`wheel-account-tab ${selectedConnectionId === conn.id ? 'wheel-account-tab-active' : ''}`}
            onClick={() => setSelectedConnectionId(conn.id)}
          >
            {conn.accountName || conn.broker?.name || 'Account'}
            {conn.accountNumber ? ` ${conn.accountNumber}` : ''}
          </button>
        ))}
      </div>

      <CapitalSummary metrics={capitalMetrics} />

      {isLoading && (
        <div className="wheel-loading">Loading positions...</div>
      )}

      {error && (
        <div className="wheel-error">Failed to load positions. Please try again.</div>
      )}

      {gridData && !isLoading && (
        <WheelGrid
          data={gridData}
          showAccount={showAccount}
          onPositionClick={handlePositionClick}
          onEmptySlotClick={handleEmptySlotClick}
        />
      )}

      {closeDialog && (
        <ClosePositionDialog
          position={closeDialog.position}
          ticker={closeDialog.ticker}
          expiryDate={closeDialog.expiryDate}
          onConfirm={handleCloseConfirm}
          onCancel={() => setCloseDialog(null)}
        />
      )}
    </div>
  )
}
