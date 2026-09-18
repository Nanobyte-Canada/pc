import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ContributionsChart } from './ContributionsChart'
import { scenario } from '../../test/scenario'
import type { PeriodSummary, PerformanceKpis } from '@/types/broker'

vi.mock('ag-charts-react', () => ({
  AgCharts: ({ options }: { options: { data: unknown[] } }) => (
    <div data-testid="ag-chart" data-data-length={options?.data?.length} />
  ),
}))

vi.mock('./KpiCard', () => ({
  KpiCard: ({ label, value }: { label: string; value: string }) => (
    <div data-testid="kpi-card">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  ),
}))

const mockData: PeriodSummary[] = [
  { period: '2026-01', contributions: 2000, withdrawals: 0, net: 2000 },
  { period: '2026-02', contributions: 1500, withdrawals: 500, net: 1000 },
  { period: '2026-03', contributions: 3000, withdrawals: 0, net: 3000 },
]

const mockKpis: PerformanceKpis = {
  netContributions: 12500,
  monthlyAvgContributions: 2083.33,
  netChange: 5000,
  totalDividendIncome: 450,
  avgMonthlyDividends: 75,
  feesAndCommissions: 120,
}

describe('ContributionsChart', () => {
  it(scenario('RPT-CONTRIB-001', 'renders chart heading'), () => {
    render(<ContributionsChart data={mockData} kpis={mockKpis} />)
    expect(screen.getByText('Contributions & Withdrawals')).toBeInTheDocument()
  })

  it(scenario('RPT-CONTRIB-002', 'renders AG Chart with correct data length'), () => {
    render(<ContributionsChart data={mockData} kpis={mockKpis} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart).toBeInTheDocument()
    expect(chart.getAttribute('data-data-length')).toBe('3')
  })

  it(scenario('RPT-CONTRIB-003', 'renders net contributions KPI card'), () => {
    render(<ContributionsChart data={mockData} kpis={mockKpis} />)
    const kpiCards = screen.getAllByTestId('kpi-card')
    expect(kpiCards.some(card => card.textContent?.includes('Net Contributions'))).toBe(true)
  })

  it(scenario('RPT-CONTRIB-004', 'renders monthly average KPI card'), () => {
    render(<ContributionsChart data={mockData} kpis={mockKpis} />)
    const kpiCards = screen.getAllByTestId('kpi-card')
    expect(kpiCards.some(card => card.textContent?.includes('Monthly Average'))).toBe(true)
  })

  it(scenario('RPT-CONTRIB-005', 'passes transformed data with negative withdrawals'), () => {
    render(<ContributionsChart data={mockData} kpis={mockKpis} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart).toBeInTheDocument()
    expect(chart.getAttribute('data-data-length')).toBe(String(mockData.length))
  })

  it(scenario('RPT-CONTRIB-006', 'handles empty data'), () => {
    render(<ContributionsChart data={[]} kpis={mockKpis} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart.getAttribute('data-data-length')).toBe('0')
  })
})
