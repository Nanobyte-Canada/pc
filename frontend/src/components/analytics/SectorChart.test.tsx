import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SectorChart } from './SectorChart'
import type { SectorExposure } from '@/types/portfolio'
import { scenario } from '../../test/scenario'

vi.mock('ag-charts-react', () => ({
  AgCharts: ({ options }: { options: { data: unknown[] } }) => (
    <div data-testid="ag-chart" data-data-length={options?.data?.length} />
  ),
}))

const mockData: SectorExposure[] = [
  { sectorCode: '45', sectorName: 'Information Technology', weight: 0.35 },
  { sectorCode: '35', sectorName: 'Health Care', weight: 0.20 },
  { sectorCode: '40', sectorName: 'Financials', weight: 0.15 },
]

describe('SectorChart', () => {
  it(scenario('ANALYTICS-CHART-001', 'renders chart container'), () => {
    render(<SectorChart data={mockData} />)
    expect(screen.getByTestId('ag-chart')).toBeInTheDocument()
  })

  it(scenario('ANALYTICS-CHART-002', 'passes correct data length to chart'), () => {
    render(<SectorChart data={mockData} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart.getAttribute('data-data-length')).toBe('3')
  })

  it(scenario('ANALYTICS-CHART-003', 'handles empty data'), () => {
    render(<SectorChart data={[]} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart.getAttribute('data-data-length')).toBe('0')
  })

  it(scenario('ANALYTICS-CHART-004', 'renders chart with single sector'), () => {
    render(<SectorChart data={[mockData[0]]} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart.getAttribute('data-data-length')).toBe('1')
  })

  it(scenario('ANALYTICS-CHART-005', 'renders chart with many sectors'), () => {
    const manySectors: SectorExposure[] = Array.from({ length: 11 }, (_, i) => ({
      sectorCode: String(i),
      sectorName: `Sector ${i}`,
      weight: 0.1,
    }))
    render(<SectorChart data={manySectors} />)
    const chart = screen.getByTestId('ag-chart')
    expect(chart.getAttribute('data-data-length')).toBe('11')
  })
})
