export const proactiveDefaults = {
  proactivePrivateEnabled:true, proactiveGroupEnabled:true,
  proactiveIdleMinutes:120, proactiveIntervalMinutes:120, proactiveCooldownMinutes:120,
  proactiveProbability:0.3, proactiveMaxPerDay:8,
  proactiveQuietStartHour:23, proactiveQuietEndHour:8, proactiveZoneId:'Asia/Shanghai',
  dailyGreetingsEnabled:true, morningGreetingTime:'07:00', eveningGreetingTime:'22:50',
  nightReplyMuteEnabled:true, nightReplyStartHour:0, nightReplyEndHour:6,
}
export type ProactiveValues=typeof proactiveDefaults
type Key=keyof ProactiveValues
const fields: {key:Key; zh:string; en:string; min?:number; max?:number}[]=[
  {key:'proactivePrivateEnabled',zh:'允许私聊主动冒泡',en:'Enable proactive private messages'},
  {key:'proactiveGroupEnabled',zh:'允许群聊主动冒泡',en:'Enable proactive group messages'},
  {key:'proactiveIdleMinutes',zh:'至少闲置（分钟）',en:'Minimum idle time (minutes)',min:5,max:1440},
  {key:'proactiveIntervalMinutes',zh:'抽样间隔（分钟）',en:'Sampling interval (minutes)',min:5,max:1440},
  {key:'proactiveCooldownMinutes',zh:'每次尝试后冷却（分钟）',en:'Cooldown after each attempt (minutes)',min:30,max:10080},
  {key:'proactiveProbability',zh:'每次检查的主动冒泡概率（%）',en:'Proactive probability per check (%)',min:0,max:100},
  {key:'proactiveMaxPerDay',zh:'每个会话每日最多尝试次数',en:'Maximum attempts per conversation per day',min:1,max:12},
  {key:'proactiveQuietStartHour',zh:'安静时段开始（小时）',en:'Quiet hours start (hour)',min:0,max:23},
  {key:'proactiveQuietEndHour',zh:'安静时段结束（小时）',en:'Quiet hours end (hour)',min:0,max:23},
  {key:'proactiveZoneId',zh:'社交时间规则时区',en:'Social schedule timezone'},
  {key:'dailyGreetingsEnabled',zh:'启用每日早晚问候',en:'Enable daily greetings'},
  {key:'morningGreetingTime',zh:'早安时间',en:'Morning greeting time'},
  {key:'eveningGreetingTime',zh:'晚安时间',en:'Evening greeting time'},
  {key:'nightReplyMuteEnabled',zh:'凌晨普通消息静默',en:'Mute ordinary overnight messages'},
  {key:'nightReplyStartHour',zh:'凌晨静默开始（小时）',en:'Overnight silence starts (hour)',min:0,max:23},
  {key:'nightReplyEndHour',zh:'凌晨静默结束（小时）',en:'Overnight silence ends (hour)',min:0,max:23},
]

