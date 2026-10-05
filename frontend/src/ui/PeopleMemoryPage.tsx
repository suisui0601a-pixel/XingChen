import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, apiErrorText } from './api'
import { memoryFilterInstant } from './memoryTime'
import type { Language } from './i18n'

type Row = Record<string, string | number | boolean>
const tr = (language: Language, zh: string, en: string) => language === 'zh-CN' ? zh : en
const copy = (language: Language, zh: string, en: string) => tr(language, zh, en)
const enumNames: Record<string, [string, string]> = {
  GLOBAL: ['全局', 'Global'], OWNER_GLOBAL: ['所有者全局', 'Owner global'], PERSON_GLOBAL: ['人物长期', 'Person global'],
  CONVERSATION: ['当前会话', 'Conversation'], PROJECT: ['项目', 'Project'], PRIVATE: ['私有', 'Private'],
  EPISODIC: ['事件', 'Episodic'], SEMANTIC: ['语义', 'Semantic'], PERSON: ['人物', 'Person'], RELATIONSHIP: ['关系', 'Relationship'],
  TASK: ['任务', 'Task'], PREFERENCE: ['偏好', 'Preference'], CONVERSATION_SUMMARY: ['会话摘要', 'Conversation summary'],
  ADMIN_MANUAL: ['管理员手工录入', 'Admin manual'], MANUAL_EXPLICIT: ['管理员手工显式', 'Admin manual explicit'],
  GLOBAL_EXPLICIT: ['全局显式', 'Global explicit'], CONVERSATION_EXPLICIT: ['会话显式', 'Conversation explicit'], GLOBAL_INFERRED: ['全局推断', 'Global inferred'], CONVERSATION_INFERRED: ['会话推断', 'Conversation inferred'], INFERRED: ['推断', 'Inferred'], DISPLAY_NAME_FALLBACK: ['回退至显示名称', 'Display-name fallback'],
  ACTIVE: ['有效', 'Active'], SOFT_DELETED: ['已停用', 'Forgotten'], GROUP: ['群聊', 'Group'], PRIVATE_CHAT: ['私聊', 'Private chat'], SELF: ['机器人自身', 'Self'], OWNER: ['所有者', 'Owner'], MEMBER: ['成员', 'Member'],
}
const fieldNames: Record<string, [string, string]> = {
  alias: ['别名', 'Alias'], conversationType: ['会话类型', 'Conversation type'], platformConversationId: ['平台会话 ID', 'Platform conversation ID'],
  firstSeenAt: ['首次出现', 'First seen'], lastSeenAt: ['最近出现', 'Last seen'], card: ['群名片', 'Group card'], displayName: ['显示名称', 'Display name'],
  role: ['成员角色', 'Role'], term: ['称呼', 'Term'], scope: ['范围', 'Scope'], source: ['来源', 'Source'], updatedAt: ['更新时间', 'Updated'],
  subjectPlatformUserId: ['称呼方平台 ID', 'Subject platform ID'], targetPlatformUserId: ['目标平台 ID', 'Target platform ID'],
  type: ['记忆类型', 'Memory type'], personPlatformUserId: ['人物平台 ID', 'Person platform ID'], conversationId: ['会话 ID', 'Conversation ID'],
  projectKey: ['项目键', 'Project key'], content: ['内容摘要', 'Content preview'], createdAt: ['创建时间', 'Created'], status: ['状态', 'Status'],
  provenance: ['来源记录', 'Provenance'], count: ['数量', 'Count'], priority: ['优先级', 'Priority'], reason: ['原因', 'Reason'], priorityClass: ['优先级来源', 'Priority class'], effective: ['当前生效', 'Currently effective'],
}
const enumLabel = (language: Language, value: unknown) => {
  const text = String(value ?? '')
  const pair = enumNames[text]
  return pair ? `${language === 'zh-CN' ? pair[0] : pair[1]} (${text})` : text
}
const memoryReasonLabel = (language: Language, value: unknown) => {
  const labels: Record<string, [string, string]> = {
    'PERSON_GLOBAL same person': ['同一人物的长期记忆', 'Long-term memory for the same person'],
    'PRIVATE same person': ['同一人物的私有记忆', 'Private memory for the same person'],
    'PRIVATE different actor': ['其他人物的私有记忆', 'Private memory for another person'],
    'CONVERSATION same conversation': ['当前会话记忆', 'Memory from this conversation'],
    'CONVERSATION different conversation': ['其他会话记忆', 'Memory from another conversation'],
    'PROJECT same project': ['当前项目记忆', 'Memory from this project'],
    'PROJECT different project': ['其他项目记忆', 'Memory from another project'],
    'OWNER_GLOBAL owner policy': ['所有者专属记忆', 'Owner-only memory'],
    'GLOBAL policy': ['全局记忆策略', 'Global memory policy'],
  }
  const pair = labels[String(value ?? '')]
  return pair ? pair[language === 'zh-CN' ? 0 : 1] : String(value ?? '—')
}

