import { ReactNode, useState } from 'react';
import { ChevronDown, SlidersHorizontal, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import './ScreenerFilters.css';

interface ScreenerFiltersProps {
  children: ReactNode;
  onApply: () => void;
  onReset: () => void;
  activeFilters?: { key: string; label: string; value: string }[];
  onRemoveFilter?: (key: string) => void;
}

export function ScreenerFilters({ children, onApply, onReset, activeFilters, onRemoveFilter }: ScreenerFiltersProps) {
  const [isExpanded, setIsExpanded] = useState(true);
  const filterCount = activeFilters?.length ?? 0;

  return (
    <div className="screener-filters-card">
      <button
        className="screener-filters-toggle"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <span className="screener-filters-toggle-left">
          <SlidersHorizontal size={16} />
          <span>Filters</span>
          {filterCount > 0 && (
            <span className="filter-count-badge">{filterCount}</span>
          )}
        </span>
        <ChevronDown
          size={16}
          className={cn('filter-chevron', isExpanded && 'filter-chevron--open')}
        />
      </button>

      <div className={cn('screener-filters-body', !isExpanded && 'screener-filters-body--collapsed')}>
        <div className="filter-inputs">
          {children}
        </div>
        <div className="filter-actions">
          <button className="filter-btn secondary" onClick={onReset}>Reset</button>
          <button className="filter-btn primary" onClick={onApply}>Apply</button>
        </div>
      </div>

      {filterCount > 0 && (
        <div className="active-filter-chips">
          {activeFilters!.map((f) => (
            <span key={f.key} className="filter-chip">
              {f.label}: {f.value}
              <button
                className="filter-chip-remove"
                onClick={() => onRemoveFilter?.(f.key)}
              >
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

interface FilterInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function FilterInput({ label, value, onChange, placeholder }: FilterInputProps) {
  return (
    <div className="filter-field">
      <label className="filter-label">{label}</label>
      <input
        type="text"
        className="filter-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </div>
  );
}

interface FilterSelectProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}

export function FilterSelect({ label, value, onChange, options }: FilterSelectProps) {
  return (
    <div className="filter-field">
      <label className="filter-label">{label}</label>
      <select
        className="filter-select"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}
