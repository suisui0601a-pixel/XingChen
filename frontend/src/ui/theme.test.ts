import { afterEach, describe, expect, it } from 'vitest'
import { applyTheme, effectiveTheme, loadTheme } from './theme'

afterEach(() => localStorage.clear())

describe('theme preference', () => {
  it('persists all explicit themes locally', () => {
    applyTheme('late')
    expect(loadTheme()).toBe('late')
    expect(document.documentElement.dataset.theme).toBe('late')
  })
  it('maps System only to light or dark', () => {
    expect(effectiveTheme('system', false)).toBe('light')
    expect(effectiveTheme('system', true)).toBe('dark')
    expect(effectiveTheme('late', true)).toBe('late')
  })
})
