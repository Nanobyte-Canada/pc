import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import './Pagination.css';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

function getPageNumbers(current: number, total: number): (number | 'ellipsis')[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i);
  }

  const pages: (number | 'ellipsis')[] = [0];

  const windowStart = Math.max(2, current - 1);
  const windowEnd = Math.min(total - 3, current + 1);

  if (windowStart > 1) {
    pages.push('ellipsis');
  }

  for (let i = windowStart; i <= windowEnd; i++) {
    pages.push(i);
  }

  if (windowEnd < total - 2) {
    pages.push('ellipsis');
  }

  pages.push(total - 1);

  return pages;
}

export function Pagination({ currentPage, totalPages, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) return null;

  const pages = getPageNumbers(currentPage, totalPages);

  return (
    <div className={cn('pagination', className)}>
      <button
        className="pagination-btn"
        disabled={currentPage === 0}
        onClick={() => onPageChange(0)}
        aria-label="First page"
      >
        <ChevronsLeft size={14} />
      </button>

      <button
        className="pagination-btn"
        disabled={currentPage === 0}
        onClick={() => onPageChange(currentPage - 1)}
        aria-label="Previous page"
      >
        <ChevronLeft size={14} />
      </button>

      {pages.map((p, idx) =>
        p === 'ellipsis' ? (
          <span key={`ellipsis-${idx}`} className="pagination-ellipsis">...</span>
        ) : (
          <button
            key={p}
            className={cn('pagination-btn', 'pagination-page', currentPage === p && 'pagination-active')}
            onClick={() => onPageChange(p)}
          >
            {p + 1}
          </button>
        )
      )}

      <button
        className="pagination-btn"
        disabled={currentPage >= totalPages - 1}
        onClick={() => onPageChange(currentPage + 1)}
        aria-label="Next page"
      >
        <ChevronRight size={14} />
      </button>

      <button
        className="pagination-btn"
        disabled={currentPage >= totalPages - 1}
        onClick={() => onPageChange(totalPages - 1)}
        aria-label="Last page"
      >
        <ChevronsRight size={14} />
      </button>
    </div>
  );
}
