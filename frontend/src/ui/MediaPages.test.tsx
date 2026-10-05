import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StickersPage } from './StickersPage'
import { SlangPage } from './SlangPage'
import { VoicePage } from './VoicePage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('./api', async () => ({...await vi.importActual<typeof import('./api')>('./api'),api}))

const sticker = { id:'00000000-0000-0000-0000-000000000001',path:'library/test.png',sha256:'a'.repeat(64),tags:['开心'],note:'回应称赞',usageCount:0,source:'console-scan',createdAt:'2026-10-01T00:00:00Z',fileName:'test.png',mimeType:'image/png',fileSize:68,width:1,height:1,animated:false,enabled:true,updatedAt:'2026-10-01T00:00:00Z' }
const candidate = { id:'00000000-0000-0000-0000-000000000002',term:'绝绝子',meaning:'非常好',status:'CANDIDATE',source:'ADMIN_MANUAL',sources:['ADMIN_MANUAL'],evidenceCount:0,evidencePreview:[],createdAt:'2026-10-01T00:00:00Z',updatedAt:'2026-10-01T00:00:00Z' }
const slangPage = { items:[candidate],total:1,page:0,pageSize:20,webResearch:'DISABLED' }
const voice = {status:'PARTIAL_METADATA_ONLY',receiveContentTypes:['record'],recordPayloadStoredAsAudioAsset:false,transcription:false,tts:false,voiceSend:false,sendTest:false,rateSetting:false,providerSettings:false,supportedSendMediaTypes:[],message:'Only record-segment metadata is recognized.'}
beforeEach(()=>api.mockReset())
afterEach(()=>cleanup())

describe('Sticker, slang and voice admin pages',()=>{
  it('edits sticker metadata and exposes disabled send-test truth',async()=>{
    api.mockImplementation(async(path:string,options?:RequestInit)=>{const target=typeof path==='string'?path:'';if(target.startsWith('/api/stickers/admin?'))return {items:[sticker],total:1,page:0,pageSize:24,maxFileBytes:10485760,sendTest:'UNSUPPORTED_ADMIN_SAFE_PATH_NOT_AVAILABLE',formats:{}};if(options?.method==='PATCH')return {...sticker,tags:['开心','猫猫'],note:'新备注'};return {}})
    render(<StickersPage language="zh-CN"/>);await screen.findByRole('button',{name:/test.png/});fireEvent.click(screen.getByRole('button',{name:/test.png/}));fireEvent.change(screen.getByLabelText('标签（逗号分隔）'),{target:{value:'开心, 猫猫'}});fireEvent.change(screen.getByLabelText('使用备注'),{target:{value:'新备注'}});fireEvent.click(screen.getByRole('button',{name:'保存元数据'}));await waitFor(()=>expect(api).toHaveBeenCalledWith(expect.stringMatching('/api/stickers/admin/00000000-0000-0000-0000-000000000001'),expect.objectContaining({method:'PATCH'})));expect(await screen.findByText(/没有管理员测试发送安全入口/)).toBeInTheDocument()
  })
  it('reviews slang with explicit status actions and labels manual provenance',async()=>{
    api.mockImplementation(async(path:string,options?:RequestInit)=>{const target=typeof path==='string'?path:'';if(target==='/api/slang/admin'&&options?.method==='POST')return {...candidate,term:'新词',status:'CANDIDATE'};if(target.includes('/api/slang/admin?'))return slangPage;if(options?.method==='PATCH')return {...candidate,status:JSON.parse(String(options.body)).action==='CONFIRM'?'CONFIRMED':'REJECTED'};return {}})
    render(<SlangPage language="zh-CN"/>);await screen.findByRole('row',{name:/绝绝子/});expect(screen.getByText('ADMIN_MANUAL')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'确认'}));await waitFor(()=>expect(api).toHaveBeenCalledWith(expect.stringContaining('/api/slang/admin/'),expect.objectContaining({method:'PATCH'})));fireEvent.change(screen.getByLabelText('词条'),{target:{value:'新词'}});fireEvent.change(screen.getByLabelText('释义'),{target:{value:'人工释义'}});fireEvent.click(screen.getByRole('button',{name:'添加候选词'}));await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/slang/admin',expect.objectContaining({method:'POST'})));expect(screen.getByText(/网络研究：关闭/)).toBeInTheDocument()
  })
  it('reports real voice metadata capability without invented controls',async()=>{
    api.mockResolvedValue(voice);render(<VoicePage language="en-US"/>);await screen.findByRole('heading',{name:'Voice capabilities'});expect(screen.getByText('record')).toBeInTheDocument();expect(screen.getAllByText('Unsupported').length).toBeGreaterThan(3);expect(screen.queryByLabelText(/rate|speaker|emotion/i)).not.toBeInTheDocument()
  })
})
