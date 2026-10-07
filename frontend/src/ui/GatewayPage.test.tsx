import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { GatewayPage } from './App'
import { api } from './api'
import { translate } from './i18n'

vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

const status = {
  state: 'CONNECTED', enabled: true, transportConnected: true, detail: 'connected',
  capabilities: { qrLogin: false, logout: false, reconnect: false, accountLookup: true },
  config: { httpUrl: 'http://127.0.0.1:3000', wsUrl: 'ws://127.0.0.1:3001', enabled: true,
    httpTokenConfigured: true, wsTokenConfigured: true, legacyTokenConfigured: false },
}

describe('OneBot independent token controls', () => {
  it('sends explicit HTTP transport and leaves the WS field independent', async () => {
    vi.mocked(api).mockImplementation(async path => path === '/api/gateway/status' ? status as never : {} as never)
    render(<GatewayPage language="zh-CN" t={key => translate('zh-CN', key)} />)
    const httpSection = (await screen.findByRole('heading', { name: 'OneBot HTTP 令牌' })).closest('section')!
    const wsInput = screen.getByLabelText('新令牌', { selector: '#onebot-ws-token' })
    fireEvent.change(screen.getByLabelText('新令牌', { selector: '#onebot-http-token' }), { target: { value: 'http-fixture' } })
    fireEvent.change(wsInput, { target: { value: 'ws-fixture' } })
    fireEvent.click(within(httpSection).getByRole('button', { name: '保存设置' }))
    await waitFor(() => expect(api).toHaveBeenCalledWith('/api/gateway/secret', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ transport: 'http', action: 'replace', token: 'http-fixture' }),
    })))
    expect((wsInput as HTMLInputElement).value).toBe('ws-fixture')
    expect(await screen.findByText('HTTP 令牌已保存，WebSocket 令牌未修改。')).toBeInTheDocument()
  })

  it('sends explicit WS transport', async () => {
    vi.mocked(api).mockImplementation(async path => path === '/api/gateway/status' ? status as never : {} as never)
    render(<GatewayPage language="zh-CN" t={key => translate('zh-CN', key)} />)
    const wsSection = (await screen.findByRole('heading', { name: 'OneBot WebSocket 令牌' })).closest('section')!
    fireEvent.change(screen.getByLabelText('新令牌', { selector: '#onebot-ws-token' }), { target: { value: 'ws-fixture' } })
    fireEvent.click(within(wsSection).getByRole('button', { name: '保存设置' }))
    await waitFor(() => expect(api).toHaveBeenCalledWith('/api/gateway/secret', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ transport: 'ws', action: 'replace', token: 'ws-fixture' }),
    })))
  })

  it('renders credential probe outcomes in plain Chinese', async () => {
    vi.mocked(api).mockImplementation(async path => {
      if (path === '/api/gateway/status') return status as never
      return { status: 'CREDENTIAL_REJECTED' } as never
    })
    render(<GatewayPage language="zh-CN" t={key => translate('zh-CN', key)} />)
    const section = (await screen.findByRole('heading', { name: 'OneBot HTTP 令牌' })).closest('section')!
    fireEvent.click(within(section).getByRole('button', { name: '检查连接' }))
    expect(await within(section).findByText('HTTP 检查结果: 访问令牌不正确（认证失败）')).toBeInTheDocument()
  })
})
