import { afterEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SlangPage } from './SlangPage'
import { api } from './api'
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }))
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.resetAllMocks() })

it('reloads the current filter when a moderation mutation completes after the filter changes', async () => {
  const entry={id:'synthetic',term:'Synthetic term',meaning:'fixture',status:'CANDIDATE',source:'ADMIN_MANUAL',sources:[],evidenceCount:0,evidencePreview:[],createdAt:'2026-01-01',updatedAt:'2026-01-01'}
  let committed=false
  let finish:()=>void=()=>{}
  vi.mocked(api).mockImplementation((path,options)=>{
    if(options?.method==='PATCH')return new Promise(resolve=>{finish=()=>{committed=true;resolve({} as never)}})
    const status=new URL(String(path),'http://localhost').searchParams.get('status')
    const items=status==='REJECTED'?(committed?[{...entry,status:'REJECTED'}]:[]):committed?[]:[entry]
    return Promise.resolve({items,total:items.length,page:0,pageSize:20,webResearch:'disabled'} as never)
  })
  vi.spyOn(window,'confirm').mockReturnValue(true)
  render(<SlangPage language="zh-CN" />)
  await screen.findByRole('row',{name:/Synthetic term/})
  fireEvent.change(screen.getByLabelText('状态'),{target:{value:'CANDIDATE'}})
  await waitFor(()=>expect(vi.mocked(api).mock.calls.some(([path])=>String(path).includes('status=CANDIDATE'))).toBe(true))
  fireEvent.click(screen.getByRole('button',{name:'拒绝'}))
  fireEvent.change(screen.getByLabelText('状态'),{target:{value:'REJECTED'}})
  await waitFor(()=>expect(vi.mocked(api).mock.calls.some(([path])=>String(path).includes('status=REJECTED'))).toBe(true))
  await act(async()=>finish())
  expect(await screen.findByRole('row',{name:/Synthetic term/})).toHaveTextContent('已拒绝')
  expect(screen.getByLabelText('状态')).toHaveValue('REJECTED')
})
