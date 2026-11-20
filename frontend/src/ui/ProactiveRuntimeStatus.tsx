import {useEffect,useState} from 'react'
import {api} from './api'
type Status={running:boolean;inFlight:number;globalHourlyTurnLimit:number;history:{outcomes:{status:string;count:number}[]}}
const names:Record<string,string>={SENT:'已发送',NO_REPLY:'模型选择不发言',SKIPPED_RANDOM:'概率未命中',CANCELLED:'已取消',FAILED:'失败',UNKNOWN:'发送结果未知',INTERRUPTED:'重启中断，未重放',RATE_LIMITED:'限流拒绝',CLAIMED:'执行中'}
export function ProactiveRuntimeStatus({zh}:{zh:boolean}){
  const [status,setStatus]=useState<Status|null>(null),[error,setError]=useState(false),[synced,setSynced]=useState('')
  useEffect(()=>{
    let active=true,request:AbortController|undefined
    const load=async()=>{if(document.visibilityState==='hidden')return;request?.abort();request=new AbortController()
      try{const value=await api<Status>('/api/social-settings/proactive-status',{signal:request.signal});if(active){setStatus(value);setError(false);setSynced(new Date().toLocaleTimeString())}}
      catch{if(active)setError(true)}
    }
    void load();const interval=window.setInterval(()=>void load(),30000)
    return()=>{active=false;request?.abort();window.clearInterval(interval)}
  },[])
  return <section className="settings-section" aria-label={zh?'主动冒泡调度状态':'Proactive timer status'}>
    <h2>{zh?'主动冒泡调度状态':'Proactive timer status'}</h2>
    {error?<p role="status">{zh?'调度状态暂时无法读取，不能据此认定运行正常。':'Timer status unavailable; health is not confirmed.'}</p>:<p role="status">{!status?(zh?'正在读取…':'Loading…'):status.running?(zh?`调度器运行中 · 执行中 ${status.inFlight}`:`Timer running · ${status.inFlight} in flight`):(zh?'调度器未运行：请检查 SocialRuntime 与 OneBot 是否启用并连接。':'Timer stopped: check SocialRuntime and OneBot enablement and connection.')}</p>}
    <small>{zh?'此处仅反映调度器状态，不代表 QQ 或 Provider 全链路已通过验收。每 30 秒同步一次；最近成功同步：':'Timer status is not full QQ or Provider validation. Synchronizes every 30 seconds; last success: '}{synced||'—'}</small>
    {!!status?.history?.outcomes?.length&&<ul>{status.history.outcomes.map(row=><li key={row.status}>{zh?(names[row.status]??row.status):row.status}: {row.count}</li>)}</ul>}
  </section>
}
