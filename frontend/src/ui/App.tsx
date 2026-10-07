import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { api, ApiError, apiErrorText, login } from './api'
import { loadLanguage, translate } from './i18n'
import type { Language, MessageKey } from './i18n'
import { applyTheme, loadTheme } from './theme'
import type { Theme } from './theme'
import { ConversationsPage } from './ConversationsPage'
import { PeopleMemoryPage } from './PeopleMemoryPage'
import { PromptPage } from './PromptPage'
import { SocialSettingsPage } from './SocialSettingsPage'
import { ModelsPage } from './ModelsPage'
import { StickersPage } from './StickersPage'
import { SlangPage } from './SlangPage'
import { VoicePage } from './VoicePage'
import { AccessPage, SecurityPage, LogsPage, OperationsPage } from './AdminOpsPages'
import { UsageConsolePage } from './UsageConsolePage'
import { InitializationPage } from './InitializationPage'

type Session = { authenticated: boolean; username: string }
type Overview = { status: string; version: string; runtimeEnabled: boolean; gateway: string; model: string; database: string; activeConversations: number; waitingConversations: number; outboundUnknown: number; interruptedTurns: number; externalIntegrationsEnabled: boolean }
type GatewayInfo = { state: string; enabled: boolean; transportConnected: boolean; detail: string; account?: { platform: string; userId: string; displayName: string }; capabilities: { qrLogin: boolean; logout: boolean; reconnect: boolean; accountLookup: boolean }; config: { httpUrl: string; wsUrl: string; managementUrl?: string; httpTokenConfigured: boolean; wsTokenConfigured: boolean; httpTokenExplicit: boolean; wsTokenExplicit: boolean; legacyTokenConfigured: boolean; enabled: boolean } }
const nav: { group: MessageKey; items: { path: string; key: MessageKey; active?: boolean }[] }[] = [
  { group: 'home', items: [{ path: '/', key: 'overview', active: true }] },
  { group: 'social', items: [{ path: '/conversations', key: 'conversations' }, { path: '/people', key: 'people' }, { path: '/relationships', key: 'relationships' }, { path: '/memory', key: 'memory' }] },
  { group: 'personality', items: [{ path: '/persona', key: 'persona' }, { path: '/simulation', key: 'simulation' }, { path: '/social-settings', key: 'socialSettings' }] },
  { group: 'capabilities', items: [{ path: '/stickers', key: 'stickers' }, { path: '/slang', key: 'slang' }, { path: '/voice', key: 'voice' }] },
  { group: 'platform', items: [{ path: '/gateway', key: 'gateway' }, { path: '/models', key: 'models' }] },
  { group: 'operations', items: [{ path: '/usage', key: 'usage' }, { path: '/access', key: 'access' }, { path: '/security', key: 'security' }, { path: '/logs', key: 'logs' }, { path: '/operations', key: 'operationsPage' }] },
]

