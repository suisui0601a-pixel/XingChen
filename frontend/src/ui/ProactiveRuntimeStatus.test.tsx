import {afterEach,expect,it,vi} from 'vitest'
import {act,cleanup,render,screen} from '@testing-library/react'
import {api} from './api'
import {ProactiveRuntimeStatus} from './ProactiveRuntimeStatus'
vi.mock('./api',()=>({api:vi.fn()}))
afterEach(()=>{cleanup();vi.useRealTimers();vi.resetAllMocks()})
const status={running:true,inFlight:0,globalHourlyTurnLimit:4,history:{outcomes:[{status:'UNKNOWN',count:1}]}}
it('does not present an UNKNOWN outcome as delivered',async()=>{
  vi.mocked(api).mockResolvedValue(status)
  render(<ProactiveRuntimeStatus zh/>);expect(await screen.findByText('发送结果未知: 1')).toBeInTheDocument()
  expect(screen.queryByText('已发送: 1')).not.toBeInTheDocument()
})
it('releases polling and the active request when unmounted',async()=>{
  vi.useFakeTimers();vi.mocked(api).mockResolvedValue(status)
  const view=render(<ProactiveRuntimeStatus zh/>);await act(async()=>{await Promise.resolve()})
  expect(api).toHaveBeenCalledTimes(1)
  await act(async()=>{await vi.advanceTimersByTimeAsync(30000)})
  expect(api).toHaveBeenCalledTimes(2)
  const signal=vi.mocked(api).mock.calls[1][1]!.signal!
  view.unmount();expect(signal.aborted).toBe(true)
  await act(async()=>{await vi.advanceTimersByTimeAsync(60000)})
  expect(api).toHaveBeenCalledTimes(2)
})
it('shows an unavailable state when authoritative diagnostics fail',async()=>{
  vi.mocked(api).mockRejectedValue(new Error('synthetic failure'))
  render(<ProactiveRuntimeStatus zh/>);expect(await screen.findByText(/调度状态暂时无法读取/)).toBeInTheDocument()
})