/** No separate browser storage: all values use the revision-checked social-settings API. */
export function ProactiveSettings({values,onChange,zh,overrides,effective}:{
  values:ProactiveValues;onChange:(key:Key,value:unknown)=>void;zh:boolean;
  overrides?:Record<string,unknown>;effective?:ProactiveValues;
}){
  const inherited=overrides!==undefined
  return <section className="settings-section" aria-label={zh?'闲置主动冒泡':'Idle proactive participation'}>
    <h2>{zh?(inherited?'本会话主动冒泡覆盖':'闲置主动冒泡'):(inherited?'Conversation proactive overrides':'Idle proactive participation')}</h2>
    <p className="quiet-note">{zh?'与收到普通消息后的随机插话独立。默认开启，只向通过访问权限且最近 24 小时内有人发言的会话尝试；WAIT、休眠、CLOSED_AGENT 和忙碌会话不会被打断。':'Independent of ordinary inbound probability. On by default. Only authorized conversations active within 24 hours are eligible. WAIT, sleeping, CLOSED_AGENT and busy conversations are excluded.'}</p>
    <div className="social-numeric-grid">{fields.map(field=>{
      const key=field.key, label=zh?field.zh:field.en
      const overridden=inherited&&overrides[key]!==undefined&&overrides[key]!==null
      const value=(overridden?overrides[key]:values[key])??proactiveDefaults[key]
      const probability=key==='proactiveProbability',boolean=typeof proactiveDefaults[key]==='boolean',text=typeof proactiveDefaults[key]==='string',time=key==='morningGreetingTime'||key==='eveningGreetingTime'
      return <div key={key}>
        {inherited&&<label className="social-check"><input type="checkbox" aria-label={`${label} ${zh?'覆盖':'override'}`} checked={overridden} onChange={e=>onChange(key,e.target.checked?value:null)}/>{zh?'覆盖此值':'Override'}</label>}
        <label className={boolean?'social-check':undefined}>{label}<input aria-label={label} disabled={inherited&&!overridden} type={boolean?'checkbox':time?'time':text?'text':'number'} min={field.min} max={field.max} step={time?60:1}
          {...(boolean?{checked:Boolean(value)}:{value:probability?Math.round(Number(value)*100):String(value)})}
          onChange={e=>onChange(key,boolean?e.target.checked:text?e.target.value:probability?Number(e.target.value)/100:Number(e.target.value))}/></label>
        {inherited&&effective&&<small>{zh?'生效值':'Effective'}: {String(effective[key]??proactiveDefaults[key])}</small>}
      </div>
    })}</div>
    <p className="quiet-note">{zh?'安静时段开始与结束相同表示全天允许。每天次数按所选时区统计；NO_REPLY、失败或 UNKNOWN 也计入尝试和冷却。随机冒泡全局最多每小时 4 次模型尝试，每次最多发送一条文字；不会自动重试。调度每分钟检查，实际检查可能因会话数量或繁忙稍有延后。':'Equal quiet-hour endpoints allow all hours. Daily limits use the selected timezone. NO_REPLY, failures and UNKNOWN still consume attempts and cooldown. Random global cap: four model attempts per hour, one final text per attempt; no automatic retries. The timer scans once a minute; busy or large conversation sets may defer checks.'}</p>
    <p className="quiet-note">{zh?'公开版预设：插话 5%；随机冒泡每 120 分钟抽样 30%，23:00–08:00 不随机冒泡。07:00 早安和 22:50 晚安独立于随机概率与冷却；仅在到点后 10 分钟内尝试，每会话每日各一次，全局问候最多每小时 4 次，不补发错过的问候。仍受权限、近 24 小时活动、WAIT、休眠与繁忙保护约束。':'Public preset: 5% interjection; random proactive sampling at 30% every 120 minutes outside 23:00–08:00. Greetings at 07:00 and 22:50 have independent probability/cooldown budgets, at most once each per conversation per day within a ten-minute window and four greetings per hour globally. No missed-greeting replay; existing access, recent-activity, WAIT, sleep and busy protections apply.'}</p>
    <p className="quiet-note">{zh?'凌晨静默默认 00:00–06:00；仅直接 @ 本机器人或已配置 Owner 的私聊可继续按原规则处理。回复引用、戳一戳、名字与普通消息不会绕过静默。升级首次启动会备份并覆盖旧的对应手工配置与会话覆盖；此后新保存的设置持续生效。':'Overnight silence defaults to 00:00–06:00; only direct mentions of this bot and configured Owner private messages may proceed through existing rules. The first upgrade backs up and replaces previous matching manual values and overrides; later saved settings persist.'}</p>
    <p className="quiet-note">{zh?'在本页保存全局设置或会话覆盖后生效。访问控制仍需单独配置，不会因开启主动冒泡而放开权限。':'Save global settings or conversation overrides on this page to apply. Access rules remain separate and are never relaxed by enabling this feature.'}</p>
  </section>
}
