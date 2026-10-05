import {expect,test} from './auditFixtures'
import type {Page,Locator} from '@playwright/test'
const routes=['/','/gateway','/conversations','/people','/relationships','/memory','/persona','/simulation','/social-settings','/models','/stickers','/slang','/voice','/usage','/access','/security','/logs','/operations','/settings']
async function login(page:Page){await page.goto('/login');await page.getByLabel('管理员账号').fill('e2e-admin');await page.getByLabel('密码').fill('local-e2e-console-password');await page.getByRole('button',{name:'登录'}).click();await expect(page).toHaveURL('/')}
async function layout(page:Page){
 await expect(page.locator('h1')).toBeVisible()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no body overflow').toBe(true)
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
test('administrator HTML payload remains text in prompt editor, history and diff',async({page})=>{
 await login(page);await page.goto('/persona')
 const editor=page.getByLabel('人格');await expect(editor).toBeVisible()
 const original=await editor.inputValue()
 const originalVersion=await (await page.request.get('/api/prompts/PERSONA/current')).json()
 const payload='<script>window.__xingchenXss=1</script><img src=x onerror="window.__xingchenXss=2">'
 await editor.fill(original+'\n'+payload)
 await page.getByRole('button',{name:'保存为新版本并启用'}).click()
 await expect(page.locator('.notice')).toContainText('已创建并启用')
 const saved=await (await page.request.get('/api/prompts/PERSONA/current')).json()
 await expect(page.locator('.prompt-version.active .prompt-version-title')).toContainText('版本 v'+saved.version)
 await page.locator('.prompt-version.active .prompt-version-title').click()
 await expect(page.locator('.prompt-preview').first()).toContainText(payload)
 await page.getByRole('button',{name:'与当前比较',exact:true}).last().click()
 await expect(page.getByLabel('逐行版本差异')).toContainText(payload)
 expect(await page.locator('.page script,.page img[onerror]').count()).toBe(0)
 expect(await page.evaluate(()=>Reflect.get(window,'__xingchenXss'))).toBeUndefined()
 await page.getByRole('button',{name:'版本 v'+originalVersion.version,exact:true}).click()
 await page.getByRole('button',{name:'回滚到此内容'}).click()
 await page.getByRole('dialog',{name:'确认回滚'}).getByRole('button',{name:'创建回滚版本'}).click()
 await expect(editor).toHaveValue(original)
})
test('CSRF 403 mutation shows a distinct safe error associated with prompt fields without replay',async({page,expectedHttpErrors})=>{
 await login(page);await page.goto('/persona');const editor=page.getByLabel('人格');await expect(editor).toBeVisible();const original=await editor.inputValue()
 let writes=0;expectedHttpErrors.push({path:'/api/prompts/PERSONA/versions',status:403})
 await page.route('**/api/prompts/PERSONA/versions',r=>{writes++;return r.fulfill({status:403,contentType:'application/json',body:'{"code":"CSRF_INVALID","message":"synthetic secret body"}'})})
 await editor.fill(original+' csrf test');await page.getByRole('button',{name:'保存为新版本并启用'}).click()
 await expect(page.getByRole('alert')).toContainText('请求校验失败')
 await expect(editor).toHaveAttribute('aria-invalid','true');await expect(editor).toHaveAttribute('aria-describedby',/prompt-error/)
 await expect(page.locator('body')).not.toContainText('synthetic secret body');expect(writes).toBe(1)
 await editor.fill(original)
})
test('keyboard-only login, mobile navigation, form mutation, dialog cancel/confirm and logout',async({page})=>{
 test.setTimeout(90_000);await page.setViewportSize({width:430,height:900});await page.goto('/login')
 await tabTo(page,page.getByLabel('管理员账号'));await page.keyboard.type('e2e-admin');await page.keyboard.press('Tab');await page.keyboard.type('local-e2e-console-password');await page.keyboard.press('Enter');await expect(page).toHaveURL('/')
 await tabTo(page,page.getByRole('button',{name:'打开导航'}));await page.keyboard.press('Enter')
 await tabTo(page,page.getByRole('link',{name:'人格',exact:true}));await page.keyboard.press('Enter');await expect(page).toHaveURL('/persona')
 const editor=page.getByLabel('人格');await tabTo(page,editor);await page.keyboard.press('End');await page.keyboard.type(' keyboard audit');await tabTo(page,page.getByRole('button',{name:'保存为新版本并启用'}));await page.keyboard.press('Enter');await expect(page.locator('.notice')).toContainText('已创建并启用')
 await tabTo(page,page.getByRole('button',{name:'版本 v1',exact:true}));await page.keyboard.press('Enter');await tabTo(page,page.getByRole('button',{name:'回滚到此内容'}));await page.keyboard.press('Enter')
 const dialog=page.getByRole('dialog',{name:'确认回滚'});await expect(dialog).toBeVisible();await expect(dialog.getByRole('button',{name:'取消'})).toBeFocused();await page.keyboard.press('Shift+Tab');await expect(dialog.getByRole('button',{name:'创建回滚版本'})).toBeFocused();await page.keyboard.press('Tab');await expect(dialog.getByRole('button',{name:'取消'})).toBeFocused();await page.keyboard.press('Escape');await expect(dialog).not.toBeVisible();await expect(page.getByRole('button',{name:'回滚到此内容'})).toBeFocused()
 await page.keyboard.press('Enter');await page.keyboard.press('Tab');await page.keyboard.press('Enter');await expect(dialog).not.toBeVisible()
 await tabTo(page,page.getByRole('button',{name:'打开导航'}));await page.keyboard.press('Enter');await tabTo(page,page.getByRole('button',{name:'退出登录'}));await page.keyboard.press('Enter');await expect(page.getByRole('alertdialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('button',{name:'退出登录'})).toBeFocused();await page.keyboard.press('Enter');await page.keyboard.press('Tab');await page.keyboard.press('Enter');await expect(page).toHaveURL(/\/login$/)
})
