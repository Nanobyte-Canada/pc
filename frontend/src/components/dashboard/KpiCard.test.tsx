import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { KpiCard } from './KpiCard'
import { scenario } from '../../test/scenario'

describe('KpiCard', () => {
  it(scenario('DASH-KPI-001', 'renders label and value'), () => {
    render(<KpiCard label="Total Value" value="$125,000.00" />)
    expect(screen.getByText('Total Value')).toBeInTheDocument()
    expect(screen.getByText('$125,000.00')).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-002', 'renders with emerald variant class'), () => {
    const { container } = render(<KpiCard label="Cash" value="$5,000" variant="emerald" />)
    const card = container.querySelector('.kpi-card')
    expect(card).toHaveClass('kpi-card--emerald')
  })

  it(scenario('DASH-KPI-003', 'renders with default variant when no variant specified'), () => {
    const { container } = render(<KpiCard label="Value" value="$100" />)
    const card = container.querySelector('.kpi-card')
    expect(card).not.toHaveClass('kpi-card--emerald')
    expect(card).not.toHaveClass('kpi-card--combined')
  })

  it(scenario('DASH-KPI-004', 'renders icon when provided'), () => {
    render(
      <KpiCard
        label="Value"
        value="$100"
        icon={<span data-testid="test-icon">📊</span>}
      />
    )
    expect(screen.getByTestId('test-icon')).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-005', 'does not render icon slot when not provided'), () => {
    const { container } = render(<KpiCard label="Value" value="$100" />)
    expect(container.querySelector('.kpi-card__icon')).toBeNull()
  })

  it(scenario('DASH-KPI-006', 'renders breakdown items'), () => {
    render(
      <KpiCard
        label="Returns"
        value="+12.5%"
        variant="returns"
        breakdown={[
          { label: 'CAD', value: '+10.2%' },
          { label: 'USD', value: '+2.3%', variant: 'positive' },
        ]}
      />
    )
    expect(screen.getByText('CAD')).toBeInTheDocument()
    expect(screen.getByText('+10.2%')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('+2.3%')).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-007', 'applies positive variant class to breakdown value'), () => {
    const { container } = render(
      <KpiCard
        label="Returns"
        value="+5%"
        variant="returns"
        breakdown={[{ label: 'Gains', value: '+$500', variant: 'positive' }]}
      />
    )
    const value = container.querySelector('.kpi-card__breakdown-value--positive')
    expect(value).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-008', 'applies negative variant class to breakdown value'), () => {
    const { container } = render(
      <KpiCard
        label="Returns"
        value="-2%"
        variant="returns"
        breakdown={[{ label: 'Losses', value: '-$200', variant: 'negative' }]}
      />
    )
    const value = container.querySelector('.kpi-card__breakdown-value--negative')
    expect(value).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-009', 'renders positive returns with positive class'), () => {
    const { container } = render(
      <KpiCard label="Returns" value="+8.5%" variant="returns" />
    )
    const value = container.querySelector('.kpi-card__value--positive')
    expect(value).toBeInTheDocument()
    expect(value).toHaveTextContent('+8.5%')
  })

  it(scenario('DASH-KPI-010', 'renders negative returns with negative class'), () => {
    const { container } = render(
      <KpiCard label="Returns" value="-3.2%" variant="returns" />
    )
    const value = container.querySelector('.kpi-card__value--negative')
    expect(value).toBeInTheDocument()
    expect(value).toHaveTextContent('-3.2%')
  })

  it(scenario('DASH-KPI-011', 'renders sector list with names and weights'), () => {
    render(
      <KpiCard
        label="Sectors"
        value="5 sectors"
        sectors={[
          { name: 'Technology', weight: 0.35, color: '#3b82f6' },
          { name: 'Healthcare', weight: 0.20, color: '#10b981' },
        ]}
      />
    )
    expect(screen.getByText('Technology')).toBeInTheDocument()
    expect(screen.getByText('35.0%')).toBeInTheDocument()
    expect(screen.getByText('Healthcare')).toBeInTheDocument()
    expect(screen.getByText('20.0%')).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-012', 'renders combined variant with total value, investment, and cash'), () => {
    render(
      <KpiCard
        label="Portfolio"
        value=""
        variant="combined"
        combined={{
          totalValue: { cad: '$250,000.00', usd: '$185,000.00' },
          investment: { cad: '$230,000.00', usd: '$170,000.00' },
          cash: { cad: '$20,000.00', usd: '$15,000.00' },
        }}
      />
    )
    expect(screen.getByText('$250,000.00')).toBeInTheDocument()
    expect(screen.getByText('$185,000.00')).toBeInTheDocument()
    expect(screen.getByText('Investment')).toBeInTheDocument()
    expect(screen.getByText('$230,000.00')).toBeInTheDocument()
    expect(screen.getByText('Cash')).toBeInTheDocument()
    expect(screen.getByText('$20,000.00')).toBeInTheDocument()
  })

  it(scenario('DASH-KPI-013', 'combined variant omits USD when not provided'), () => {
    render(
      <KpiCard
        label="Portfolio"
        value=""
        variant="combined"
        combined={{
          totalValue: { cad: '$100,000' },
          investment: { cad: '$90,000' },
          cash: { cad: '$10,000' },
        }}
      />
    )
    expect(screen.getByText('$100,000')).toBeInTheDocument()
    expect(screen.getByText('$90,000')).toBeInTheDocument()
    expect(screen.getByText('$10,000')).toBeInTheDocument()
  })
})
