import { useEffect, useMemo, useRef, useState } from 'react'
import { api, ApiError, apiErrorText } from './api'
import type { Language } from './i18n'

type Layer = 'PERSONA' | 'SIMULATION'
type Version = { id: string; layer: Layer; version: number; content: string; createdAt: string; createdBy: string; note: string; active?: boolean; estimatedTokens: number }
type Current = Version & { revision: number }
type PageData = { items: Omit<Version, 'content'>[]; page: number; size: number; total: number }

export function PromptPage({ layer, language }: { layer: Layer; language: Language }) {
  const zh = language === 'zh-CN'
  const text = (cn: string, en: string) => zh ? cn : en
  const title = layer === 'PERSONA' ? text('人格', 'Persona') : text('行为模拟提示词', 'Simulation prompt')
  const layerLabel=(value:string)=>({HARD_SECURITY:text('硬性安全策略','Hard Security Policy'),SIMULATION:text('行为模拟提示词','Simulation Prompt'),PERSONA:text('人格提示词','Persona Prompt'),RUNTIME:text('身份 / 关系 / 记忆 / 运行上下文','Identity / Relationship / Memory / Runtime Context'),USER:text('用户消息','User Message')}[value]??value)
  const [current, setCurrent] = useState<Current | null>(null)
  const [history, setHistory] = useState<PageData | null>(null)
  const [composition, setComposition] = useState<{ precedence: { layer: string; label: string; version?: number | string; estimatedTokens?: number }[] } | null>(null)
  const [security, setSecurity] = useState<{ readOnly: boolean; version: string; summary: string; content: string } | null>(null)
  const [draft, setDraft] = useState(''); const [note, setNote] = useState(''); const [selected, setSelected] = useState<Version | null>(null)
  const [compare, setCompare] = useState<Version | null>(null); const [notice, setNotice] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const [rollbackTarget, setRollbackTarget] = useState<Version | null>(null)
  const rollbackDialog = useRef<HTMLDialogElement>(null)
  const rollbackTrigger = useRef<HTMLElement | null>(null)
  useEffect(() => {
    if (!rollbackTarget) return
    const dialog = rollbackDialog.current
    dialog?.showModal()
    dialog?.querySelector<HTMLButtonElement>('[data-cancel]')?.focus()
    return () => { if (dialog?.open) dialog.close(); rollbackTrigger.current?.focus() }
  }, [rollbackTarget])
  const base = `/api/prompts/${layer}`
  const refresh = async () => {
    setError('')
    try { const [active, page, order, policy] = await Promise.all([api<Current>(`${base}/current`), api<PageData>(`${base}/history?page=0&size=20`), api<typeof composition>('/api/prompts/composition'), api<typeof security>('/api/prompts/security')]); setCurrent(active); setDraft(active.content); setHistory(page); setComposition(order); setSecurity(policy) }
    catch { setError(text('无法加载提示词配置。', 'Unable to load prompt configuration.')) }
  }
  useEffect(() => { void refresh() }, [base])
  useEffect(() => { const unload = (event: BeforeUnloadEvent) => { if (current && draft !== current.content) { event.preventDefault(); event.returnValue = '' } }; window.addEventListener('beforeunload', unload); return () => window.removeEventListener('beforeunload', unload) }, [draft, current])
  useEffect(() => { const guard = (event: MouseEvent) => { if (!current || draft === current.content) return; const target = event.target instanceof Element ? event.target.closest('a[href]') as HTMLAnchorElement | null : null; if (target && target.origin === location.origin && target.pathname !== location.pathname && !window.confirm(text('有未保存的提示词更改，确定离开吗？','You have unsaved prompt changes. Leave this page?'))) { event.preventDefault(); event.stopImmediatePropagation() } }; document.addEventListener('click', guard, true); return () => document.removeEventListener('click', guard, true) }, [draft, current, text])
  const changed = !!current && draft !== current.content
  const tokens = useMemo(() => Math.max(0, Math.ceil(draft.length / 4)), [draft])
  const loadHistory = async (page: number) => { try { setHistory(await api<PageData>(`${base}/history?page=${page}&size=20`)) } catch { setError(text('无法加载历史版本。','Unable to load prompt history.')) } }
  const openVersion = async (id: string) => { try { const value = await api<Version>(`${base}/versions/${id}`); setSelected(value); setCompare(null) } catch { setError(text('无法读取此版本。','Unable to load this version.')) } }
  const save = async () => { if (!current || !changed) return; setBusy(true); setNotice(''); setError(''); try { await api(`${base}/versions`, { method: 'POST', body: JSON.stringify({ content: draft, note, expectedActiveVersionId: current.id }) }); setNotice(text('已创建并启用新版本。','A new version was created and activated.')); setNote(''); await refresh() } catch (cause) { if (cause instanceof ApiError && cause.status === 409) setError(text('提示词已在其他位置更新。请重新加载后查看差异；未覆盖新版本。','Prompt was updated elsewhere. Reload and compare; the newer version was not overwritten.')); else setError(apiErrorText(cause,language)) } finally { setBusy(false) } }
  const rollback = async () => { if (!current || !rollbackTarget) return; setBusy(true); setError(''); try { await api(`${base}/rollback`, { method: 'POST', body: JSON.stringify({ targetVersionId: rollbackTarget.id, expectedActiveVersionId: current.id }) }); setRollbackTarget(null); setNotice(text('已从历史内容创建并启用一个新版本。','A new active version was created from the historical content.')); await refresh() } catch (cause) { setRollbackTarget(null); setError(apiErrorText(cause,language)) } finally { setBusy(false) } }
  const compareWithActive = async (item: Pick<Version, 'id'>) => { if (!current) return; try { const old = await api<Version>(`${base}/versions/${item.id}`); const active = await api<Version>(`${base}/versions/${current.id}`); setSelected(old); setCompare(active) } catch { setError(text('无法比较所选版本。','Unable to compare the selected versions.')) } }
  const compareLatestAfterConflict = async () => { if (!current) return; try { const latest=await api<Current>(`${base}/current`);const [old,next]=await Promise.all([api<Version>(`${base}/versions/${current.id}`),api<Version>(`${base}/versions/${latest.id}`)]);setSelected(old);setCompare(next) } catch { setError(text('无法读取最新版本用于比较。','Unable to load the latest version for comparison.')) } }
  if (error && !current) return <main className="page"><div role="alert">{error} <button className="button secondary" onClick={() => void refresh()}>{text('重新加载','Reload')}</button></div></main>
  if (!current) return <main className="page" role="status">{text('正在加载…','Loading…')}</main>
  return <main className={`page prompt-page prompt-${layer.toLowerCase()}`}>
    <div className="page-heading"><div><p className="eyebrow">{text('人格与行为','Persona & behavior')}</p><h1>{title}</h1><p>{layer === 'PERSONA' ? text('定义机器人是谁，以及表达风格。','Define who the bot is and how it expresses itself.') : text('定义机器人在社交环境中如何行动。','Define how the bot acts in social settings.')}</p></div><span className="status-pill"><i />{text('当前版本 v','Active v')}{current.version}</span></div>
    {notice && <p className="notice" role="status">{notice}</p>}{error && <p id="prompt-error" className="inline-error" role="alert">{error} {error.includes('提示词已在其他位置更新')||error.includes('Prompt was updated elsewhere') ? <button className="button secondary" onClick={() => void compareLatestAfterConflict()}>{text('查看最新版本差异','Compare latest version')}</button> : null} <button className="button secondary" onClick={() => void refresh()}>{text('重新加载','Reload')}</button></p>}
    <section className="settings-section"><h2>{text('编辑当前版本','Edit active version')}</h2><p>{text('未保存更改仅保留在当前页面状态，不会写入浏览器存储。','Unsaved edits stay in this page state and are never stored in browser storage.')}</p>
      <label htmlFor="prompt-editor">{title}</label><textarea id="prompt-editor" className="prompt-editor" maxLength={30000} value={draft} onChange={e => setDraft(e.target.value)} aria-invalid={!!error} aria-describedby={error ? 'prompt-count prompt-error' : 'prompt-count'} />
      <div id="prompt-count" className="prompt-meta"><span>{draft.length.toLocaleString()} / 30,000 {text('字符','characters')}</span><span>{text('估算','Estimate')}: ~{tokens.toLocaleString()} {text('tokens；非计费精确值','tokens; not exact billing tokens')}</span></div>
      {tokens > 5000 && <p className="prompt-warning" role="status">{text('提示词可能显著占用上下文预算。','This prompt may use a significant portion of the context budget.')}</p>}
      <label htmlFor="prompt-note">{text('版本备注（可选）','Version note (optional)')}</label><input id="prompt-note" maxLength={240} value={note} aria-invalid={!!error} aria-describedby={error?'prompt-error':undefined} onChange={e => setNote(e.target.value)} />
      {changed && <p className="prompt-dirty" role="status">{text('未保存更改','Unsaved changes')}</p>}<button className="button primary" disabled={!changed || busy || draft.trim().length === 0} onClick={() => void save()}>{busy ? text('处理中…','Working…') : text('保存为新版本并启用','Save as new active version')}</button>
    </section>
    <section className="settings-section"><h2>{text('版本历史','Version history')}</h2><div className="prompt-history">{history?.items.map(item => <article className={`prompt-version ${item.active ? 'active' : ''}`} key={item.id}><button className="prompt-version-title" onClick={() => void openVersion(item.id)}>{text('版本 v','Version v')}{item.version}{item.active ? ` · ${text('当前','Active')}` : ''}</button><p>{item.createdAt} · {item.createdBy} · ~{item.estimatedTokens} tokens</p>{item.note && <p>{item.note}</p>}<div className="prompt-actions"><button className="button secondary" onClick={() => void compareWithActive(item)} disabled={item.active}>{text('与当前比较','Compare with active')}</button>{!item.active && <button className="button secondary" onClick={() => void openVersion(item.id)}>{text('预览 / 回滚','Preview / rollback')}</button>}</div></article>)}</div>
      {history && history.total > history.size && <div className="prompt-actions"><button className="button secondary" disabled={history.page===0} onClick={() => void loadHistory(history.page-1)}>{text('上一页','Previous')}</button><span>{history.page+1} / {Math.ceil(history.total/history.size)}</span><button className="button secondary" disabled={(history.page+1)*history.size>=history.total} onClick={() => void loadHistory(history.page+1)}>{text('下一页','Next')}</button></div>}
    </section>
    {selected && <section className="settings-section" aria-live="polite"><h2>{text('版本预览','Version preview')} · v{selected.version}</h2><p>{selected.createdAt} · {selected.createdBy} · ~{selected.estimatedTokens} tokens</p><pre className="prompt-preview">{selected.content}</pre>{!selected.active && <button className="button secondary" onClick={e => { rollbackTrigger.current=e.currentTarget; setRollbackTarget(selected) }} disabled={busy}>{text('回滚到此内容','Rollback to this content')}</button>}</section>}
    {compare && selected && <section className="settings-section"><h2>{text('版本比较','Version comparison')} · v{selected.version} → v{compare.version}</h2><PromptDiff left={selected.content} right={compare.content} language={language} /></section>}
    <section className="settings-section"><h2>{text('提示词优先级','Prompt precedence')}</h2><ol className="prompt-precedence">{composition?.precedence.map((part,index)=><li key={part.layer}><strong>{index+1}. {layerLabel(part.layer)}</strong>{part.version !== undefined && <span> · v{part.version}</span>}{part.estimatedTokens !== undefined && <small> · ~{part.estimatedTokens} tokens</small>}</li>)}</ol><p>{text('Hard Security Policy 由可信系统固定；Persona 与 Simulation 无法覆盖它。以下层级是结构顺序，不会拼接其他用户或会话的私密内容。','Hard Security Policy is fixed by the trusted system and cannot be overridden by Persona or Simulation. This is a structural order; private conversation content is not assembled here.')}</p></section>
    <section className="settings-section"><h2>{text('硬性安全策略（只读）','Hard Security Policy (read-only)')}</h2><p>{text('可信系统固定的策略原文；管理界面不能修改。','Fixed trusted-system policy text; not editable here.')} · {security?.version}</p><pre className="prompt-preview security-preview" aria-readonly="true">{security?.content}</pre></section>
    <dialog ref={rollbackDialog} id="prompt-rollback-dialog" className="dialog" aria-labelledby="prompt-rollback-title" aria-describedby="prompt-rollback-description" onKeyDown={event=>{if(event.key!=='Tab')return;const buttons=rollbackDialog.current?.querySelectorAll<HTMLButtonElement>('button:not([disabled])');if(!buttons?.length)return;const first=buttons[0],last=buttons[buttons.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}} onCancel={()=>setRollbackTarget(null)} onClose={()=>setRollbackTarget(null)}><h2 id="prompt-rollback-title">{text('确认回滚','Confirm rollback')}</h2><p id="prompt-rollback-description">{text(`当前版本 v${current.version}；目标历史版本 v${rollbackTarget?.version}。回滚将创建并启用一个新版本，不会删除后续历史。`,`Current version v${current.version}; target historical version v${rollbackTarget?.version}. Rollback creates a new active version and keeps later history.`)}</p><div className="dialog-actions"><button className="button secondary" data-cancel onClick={()=>setRollbackTarget(null)}>{text('取消','Cancel')}</button><button className="button primary" data-confirm disabled={busy} onClick={()=>void rollback()}>{text('创建回滚版本','Create rollback version')}</button></div></dialog>
  </main>
}

function PromptDiff({ left, right, language }: { left: string; right: string; language: Language }) {
  const zh = language === 'zh-CN'; const lines = lineDiff(left, right)
  return <div className="prompt-diff" aria-label={zh ? '逐行版本差异' : 'Line-by-line version diff'}>{lines.map((line,index)=><div key={index} className={`diff-line diff-${line.kind}`}><span aria-hidden="true">{line.kind==='added'?'+':line.kind==='removed'?'−':' '}</span><code>{line.text || ' '}</code><small>{line.kind==='added'?(zh?'新增':'Added'):line.kind==='removed'?(zh?'删除':'Removed'):(zh?'未变':'Unchanged')}</small></div>)}</div>
}
function lineDiff(before: string, after: string): { kind: 'added'|'removed'|'same'; text: string }[] {
  const a=before.split(/\r?\n/),b=after.split(/\r?\n/); if(a.length*b.length>1_000_000)return [...a.map(text=>({kind:'removed' as const,text})),...b.map(text=>({kind:'added' as const,text}))]
  const dp=Array.from({length:a.length+1},()=>new Uint32Array(b.length+1));for(let i=a.length-1;i>=0;i--)for(let j=b.length-1;j>=0;j--)dp[i][j]=a[i]===b[j]?dp[i+1][j+1]+1:Math.max(dp[i+1][j],dp[i][j+1]);
  const out:{kind:'added'|'removed'|'same';text:string}[]=[];let i=0,j=0;while(i<a.length&&j<b.length){if(a[i]===b[j]){out.push({kind:'same',text:a[i++]});j++}else if(dp[i+1][j]>=dp[i][j+1])out.push({kind:'removed',text:a[i++]});else out.push({kind:'added',text:b[j++]})}while(i<a.length)out.push({kind:'removed',text:a[i++]});while(j<b.length)out.push({kind:'added',text:b[j++]});return out
}
