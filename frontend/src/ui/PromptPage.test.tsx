import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PromptPage } from './PromptPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('./api', async importOriginal => ({...await importOriginal<typeof import('./api')>(), api }))
const persona={id:'BUILT_IN:persona',layer:'PERSONA',version:'BUILT_IN',content:'You are a synthetic CI persona.',sha256:'01a694ed6be58e2c8c92a7db5a288f615c30aa222f2f7d10cace3af3ba262cad',chars:31,estimatedTokens:8,source:'BUILT_IN',mutable:false}
const simulation={id:'BUILT_IN:simulation',layer:'SIMULATION',version:'BUILT_IN',content:'Synthetic CI simulation rules.',sha256:'8a111de40876087249bf0bb9e45f82e31cf39a46391f807b99cf8810920deaa7',chars:30,estimatedTokens:6,source:'BUILT_IN',mutable:false}
const history={items:[{id:'old-v1',version:1,createdAt:'2026-10-01T00:00:00Z',createdBy:'admin',note:'old',checksum:'legacy-hash',estimatedTokens:4,active:false,legacyActive:true,source:'LEGACY_DATABASE_HISTORY'}],page:0,size:20,total:1}
const composition={precedence:[{layer:'HARD_SECURITY',label:'Hard Security Policy',version:'built-in'},{layer:'IDENTITY',label:'Identity'},{layer:'PERSONA',label:'Built-in Persona',version:'BUILT_IN',source:'BUILT_IN',sha256:persona.sha256},{layer:'SIMULATION',label:'Built-in Simulation',version:'BUILT_IN',source:'BUILT_IN',sha256:simulation.sha256},{layer:'RELATIONSHIP',label:'Relationship'},{layer:'MEMORY',label:'Memory'},{layer:'RUNTIME',label:'Runtime Context'}]}
const security={readOnly:true,version:'built-in',summary:'Trusted system policy',content:'Server-side checks are authoritative.'}
function replies(){api.mockImplementation(async(path:string)=>{if(path.endsWith('/PERSONA/current'))return persona;if(path.endsWith('/SIMULATION/current'))return simulation;if(path.endsWith('/history?page=0&size=20'))return history;if(path.includes('/composition'))return composition;if(path.endsWith('/security'))return security;return {}})}
afterEach(()=>{cleanup();api.mockReset();vi.restoreAllMocks()})

describe('immutable prompt baseline page',()=>{
  it('shows recovered Persona as read-only and preserves legacy records as history only',async()=>{
    replies();render(<PromptPage layer="PERSONA" language="zh-CN"/>);
    expect(await screen.findByRole('heading',{name:'人格（Persona）',level:1})).toBeInTheDocument()
    expect(screen.getAllByText('Core 内建 · 只读')).toHaveLength(2);expect(screen.getByText('Legacy Recovered')).toBeInTheDocument()
    expect(screen.getByText(persona.sha256)).toBeInTheDocument();expect(screen.getByText('You are a synthetic CI persona.')).toBeInTheDocument()
    expect(screen.getByText(/旧数据库版本仅保留为历史证据/)).toBeInTheDocument();expect(screen.getByText(/v1 · 2026-10-01/)).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:/保存|回滚|rollback/i})).not.toBeInTheDocument()
  })

  it('shows Simulation as an independent immutable Core resource',async()=>{
    replies();render(<PromptPage layer="SIMULATION" language="en-US"/>);
    expect(await screen.findByRole('heading',{name:'Simulation',level:1})).toBeInTheDocument()
    expect(screen.getByText('Built into Core')).toBeInTheDocument();expect(screen.getByText(simulation.sha256)).toBeInTheDocument()
    expect(screen.getByText('Synthetic CI simulation rules.')).toBeInTheDocument();expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('displays the prompt order and read-only Hard Security Policy',async()=>{
    replies();render(<PromptPage layer="PERSONA" language="en-US"/>);
    expect(await screen.findByText('Identity')).toBeInTheDocument();expect(screen.getByText('Built-in Persona')).toBeInTheDocument();
    expect(screen.getByText('Relationship')).toBeInTheDocument();expect(screen.getByText('Memory')).toBeInTheDocument()
    expect(screen.getByText('Server-side checks are authoritative.')).toBeInTheDocument()
  })
})
