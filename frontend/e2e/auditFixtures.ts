import { test as base, expect } from '@playwright/test'

// Exact path/status pairs used by explicit negative browser tests, not a status-wide exemption.
const expectedResourceErrors = [
 {path:/^\/api\/auth\/session$/,status:401},
 {path:/^\/api\/auth\/login$/,status:401},
 {path:/^\/api\/prompts\/PERSONA\/versions$/,status:409},
 {path:/^\/api\/social-settings\/conversations\/[a-f0-9-]+$/,status:409},
]
export const test = base.extend<{ browserAudit:void; expectedHttpErrors:{path:string;status:number}[] }>({
 expectedHttpErrors:async({},use)=>{await use([])},
 browserAudit:[async({page,expectedHttpErrors},use)=>{
  const errors:string[]=[]
  const resourceErrors:{url:string;text:string}[]=[]
  const failedResponses=new Set<string>()
  page.on('pageerror',error=>errors.push('pageerror:'+error.name))
  page.on('console',message=>{if(message.type()!=='error')return;const text=message.text();if(/^Failed to load resource: the server responded with a status of \d+ \([^)]*\)$/.test(text))resourceErrors.push({url:message.location().url,text});else errors.push('console.error')})
  page.on('response',response=>{if(response.status()>=400)failedResponses.add(response.url()+'|'+response.status())})
  page.on('requestfailed',request=>{
   // AbortController cleanup of an API GET on page unmount is deliberate; other failures remain fatal.
   if(request.failure()?.errorText==='net::ERR_ABORTED' && request.method()==='GET' && (request.isNavigationRequest() || new URL(request.url()).pathname.startsWith('/api/')))return
   errors.push('requestfailed:'+request.method()+':'+new URL(request.url()).pathname)
  })
  await page.exposeBinding('__xingchenReportUnhandled',()=>{errors.push('unhandledrejection')})
  await page.addInitScript(()=>{window.addEventListener('unhandledrejection',()=>{void (window as typeof window & {__xingchenReportUnhandled:()=>Promise<void>}).__xingchenReportUnhandled()})})
  await use()
  for(const resource of resourceErrors){
   const status=Number(resource.text.match(/status of (\d+)/)?.[1])
   const path=new URL(resource.url,page.url()).pathname
   const allowed=expectedResourceErrors.some(e=>e.status===status&&e.path.test(path))||expectedHttpErrors.some(e=>e.status===status&&e.path===path)
   if(!allowed||!failedResponses.has(resource.url+'|'+status))errors.push('unexpected HTTP resource error:'+status+':'+path)
  }
  expect(errors,'Unexpected browser errors across every navigation').toEqual([])
 },{auto:true}],
})
export { expect }
