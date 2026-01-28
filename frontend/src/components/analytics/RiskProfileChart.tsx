import { AgCharts } from 'ag-charts-react';
import { RiskMetrics } from '../../types/portfolio';
import './ChartStyles.css';

interface RiskProfileChartProps {
  metrics: RiskMetrics;
}

interface RiskBar {
  metric: string;
  value: number;
  threshold: number;
  status: 'good' | 'warning' | 'high';
}

const getColor = (status: string) => {
  switch (status) {
    case 'good':
      return '#00ff88';
    case 'warning':
      return '#ffaa00';
    case 'high':
      return '#ff6b6b';
    default:
      return '#00d9ff';
  }
};

export function RiskProfileChart({ metrics }: RiskProfileChartProps) {
  const chartData: RiskBar[] = [
    {
      metric: 'Top 10 Concentration',
      value: metrics.top10Concentration * 100,
      threshold: 50,
      status: metrics.top10Concentration < 0.4 ? 'good' : metrics.top10Concentration < 0.6 ? 'warning' : 'high',
    },
    {
      metric: 'Holding HHI',
      value: metrics.concentrationHHI * 100,
      threshold: 10,
      status: metrics.concentrationHHI < 0.05 ? 'good' : metrics.concentrationHHI < 0.15 ? 'warning' : 'high',
    },
    {
      metric: 'Sector HHI',
      value: metrics.sectorConcentrationHHI * 100,
      threshold: 25,
      status: metrics.sectorConcentrationHHI < 0.15 ? 'good' : metrics.sectorConcentrationHHI < 0.25 ? 'warning' : 'high',
    },
    {
      metric: 'Est. Volatility',
      value: metrics.estimatedVolatility * 100,
      threshold: 20,
      status: metrics.estimatedVolatility < 0.15 ? 'good' : metrics.estimatedVolatility < 0.22 ? 'warning' : 'high',
    },
  ];

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar' as const,
        direction: 'horizontal' as const,
        xKey: 'metric',
        yKey: 'value',
        yName: 'Current Value',
        fill: '#00d9ff',
        stroke: 'transparent',
        cornerRadius: 4,
        formatter: (params: { itemId: number }) => ({
          fill: getColor(chartData[params.itemId]?.status || 'good'),
        }),
        label: {
          enabled: true,
          color: '#fff',
          formatter: ({ value }: { value: number }) => `${value.toFixed(1)}%`,
        },
        tooltip: {
          renderer: ({ datum }: { datum: RiskBar }) => ({
            title: datum.metric,
            content: `Value: ${datum.value.toFixed(2)}% | Threshold: ${datum.threshold}%`,
          }),
        },
      },
    ],
    axes: [
      {
        type: 'category' as const,
        position: 'left' as const,
        label: {
          color: '#a0aec0',
          fontSize: 12,
        },
        line: {
          enabled: false,
        },
      },
      {
        type: 'number' as const,
        position: 'bottom' as const,
        label: {
          color: '#718096',
          formatter: ({ value }: { value: number }) => `${value}%`,
        },
        gridLine: {
          style: [{ stroke: '#16213e', lineDash: [4, 4] }],
        },
      },
    ],
    legend: {
      enabled: false,
    },
  };

  return (
    <div className="risk-profile-container">
      <div className="chart-container" style={{ height: '200px' }}>
        <AgCharts options={options} />
      </div>
      <div className="risk-legend">
        <span className="legend-item">
          <span className="legend-dot good"></span> Low Risk
        </span>
        <span className="legend-item">
          <span className="legend-dot warning"></span> Moderate
        </span>
        <span className="legend-item">
          <span className="legend-dot high"></span> High Risk
        </span>
      </div>
    </div>
  );
}
