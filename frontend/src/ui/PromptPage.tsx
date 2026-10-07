import { useEffect, useState } from 'react'
import { api } from './api'
import type { Language } from './i18n'

type Layer = 'PERSONA' | 'SIMULATION'
type BuiltInPrompt = { id: string; layer: Layer; content: string; version: string; sha256: string; chars: number; estimatedTokens: number; source: 'BUILT_IN'; mutable: false }
type PageData = { items: { id: string; version: number; createdAt: string; createdBy: string; note: string; checksum: string; estimatedTokens: number; active: false; legacyActive: boolean; source: string }[]; total: number }
type Composition = { precedence: { layer: string; label: string; version?: string | number; sha256?: string; chars?: number; estimatedTokens?: number; source?: string; mutable?: boolean }[] }
type Security = { readOnly: true; version: string; summary: string; content: string }

export function PromptPage({ layer, language }: { layer: Layer; language: Language }) {
  const zh = language === 'zh-CN'
  const t = (cn: string, en: string) => zh ? cn : en
  const [current, setCurrent] = useState<BuiltInPrompt | null>(null)
  const [history, setHistory] = useState<PageData | null>(null)
  const [composition, setComposition] = useState<Composition | null>(null)
  const [security, setSecurity] = useState<Security | null>(null)
  const [error, setError] = useState('')

  const refresh = async () => {
    setError('')
    try {
      const base = `/api/prompts/${layer}`
      const [active, versions, order, policy] = await Promise.all([
        api<BuiltInPrompt>(`${base}/current`), api<PageData>(`${base}/history?page=0&size=20`),
        api<Composition>('/api/prompts/composition'), api<Security>('/api/prompts/security'),
      ])
      setCurrent(active); setHistory(versions); setComposition(order); setSecurity(policy)
    } catch { setError(t('无法读取 Core 内建提示词状态。','Unable to load the Core built-in prompt status.')) }
  }
  useEffect(() => { void refresh() }, [layer, language])

  if (error && !current) return <main className="page"><div role="alert">{error} <button className="button secondary" onClick={() => void refresh()}>{t('重新加载','Reload')}</button></div></main>
  if (!current) return <main className="page" role="status">{t('正在加载…','Loading…')}</main>

  const persona = layer === 'PERSONA'
  const title = persona ? t('人格（Persona）','Persona') : t('仿真规则（Simulation）','Simulation')
  const versionLabel = persona ? t('Legacy Recovered','Legacy Recovered') : t('Core 内建','Built into Core')
  return <main className={`page prompt-page prompt-${layer.toLowerCase()}`}>
    <div className="page-heading"><div><p className="eyebrow">{t('固定运行基线','Immutable runtime baseline')}</p><h1>{title}</h1><p>{t('提示词由 XingChen Core 资源提供，数据库历史不会覆盖当前运行版本。','Prompt text is bundled with XingChen Core; database history cannot override the active runtime baseline.')}</p></div><span className="status-pill"><i />{t('Core 内建 · 只读','Built-in · Read-only')}</span></div>
    {error && <p className="inline-error" role="alert">{error} <button className="button secondary" onClick={() => void refresh()}>{t('重新加载','Reload')}</button></p>}
    <section className="settings-section prompt-baseline-meta">
      <h2>{t('运行基线信息','Runtime baseline')}</h2>
      <dl><dt>{t('状态','Status')}</dt><dd>{t('Core 内建 · 只读','Built into Core · read-only')}</dd>
        <dt>{t('来源','Source')}</dt><dd>{current.source}</dd><dt>{t('版本','Version')}</dt><dd>{versionLabel}</dd>
        <dt>SHA-256</dt><dd><code>{current.sha256}</code></dd><dt>{t('字符数','Characters')}</dt><dd>{current.chars}</dd></dl>
      <p>{t('人格与仿真规则不再提供运行时编辑、保存、切换或回滚操作。旧数据库版本仅保留为历史证据。','Persona and Simulation have no runtime edit, save, activation, or rollback operations. Database versions are retained only as historical evidence.')}</p>
    </section>
    <section className="settings-section"><h2>{title}</h2><pre className="prompt-preview" aria-readonly="true">{current.content}</pre></section>
    <section className="settings-section"><h2>{t('提示词组装顺序','Prompt assembly order')}</h2><ol className="prompt-precedence">{composition?.precedence.map(part=><li key={part.layer}><strong>{part.label}</strong>{part.version!==undefined&&<span> · {part.version}</span>}{part.source&&<small> · {part.source} · SHA-256 {part.sha256?.slice(0,12)}…</small>}</li>)}</ol>
      <p>{t('Identity、Relationship 与 Memory 仍由运行时动态提供；Persona 与 Simulation 各注入一次。Hard Security Policy 仍独立优先。','Identity, Relationship, and Memory remain dynamic at runtime. Persona and Simulation are each injected once; Hard Security Policy remains independently first.')}</p>
    </section>
    <section className="settings-section"><h2>{t('硬性安全策略（只读）','Hard Security Policy (read-only)')}</h2><p>{security?.summary}</p><pre className="prompt-preview security-preview" aria-readonly="true">{security?.content}</pre></section>
    <section className="settings-section"><h2>{t('旧数据库版本（仅历史）','Legacy database versions (history only)')}</h2><p>{t('历史记录保留，不会成为 active prompt，也不能通过 Console 回滚。','Historical records are retained, are never active prompts, and cannot be activated through the Console.')}</p>
      {history?.total===0 ? <p>{t('当前数据库没有旧版本记录。','No legacy versions are present in the current database.')}</p> : <ul className="prompt-history">{history?.items.map(item=><li key={item.id}>v{item.version} · {item.createdAt} · {item.createdBy} · SHA-256 {item.checksum}</li>)}</ul>}
    </section>
  </main>
}
