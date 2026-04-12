import { Sun, Moon } from 'lucide-react'
import { useThemeStore } from '@/stores/themeStore'
import './ThemeToggle.css'

export function ThemeToggle() {
  const { theme, toggleTheme } = useThemeStore()

  return (
    <button
      onClick={toggleTheme}
      className="theme-toggle-btn"
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
    >
      {theme === 'dark' ? <Sun style={{ height: '1rem', width: '1rem' }} /> : <Moon style={{ height: '1rem', width: '1rem' }} />}
    </button>
  )
}
