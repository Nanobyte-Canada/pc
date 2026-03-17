import { useThemeStore } from '@/stores/themeStore'

export function useChartTheme() {
  const theme = useThemeStore((s) => s.theme)

  return {
    theme: theme === 'dark' ? ('ag-default-dark' as const) : ('ag-default' as const),
    overrides: {
      common: {
        background: { fill: 'transparent' },
      },
    },
  }
}
