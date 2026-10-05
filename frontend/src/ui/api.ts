export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly traceId?: string) { super(message) }
}

let csrfTokenInMemory: string | undefined
let csrfRequest: Promise<string> | undefined

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))?.slice(name.length + 1)
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 12_000)
  const signal = options.signal ? AbortSignal.any([controller.signal, options.signal]) : controller.signal
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  try {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const token = await getCsrfToken(signal)
      headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
    }
    signal.throwIfAborted()
    const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin', signal })
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string; traceId?: string; token?: string }
    if (path.endsWith('/auth/csrf') && body.token) csrfTokenInMemory = body.token
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) csrfTokenInMemory = undefined
      if (response.status === 401 && !path.endsWith('/auth/login') && !path.endsWith('/auth/session') && !path.endsWith('/auth/csrf')) window.dispatchEvent(new Event('xingchen:unauthorized'))
      throw new ApiError(response.status, body.code ?? 'REQUEST_FAILED', body.message ?? 'Request failed', body.traceId ?? response.headers.get('X-Trace-Id') ?? undefined)
    }
    if (path.endsWith('/auth/logout')) csrfTokenInMemory = undefined
    return body as T
  } finally { window.clearTimeout(timeout) }
}

async function getCsrfToken(signal: AbortSignal): Promise<string> {
  const value = cookie('XSRF-TOKEN') ?? csrfTokenInMemory
  if (value) return value
  if (!csrfRequest) csrfRequest = (async () => {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal, headers: { Accept: 'application/json' } })
    if (!response.ok) throw new ApiError(response.status, 'CSRF_TOKEN_UNAVAILABLE', 'Could not retrieve a request token')
    const body = await response.json() as { token?: string }
    if (!body.token) throw new ApiError(403, 'CSRF_TOKEN_UNAVAILABLE', 'Could not retrieve a request token')
    csrfTokenInMemory = body.token
    return body.token
  })().finally(() => { csrfRequest = undefined })
  // Concurrent discovery is shared; cancellation never retries a mutation.
  return new Promise<string>((resolve, reject) => {
    const abort = () => { cleanup(); reject(signal.reason ?? new DOMException('Request aborted', 'AbortError')) }
    const cleanup = () => signal.removeEventListener('abort', abort)
    if (signal.aborted) { abort(); return }
    signal.addEventListener('abort', abort, { once: true })
    csrfRequest!.then(value => { cleanup(); resolve(value) }, error => { cleanup(); reject(error) })
  })
}

export function apiErrorText(error: unknown, language: 'zh-CN' | 'en-US'): string {
  const messages: Record<number, [string, string]> = {
    400: ['输入无效，请检查字段与范围。', 'Invalid input. Check the fields and ranges.'],
    401: ['会话已失效，请重新登录。', 'Your session expired. Sign in again.'],
    403: ['请求校验失败或操作不被允许，请重新加载后再试。', 'Request verification failed or this operation is forbidden. Reload before retrying.'],
    404: ['请求的记录不存在。', 'The requested record was not found.'],
    409: ['记录已在其他位置更新，请刷新后重试。', 'The record changed elsewhere. Refresh before retrying.'],
    429: ['请求过于频繁，请稍后重试。', 'Too many requests. Try again later.'],
    500: ['服务暂时无法完成请求，请稍后重试。', 'The service could not complete the request. Try again later.'],
  }
  const pair = error instanceof ApiError ? messages[error.status] : undefined
  return (pair ?? ['请求未能完成，请检查连接后重试。', 'The request could not be completed. Check the connection and retry.'])[language === 'zh-CN' ? 0 : 1]
}

export async function login(username: string, password: string): Promise<void> {
  await api('/api/auth/csrf')
  await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
}
