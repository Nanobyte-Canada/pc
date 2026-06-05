import { useState, useCallback } from 'react'
import './UnderlyingSearch.css'

interface UnderlyingSearchProps {
  onSearch: (symbol: string) => void
  isLoading?: boolean
}

export function UnderlyingSearch({ onSearch, isLoading }: UnderlyingSearchProps) {
  const [input, setInput] = useState('')

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const symbol = input.trim().toUpperCase()
      if (symbol) onSearch(symbol)
    },
    [input, onSearch]
  )

  return (
    <form className="underlying-search" onSubmit={handleSubmit}>
      <input
        className="underlying-search__input"
        type="text"
        placeholder="Enter symbol (e.g. SPY)"
        value={input}
        onChange={(e) => setInput(e.target.value)}
      />
      <button className="underlying-search__button" type="submit" disabled={isLoading || !input.trim()}>
        {isLoading ? 'Loading...' : 'Load Chain'}
      </button>
    </form>
  )
}
