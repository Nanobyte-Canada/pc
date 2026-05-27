import { useThemeStore } from '@/stores/themeStore'

export function useAgGridTheme(): string {
  const theme = useThemeStore((s) => s.theme)
  return theme === 'dark' ? 'ag-theme-quartz-dark' : 'ag-theme-quartz'
}
