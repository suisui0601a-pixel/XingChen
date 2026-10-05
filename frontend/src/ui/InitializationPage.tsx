import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import type { Language } from './i18n'

export function InitializationPage({ language }: { language: Language }) {
  const zh = language === 'zh-CN'
  const [state, setState] = useState<'checking' | 'ready' | 'closed' | 'done'>('checking')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  useEffect(() => {
    let live = true
    api<{ initializationRequired: boolean }>('/api/auth/initialize')
      .then(result => { if (live) setState(result.initializationRequired ? 'ready' : 'closed') })
      .catch(() => { if (live) setState('closed') })
    return () => { live = false }
  }, [])
  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (busy) return
    setError('')
    if (!username.trim() || username.trim().length > 100 || password.length < 14
      || new TextEncoder().encode(password).length > 72 || new Set(password).size < 4 || password !== confirmation) {
      setError(zh ? '请输入有效账号，并确认至少 14 字符、至多 72 UTF-8 字节的高强度密码。' : 'Enter a valid account and confirm a strong password of at least 14 characters and at most 72 UTF-8 bytes.')
      return
    }
    setBusy(true)
    try {
      await api('/api/auth/initialize', { method: 'POST', body: JSON.stringify({ username, password, confirmation }) })
      setState('done')
    } catch {
      // Never expose a submitted credential or driver error. Failed submissions require manual re-entry.
      setError(zh ? '初始化未完成。请重新加载确认入口状态，不要重复提交。' : 'Initialization did not complete. Reload to check availability before submitting again.')
    } finally { setPassword(''); setConfirmation(''); setBusy(false) }
  }
  return <main className="login-page"><section className="login-panel">
    <h1>{zh ? '首次管理员初始化' : 'First administrator initialization'}</h1>
    <p>{zh ? '仅通过本机 SSH 隧道访问。初始化成功后入口关闭，不会自动登录。' : 'Use only through a local SSH tunnel. The endpoint closes after initialization; no session is granted.'}</p>
    {state === 'checking' && <p role="status">{zh ? '正在检查…' : 'Checking…'}</p>}
    {state === 'closed' && <p role="status">{zh ? '初始化入口未开放或已完成初始化。请使用登录页。' : 'Initialization is unavailable or already completed. Use the login page.'}</p>}
    {state === 'done' && <p role="status">{zh ? '管理员初始化已完成。请告知部署执行者，然后等待继续验证。' : 'Administrator initialized. Notify your deployment operator before proceeding.'}</p>}
    {state === 'ready' && <form onSubmit={submit}>
      <label htmlFor="init-username">{zh ? '管理员账号' : 'Administrator account'}</label>
      <input id="init-username" autoComplete="username" maxLength={100} value={username} onChange={event => setUsername(event.target.value)} required disabled={busy} />
      <label htmlFor="init-password">{zh ? '密码' : 'Password'}</label>
      <input id="init-password" type="password" autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} required disabled={busy} />
      <label htmlFor="init-confirmation">{zh ? '确认密码' : 'Confirm password'}</label>
      <input id="init-confirmation" type="password" autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)} required disabled={busy} />
      {error && <p role="alert">{error}</p>}
      <button className="button primary full" type="submit" disabled={busy}>{zh ? '创建首位管理员' : 'Create first administrator'}</button>
    </form>}
    {state !== 'ready' && state !== 'checking' && <Link to="/login">{zh ? '登录页' : 'Login page'}</Link>}
  </section></main>
}
