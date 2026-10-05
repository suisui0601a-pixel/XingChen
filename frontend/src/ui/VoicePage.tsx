import { useEffect, useState } from 'react'
import { api } from './api'
import type { Language } from './i18n'

type Capabilities = {
  status: string
  receiveContentTypes: string[]
  recordPayloadStoredAsAudioAsset: boolean
  transcription: boolean
  tts: boolean
  voiceSend: boolean
  sendTest: boolean
  rateSetting: boolean
  providerSettings: boolean
  supportedSendMediaTypes: string[]
  message: string
}

export function VoicePage({ language }: { language: Language }) {
  const zh = language === 'zh-CN'
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => { api<Capabilities>('/api/voice/capabilities').then(setCapabilities).catch(() => setFailed(true)) }, [])
  const title = zh ? '语音能力' : 'Voice capabilities'
  return <main className="page media-page voice-page">
    <div className="page-heading"><div><p className="eyebrow">{zh ? '运行时能力' : 'Runtime capabilities'}</p><h1>{title}</h1><p>{zh ? '这里只呈现当前运行时实际提供的语音能力，不显示未实现的合成或发送设置。' : 'This page reports only capabilities provided by the current runtime; it does not imply unavailable synthesis or send settings.'}</p></div></div>
    {failed && <p role="alert">{zh ? '语音能力状态暂时不可用。' : 'Voice capability status is temporarily unavailable.'}</p>}
    {!failed && !capabilities && <p role="status">{zh ? '正在读取能力状态…' : 'Loading capability status…'}</p>}
    {capabilities && <>
      <section className="settings-section voice-status" aria-labelledby="voice-status-title">
        <h2 id="voice-status-title">{zh ? '当前状态' : 'Current status'}</h2>
        <p className="status-pill"><i />{zh ? '仅识别消息中的 record 元数据' : 'Record message metadata only'}</p>
        <p>{zh ? '可识别的消息段：' : 'Recognized message segments: '}<code>{capabilities.receiveContentTypes.join(', ') || '—'}</code></p>
        <p>{zh ? '音频文件已保存：' : 'Audio asset stored: '}<strong>{capabilities.recordPayloadStoredAsAudioAsset ? (zh ? '是' : 'Yes') : (zh ? '否' : 'No')}</strong></p>
      </section>
      <section className="settings-section voice-capability-list" aria-labelledby="voice-capability-title">
        <h2 id="voice-capability-title">{zh ? '功能状态' : 'Feature status'}</h2>
        <Capability label={zh ? '语音转写' : 'Voice transcription'} supported={capabilities.transcription} zh={zh} />
        <Capability label="TTS" supported={capabilities.tts} zh={zh} />
        <Capability label={zh ? '语音发送' : 'Voice send'} supported={capabilities.voiceSend} zh={zh} />
        <Capability label={zh ? '安全测试发送' : 'Safe send test'} supported={capabilities.sendTest} zh={zh} />
        <Capability label={zh ? '语音速率设置' : 'Voice rate setting'} supported={capabilities.rateSetting} zh={zh} />
        <Capability label={zh ? '语音提供商设置' : 'Voice provider settings'} supported={capabilities.providerSettings} zh={zh} />
        <p className="quiet-note">{zh ? '当前只识别消息元数据，不提供语音合成或发送。' : 'Only message metadata is recognized; synthesis and voice sending are unavailable.'}</p>
      </section>
    </>}
  </main>
}

function Capability({ label, supported, zh }: { label: string; supported: boolean; zh: boolean }) {
  return <div className="voice-capability"><span>{label}</span><strong>{supported ? (zh ? '支持' : 'Supported') : (zh ? '不支持' : 'Unsupported')}</strong></div>
}
