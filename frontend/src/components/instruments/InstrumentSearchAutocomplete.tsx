import { useState, useRef, useEffect, useCallback } from 'react';
import { useInstrumentSearch } from '../../hooks/useInstrumentSearch';
import { usePortfolioStore } from '../../store/portfolioStore';
import { InstrumentType, SearchResult } from '../../types/instrument';
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

  const { data, isLoading } = useInstrumentSearch(debouncedQuery, filterType);
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
    const idParts = result.id.split('-');
    const instrumentId = parseInt(idParts[1], 10);

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
    switch (type) {
      case 'STOCK': return 'Stock';
      case 'ETF': return 'ETF';
      default: return type;
    }
  };

  const getTypeClass = (type: InstrumentType) => {
    switch (type) {
      case 'STOCK': return 'type-stock';
      case 'ETF': return 'type-etf';
      default: return 'type-other';
    }
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
            const idParts = result.id.split('-');
            const instrumentId = parseInt(idParts[1], 10);
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
