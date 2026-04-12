import { useNavigate } from 'react-router-dom';
import { INSTRUMENT_TYPE_CONFIG } from '@/types/screener';
import { cn } from '@/lib/utils';
import './ScreenerSidebar.css';

const TYPE_ORDER = ['STOCK', 'ETF', 'MUTUAL_FUND', 'PREFERRED_STOCK', 'INDEX', 'BOND'] as const;

interface ScreenerSidebarProps {
  activeType: string;
  counts: Record<string, number> | undefined;
  collapsed: boolean;
}

export function ScreenerSidebar({ activeType, counts, collapsed }: ScreenerSidebarProps) {
  const navigate = useNavigate();

  return (
    <div className={cn('screener-sidebar', collapsed && 'screener-sidebar--collapsed')}>
      <div className="screener-sidebar__header">
        {collapsed ? 'Types' : 'Instrument Types'}
      </div>
      <ul className="screener-sidebar__list">
        {TYPE_ORDER.map((typeKey) => {
          const config = INSTRUMENT_TYPE_CONFIG[typeKey];
          if (!config) return null;

          const isActive = activeType === config.route;
          const count = counts?.[typeKey];

          return (
            <li
              key={typeKey}
              className={cn(
                'screener-sidebar__item',
                isActive && 'screener-sidebar__item--active'
              )}
              onClick={() => navigate(`/screener/${config.route}`)}
            >
              <span className="screener-sidebar__label">
                {collapsed ? config.label : config.pluralLabel}
              </span>
              {count !== undefined && (
                <span className="screener-sidebar__count">
                  {count.toLocaleString()}
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
