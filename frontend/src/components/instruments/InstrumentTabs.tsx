import { useState } from 'react';
import { InstrumentType } from '../../types/instrument';
import { InstrumentSearchAutocomplete } from './InstrumentSearchAutocomplete';
import './InstrumentTabs.css';

type TabType = 'all' | InstrumentType;

interface Tab {
  id: TabType;
  label: string;
}

const TABS: Tab[] = [
  { id: 'all', label: 'All' },
  { id: 'STOCK', label: 'Stocks' },
  { id: 'ETF', label: 'ETFs' },
];

export function InstrumentTabs() {
  const [activeTab, setActiveTab] = useState<TabType>('all');

  return (
    <div className="instrument-tabs">
      <div className="tabs-header">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="tabs-content">
        <InstrumentSearchAutocomplete filterType={activeTab} />
      </div>
    </div>
  );
}
