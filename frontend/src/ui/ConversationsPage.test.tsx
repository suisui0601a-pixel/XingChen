import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConversationsPage } from './ConversationsPage'
import { ApiError } from './api'
import { translate } from './i18n'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('./api', async () => ({...await vi.importActual<typeof import('./api')>('./api'),api}))

const id='10000000-0000-0000-0000-000000000003'
const conversation={id,platform:'QQ',type:'PRIVATE',platform_conversation_id:'e2e-owner',display_name:'Console Owner',mode:'SOCIAL',wake_state:'SLEEPING',generation:7,runtime_state:'SLEEPING',member_count:2,unknown_count:1,interrupted_count:0,active_turn_count:0}
const detail={...conversation,canWake:true,runtime:{mode:'SOCIAL',wake_state:'SLEEPING',generation:7,waitReason:'',queueDepth:0,active:false,activeTurn:null,interruptedTurn:null,unknownOutbound:[{logicalMessageId:'fixture-…',turnId:'fixture-…',timestamp:'2026-10-01T12:00:00Z',status:'UNKNOWN'}]},context:{currentEstimate:10,thresholds:{soft:100,rollover:150,hard:200},lifecycleState:'NORMAL',recentMessageCount:1,unreadCount:1,session:{},handoff:{status:'NONE'}},dsh:{mappingExists:false,sessionId:'',status:'NONE'},pending:{questions:0,approvals:0},members:{items:[],total:0,page:0,size:20},recentMessages:{items:[],total:0,page:0,size:20}}
const page={items:[conversation],total:1,page:0,size:20}
const t=(key:Parameters<typeof translate>[1])=>translate('zh-CN',key)

function mount(path='/conversations') {
  return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/conversations" element={<ConversationsPage t={t}/>} /><Route path="/conversations/:id" element={<ConversationsPage t={t}/>} /></Routes></MemoryRouter>)
}

afterEach(()=>{cleanup();api.mockReset()})

describe('conversation operations console',()=>{
  it('renders the server-filtered list and submits a bounded state filter',async()=>{
    api.mockResolvedValue(page)
    mount()
    expect(await screen.findByRole('button',{name:/Console Owner/})).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('运行状态'),{target:{value:'WAITING'}})
    await waitFor(()=>expect(api).toHaveBeenCalledWith(expect.stringContaining('state=WAITING')))
  })

  it('shows UNKNOWN no-resend warning and confirms reset with long-term preservation copy',async()=>{
    api.mockImplementation(async(path:string)=>path.includes('/messages?')?detail.recentMessages:path.includes('/api/conversations/')?detail:page)
    mount(`/conversations/${id}`)
    expect(await screen.findByText(t('unknownNoRetry'))).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button',{name:t('resetConversation')}))
    const dialog=screen.getByRole('alertdialog')
    expect(dialog).toHaveTextContent(t('resetPreserveLongTerm'))
    expect(dialog.querySelector('button')).toHaveFocus()
  })

  it('surfaces optimistic mode conflicts and reloads the current detail',async()=>{
    api.mockImplementation(async(path:string,options?:RequestInit)=>{
      if(options?.method==='PATCH')throw new ApiError(409,'STATE_CONFLICT',t('conversationConflict'))
      if(path.includes('/messages?'))return detail.recentMessages
      if(path.includes('/api/conversations/'))return detail
      return page
    })
    mount(`/conversations/${id}`)
    await screen.findByRole('heading',{name:'Console Owner'})
    fireEvent.change(document.getElementById('conversation-mode')!,{target:{value:'CLOSED_AGENT'}})
    await waitFor(()=>expect(screen.getByRole('button',{name:t('saveMode')})).toBeEnabled())
    fireEvent.click(screen.getByRole('button',{name:t('saveMode')}))
    expect(await screen.findByRole('status')).toHaveTextContent(t('conversationConflict'))
    expect(api).toHaveBeenCalledWith(`/api/conversations/${id}/mode`,expect.objectContaining({method:'PATCH'}))
  })
})
