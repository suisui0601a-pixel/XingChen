import {expect,test} from './auditFixtures'
import type {Page,Locator} from '@playwright/test'
const routes=['/','/gateway','/conversations','/people','/relationships','/memory','/persona','/simulation','/social-settings','/models','/stickers','/slang','/voice','/usage','/access','/security','/logs','/operations','/settings']
async function login(page:Page){await page.goto('/login');await page.getByLabel('管理员账号').fill('e2e-admin');await page.getByLabel('密码').fill('local-e2e-console-password');await page.getByRole('button',{name:'登录'}).click();await expect(page).toHaveURL('/')}
async function layout(page:Page){
 await expect(page.locator('h1')).toBeVisible()
 const viewport=await page.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,offenders:Array.from(document.querySelectorAll('*')).map(el=>({tag:el.tagName,cls:(el as HTMLElement).className?.toString().slice(0,80),right:Math.round(el.getBoundingClientRect().right),width:Math.round(el.getBoundingClientRect().width)})).filter(el=>el.right>innerWidth+1).slice(0,8)}))
 expect(viewport.scrollWidth,`no body overflow: ${JSON.stringify(viewport)}`).toBeLessThanOrEqual(viewport.width)
 const escaped=await page.locator('.content button,.content input,.content select,.content textarea').evaluateAll(elements=>elements.filter(el=>{
  const r=el.getBoundingClientRect();if(!r.width||!r.height)return false
  const table=el.closest('.responsive-table,.slang-table-wrap')
  if(table){const bounds=table.getBoundingClientRect();const style=getComputedStyle(table);if(['auto','scroll'].includes(style.overflowX)&&bounds.left>=-1&&bounds.right<=innerWidth+1)return false}
  return r.left < -1 || r.right > innerWidth+1
 }).map(el=>({tag:el.tagName,label:el.getAttribute('aria-label')||el.textContent?.slice(0,60),left:el.getBoundingClientRect().left,right:el.getBoundingClientRect().right})))
 expect(escaped,'controls stay within '+new URL(page.url()).pathname).toEqual([])
}
test('all 20 pages at 375, 430, 1280 and 1440 have bounded layouts and no dead navigation',async({page})=>{
 test.setTimeout(240_000)
 for(const width of [375,430,1280,1440]){await page.setViewportSize({width,height:900});await page.goto('/login');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}
 await login(page)
 expect(await page.getByRole('link',{name:'Agent',exact:true}).count()).toBe(0)
 expect(await page.locator('a[href="/groups"],a[href="/agent"]').count()).toBe(0)
 for(const width of [375,430,1280,1440]){
  await page.setViewportSize({width,height:900})
  for(const route of routes){const response=await page.goto(route);expect(response?.status(),route).toBe(200);await page.waitForLoadState('networkidle');await layout(page)}
 }
})
test('four themes, both System schemes, both languages, reduced motion and visible keyboard focus',async({page})=>{
 test.setTimeout(180_000);await login(page)
 for(const preference of ['light','dark','late','system']){
  for(const scheme of preference==='system'?['light','dark']:['light']){
   await page.emulateMedia({colorScheme:scheme as 'light'|'dark',reducedMotion:'reduce'})
   await page.getByLabel('主题', {exact:true}).selectOption(preference)
   await expect(page.locator('html')).toHaveAttribute('data-theme',preference==='system'?scheme:preference)
   const contrast=await page.evaluate(()=>{
    const vars=getComputedStyle(document.documentElement)
    const lum=(hex:string)=>{let value=hex.trim().replace('#','');if(value.length===3)value=value.split('').map(v=>v+v).join('');const rgb=value.match(/../g)!.map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722}
    const ratio=(a:string,b:string)=>{const x=lum(vars.getPropertyValue(a)),y=lum(vars.getPropertyValue(b));return (Math.max(x,y)+.05)/(Math.min(x,y)+.05)}
    return {text:ratio('--text','--page'),muted:ratio('--muted','--surface'),focus:ratio('--accent-strong','--surface'),error:ratio('--danger','--danger-soft'),warning:ratio('--warning','--surface'),selected:ratio('--accent-strong','--accent-soft')}
   })
   for(const [name,value] of Object.entries(contrast))expect(value,name+' contrast').toBeGreaterThanOrEqual(3)
   for(const route of ['/','/memory','/persona','/usage','/security','/logs']){
    await page.goto(route);await layout(page)
    const colors=await page.locator('.page').evaluate(el=>{const s=getComputedStyle(el);return {text:s.color,background:getComputedStyle(document.documentElement).backgroundColor}})
    expect(colors.text).not.toBe(colors.background)
    await page.getByLabel('语言',{exact:true}).focus();await page.keyboard.press('Tab')
    expect(await page.evaluate(()=>{const s=getComputedStyle(document.activeElement!);return s.outlineStyle!=='none'&&parseFloat(s.outlineWidth)>=2})).toBe(true)
   }
   await page.screenshot({path:test.info().outputPath(preference+'-'+scheme+'.png'),fullPage:true})
  }
 }
 await page.getByLabel('语言',{exact:true}).selectOption('en-US')
 for(const [route,title] of [['/access','Access control'],['/usage','Usage & pricing'],['/logs','Logs & audit'],['/operations','Operations']]){
  await page.goto(route);await expect(page.getByRole('heading',{name:title,exact:true,level:1})).toBeVisible()
  expect(await page.locator('.page').innerText()).not.toMatch(/[\u4e00-\u9fff]/)
 }
 await page.goto('/usage');await expect(page.locator('.usage-bars')).toHaveAttribute('role','img');expect(await page.locator('.responsive-table table').count()).toBeGreaterThan(0)
 await page.goto('/stickers');for(const image of await page.locator('.sticker-card img').all())expect(await image.getAttribute('alt')).toBeTruthy()
 expect(await page.evaluate(()=>Object.keys(localStorage).filter(k=>!['xingchen.theme','xingchen.language'].includes(k)))).toEqual([])
 expect(await page.evaluate(()=>Object.keys(sessionStorage))).toEqual([])
})
test('failure injection distinguishes Logs empty/error, Operations retry, Access stale preview and safe 500',async({page,expectedHttpErrors})=>{
 await login(page)
 expectedHttpErrors.push({path:'/api/operations/status',status:500},{path:'/api/logs/operational',status:500},{path:'/api/access/preview',status:500})
 await page.route('**/api/operations/status',r=>r.fulfill({status:500,contentType:'application/json',body:'{"code":"INTERNAL_ERROR","message":"synthetic private content"}'}))
 await page.goto('/operations');await expect(page.getByRole('alert')).toContainText('服务暂时无法');await expect(page.locator('body')).not.toContainText('synthetic private content');await page.unrouteAll();await page.getByRole('button',{name:'重试'}).click();await expect(page.getByRole('cell',{name:'未跟踪 (NOT_TRACKED)',exact:true})).toBeVisible()
 await page.route('**/api/logs/operational?*',r=>r.fulfill({status:500,contentType:'application/json',body:'{"code":"INTERNAL_ERROR"}'}))
 await page.goto('/logs');await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByText('暂无记录')).toHaveCount(0)
 await page.unrouteAll();await page.route('**/api/logs/operational?*',r=>r.fulfill({status:200,contentType:'application/json',body:'{"records":[]}'}));await page.getByRole('button',{name:'重试'}).click();await expect(page.getByText('暂无记录')).toBeVisible();await page.unrouteAll()
 await page.goto('/access');await page.getByLabel('用户稳定 ID').fill('e2e-member');await page.getByLabel('会话 / 群组 ID').fill('audit-preview');await page.getByRole('button',{name:'检查（不调用模型）'}).click();await expect(page.getByText('允许 (ALLOW) · 显式允许 (EXPLICIT_ALLOW)')).toBeVisible()
 await page.route('**/api/access/preview?*',r=>r.fulfill({status:500,contentType:'application/json',body:'{"code":"INTERNAL_ERROR"}'}));await page.getByRole('button',{name:'检查（不调用模型）'}).click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByText('允许 (ALLOW) · 显式允许 (EXPLICIT_ALLOW)')).toHaveCount(0)
 const failed=await page.request.get('/api/fake/conversations/internal-failure');expect(failed.status()).toBe(500);const body=await failed.json();expect(Object.keys(body).sort()).toEqual(['code','message','traceId']);expect(JSON.stringify(body)).not.toMatch(/SELECT|synthetic-private|absolute|<html>|stack/)
})
async function tabTo(page:Page,target:Locator){
 for(let i=0;i<100;i++){if(await target.evaluate(el=>el===document.activeElement))return;await page.keyboard.press('Tab')}
 throw new Error('Keyboard target was not reachable')
}
test('prompt baseline is immutable and HTML input cannot mutate or execute in it',async({page})=>{
 await login(page);await page.goto('/persona')
 await expect(page.getByRole('heading',{name:'人格（Persona）',level:1})).toBeVisible()
 await expect(page.locator('textarea')).toHaveCount(0);await expect(page.getByRole('button',{name:/保存|回滚/})).toHaveCount(0)
 const original=await (await page.request.get('/api/prompts/PERSONA/current')).json()
 const payload='<script>window.__xingchenXss=1</script><img src=x onerror="window.__xingchenXss=2">'
 const csrf=(await (await page.request.get('/api/auth/csrf')).json()).token
 const rejected=await page.request.post('/api/prompts/PERSONA/versions',{headers:{'X-XSRF-TOKEN':csrf},data:{content:payload,note:'security test',expectedActiveVersionId:original.id}})
 expect(rejected.status()).toBe(409)
 expect((await (await page.request.get('/api/prompts/PERSONA/current')).json()).sha256).toBe(original.sha256)
 expect(await page.locator('.page script,.page img[onerror]').count()).toBe(0)
 expect(await page.evaluate(()=>Reflect.get(window,'__xingchenXss'))).toBeUndefined()
})
test('prompt write APIs enforce CSRF and immutability without exposing request data',async({page,expectedHttpErrors})=>{
 await login(page);await page.goto('/persona');await expect(page.locator('textarea')).toHaveCount(0)
 const path='/api/prompts/PERSONA/versions';const body={content:'synthetic private prompt',note:'test',expectedActiveVersionId:'legacy'}
 expectedHttpErrors.push({path,status:403})
 const csrfFailure=await page.request.post(path,{data:body});expect(csrfFailure.status()).toBe(403)
 const csrf=(await (await page.request.get('/api/auth/csrf')).json()).token
 const immutable=await page.request.post(path,{headers:{'X-XSRF-TOKEN':csrf},data:body});expect(immutable.status()).toBe(409)
 expect(JSON.stringify(await immutable.json())).not.toContain(body.content)
 expect(await page.locator('body').innerText()).not.toContain(body.content)
})
test('keyboard-only login, read-only Persona navigation and logout',async({page})=>{
 test.setTimeout(90_000);await page.setViewportSize({width:430,height:900});await page.goto('/login')
 await tabTo(page,page.getByLabel('管理员账号'));await page.keyboard.type('e2e-admin');await page.keyboard.press('Tab');await page.keyboard.type('local-e2e-console-password');await page.keyboard.press('Enter');await expect(page).toHaveURL('/')
 await tabTo(page,page.getByRole('button',{name:'打开导航'}));await page.keyboard.press('Enter')
 await tabTo(page,page.getByRole('link',{name:'人格',exact:true}));await page.keyboard.press('Enter');await expect(page).toHaveURL('/persona')
 await expect(page.getByRole('heading',{name:'人格（Persona）',level:1})).toBeVisible();await expect(page.locator('textarea')).toHaveCount(0);await expect(page.getByRole('button',{name:/保存|回滚/})).toHaveCount(0)
 await tabTo(page,page.getByRole('button',{name:'打开导航'}));await page.keyboard.press('Enter');await tabTo(page,page.getByRole('button',{name:'退出登录'}));await page.keyboard.press('Enter');await expect(page.getByRole('alertdialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('button',{name:'退出登录'})).toBeFocused();await page.keyboard.press('Enter');await page.keyboard.press('Tab');await page.keyboard.press('Enter');await expect(page).toHaveURL(/\/login$/)
})
