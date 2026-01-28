import { useCallback } from 'react';
import { AgGridReact } from 'ag-grid-react';
import { ColDef, GridReadyEvent } from 'ag-grid-community';
import { usePortfolioStore } from '../../store/portfolioStore';
import { InstrumentType } from '../../types/instrument';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import './ScreenerGrid.css';

interface ScreenerGridProps<T> {
  rowData: T[];
  columnDefs: ColDef<T>[];
  loading?: boolean;
  instrumentType: InstrumentType;
  getRowId: (data: T) => number;
  getTicker: (data: T) => string;
  getName: (data: T) => string;
}

export function ScreenerGrid<T>({
  rowData,
  columnDefs,
  loading = false,
  instrumentType,
  getRowId,
  getTicker,
  getName,
}: ScreenerGridProps<T>) {
  const { addPosition, hasPosition } = usePortfolioStore();

  const handleAddToPortfolio = useCallback((data: T) => {
    const instrumentId = getRowId(data);
    if (!hasPosition(instrumentType, instrumentId)) {
      addPosition({
        instrumentType,
        instrumentId,
        symbol: getTicker(data),
        name: getName(data),
      });
    }
  }, [addPosition, hasPosition, instrumentType, getRowId, getTicker, getName]);

  const ActionCellRenderer = useCallback((params: { data: T }) => {
    const instrumentId = getRowId(params.data);
    const alreadyAdded = hasPosition(instrumentType, instrumentId);

    return alreadyAdded ? (
      <span className="added-badge">Added</span>
    ) : (
      <button
        className="add-btn"
        onClick={() => handleAddToPortfolio(params.data)}
      >
        + Add
      </button>
    );
  }, [handleAddToPortfolio, hasPosition, instrumentType, getRowId]);

  const allColumns: ColDef<T>[] = [
    ...columnDefs,
    {
      headerName: '',
      field: undefined,
      width: 100,
      sortable: false,
      filter: false,
      cellRenderer: ActionCellRenderer,
    },
  ];

  const onGridReady = useCallback((event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  }, []);

  const defaultColDef: ColDef = {
    sortable: true,
    resizable: true,
    filter: true,
  };

  return (
    <div className="ag-theme-quartz-dark screener-grid">
      <AgGridReact
        rowData={rowData}
        columnDefs={allColumns}
        defaultColDef={defaultColDef}
        onGridReady={onGridReady}
        loading={loading}
        animateRows={true}
        pagination={false}
        domLayout="autoHeight"
      />
    </div>
  );
}
