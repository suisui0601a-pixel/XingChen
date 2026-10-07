import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SocialSettingsPage } from './SocialSettingsPage'
import { api } from './api'

vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

const values = { mentionWake:true, replyToBotWake:true, pokeWake:true, botNameWake:true, botNameAliases:[], questionWake:true,
  configuredSpeakerWake:true, configuredSpeakerIds:[], ordinaryMessageProbability:0.2, maxReplyMessages:8, maxPerMinute:30, gapMillis:0 }

describe('wake probability display and storage contract', () => {
  it('shows stored 0.2 as 20% and sends 25% back as 0.25', async () => {
    vi.mocked(api).mockImplementation(async (_path, options) => {
      if (options?.method === 'PATCH') return { scope:'GLOBAL', revision:1, values:{...values, ordinaryMessageProbability:0.25} } as never
      return { scope:'GLOBAL', revision:0, values } as never
    })
    render(<MemoryRouter><SocialSettingsPage language="zh-CN" /></MemoryRouter>)
    expect((await screen.findAllByDisplayValue('20'))).toHaveLength(2)
    expect(screen.getByText(/普通消息有 20% 概率参与/)).toBeInTheDocument()
    const probabilityInputs = screen.getAllByLabelText('随机插话概率（ordinaryMessageProbability）')
    fireEvent.change(probabilityInputs[1], { target: { value:'25' } })
    fireEvent.click(screen.getByRole('button', { name:'保存全局设置' }))
    await waitFor(() => expect(api).toHaveBeenCalledWith('/api/social-settings/global', expect.objectContaining({
      method:'PATCH', body:expect.stringContaining('"ordinaryMessageProbability":0.25'),
    })))
  })
})
