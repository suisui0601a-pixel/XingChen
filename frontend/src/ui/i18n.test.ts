import { describe, expect, it } from 'vitest'
import { translate } from './i18n'

describe('locale catalog', () => {
  it('has matching primary navigation labels in both locales', () => {
    for (const key of ['overview', 'settings', 'gateway', 'memory', 'notFound'] as const) {
      expect(translate('zh-CN', key)).not.toBe(translate('en-US', key))
    }
  })
  it('loads the persisted locale and defaults safely', async () => {
    const { loadLanguage } = await import('./i18n')
    expect(loadLanguage()).toBe('zh-CN')
    localStorage.setItem('xingchen.language', 'en-US')
    expect(loadLanguage()).toBe('en-US')
    localStorage.setItem('xingchen.language', 'invalid')
    expect(loadLanguage()).toBe('zh-CN')
  })
})