export function App() {
  const [language, setLanguage] = useState<Language>(loadLanguage)
  const [theme, setTheme] = useState<Theme>(loadTheme)
  const [session, setSession] = useState<Session | null>(null)
  const [checking, setChecking] = useState(true)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [toast, setToast] = useState('')
  const t = useMemo(() => (key: MessageKey) => translate(language, key), [language])
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    applyTheme(theme)
    if (theme !== 'system' || typeof window.matchMedia !== 'function') return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const update = () => applyTheme('system')
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [theme])
  useEffect(() => { localStorage.setItem('xingchen.language', language); document.documentElement.lang = language }, [language])
  useEffect(() => {
    let live = true
    api<Session>('/api/auth/session').then((value) => { if (live) setSession(value) }).catch(() => { if (live) setSession({ authenticated: false, username: '' }) }).finally(() => { if (live) setChecking(false) })
    return () => { live = false }
  }, [location.pathname])
  useEffect(() => { const onUnauthorized = () => { setSession({ authenticated: false, username: '' }); navigate('/login', { replace: true }) }; window.addEventListener('xingchen:unauthorized', onUnauthorized); return () => window.removeEventListener('xingchen:unauthorized', onUnauthorized) }, [navigate])
  const changeLanguage = (next: Language) => { setLanguage(next); setToast(t('languageSaved')) }
  const changeTheme = (next: Theme) => { setTheme(next); setToast(t('themeSaved')) }
  const logout = async () => {
    try { await api('/api/auth/logout', { method: 'POST' }); setSession({ authenticated: false, username: '' }); navigate('/login', { replace: true }) }
    catch (error) { setToast(apiErrorText(error, language)) }
  }
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(''), 2800); return () => window.clearTimeout(timer) }, [toast])

  if (checking) return <div className="screen-center" role="status">{t('loading')}</div>
  return <>
    <Routes>
      <Route path="/initialize" element={<InitializationPage language={language} />} />
      <Route path="/login" element={session?.authenticated ? <Navigate to="/" replace /> : <LoginPage t={t} language={language} onLogin={(value) => { setSession(value); navigate('/', { replace: true }) }} />} />
      <Route element={session?.authenticated
        ? <Shell t={t} username={session.username} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} onLogout={logout} theme={theme} setTheme={changeTheme} language={language} setLanguage={changeLanguage} />
        : <Navigate to="/login" replace state={{ from: location.pathname }} />}>
        <Route path="/" element={<OverviewPage t={t} />} />
        <Route path="/settings" element={<SettingsPage t={t} theme={theme} setTheme={changeTheme} language={language} setLanguage={changeLanguage} />} />
        <Route path="/gateway" element={<GatewayPage t={t} language={language} />} />
        <Route path="/conversations" element={<ConversationsPage t={t} language={language} />} />
        <Route path="/conversations/:id" element={<ConversationsPage t={t} language={language} />} />
        <Route path="/people" element={<PeopleMemoryPage language={language} />} />
        <Route path="/people/:id" element={<PeopleMemoryPage language={language} />} />
        <Route path="/relationships" element={<PeopleMemoryPage language={language} />} />
        <Route path="/memory" element={<PeopleMemoryPage language={language} />} />
        <Route path="/persona" element={<PromptPage layer="PERSONA" language={language} />} />
        <Route path="/simulation" element={<PromptPage layer="SIMULATION" language={language} />} />
        <Route path="/social-settings" element={<SocialSettingsPage language={language} />} />
        <Route path="/models" element={<ModelsPage language={language} />} />
        <Route path="/stickers" element={<StickersPage language={language} />} />
        <Route path="/slang" element={<SlangPage language={language} />} />
        <Route path="/voice" element={<VoicePage language={language} />} />
        <Route path="/usage" element={<UsageConsolePage language={language} />} />
        <Route path="/access" element={<AccessPage language={language} />} />
        <Route path="/security" element={<SecurityPage language={language} />} />
        <Route path="/logs" element={<LogsPage language={language} />} />
        <Route path="/operations" element={<OperationsPage language={language} />} />
        <Route path="*" element={<NotFoundPage t={t} />} />
      </Route>
    </Routes>
    {toast && <div className="toast" role="status">{toast}</div>}
  </>
}

type Translator = (key: MessageKey) => string
function LoginPage({ t, language, onLogin }: { t: Translator; language: Language; onLogin: (session: Session) => void }) {
  const [error, setError] = useState('')
  const schema = z.object({ username: z.string().trim().min(1, t('fieldRequired')).max(100), password: z.string().min(14, t('passwordMin')).refine((value) => new TextEncoder().encode(value).length <= 72, t('passwordMax')) })
  const form = useForm<{ username: string; password: string }>({ resolver: zodResolver(schema) })
  const submit = form.handleSubmit(async (values) => {
    setError('')
    try { await login(values.username, values.password); onLogin(await api<Session>('/api/auth/session')) }
    catch (cause) { setError(cause instanceof ApiError && cause.status === 429 ? t('attemptsLimited') : cause instanceof ApiError && cause.status === 401 ? t('loginFailed') : apiErrorText(cause,language)) }
  })
  return <main className="login-page"><section className="login-panel" aria-labelledby="login-title">
    <Brand t={t} />
    <div className="login-copy"><p className="eyebrow">{t('secureConsole')}</p><h1 id="login-title">{t('loginTitle')}</h1><p>{t('loginHint')}</p></div>
    <form onSubmit={submit} noValidate>
      <label htmlFor="username">{t('username')}</label><input id="username" autoComplete="username" {...form.register('username')} aria-invalid={!!form.formState.errors.username||!!error} aria-describedby={form.formState.errors.username?'username-error':error?'login-error':undefined} />{form.formState.errors.username && <small id="username-error" className="field-error">{form.formState.errors.username.message}</small>}
      <label htmlFor="password">{t('password')}</label><input id="password" type="password" autoComplete="current-password" {...form.register('password')} aria-invalid={!!form.formState.errors.password||!!error} aria-describedby={form.formState.errors.password?'password-field-error':error?'login-error':undefined} />{form.formState.errors.password && <small id="password-field-error" className="field-error">{form.formState.errors.password.message}</small>}
      {error && <div id="login-error" className="inline-error" role="alert">{error}</div>}
      <button className="button primary full" type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('signingIn') : t('signIn')}</button>
    </form>
    <LanguageSwitch t={t} />
  </section></main>
}

