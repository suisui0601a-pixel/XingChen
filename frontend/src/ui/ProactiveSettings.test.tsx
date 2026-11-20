import {afterEach,expect,it,vi} from 'vitest'
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react'
import {MemoryRouter} from 'react-router-dom'
import {ProactiveSettings,proactiveDefaults} from './ProactiveSettings'
import {SocialSettingsPage} from './SocialSettingsPage'
import {api} from './api'
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}))
afterEach(()=>{cleanup();vi.resetAllMocks()})
const values={...proactiveDefaults,mentionWake:true,replyToBotWake:true,pokeWake:true,botNameWake:true,botNameAliases:[],questionWake:true,configuredSpeakerWake:true,configuredSpeakerIds:[],ordinaryMessageProbability:0.2,maxReplyMessages:8,maxPerMinute:30,gapMillis:0}

it('starts disabled and keeps proactive sampling independent of ordinary inbound probability',async()=>{
  vi.mocked(api).mockImplementation(async(_path,options)=>options?.method==='PATCH'?{revision:1,values} as never:{revision:0,values} as never)
  render(<MemoryRouter><SocialSettingsPage language="zh-CN"/></MemoryRouter>)
  const privateSwitch=await screen.findByRole('checkbox',{name:'允许私聊主动冒泡'})
  expect(privateSwitch).not.toBeChecked();expect(screen.getByRole('checkbox',{name:'允许群聊主动冒泡'})).not.toBeChecked()
  fireEvent.click(privateSwitch)
  fireEvent.change(screen.getByLabelText('每次检查的主动冒泡概率（%）'),{target:{value:'35'}})
  fireEvent.click(screen.getByRole('button',{name:'保存主动冒泡设置'}))
  await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/social-settings/global',expect.objectContaining({method:'PATCH'})))
  const call=vi.mocked(api).mock.calls.find(([,o])=>o?.method==='PATCH')!
  const body=JSON.parse(String(call[1]!.body))
  expect(body.values).toMatchObject({ordinaryMessageProbability:0.2,proactiveProbability:0.35,proactivePrivateEnabled:true,proactiveGroupEnabled:false})
})
it('clearing a conversation override sends null and shows inheritance',()=>{
  const change=vi.fn()
  render(<ProactiveSettings values={values} effective={values} overrides={{proactiveProbability:0.5}} onChange={change} zh/>)
  const override=screen.getByRole('checkbox',{name:'每次检查的主动冒泡概率（%） 覆盖'})
  expect(override).toBeChecked();fireEvent.click(override);expect(change).toHaveBeenCalledWith('proactiveProbability',null)
})
it('null overrides are inherited and do not turn false into an enabled flag',()=>{
  render(<ProactiveSettings values={values} effective={values} overrides={{proactivePrivateEnabled:null}} onChange={vi.fn()} zh/>)
  expect(screen.getByRole('checkbox',{name:'允许私聊主动冒泡 覆盖'})).not.toBeChecked()
  expect(screen.getByRole('checkbox',{name:'允许私聊主动冒泡'})).not.toBeChecked()
  expect(screen.getByRole('checkbox',{name:'允许私聊主动冒泡'})).toBeDisabled()
})
it('English guidance does not claim the timer is unavailable',()=>{
  render(<ProactiveSettings values={values} onChange={vi.fn()} zh={false}/>)
  expect(screen.getByText('Idle proactive participation')).toBeInTheDocument()
  expect(screen.getByText(/four model attempts per hour/)).toBeInTheDocument()
})
