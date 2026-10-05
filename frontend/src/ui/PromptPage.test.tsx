import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PromptPage } from './PromptPage'
import { ApiError } from './api'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('./api', async importOriginal => ({...await importOriginal<typeof import('./api')>(), api }))
const old={id:'v1',layer:'PERSONA',version:1,content:'你是大肥鱼',createdAt:'2026-10-01T00:00:00Z',createdBy:'admin',note:'',estimatedTokens:4,active:false}
const current={...old,id:'v2',version:2,content:'你是温柔的大肥鱼',active:true,revision:2}
const history={items:[{...current,content:undefined},{...old,content:undefined}],page:0,size:20,total:2}
const composition={precedence:[{layer:'HARD_SECURITY',label:'Hard Security Policy',version:'built-in',estimatedTokens:30},{layer:'SIMULATION',label:'Simulation Prompt',version:1,estimatedTokens:2},{layer:'PERSONA',label:'Persona Prompt',version:2,estimatedTokens:4},{layer:'RUNTIME',label:'Identity / Relationship / Memory / Runtime Context'},{layer:'USER',label:'User Message'}]}
const security={readOnly:true,version:'built-in',summary:'Trusted system policy',content:'Server-side checks are authoritative.'}
function replies(){api.mockImplementation(async(path:string,options?:RequestInit)=>{if(path.endsWith('/current'))return {...current};if(path.endsWith('/history?page=0&size=20'))return history;if(path.includes('/composition'))return composition;if(path.endsWith('/security'))return security;if(path.endsWith('/versions/v1'))return old;if(path.endsWith('/versions/v2'))return current;if(options?.method==='POST'&&path.endsWith('/versions'))return current;return {}})}
beforeEach(()=>{HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}})
afterEach(()=>{cleanup();api.mockReset();vi.restoreAllMocks()})

describe('prompt administration page',()=>{
  it('keeps Persona separate, shows active history and sends a versioned save without browser persistence',async()=>{
    replies();const {container}=render(<PromptPage layer="PERSONA" language="zh-CN"/>);expect(await screen.findByRole('heading',{name:'人格'})).toBeInTheDocument();await waitFor(()=>expect(container.querySelector('.status-pill')).toHaveTextContent('当前版本 v2'));
    const editor=screen.getByLabelText('人格');fireEvent.change(editor,{target:{value:'persona draft'}});expect(screen.getByRole('status')).toHaveTextContent('未保存更改');fireEvent.change(screen.getByLabelText('版本备注（可选）'),{target:{value:'语气调整'}});fireEvent.click(screen.getByRole('button',{name:'保存为新版本并启用'}));
    await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/prompts/PERSONA/versions',expect.objectContaining({method:'POST',body:JSON.stringify({content:'persona draft',note:'语气调整',expectedActiveVersionId:'v2'})})));expect(container.querySelectorAll('textarea')).toHaveLength(1);expect(localStorage.getItem('persona draft')).toBeNull();
  })
  it('reports optimistic conflicts without replacing the draft and renders a textual diff',async()=>{
    let currentReads=0;const latest={...current,id:'v3',version:3,content:'server latest prompt',active:true};replies();api.mockImplementation(async(path:string,options?:RequestInit)=>{if(path.endsWith('/current'))return ++currentReads===1?current:latest;if(path.endsWith('/history?page=0&size=20'))return history;if(path.includes('/composition'))return composition;if(path.endsWith('/security'))return security;if(path.includes('/versions/'))return path.endsWith('v1')?old:path.endsWith('v3')?latest:current;if(options?.method==='POST')throw new ApiError(409,'STATE_CONFLICT','conflict');return {}})
    render(<PromptPage layer="PERSONA" language="zh-CN"/>);await screen.findByRole('heading',{name:'人格'});fireEvent.change(screen.getByLabelText('人格'),{target:{value:'unsaved wording'}});fireEvent.click(screen.getByRole('button',{name:'保存为新版本并启用'}));expect(await screen.findByRole('alert')).toHaveTextContent('提示词已在其他位置更新');expect(screen.getByLabelText('人格')).toHaveValue('unsaved wording');
    fireEvent.click(screen.getByRole('button',{name:'查看最新版本差异'}));expect(await screen.findByLabelText('逐行版本差异')).toBeInTheDocument();expect(screen.getByText('server latest prompt')).toBeInTheDocument();expect(screen.getAllByText('你是温柔的大肥鱼')).toHaveLength(2);
  })
  it('uses native rollback confirmation and exposes the immutable security policy as read-only text',async()=>{
    replies();render(<PromptPage layer="PERSONA" language="en-US"/>);await screen.findByRole('heading',{name:'Persona'});expect(screen.getByText('Server-side checks are authoritative.')).toBeInTheDocument();expect(screen.getByLabelText('Persona')).toHaveAttribute('maxLength','30000');fireEvent.click(screen.getAllByRole('button',{name:'Preview / rollback'})[0]);expect(await screen.findByRole('heading',{name:/Version preview/})).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'Rollback to this content'}));const dialog=document.getElementById('prompt-rollback-dialog')!;expect(dialog).toHaveAttribute('open');expect(dialog).toHaveTextContent('keeps later history');fireEvent.click(screen.getByRole('button',{name:'Cancel'}));expect(dialog).not.toHaveAttribute('open');
  })
})
