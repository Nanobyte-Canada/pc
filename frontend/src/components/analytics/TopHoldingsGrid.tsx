import { AgGridReact } from 'ag-grid-react';
import { ColDef } from 'ag-grid-community';
import { TopHolding } from '../../types/portfolio';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import './TopHoldingsGrid.css';

interface TopHoldingsGridProps {
  data: TopHolding[];
}

const formatSources = (sources: TopHolding['sources']): string => {
  return sources
    .map((s) => {
      if (s.type === 'DIRECT') {
        return `Direct: ${(s.contribution * 100).toFixed(1)}%`;
      }
      return `${s.instrumentSymbol || s.type}: ${(s.contribution * 100).toFixed(1)}%`;
    })
    .join(', ');
};

export function TopHoldingsGrid({ data }: TopHoldingsGridProps) {
  const columnDefs: ColDef<TopHolding>[] = [
    {
      field: 'ticker',
      headerName: 'Ticker',
      width: 100,
      cellClass: 'ticker-cell',
    },
    {
      field: 'name',
      headerName: 'Company Name',
      flex: 1,
      minWidth: 200,
    },
    {
      field: 'effectiveWeight',
      headerName: 'Weight',
      width: 100,
      valueFormatter: (params) => `${(params.value * 100).toFixed(2)}%`,
      cellClass: 'weight-cell',
    },
    {
      field: 'sources',
      headerName: 'Sources',
      flex: 1,
      minWidth: 250,
      valueFormatter: (params) => formatSources(params.value),
      cellClass: 'sources-cell',
    },
  ];

  const defaultColDef: ColDef = {
    resizable: true,
    sortable: true,
  };

  return (
    <div className="top-holdings-grid ag-theme-quartz-dark">
      <AgGridReact
        rowData={data}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        domLayout="autoHeight"
        suppressCellFocus={true}
        animateRows={true}
      />
    </div>
  );
}