function Brand({ t }: { t: Translator }) { return <div className="brand"><span className="brand-mark" aria-hidden="true">✳</span><span><strong>{t('brand')}</strong><small>{t('subtitle')}</small></span></div> }
function LanguageSwitch({ t }: { t: Translator }) {
  const [lang, setLang] = useState<Language>(() => localStorage.getItem('xingchen.language') === 'en-US' ? 'en-US' : 'zh-CN')
  return <div className="language-switch" aria-label={t('language')}><button type="button" aria-pressed={lang === 'zh-CN'} onClick={() => { localStorage.setItem('xingchen.language','zh-CN');setLang('zh-CN');window.location.reload() }}>中文</button><span>·</span><button type="button" aria-pressed={lang === 'en-US'} onClick={() => { localStorage.setItem('xingchen.language','en-US');setLang('en-US');window.location.reload() }}>English</button></div>
}

function Shell({ t, username, mobileOpen, setMobileOpen, onLogout, theme, setTheme, language, setLanguage }: { t: Translator; username: string; mobileOpen: boolean; setMobileOpen: (value: boolean) => void; onLogout: () => Promise<void>; theme: Theme; setTheme: (value: Theme) => void; language: Language; setLanguage: (value: Language) => void }) {
  const location = useLocation(); const [confirmLogout, setConfirmLogout] = useState(false)
  const [smallScreen,setSmallScreen]=useState(()=>window.matchMedia('(max-width:760px)').matches)
  const sidebarRef=useRef<HTMLElement>(null)
  const menuRef=useRef<HTMLButtonElement>(null)
  const wasConfirming=useRef(false)
  const logoutTrigger = useRef<HTMLButtonElement>(null); const dialogRef = useRef<HTMLElement>(null); const cancelRef = useRef<HTMLButtonElement>(null)
  useEffect(()=>{const media=window.matchMedia('(max-width:760px)');const update=()=>setSmallScreen(media.matches);media.addEventListener('change',update);return()=>media.removeEventListener('change',update)},[])
  useEffect(()=>{if(mobileOpen){sidebarRef.current?.querySelector<HTMLElement>('a')?.focus();return()=>menuRef.current?.focus()}},[mobileOpen])
  useEffect(() => { if (confirmLogout) {cancelRef.current?.focus();wasConfirming.current=true} else if(wasConfirming.current) {logoutTrigger.current?.focus();wasConfirming.current=false} }, [confirmLogout])
  const trapDialogFocus = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') { event.preventDefault(); setConfirmLogout(false); return }
    if (event.key !== 'Tab') return
    const buttons = dialogRef.current?.querySelectorAll<HTMLButtonElement>('button:not([disabled])')
    if (!buttons?.length) return
    const first = buttons[0]; const last = buttons[buttons.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
  return <div className="app-shell">
    {mobileOpen && <button className="scrim" aria-label={t('close')} onClick={() => setMobileOpen(false)} />}
    <aside ref={sidebarRef} inert={smallScreen&&!mobileOpen} className={`sidebar ${mobileOpen ? 'open' : ''}`} onKeyDown={event=>{if(event.key==='Escape'){event.preventDefault();setMobileOpen(false)}}}>
      <Brand t={t} /><nav aria-label={t('mainNavigation')}>{nav.map((section) => <section className="nav-group" key={section.group}><h2>{t(section.group)}</h2>{section.items.map((item) => { const current=location.pathname===item.path||(item.path==='/conversations'&&location.pathname.startsWith('/conversations/'));return <Link key={item.path} className={`nav-link ${current?'selected':''}`} to={item.path} onClick={() => setMobileOpen(false)} aria-current={current?'page':undefined}><span className="nav-dot" />{t(item.key)}</Link> })}</section>)}</nav>
      <Link className={`nav-link settings-link ${location.pathname === '/settings' ? 'selected' : ''}`} to="/settings" onClick={() => setMobileOpen(false)}><span className="nav-dot" />{t('settings')}</Link>
      <div className="account"><div className="avatar" aria-hidden="true">{username.slice(0,1).toUpperCase()}</div><div className="account-copy"><strong>{username}</strong><small>{t('sessionActive')}</small></div><button ref={logoutTrigger} className="icon-button" aria-label={t('logout')} onClick={() => setConfirmLogout(true)}>↗</button></div>
    </aside>
    <div className="main-column"><header className="topbar"><button ref={menuRef} className="icon-button menu-button" aria-label={t('menu')} aria-expanded={mobileOpen} onClick={() => setMobileOpen(!mobileOpen)}>☰</button><div className="breadcrumbs">{t(location.pathname === '/' ? 'overview' : location.pathname === '/settings' ? 'settings' : location.pathname === '/gateway' ? 'gateway' : location.pathname==='/persona'?'persona':location.pathname==='/simulation'?'simulation':location.pathname==='/social-settings'?'socialSettings':location.pathname==='/models'?'models':location.pathname==='/stickers'?'stickers':location.pathname==='/slang'?'slang':location.pathname==='/voice'?'voice':location.pathname.startsWith('/people')?'people':location.pathname==='/relationships'?'relationships':location.pathname==='/memory'?'memory':location.pathname==='/usage'?'usage':location.pathname==='/access'?'access':location.pathname==='/security'?'security':location.pathname==='/logs'?'logs':location.pathname==='/conversations'||location.pathname.startsWith('/conversations/')?'conversations':'operationsPage')}</div><div className="top-actions"><select aria-label={t('language')} value={language} onChange={(event) => setLanguage(event.target.value as Language)}><option value="zh-CN">中文</option><option value="en-US">English</option></select><select aria-label={t('theme')} value={theme} onChange={(event) => setTheme(event.target.value as Theme)}><option value="system">{t('system')}</option><option value="light">{t('light')}</option><option value="dark">{t('dark')}</option><option value="late">{t('late')}</option></select></div></header>
      <main className="content"><Outlet /></main>
    </div>
    {confirmLogout && <div className="dialog-scrim" role="presentation"><section ref={dialogRef} className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="logout-title" tabIndex={-1} onKeyDown={trapDialogFocus}><h2 id="logout-title">{t('logoutQuestion')}</h2><div className="dialog-actions"><button ref={cancelRef} className="button secondary" onClick={() => setConfirmLogout(false)}>{t('cancel')}</button><button className="button primary" onClick={() => void onLogout()}>{t('confirm')}</button></div></section></div>}
  </div>
}

function OverviewPage({ t }: { t: Translator }) {
  const [data, setData] = useState<Overview | null>(null); const [error, setError] = useState(false)
  useEffect(() => { api<Overview>('/api/console/overview').then(setData).catch(() => setError(true)) }, [])
  if (error) return <Feedback t={t} kind="error" />
  if (!data) return <Feedback t={t} kind="loading" />
  return <div className="page overview-page"><div className="page-heading"><div><p className="eyebrow">XingChen Core</p><h1>{t('overview')}</h1></div><span className="status-pill"><i />{t('running')}</span></div>
    <section className="status-section"><h2>{t('status')}</h2><div className="status-list">
      <StatusRow label={t('runtime')} value={data.runtimeEnabled ? t('running') : t('disabled')} good={data.runtimeEnabled} />
      <StatusRow label={t('gateway')} value={gatewayStateLabel(t,data.gateway)} good={data.gateway === 'connected'} />
      <StatusRow label={t('models')} value={data.model === 'configured' ? t('configured') : t('disabled')} good={data.model === 'configured'} />
      <StatusRow label={t('database')} value={data.database} good={data.database === 'UP'} />
      <StatusRow label={t('version')} value={data.version} />
    </div></section>
    <section className="status-section"><h2>{t('operations')}</h2><div className="compact-grid">
      <CountRow label={t('active')} value={data.activeConversations} to="/conversations?state=ACTIVE" /><CountRow label={t('waiting')} value={data.waitingConversations} to="/conversations?state=WAITING" /><CountRow label={t('unknown')} value={data.outboundUnknown} to="/conversations?hasUnknown=true" /><CountRow label={t('interrupted')} value={data.interruptedTurns} to="/conversations?interrupted=true" />
    </div></section>
    {!data.externalIntegrationsEnabled && <p className="quiet-note"><span className="status-dot neutral" />{t('externalOff')}</p>}
  </div>
}
function StatusRow({ label, value, good }: { label: string; value: string; good?: boolean }) { return <div className="status-row"><span><i className={`status-dot ${good ? 'good' : good === false ? 'neutral' : ''}`} />{label}</span><strong>{value}</strong></div> }
function CountRow({ label, value, to }: { label: string; value: number; to: string }) { return <Link className="count-row overview-count-link" to={to}><span>{label}</span><strong>{value}</strong></Link> }
function Feedback({ t, kind }: { t: Translator; kind: 'loading' | 'error' }) { return <div className={`feedback ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{kind === 'loading' ? t('loading') : t('offline')}</div> }

function SettingsPage({ t, theme, setTheme, language, setLanguage }: { t: Translator; theme: Theme; setTheme: (value: Theme) => void; language: Language; setLanguage: (value: Language) => void }) {
  const schema = z.object({ publicBaseUrl: z.string().trim().refine((value) => !value || /^https?:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/i.test(value), t('urlInvalid')).max(512) })
  const form = useForm<{ publicBaseUrl: string }>({ resolver: zodResolver(schema), defaultValues: { publicBaseUrl: '' } })
  const [notice, setNotice] = useState('')
  useEffect(() => { api<{ values: { publicBaseUrl?: string } }>('/api/console/config/Console').then((config) => form.reset({ publicBaseUrl: config.values.publicBaseUrl ?? '' })).catch(() => setNotice(t('offline'))) }, [form, t])
  const save = form.handleSubmit(async (value) => {
    setNotice('')
    try { await api('/api/console/config/Console/publicBaseUrl', { method: 'PUT', body: JSON.stringify({ value: value.publicBaseUrl }) }); setNotice(t('saved')) }
    catch (error) { setNotice(apiErrorText(error,language)) }
  })
  return <div className="page"><div className="page-heading"><div><p className="eyebrow">{t('operations')}</p><h1>{t('settingsTitle')}</h1></div></div>
    <section className="settings-section"><h2>{t('appearance')}</h2><p>{t('themeSaved')}</p><label htmlFor="theme-select">{t('theme')}</label><select id="theme-select" value={theme} onChange={(event) => setTheme(event.target.value as Theme)}><option value="system">{t('system')}</option><option value="light">{t('light')}</option><option value="dark">{t('dark')}</option><option value="late">{t('late')}</option></select>
      <label htmlFor="language-select">{t('language')}</label><select id="language-select" value={language} onChange={(event) => setLanguage(event.target.value as Language)}><option value="zh-CN">中文</option><option value="en-US">English</option></select></section>
    <section className="settings-section"><h2>{t('settings')}</h2><form onSubmit={save} noValidate><label htmlFor="public-url">{t('publicUrl')}</label><input id="public-url" inputMode="url" {...form.register('publicBaseUrl')} aria-invalid={!!form.formState.errors.publicBaseUrl} aria-describedby={form.formState.errors.publicBaseUrl?'public-url-error':undefined} />{form.formState.errors.publicBaseUrl && <small id="public-url-error" className="field-error">{form.formState.errors.publicBaseUrl.message}</small>}<button className="button primary" disabled={form.formState.isSubmitting}>{t('save')}</button>{notice && <p role="status" className="notice">{notice}</p>}</form></section>
  </div>
}
function gatewayStateLabel(t: Translator,state: string) { const map: Record<string,MessageKey> = {'disabled':'gatewayDisabled','not-configured':'gatewayNotConfigured','login_required':'gatewayLoginRequired','login-required':'gatewayLoginRequired','waiting_for_scan':'gatewayWaitingScan','waiting-for-scan':'gatewayWaitingScan','authenticating':'gatewayAuthenticating','connected':'gatewayConnected','disconnected':'gatewayDisconnected','error':'gatewayError'};return t(map[state]??'gatewayUnknown') }
function probeStatusLabel(t: Translator,status: string) { const map: Record<string,MessageKey> = {CONNECTED:'probeConnected',CONFIG_INCOMPLETE:'probeConfigIncomplete',CREDENTIAL_REJECTED:'probeCredentialRejected',UNAVAILABLE:'probeUnavailable',SERVICE_ERROR:'probeServiceError',NOT_CONNECTED:'probeNotConnected',DISABLED:'gatewayDisabled'};return t(map[status]??'gatewayUnknown') }
export function GatewayPage({ t, language }: { t: Translator; language: Language }) {
  const [data,setData]=useState<GatewayInfo|null>(null);const [notice,setNotice]=useState('');const [loading,setLoading]=useState(true);const [httpToken,setHttpToken]=useState('');const [wsToken,setWsToken]=useState('');const [httpProbe,setHttpProbe]=useState('');const [wsProbe,setWsProbe]=useState('')
  const [httpUrl,setHttpUrl]=useState('');const [wsUrl,setWsUrl]=useState('');const [managementUrl,setManagementUrl]=useState('');const [enabled,setEnabled]=useState(false)
  const refresh=()=>{setLoading(true);api<GatewayInfo>('/api/gateway/status').then(value=>{setData(value);setHttpUrl(value.config.httpUrl);setWsUrl(value.config.wsUrl);setManagementUrl(value.config.managementUrl??'');setEnabled(value.config.enabled)}).catch(()=>setNotice(t('offline'))).finally(()=>setLoading(false))}
  useEffect(()=>{refresh();const timer=window.setInterval(()=>{if(document.visibilityState==='visible')refresh()},4000);const visible=()=>{if(document.visibilityState==='visible')refresh()};document.addEventListener('visibilitychange',visible);return()=>{window.clearInterval(timer);document.removeEventListener('visibilitychange',visible)}},[])
  const save=async(event:React.FormEvent)=>{event.preventDefault();setNotice('');try{await api('/api/gateway/config',{method:'PATCH',body:JSON.stringify({httpUrl,wsUrl,managementUrl,enabled})});setNotice(t('gatewaySaved'));refresh()}catch(error){setNotice(apiErrorText(error,language))}}
  const saveToken=async(transport:'http'|'ws',value:string)=>{if(!value)return;try{await api('/api/gateway/secret',{method:'POST',body:JSON.stringify({transport,action:'replace',token:value})});setNotice(transport==='http'?t('httpTokenSavedOnly'):t('wsTokenSavedOnly'));if(transport==='http')setHttpToken('');else setWsToken('');refresh()}catch(error){setNotice(apiErrorText(error,language))}}
  const clearToken=async(transport:'http'|'ws')=>{try{await api('/api/gateway/secret',{method:'POST',body:JSON.stringify({transport,action:'clear'})});setNotice(transport==='http'?t('httpTokenClearedOnly'):t('wsTokenClearedOnly'));refresh()}catch(error){setNotice(apiErrorText(error,language))}}
  const probe=async(transport:'http'|'ws')=>{try{const result=await api<{status:string}>(`/api/gateway/test/${transport}`,{method:'POST'});(transport==='http'?setHttpProbe:setWsProbe)(probeStatusLabel(t,result.status))}catch(error){(transport==='http'?setHttpProbe:setWsProbe)(apiErrorText(error,language))}}
  if(loading&&!data)return <Feedback t={t} kind="loading" />
  return <div className="page"><div className="page-heading"><div><p className="eyebrow">{t('platform')}</p><h1>{t('gateway')}</h1></div><button className="button secondary" onClick={refresh}>{t('retry')}</button></div>
    {data&&<><section className="settings-section"><span className={`status-pill ${data.state==='CONNECTED'?'':'muted'}`}><i />{gatewayStateLabel(t,data.state.toLowerCase().replaceAll('_','-'))}</span><h2>{t('gatewayObservedStatus')}</h2><p>{t('gatewayTransportOnly')}</p>
      <div className="status-list"><StatusRow label={t('onebotWebsocket')} value={data.transportConnected?t('gatewayConnected'):t('gatewayDisconnected')} good={data.transportConnected}/><StatusRow label={t('qqAccount')} value={data.account?`${data.account.displayName} · ${data.account.userId}`:t('accountUnavailable')}/><StatusRow label={t('httpToken')} value={data.config.httpTokenConfigured?t('configured'):t('notConfigured')} good={data.config.httpTokenConfigured}/><StatusRow label={t('wsToken')} value={data.config.wsTokenConfigured?t('configured'):t('notConfigured')} good={data.config.wsTokenConfigured}/></div>
    </section><section className="settings-section"><h2>{t('gatewayLoginManagement')}</h2><p>{t('qrUnsupported')}</p><p>{t('gatewayExternalFlow')}</p>{data.config.managementUrl&&<a className="button secondary" href={data.config.managementUrl} target="_blank" rel="noreferrer">{t('openSnowLuma')}</a>}</section>
    <section className="settings-section"><h2>{t('gatewayConfig')}</h2><form onSubmit={save}><label htmlFor="gateway-enabled">{t('gatewayEnabled')}</label><input id="gateway-enabled" type="checkbox" checked={enabled} onChange={event=>setEnabled(event.target.checked)}/><label htmlFor="onebot-http">{t('onebotHttp')}</label><input id="onebot-http" value={httpUrl} onChange={event=>setHttpUrl(event.target.value)} autoComplete="off"/><label htmlFor="onebot-ws">{t('onebotWebsocket')}</label><input id="onebot-ws" value={wsUrl} onChange={event=>setWsUrl(event.target.value)} autoComplete="off"/><label htmlFor="snowluma-management">{t('snowlumaManagementUrl')}</label><input id="snowluma-management" value={managementUrl} onChange={event=>setManagementUrl(event.target.value)} autoComplete="url"/><p>{t('loopbackOnly')}</p><button className="button primary">{t('save')}</button>{notice&&<p className="notice" role="status">{notice}</p>}</form></section>
    <section className="settings-section"><h2>{t('httpToken')}</h2><p>{t('httpTokenDescription')}</p><p>{t('secretStatus')}: {data.config.httpTokenConfigured?t('configured'):t('notConfigured')}{!data.config.httpTokenExplicit&&data.config.legacyTokenConfigured?` · ${t('legacyFallbackActive')}`:''}</p><label htmlFor="onebot-http-token">{t('newToken')}</label><input id="onebot-http-token" type="password" autoComplete="new-password" value={httpToken} onChange={event=>setHttpToken(event.target.value)}/><div className="button-row"><button className="button primary" disabled={!httpToken} onClick={()=>void saveToken('http',httpToken)}>{t('save')}</button><button className="button secondary" disabled={!data.config.httpTokenConfigured} onClick={()=>void clearToken('http')}>{t('clearToken')}</button><button className="button secondary" onClick={()=>void probe('http')}>{t('checkConnection')}</button></div>{httpProbe&&<p role="status">{t('httpCheck')}: {httpProbe}</p>}</section>
    <section className="settings-section"><h2>{t('wsToken')}</h2><p>{t('wsTokenDescription')}</p><p>{t('secretStatus')}: {data.config.wsTokenConfigured?t('configured'):t('notConfigured')}{!data.config.wsTokenExplicit&&data.config.legacyTokenConfigured?` · ${t('legacyFallbackActive')}`:''}</p><label htmlFor="onebot-ws-token">{t('newToken')}</label><input id="onebot-ws-token" type="password" autoComplete="new-password" value={wsToken} onChange={event=>setWsToken(event.target.value)}/><div className="button-row"><button className="button primary" disabled={!wsToken} onClick={()=>void saveToken('ws',wsToken)}>{t('save')}</button><button className="button secondary" disabled={!data.config.wsTokenConfigured} onClick={()=>void clearToken('ws')}>{t('clearToken')}</button><button className="button secondary" onClick={()=>void probe('ws')}>{t('checkConnection')}</button></div>{wsProbe&&<p role="status">{t('wsCheck')}: {wsProbe}</p>}</section></>}
  </div>
}
function NotFoundPage({ t }: { t: Translator }) { return <div className="page"><h1>{t('notFound')}</h1><p>{t('notFoundHint')}</p><Link className="button secondary" to="/">{t('overview')}</Link></div> }
