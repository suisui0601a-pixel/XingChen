export type Theme = 'light' | 'dark' | 'late' | 'system'
export function loadTheme(): Theme {
  const value = localStorage.getItem('xingchen.theme')
  return value === 'dark' || value === 'late' || value === 'system' ? value : 'light'
}
export function effectiveTheme(theme: Theme, prefersDark = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: dark)').matches): Exclude<Theme, 'system'> {
  return theme === 'system' ? (prefersDark ? 'dark' : 'light') : theme
}
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = effectiveTheme(theme)
  localStorage.setItem('xingchen.theme', theme)
}
