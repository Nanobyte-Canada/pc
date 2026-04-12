import { useState, useRef, useEffect, useCallback } from 'react';
import { useNewInstrumentSearch } from '../../hooks/useNewScreener';
import { usePortfolioStore } from '../../store/portfolioStore';
import { InstrumentType, SearchResult, INSTRUMENT_TYPE_CONFIG } from '../../types/screener';
import './InstrumentSearchAutocomplete.css';

interface InstrumentSearchAutocompleteProps {
  filterType?: InstrumentType | 'all';
}

export function InstrumentSearchAutocomplete({ filterType = 'all' }: InstrumentSearchAutocompleteProps) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Convert filterType to types array for new search hook
  const types = filterType === 'all' ? undefined : [filterType];
  const { data, isLoading } = useNewInstrumentSearch(debouncedQuery, types);
  const { addPosition, hasPosition } = usePortfolioStore();

  // Debounce the query
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 200);
    return () => clearTimeout(timer);
  }, [query]);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const results = data?.data || [];

  const handleAddToPortfolio = useCallback((result: SearchResult) => {
    // New SearchResult has id as number, not string
    const instrumentId = result.id;

    addPosition({
      instrumentType: result.type,
      instrumentId,
      symbol: result.ticker,
      name: result.name,
    });

    setQuery('');
    setIsOpen(false);
    inputRef.current?.focus();
  }, [addPosition]);

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (!isOpen || results.length === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setSelectedIndex((prev) => Math.min(prev + 1, results.length - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setSelectedIndex((prev) => Math.max(prev - 1, 0));
        break;
      case 'Enter':
        event.preventDefault();
        if (selectedIndex >= 0 && selectedIndex < results.length) {
          handleAddToPortfolio(results[selectedIndex]);
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setSelectedIndex(-1);
        break;
    }
  };

  const getTypeLabel = (type: InstrumentType) => {
    return INSTRUMENT_TYPE_CONFIG[type]?.label || type;
  };

  const getTypeClass = (type: InstrumentType) => {
    // Map type to CSS class
    const classMap: Record<InstrumentType, string> = {
      'STOCK': 'type-stock',
      'ETF': 'type-etf',
      'MUTUAL_FUND': 'type-mutual-fund',
      'PREFERRED_STOCK': 'type-preferred-stock',
      'INDEX': 'type-index',
      'BOND': 'type-bond',
    };
    return classMap[type] || 'type-other';
  };

  return (
    <div className="autocomplete-container">
      <input
        ref={inputRef}
        type="text"
        className="autocomplete-input"
        placeholder="Search by ticker or name..."
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setIsOpen(true);
          setSelectedIndex(-1);
        }}
        onFocus={() => setIsOpen(true)}
        onKeyDown={handleKeyDown}
      />

      {isOpen && query.length >= 1 && (
        <div ref={dropdownRef} className="autocomplete-dropdown">
          {isLoading && (
            <div className="autocomplete-loading">Searching...</div>
          )}

          {!isLoading && results.length === 0 && (
            <div className="autocomplete-empty">No results found</div>
          )}

          {!isLoading && results.map((result, index) => {
            // New SearchResult has id as number
            const instrumentId = result.id;
            const alreadyAdded = hasPosition(result.type, instrumentId);

            return (
              <div
                key={result.id}
                className={`autocomplete-item ${index === selectedIndex ? 'selected' : ''} ${alreadyAdded ? 'disabled' : ''}`}
                onClick={() => !alreadyAdded && handleAddToPortfolio(result)}
              >
                <span className={`type-badge ${getTypeClass(result.type)}`}>
                  {getTypeLabel(result.type)}
                </span>
                <span className="ticker">{result.ticker}</span>
                <span className="name">{result.name}</span>
                {result.exchange && (
                  <span className="exchange">{result.exchange}</span>
                )}
                {alreadyAdded ? (
                  <span className="added-badge">Added</span>
                ) : (
                  <button className="add-button">+ Add</button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
