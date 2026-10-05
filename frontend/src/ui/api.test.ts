import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, apiErrorText } from './api'

afterEach(() => { vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })

describe('authenticated API client', () => {
  it('redirect signal is emitted on protected 401 responses', async () => {
    const listener = vi.fn()
    window.addEventListener('xingchen:unauthorized', listener)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', message: 'Please sign in' }), { status: 401, headers: { 'Content-Type': 'application/json' } })))
    await expect(api('/api/console/overview')).rejects.toBeInstanceOf(ApiError)
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('xingchen:unauthorized', listener)
  })
  it('sends same-origin credentials and CSRF header for mutations', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await api('/api/console/config/Console/publicBaseUrl', { method: 'PUT', body: JSON.stringify({ value: '' }) })
    const options = fetchMock.mock.calls[0][1] as RequestInit
    expect(options.credentials).toBe('same-origin')
    expect(new Headers(options.headers).get('X-XSRF-TOKEN')).toBe('abc123')
  })
  it('CSRF discovery failure clears the timeout and never sends the mutation', async () => {
    const clear = vi.spyOn(window,'clearTimeout')
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'))
    vi.stubGlobal('fetch',fetchMock)
    await expect(api('/api/access/rules',{method:'PATCH'})).rejects.toBeInstanceOf(TypeError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    expect(clear).toHaveBeenCalledOnce()
  })
  it('missing discovery token fails closed and allows a later fresh discovery',async()=>{
    const fetchMock=vi.fn().mockImplementation(()=>Promise.resolve(new Response('{}',{status:200})))
    vi.stubGlobal('fetch',fetchMock)
    await expect(api('/api/access/rules',{method:'PATCH'})).rejects.toMatchObject({status:403})
    await expect(api('/api/access/rules',{method:'PATCH'})).rejects.toMatchObject({status:403})
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
  it('a cancelled second caller shares discovery but never submits its mutation',async()=>{
    let resolveDiscovery:(r:Response)=>void=()=>{}
    const fetchMock=vi.fn().mockImplementationOnce(()=>new Promise(resolve=>{resolveDiscovery=resolve})).mockResolvedValue(new Response('{}',{status:200}))
    vi.stubGlobal('fetch',fetchMock)
    const first=api('/api/access/rules',{method:'PATCH'})
    const controller=new AbortController()
    const second=api('/api/pricing',{method:'PATCH',signal:controller.signal})
    controller.abort()
    await expect(second).rejects.toMatchObject({name:'AbortError'})
    resolveDiscovery(new Response('{"token":"synthetic-csrf"}',{status:200}))
    await first
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.some(call=>call[0]==='/api/pricing')).toBe(false)
    // Invalidate the cache without replaying a forbidden mutation.
    fetchMock.mockResolvedValueOnce(new Response('{}',{status:403}))
    await expect(api('/api/access/rules',{method:'PATCH'})).rejects.toMatchObject({status:403})
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })
  it('timeout aborts token discovery, cleans timers and never submits the mutation',async()=>{
    vi.useFakeTimers()
    try{
      const fetchMock=vi.fn().mockImplementation((_path:string,options:RequestInit)=>new Promise((_resolve,reject)=>options.signal!.addEventListener('abort',()=>reject(options.signal!.reason),{once:true})))
      vi.stubGlobal('fetch',fetchMock)
      const pending=expect(api('/api/pricing',{method:'PATCH'})).rejects.toMatchObject({name:'AbortError'})
      await vi.advanceTimersByTimeAsync(12_000)
      await pending
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(vi.getTimerCount()).toBe(0)
    }finally{vi.useRealTimers()}
  })
  it('safe errors distinguish auth, CSRF, conflict and throttling without exposing messages',()=>{
    for(const status of [400,401,403,404,409,429,500]){
      expect(apiErrorText(new ApiError(status,'TEST','secret internal content'),'zh-CN')).not.toContain('secret')
      expect(apiErrorText(new ApiError(status,'TEST','secret internal content'),'en-US')).not.toContain('secret')
    }
    expect(apiErrorText(new ApiError(401,'TEST',''),'en-US')).toContain('session expired')
    expect(apiErrorText(new ApiError(403,'TEST',''),'en-US')).toContain('verification failed')
    expect(apiErrorText(new ApiError(409,'TEST',''),'en-US')).toContain('changed elsewhere')
    expect(apiErrorText(new ApiError(429,'TEST',''),'en-US')).toContain('Too many')
  })
})
