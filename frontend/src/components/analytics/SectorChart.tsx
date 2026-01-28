import { AgCharts } from 'ag-charts-react';
import { SectorExposure } from '../../types/portfolio';
import './ChartStyles.css';

interface SectorChartProps {
  data: SectorExposure[];
}

const SECTOR_COLORS: Record<string, string> = {
  '45': '#00d9ff', // Information Technology
  '35': '#00ff88', // Health Care
  '40': '#ffaa00', // Financials
  '25': '#ff6b6b', // Consumer Discretionary
  '30': '#9b59b6', // Consumer Staples
  '55': '#3498db', // Utilities
  '10': '#e67e22', // Energy
  '15': '#1abc9c', // Materials
  '20': '#f39c12', // Industrials
  '50': '#e74c3c', // Communication Services
  '60': '#2ecc71', // Real Estate
  'UNKNOWN': '#718096',
};

export function SectorChart({ data }: SectorChartProps) {
  const chartData = data.map((sector) => ({
    ...sector,
    weightPercent: sector.weight * 100,
    color: SECTOR_COLORS[sector.sectorCode] || SECTOR_COLORS['UNKNOWN'],
  }));

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'donut' as const,
        angleKey: 'weightPercent',
        legendItemKey: 'sectorName',
        calloutLabelKey: 'sectorName',
        sectorLabelKey: 'weightPercent',
        innerRadiusRatio: 0.5,
        fills: chartData.map((d) => d.color),
        strokeWidth: 1,
        stroke: '#1a1a2e',
        calloutLabel: {
          enabled: true,
          minAngle: 20,
        },
        sectorLabel: {
          enabled: true,
          color: '#fff',
          fontWeight: 'bold' as const,
          fontSize: 11,
          formatter: ({ value }: { value: number }) => `${value.toFixed(1)}%`,
        },
        tooltip: {
          renderer: ({ datum }: { datum: { sectorName: string; weightPercent: number } }) => ({
            title: datum.sectorName,
            content: `${datum.weightPercent.toFixed(2)}%`,
          }),
        },
      },
    ],
    legend: {
      enabled: true,
      position: 'bottom' as const,
      item: {
        label: {
          color: '#a0aec0',
          fontSize: 11,
        },
      },
    },
  };

  return (
    <div className="chart-container">
      <AgCharts options={options} />
    </div>
  );
}
