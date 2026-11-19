// Regression for the independently reproduced audit counterexample.
import {afterEach,expect,it,vi} from 'vitest'
import {act,cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react'
import {GatewayPage} from './App'
import {api} from './api'
import {translate} from './i18n'
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}))
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.resetAllMocks()})
it('polling refreshes gateway status without overwriting an unsaved endpoint draft',async()=>{
 const status={state:'CONNECTED',enabled:true,transportConnected:true,detail:'fixture',capabilities:{qrLogin:false,logout:false,reconnect:false,accountLookup:true},config:{httpUrl:'http://127.0.0.1:3000',wsUrl:'ws://127.0.0.1:3001',enabled:true,httpTokenConfigured:true,wsTokenConfigured:true,legacyTokenConfigured:false}}
 vi.mocked(api).mockResolvedValue(status as never)
 vi.spyOn(document,'visibilityState','get').mockReturnValue('visible')
 let tick:()=>void=()=>{throw Error('interval not installed')}
 const realInterval=window.setInterval.bind(window)
 vi.spyOn(window,'setInterval').mockImplementation((handler,delay,...args)=>{
  if(delay===4000){tick=()=>{if(typeof handler==='function')handler()};return 123}
  return realInterval(handler,delay,...args)
 })
 render(<GatewayPage language="zh-CN" t={key=>translate('zh-CN',key)}/> )
 const input=await screen.findByDisplayValue(status.config.httpUrl) as HTMLInputElement
 fireEvent.change(input,{target:{value:'http://unsaved.invalid:3000'}})
 expect(input.value).toBe('http://unsaved.invalid:3000')
 await act(async()=>tick())
 expect(api).toHaveBeenCalledTimes(2)
 expect(input.value).toBe('http://unsaved.invalid:3000')
 expect(api).not.toHaveBeenCalledWith('/api/gateway/config',expect.anything())
})

it('preserves a draft and blocks saving when the server configuration changed elsewhere',async()=>{
 const status={state:'CONNECTED',transportConnected:true,capabilities:{},config:{httpUrl:'http://127.0.0.1:3000',wsUrl:'ws://127.0.0.1:3001',enabled:true,httpTokenConfigured:false,wsTokenConfigured:false}}
 const changed={...status,config:{...status.config,wsUrl:'ws://127.0.0.1:3011'}}
 vi.mocked(api).mockResolvedValue(status as never)
 render(<GatewayPage language="zh-CN" t={key=>translate('zh-CN',key)}/> )
 const input=await screen.findByDisplayValue(status.config.httpUrl) as HTMLInputElement
 fireEvent.change(input,{target:{value:'http://unsaved.invalid:3000'}})
 vi.mocked(api).mockResolvedValue(changed as never)
 fireEvent.click(screen.getByRole('button',{name:translate('zh-CN','retry')}))
 expect(await screen.findByRole('alert')).toHaveTextContent('当前草稿已保留')
 expect(input.value).toBe('http://unsaved.invalid:3000')
 expect(within(input.closest('form')!).getByRole('button',{name:translate('zh-CN','save')})).toBeDisabled()
 fireEvent.click(screen.getByRole('button',{name:'加载服务端配置（放弃草稿）'}))
 await waitFor(()=>expect(input.value).toBe(status.config.httpUrl))
 expect(screen.getByDisplayValue(changed.config.wsUrl)).toBeInTheDocument()
 expect(within(input.closest('form')!).getByRole('button',{name:translate('zh-CN','save')})).toBeEnabled()
})

it('aborts an outstanding gateway status request when leaving the page',async()=>{
 vi.mocked(api).mockImplementation(()=>new Promise(()=>{}))
 const view=render(<GatewayPage language="zh-CN" t={key=>translate('zh-CN',key)}/> )
 const signal=vi.mocked(api).mock.calls[0][1]?.signal
 expect(signal?.aborted).toBe(false)
 view.unmount()
 expect(signal?.aborted).toBe(true)
})

it('does not start a new status subscription when a save finishes after leaving the page',async()=>{
 const status={state:'CONNECTED',transportConnected:true,capabilities:{},config:{httpUrl:'http://127.0.0.1:3000',wsUrl:'ws://127.0.0.1:3001',enabled:true}}
 let finishSave:(value:unknown)=>void=()=>{}
 vi.mocked(api).mockImplementation(path=>path==='/api/gateway/config'?new Promise(resolve=>{finishSave=resolve}):Promise.resolve(status as never))
 const view=render(<GatewayPage language="zh-CN" t={key=>translate('zh-CN',key)}/> )
 const input=await screen.findByDisplayValue(status.config.httpUrl)
 fireEvent.submit(input.closest('form')!)
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/gateway/config',expect.anything()))
 view.unmount()
 await act(async()=>finishSave({saved:true}))
 expect(vi.mocked(api).mock.calls.filter(([path])=>path==='/api/gateway/status')).toHaveLength(1)
})