export function PeopleMemoryPage({ language }: { language: Language }) {
  const path = useLocation().pathname
  if (path === '/relationships') return <Relationships language={language} />
  if (path === '/memory') return <Memories language={language} />
  return <People language={language} />
}

function People({ language }: { language: Language }) {
  const { id } = useParams()
  const [q, setQ] = useState('')
  const [data, setData] = useState<any>()
  const [error, setError] = useState('')
  const load = () => {
    setError(''); setData(undefined)
    api<any>(id ? `/api/people/${id}` : `/api/people?q=${encodeURIComponent(q)}&page=0&size=20`).then(setData).catch(e => setError(safeError(e, language)))
  }
  useEffect(() => { load() }, [id])
  if (error) return <Page language={language} title={copy(language, '人物', 'People')} error={error} retry={load} />
  if (!data || (!id && !data.items)) return <Page language={language} title={copy(language, '人物', 'People')} loading />
  if (!id) return <Page language={language} title={copy(language, '人物', 'People')}>
    <form className="console-filters" onSubmit={e => { e.preventDefault(); load() }}>
      <label>{copy(language, '搜索平台 ID、昵称、别名或群名片', 'Search platform ID, display name, alias, or group card')}<input value={q} onChange={e => setQ(e.target.value)} /></label>
      <button className="button primary">{copy(language, '搜索', 'Search')}</button>
    </form>
    <div className="console-rows">{data.items.map((p: Row) => <Link className="console-row" to={`/people/${p.id}`} key={String(p.id)}>
      <strong>{p.displayName || p.platformUserId}</strong><span>{p.platform} · {p.platformUserId}{p.owner ? ` · ${copy(language, '所有者', 'Owner')}` : ''}</span>
      <span>{copy(language, '别名', 'Aliases')} {p.aliasCount} · {copy(language, '成员/会话', 'Memberships')} {p.membershipCount} · {copy(language, '有效记忆', 'Active memories')} {p.memoryCount}</span>
      <span>{copy(language, '关系数', 'Relationships')} {p.relationshipCount} · {copy(language, '最近出现', 'Last seen')} {p.lastSeen || '—'}</span>
    </Link>)}</div><p>{data.total} · {data.size} / {copy(language, '页', 'page')}</p>
  </Page>
  if (!data.person) return <Page language={language} title={copy(language, '人物', 'People')} loading />
  const p = data.person
  return <Page language={language} title={String(p.platformUserId)}>
    <div className="identity-box"><strong>{copy(language, '平台身份键不可编辑', 'Platform identity key is read-only')}</strong><p>{p.platform} · {p.platformUserId} · {enumLabel(language, p.self ? 'SELF' : p.owner ? 'OWNER' : 'MEMBER')}</p><small>{p.id}</small><p>{copy(language, '首次记录', 'First seen')}: {p.createdAt}</p></div>
    <section><h2>{copy(language, '别名历史', 'Alias history')}</h2><Rows language={language} rows={data.aliases} fields={['alias', 'conversationType', 'platformConversationId', 'firstSeenAt', 'lastSeenAt']} /></section>
    <section><h2>{copy(language, '成员与会话历史', 'Membership and conversation history')}</h2><Rows language={language} rows={data.memberships} fields={['conversationType', 'platformConversationId', 'card', 'displayName', 'role', 'firstSeenAt', 'lastSeenAt']} /></section>
    <section><h2>{copy(language, '关系记录', 'Relationship terms')}</h2><Rows language={language} rows={data.relationships} fields={['term', 'scope', 'source', 'updatedAt']} /><Link className="button secondary" to="/relationships">{copy(language, '管理关系', 'Manage relationships')}</Link></section>
    <section><h2>{copy(language, '记忆摘要', 'Memory summary')}</h2><Rows language={language} rows={data.memorySummary} fields={['type', 'scope', 'count']} /><Link className="button secondary" to={`/memory?person=${p.id}`}>{copy(language, '查看记忆', 'View memories')}</Link></section>
    <Link to="/people" className="button secondary">{copy(language, '返回人物列表', 'Back to people')}</Link>
  </Page>
}

