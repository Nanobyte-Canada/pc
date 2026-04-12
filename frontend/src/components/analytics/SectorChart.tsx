import { AgCharts } from 'ag-charts-react';
import { SectorExposure } from '../../types/portfolio';
import './ChartStyles.css';

interface SectorChartProps {
  data: SectorExposure[];
}

const SECTOR_COLORS: Record<string, string> = {
  '45': '#546d84', // Information Technology
  '35': '#059669', // Health Care
  '40': '#d97706', // Financials
  '25': '#dc2626', // Consumer Discretionary
  '30': '#7c3aed', // Consumer Staples
  '55': '#0284c7', // Utilities
  '10': '#ea580c', // Energy
  '15': '#0d9488', // Materials
  '20': '#ca8a04', // Industrials
  '50': '#e11d48', // Communication Services
  '60': '#16a34a', // Real Estate
  'UNKNOWN': '#6b7280',
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
        stroke: '#ffffff',
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
          color: '#6e7182',
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
