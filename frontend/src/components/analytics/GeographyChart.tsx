import { AgCharts } from 'ag-charts-react';
import { GeographyExposure } from '../../types/portfolio';
import './ChartStyles.css';

interface GeographyChartProps {
  data: GeographyExposure[];
}

const REGION_COLORS: Record<string, string> = {
  'North America': '#00d9ff',
  'Europe': '#00ff88',
  'Asia Pacific': '#ffaa00',
  'Latin America': '#ff6b6b',
  'Middle East & Africa': '#9b59b6',
  'Other': '#718096',
};

export function GeographyChart({ data }: GeographyChartProps) {
  // Aggregate by region for cleaner visualization
  const regionData = data.reduce((acc, geo) => {
    const region = geo.region || 'Other';
    const existing = acc.find((r) => r.region === region);
    if (existing) {
      existing.weight += geo.weight;
    } else {
      acc.push({ region, weight: geo.weight });
    }
    return acc;
  }, [] as { region: string; weight: number }[]);

  const chartData = regionData
    .sort((a, b) => b.weight - a.weight)
    .map((region) => ({
      ...region,
      weightPercent: region.weight * 100,
      color: REGION_COLORS[region.region] || REGION_COLORS['Other'],
    }));

  const options = {
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'donut' as const,
        angleKey: 'weightPercent',
        legendItemKey: 'region',
        calloutLabelKey: 'region',
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
          renderer: ({ datum }: { datum: { region: string; weightPercent: number } }) => ({
            title: datum.region,
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