function Relationships({ language }: { language: Language }) {
  const query = new URLSearchParams(useLocation().search)
  const [data, setData] = useState<any>()
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(''), [formError,setFormError]=useState('')
  const [subject, setSubject] = useState(query.get('personId') ?? '')
  const [target, setTarget] = useState('')
  const [term, setTerm] = useState('')
  const [scope, setScope] = useState('GLOBAL')
  const [scopeId, setScopeId] = useState('')
  const [editing, setEditing] = useState<any>()
  const [preview, setPreview] = useState<any>()
  const previewSequence=useRef(0)
  const [previewConversation, setPreviewConversation] = useState('')
  const load = () => { setError(''); api<any>('/api/relationships?page=0&size=100').then(setData).catch(e => setError(safeError(e, language))) }
  useEffect(() => { load() }, [])
  const clearForm = () => { setEditing(undefined); setTerm(''); setScope('GLOBAL'); setScopeId('') }
  const save = async (event: FormEvent) => {
    event.preventDefault(); setNotice(''); setFormError('')
    try {
      await api(editing ? `/api/relationships/${editing.id}` : '/api/relationships', { method: editing ? 'PUT' : 'POST', body: JSON.stringify({ subjectPersonId: subject, targetPersonId: target, term, scope, scopeId: scope === 'GLOBAL' ? '' : scopeId, explicit: true, expectedUpdatedAt: editing?.updatedAt, operation: editing ? 'EDIT' : 'CREATE' }) })
      setNotice(copy(language, editing ? '称呼已更新' : '称呼已创建', editing ? 'Address term updated' : 'Address term created')); clearForm(); load()
    } catch (e) { setFormError(safeError(e, language)) }
  }
  const edit = (row: any) => { setEditing(row); setSubject(row.subjectPersonId); setTarget(row.targetPersonId); setTerm(row.term); setScope(row.scope); setScopeId(row.conversationId || '') }
  const deactivate = async (row: any) => {
    const prompt = copy(language, `停用称呼“${row.term}”？该操作会保留审计记录。`, `Deactivate “${row.term}”? An audit record will be retained.`)
    if (!window.confirm(prompt)) return
    try { await api(`/api/relationships/${row.id}/forget`, { method: 'POST', body: JSON.stringify({ expectedUpdatedAt: row.updatedAt }) }); setNotice(copy(language, '称呼已停用', 'Address term deactivated')); if (editing?.id === row.id) clearForm(); load() } catch (e) { setFormError(safeError(e, language)) }
  }
  const runPreview = async (event: FormEvent) => {
    event.preventDefault(); setNotice(''); setFormError(''); setPreview(undefined);const sequence=++previewSequence.current
    try { const result=await api(`/api/relationships/preview?subjectPersonId=${encodeURIComponent(subject)}&targetPersonId=${encodeURIComponent(target)}&conversationId=${encodeURIComponent(previewConversation)}`);if(sequence===previewSequence.current)setPreview(result) } catch (e) { if(sequence===previewSequence.current)setFormError(safeError(e, language)) }
  }
  const form = (row: any) => <div className="console-row relationship-entry" key={row.id}>
    <span><small>{copy(language, '称呼', 'Term')}</small> {row.term}</span><span><small>{copy(language, '人物', 'People')}</small> {row.subjectPlatformUserId} → {row.targetPlatformUserId}</span>
    <span><small>{copy(language, '范围 / 来源', 'Scope / source')}</small> {enumLabel(language, row.scope)} · {enumLabel(language, row.source)}</span>
    {row.scopeId && <span><small>{copy(language, '平台会话范围', 'Platform conversation scope')}</small> {row.scopeId}</span>}
    <span><small>{copy(language, '更新时间', 'Updated')}</small> {row.updatedAt}</span>
    {row.explicit && <div className="row-actions"><button className="button secondary" onClick={() => edit(row)}>{copy(language, '编辑', 'Edit')}</button><button className="button danger-button" aria-label={copy(language, `停用称呼 ${row.term}`, `Deactivate term ${row.term}`)} onClick={() => void deactivate(row)}>{copy(language, '停用', 'Deactivate')}</button></div>}
    {!row.explicit && <span>{copy(language, '推断证据只读', 'Inferred evidence is read-only')}</span>}
  </div>
  return <Page language={language} title={copy(language, '关系管理', 'Relationship management')} error={error} retry={load}>
    {!data ? <p role="status">{copy(language, '正在加载', 'Loading')}</p> : <>
      <div className="console-rows">{data.items.map(form)}</div>
      <h2>{copy(language, editing ? '编辑显式称呼' : '创建显式称呼', editing ? 'Edit explicit address' : 'Create explicit address')}</h2>
      <form className="console-filters" onSubmit={save}>
        <label>{copy(language, '称呼方人物 ID', 'Subject person ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} aria-label={copy(language,'称呼方人物 ID','Subject person ID')} required value={subject} readOnly={Boolean(editing)} onChange={e => setSubject(e.target.value)} /></label>
        <label>{copy(language, '目标人物 ID', 'Target person ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} aria-label={copy(language,'目标人物 ID','Target person ID')} required value={target} readOnly={Boolean(editing)} onChange={e => setTarget(e.target.value)} /></label>
        <label>{copy(language, '称呼', 'Address term')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} required maxLength={80} value={term} onChange={e => setTerm(e.target.value)} /></label>
        <label>{copy(language, '范围', 'Scope')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={scope} onChange={e => setScope(e.target.value)}>{(data.scopes ?? []).map((value: string) => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        {scope !== 'GLOBAL' && <label>{copy(language, '会话 ID', 'Conversation ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} aria-label={copy(language,'会话 ID','Conversation ID')} required value={scopeId} onChange={e => setScopeId(e.target.value)} /></label>}
        <div className="row-actions"><button className="button primary">{copy(language, editing ? '保存修改' : '创建显式关系', editing ? 'Save changes' : 'Create explicit term')}</button>{editing && <button type="button" className="button secondary" onClick={clearForm}>{copy(language, '取消编辑', 'Cancel edit')}</button>}</div>
      </form>
      <h2>{copy(language, '有效称呼与优先级预览', 'Effective address and priority preview')}</h2>
      <form className="console-filters" onSubmit={runPreview}>
        <label>{copy(language, '用于预览的会话 ID', 'Conversation ID for preview')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} required value={previewConversation} onChange={e => setPreviewConversation(e.target.value)} /></label>
        <button className="button secondary">{copy(language, '预览当前称呼', 'Preview effective address')}</button>
      </form>
      {preview && <section className="identity-box"><strong>{copy(language, '当前生效称呼', 'Effective address')}: {preview.address || '—'}</strong><p>{enumLabel(language, preview.reason)} · {enumLabel(language, preview.scope)}</p><Rows language={language} rows={preview.candidates} fields={['term', 'scope', 'source', 'priorityClass', 'priority', 'effective']} /></section>}
      {formError&&<p id="people-form-error" role="alert">{formError}</p>}{notice && <p role="status">{notice}</p>}
    </>}
  </Page>
}

function Memories({ language }: { language: Language }) {
  const loc = useLocation(), nav = useNavigate(), params = new URLSearchParams(loc.search), id = params.get('id')
  const previewSequence=useRef(0)
  const [formError,setFormError]=useState('')
  const [data, setData] = useState<any>(), [detail, setDetail] = useState<any>(), [notice, setNotice] = useState(''), [preview, setPreview] = useState<any>()
  const [q, setQ] = useState(''), [person, setPerson] = useState(params.get('person') ?? ''), [conversation, setConversation] = useState(params.get('conversation') ?? ''), [project, setProject] = useState('')
  const [type, setType] = useState(''), [scope, setScope] = useState(''), [status, setStatus] = useState('ACTIVE'), [createdFrom, setCreatedFrom] = useState(''), [createdTo, setCreatedTo] = useState('')
  const [content, setContent] = useState(''), [memoryType, setMemoryType] = useState('SEMANTIC'), [memoryScope, setMemoryScope] = useState('PERSON_GLOBAL')
  const load = () => {
    const query = new URLSearchParams({ q, person, conversation, project, type, scope, status, page: '0', size: '20' })
    if (createdFrom) query.set('from', memoryFilterInstant(createdFrom))
    if (createdTo) query.set('to', memoryFilterInstant(createdTo))
    api<any>(`/api/memory?${query}`).then(setData).catch(e => setNotice(safeError(e, language)))
  }
  useEffect(() => { load() }, [loc.search])
  useEffect(() => { if (!id) { setDetail(null); return }; api<any>(`/api/memory/${id}`).then((value: any) => { setDetail(value); setContent(value.memory.content); setMemoryType(value.memory.type); setMemoryScope(value.memory.scope) }).catch(e => setNotice(safeError(e, language))) }, [id])
  const m = detail?.memory
  const save = async (event: FormEvent) => {
    event.preventDefault(); setNotice(''); setFormError('')
    if (createdFrom && createdTo && new Date(createdFrom) >= new Date(createdTo)) { setNotice(copy(language, '起始时间必须早于结束时间', 'Start time must be before end time')); return }
    if (['PRIVATE', 'PERSON_GLOBAL', 'OWNER_GLOBAL'].includes(memoryScope) && !person) { setFormError(copy(language, '此范围需要人物 ID', 'This scope requires a person ID')); return }
    if (memoryScope === 'CONVERSATION' && !conversation) { setFormError(copy(language, '此范围需要会话 ID', 'This scope requires a conversation ID')); return }
    if (memoryScope === 'PROJECT' && !project) { setFormError(copy(language, '此范围需要项目键', 'This scope requires a project key')); return }
    if (m && scopeExpands(m.scope, memoryScope) && !window.confirm(copy(language, '此操作会扩大记忆可见范围，确认继续？', 'This change expands memory visibility. Continue?'))) return
    try {
      await api(id ? `/api/memory/${id}` : '/api/memory', { method: id ? 'PUT' : 'POST', body: JSON.stringify({ content, type: memoryType, scope: memoryScope, personId: person, conversationId: conversation, projectKey: project, expectedUpdatedAt: m?.updatedAt, confirmScopeExpansion: true, operation: id ? 'EDIT' : 'CREATE' }) })
      setNotice(copy(language, id ? '记忆已保存' : '记忆已创建', id ? 'Memory saved' : 'Memory created'))
      if (id) { const refreshed: any = await api(`/api/memory/${id}`); setDetail(refreshed); setContent(refreshed.memory.content) } else { setContent(''); load() }
    } catch (e) { setFormError(safeError(e, language)) }
  }
  const forget = async () => {
    if (!m || !window.confirm(copy(language, '将停用这条记忆；审计记录不会立即物理擦除。继续？', 'This will forget the memory; its audit record will remain. Continue?'))) return
    try { await api(`/api/memory/${m.id}/forget`, { method: 'POST', body: JSON.stringify({ expectedUpdatedAt: m.updatedAt }) }); setNotice(copy(language, '记忆已停用', 'Memory forgotten')); setDetail(undefined); setContent(''); setMemoryType('SEMANTIC'); setMemoryScope('PERSON_GLOBAL'); setPreview(undefined); nav('/memory'); load() } catch (e) { setFormError(safeError(e, language)) }
  }
  const runPreview = async () => { setPreview(undefined); setNotice('');setFormError('');const sequence=++previewSequence.current; try { const result=await api(`/api/memory/retrieval-preview?personId=${encodeURIComponent(person)}&conversationId=${encodeURIComponent(conversation)}&projectKey=${encodeURIComponent(project)}`);if(sequence===previewSequence.current)setPreview(result) } catch (e) { if(sequence===previewSequence.current)setFormError(safeError(e, language)) } }
  const scopes: string[] = data?.scopes ?? []
  const types: string[] = data?.types ?? []
  return <Page language={language} title={copy(language, '长期记忆', 'Long-term memory')}>
    {formError&&<p id="people-form-error" role="alert">{formError}</p>}{notice && <p role="status">{notice}</p>}
    {id && m ? <>
      <p>{enumLabel(language, m.type)} · {enumLabel(language, m.scope)} · {enumLabel(language, m.status)} · {enumLabel(language, m.provenance)}</p>
      <pre className="memory-content">{m.content}</pre>
      <p>{copy(language, '来源与溯源', 'Provenance')}: {enumLabel(language, m.provenance)} · {m.createdAt} · {m.updatedAt}</p>
      {detail.sources?.map((source: Row, index: number) => <p key={index}>{String(source.platform)} · {String(source.conversationId)} · {String(source.messageId)} · {String(source.timestamp)}</p>)}
      <label>{copy(language, '人物 ID', 'Person ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={person} onChange={e => setPerson(e.target.value)} /></label>
      <label>{copy(language, '会话 ID', 'Conversation ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={conversation} onChange={e => setConversation(e.target.value)} /></label>
      <label>{copy(language, '项目键', 'Project key')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={project} onChange={e => setProject(e.target.value)} /></label>
      <div className="row-actions"><button className="button secondary" onClick={() => void runPreview()}>{copy(language, '预览检索可见性', 'Preview retrieval visibility')}</button><button className="button danger-button" onClick={() => void forget()}>{copy(language, '停用 / 忘记', 'Forget memory')}</button></div>
      {preview && <div className="identity-box"><strong>{copy(language, '检索预览', 'Retrieval preview')}</strong><p>{copy(language, '模型未调用', 'Model not invoked')}: {preview.modelInvoked===false?copy(language,'是','Yes'):copy(language,'否','No')}</p><Rows language={language} rows={preview.items} fields={['type', 'scope', 'reason']} /></div>}
      <form onSubmit={save}><label>{copy(language, '记忆正文', 'Memory content')}<textarea aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} required maxLength={20000} value={content} onChange={e => setContent(e.target.value)} /></label>
        <label>{copy(language, '类型', 'Type')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={memoryType} onChange={e => setMemoryType(e.target.value)}>{types.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <label>{copy(language, '范围', 'Scope')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={memoryScope} onChange={e => setMemoryScope(e.target.value)}>{scopes.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <button className="button primary">{copy(language, '保存修改', 'Save changes')}</button>
      </form>
    </> : <>
      <form className="console-filters" onSubmit={e => { e.preventDefault(); load() }}>
        <label>{copy(language, '全文搜索', 'Full-text search')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={q} onChange={e => setQ(e.target.value)} /></label>
        <label>{copy(language, '人物 ID', 'Person ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={person} onChange={e => setPerson(e.target.value)} /></label>
        <label>{copy(language, '会话 ID', 'Conversation ID')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={conversation} onChange={e => setConversation(e.target.value)} /></label>
        <label>{copy(language, '项目键', 'Project key')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={project} onChange={e => setProject(e.target.value)} /></label>
        <label>{copy(language, '类型', 'Type')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={type} onChange={e => setType(e.target.value)}><option value="">{copy(language, '全部', 'All')}</option>{types.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <label>{copy(language, '范围', 'Scope')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={scope} onChange={e => setScope(e.target.value)}><option value="">{copy(language, '全部', 'All')}</option>{scopes.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <label>{copy(language, '状态', 'Status')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={status} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">{enumLabel(language, 'ACTIVE')}</option><option value="SOFT_DELETED">{enumLabel(language, 'SOFT_DELETED')}</option></select></label>
        <label>{copy(language, '创建时间起（含）', 'Created from (inclusive)')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} type="datetime-local" value={createdFrom} onChange={e => setCreatedFrom(e.target.value)} /></label>
        <label>{copy(language, '创建时间止（不含）', 'Created to (exclusive)')}<input aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} type="datetime-local" value={createdTo} onChange={e => setCreatedTo(e.target.value)} /></label>
        <button className="button primary">{copy(language, '筛选', 'Filter')}</button>
      </form>
      <Rows language={language} rows={data?.items ?? []} fields={['type', 'scope', 'content', 'personPlatformUserId', 'conversationId', 'projectKey', 'createdAt', 'status', 'provenance']} linkField="id" base="/memory?id=" />
      <h2>{copy(language, '新建记忆', 'Create memory')}</h2>
      <form onSubmit={save}><label>{copy(language, '记忆正文', 'Memory content')}<textarea aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} required maxLength={20000} value={content} onChange={e => setContent(e.target.value)} /></label>
        <label>{copy(language, '类型', 'Type')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={memoryType} onChange={e => setMemoryType(e.target.value)}>{types.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <label>{copy(language, '范围', 'Scope')}<select aria-invalid={!!formError} aria-describedby={formError?'people-form-error':undefined} value={memoryScope} onChange={e => setMemoryScope(e.target.value)}>{scopes.map(value => <option key={value} value={value}>{enumLabel(language, value)}</option>)}</select></label>
        <button className="button primary">{copy(language, '创建记忆', 'Create memory')}</button>
      </form>
    </>}
  </Page>
}

function scopeExpands(oldScope: string, nextScope: string) {
  if (oldScope === nextScope || nextScope === 'PRIVATE') return false
  if (['PRIVATE', 'CONVERSATION', 'PROJECT'].includes(oldScope)) return ['PERSON_GLOBAL', 'OWNER_GLOBAL', 'GLOBAL'].includes(nextScope)
  if (['PERSON_GLOBAL', 'OWNER_GLOBAL'].includes(oldScope)) return nextScope === 'GLOBAL'
  return false
}

function Rows({ language, rows, fields, linkField, base }: { language: Language; rows: Row[]; fields: string[]; linkField?: string; base?: string }) {
  if (!rows?.length) return <p className="quiet-note">{copy(language, '暂无记录', 'No records')}</p>
  return <div className="console-rows">{rows.map((row, i) => <div className="console-row" key={String(row.id ?? i)}>{fields.map(field => {
    const label = fieldNames[field]?.[language === 'zh-CN' ? 0 : 1] ?? field
    const raw = row[field]
    const value = ['type', 'scope', 'source', 'priorityClass', 'status'].includes(field) ? enumLabel(language, raw) : field === 'reason' ? memoryReasonLabel(language, raw) : field === 'effective' ? (raw ? copy(language, '是', 'Yes') : copy(language, '否', 'No')) : String(raw ?? '—')
    return <span key={field}><small>{label}</small> {linkField && field === 'content' ? <Link to={`${base}${row[linkField]}`}>{value}</Link> : value}</span>
  })}</div>)}</div>
}

function Page({ language, title, children, loading, error, retry }: { language: Language; title: string; children?: ReactNode; loading?: boolean; error?: string; retry?: () => void }) {
  return <div className="page people-memory-page"><div className="page-heading"><h1>{title}</h1></div>{loading ? <p role="status">{copy(language, '正在加载', 'Loading')}</p> : error ? <div role="alert">{error} <button onClick={retry}>{copy(language, '重试', 'Retry')}</button></div> : children}</div>
}

function safeError(error: unknown, language: Language) {
  if (error instanceof ApiError && error.status === 409) return copy(language, '记录已被修改，请刷新后重试', 'This record changed; refresh and retry')
  if (error instanceof ApiError && language === 'en-US') {
    if (error.status === 400) return 'The request is invalid. Check the selected values and time range.'
    if (error.status === 401) return 'Your session expired. Sign in again.'
    if (error.status === 403) return 'This operation is not allowed.'
    if (error.status === 404) return 'The requested record was not found.'
    return 'The request could not be completed. Retry or contact an administrator.'
  }
  return apiErrorText(error, language)
}
