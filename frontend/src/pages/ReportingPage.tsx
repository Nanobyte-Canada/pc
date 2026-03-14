import { useState } from 'react'
import { useBrokerConnections } from '../hooks/useBrokerConnections'
import { useReportingPerformance } from '../hooks/useReporting'
import { DateRangeSelector } from '../components/reporting/DateRangeSelector'
import { ContributionsChart } from '../components/reporting/ContributionsChart'
import { TotalValueChart } from '../components/reporting/TotalValueChart'
import { DividendHistoryChart } from '../components/reporting/DividendHistoryChart'
import { TotalDividendsChart } from '../components/reporting/TotalDividendsChart'
import { ActivityTable } from '../components/reporting/ActivityTable'

import './ReportingPage.css'

type ReportingTab = 'performance' | 'activity'

export function ReportingPage() {
  const [activeTab, setActiveTab] = useState<ReportingTab>('performance')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [selectedAccounts, setSelectedAccounts] = useState<number[]>([])

  const { data: connectionsData } = useBrokerConnections()
  const connections = connectionsData?.connections.filter(c => c.status !== 'DISCONNECTED') || []

  const accountsParam = selectedAccounts.length > 0 ? selectedAccounts.join(',') : undefined

  // Calculate dynamic granularity based on date range
  const granularity = (() => {
    const start = startDate ? new Date(startDate) : null
    const end = endDate ? new Date(endDate) : new Date()
    if (!start) return 'MONTHLY'
    const diffMs = end.getTime() - start.getTime()
    const diffYears = diffMs / (365.25 * 24 * 60 * 60 * 1000)
    if (diffYears > 3) return 'YEARLY'
    if (diffYears > 1) return 'QUARTERLY'
    return 'MONTHLY'
  })()

  const { data: performanceData, isLoading } = useReportingPerformance({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
    accounts: accountsParam,
    granularity
  })

  const handleDateChange = (start: string, end: string) => {
    setStartDate(start)
    setEndDate(end)
  }

  const handleAccountToggle = (connectionId: number) => {
    setSelectedAccounts(prev =>
      prev.includes(connectionId)
        ? prev.filter(id => id !== connectionId)
        : [...prev, connectionId]
    )
  }

  return (
    <div className="reporting-page">
      <div className="reporting-header">
        <h1>Reporting</h1>
      </div>

      {/* Filters */}
      <div className="reporting-filters">
        <DateRangeSelector
          startDate={startDate}
          endDate={endDate}
          onDateChange={handleDateChange}
        />
        {connections.length > 1 && (
          <div className="account-filter">
            <span className="filter-label">Accounts:</span>
            <div className="account-chips">
              {connections.map(conn => (
                <button
                  key={conn.id}
                  className={`account-chip ${selectedAccounts.length === 0 || selectedAccounts.includes(conn.id) ? 'active' : ''}`}
                  onClick={() => handleAccountToggle(conn.id)}
                >
                  {conn.accountName || conn.accountNumber || `Account ${conn.id}`}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="reporting-tabs">
        <button
          className={`reporting-tab ${activeTab === 'performance' ? 'active' : ''}`}
          onClick={() => setActiveTab('performance')}
        >
          Performance
        </button>
        <button
          className={`reporting-tab ${activeTab === 'activity' ? 'active' : ''}`}
          onClick={() => setActiveTab('activity')}
        >
          Activity
        </button>
      </div>

      {/* Tab Content */}
      {activeTab === 'performance' ? (
        <div className="reporting-performance">
          {isLoading ? (
            <div className="reporting-loading">Loading performance data...</div>
          ) : !performanceData ? (
            <div className="reporting-empty">No data available. Sync your broker activities first.</div>
          ) : (
            <>
              <ContributionsChart
                data={performanceData.contributionsWithdrawals}
                kpis={performanceData.kpis}
              />
              <TotalValueChart
                data={performanceData.totalValueHistory}
                kpis={performanceData.kpis}
              />
              <DividendHistoryChart
                data={performanceData.dividendHistory}
                kpis={performanceData.kpis}
              />
              {performanceData.totalDividendsBySymbol.length > 0 && (
                <TotalDividendsChart data={performanceData.totalDividendsBySymbol} />
              )}
            </>
          )}
        </div>
      ) : (
        <ActivityTable
          startDate={startDate}
          endDate={endDate}
          accounts={accountsParam || ''}
        />
      )}
    </div>
  )
}
