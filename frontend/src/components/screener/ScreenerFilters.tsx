import { ReactNode } from 'react';
import './ScreenerFilters.css';

interface ScreenerFiltersProps {
  children: ReactNode;
  onApply: () => void;
  onReset: () => void;
}

export function ScreenerFilters({ children, onApply, onReset }: ScreenerFiltersProps) {
  return (
    <div className="screener-filters">
      <div className="filter-inputs">
        {children}
      </div>
      <div className="filter-actions">
        <button className="filter-btn secondary" onClick={onReset}>Reset</button>
        <button className="filter-btn primary" onClick={onApply}>Apply</button>
      </div>
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
