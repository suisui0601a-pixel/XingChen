import {expect,test} from './auditFixtures'

test('proactive controls persist independently and timer diagnostics require admin authentication',async({page})=>{
  expect((await page.request.get('/api/social-settings/proactive-status')).status()).toBe(401)
  await page.goto('/login')
  await page.getByLabel('管理员账号').fill(process.env.XINGCHEN_E2E_ADMIN_USER??'e2e-admin')
  await page.getByLabel('密码').fill(process.env.XINGCHEN_E2E_ADMIN_PASSWORD??'local-e2e-console-password')
  await page.getByRole('button',{name:'登录'}).click();await expect(page).toHaveURL('/')
  const before=await(await page.request.get('/api/social-settings/global')).json()
  try{
    await page.goto('/social-settings')
    await expect(page.getByRole('heading',{name:'闲置主动冒泡',exact:true})).toBeVisible()
    await page.getByRole('checkbox',{name:'允许私聊主动冒泡',exact:true}).check()
    await page.getByLabel('每次检查的主动冒泡概率（%）',{exact:true}).fill('37')
    const saved=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/social-settings/global'&&r.request().method()==='PATCH')
    await page.getByRole('button',{name:'保存主动冒泡设置'}).click();expect((await saved).status()).toBe(200)
    await page.reload();await expect(page.getByRole('checkbox',{name:'允许私聊主动冒泡',exact:true})).toBeChecked()
    await expect(page.getByLabel('每次检查的主动冒泡概率（%）',{exact:true})).toHaveValue('37')
    const after=await(await page.request.get('/api/social-settings/global')).json()
    expect(after.values.ordinaryMessageProbability).toBe(before.values.ordinaryMessageProbability)
    const response=await page.request.get('/api/social-settings/proactive-status');expect(response.status()).toBe(200)
    expect(response.headers()['cache-control']).toContain('no-store')
    const status=await response.json();expect(status.globalHourlyTurnLimit).toBe(4)
    expect(JSON.stringify(status)).not.toMatch(/output_json|event_json|personaPrompt|simulationPrompt|apiKey|accessToken/)
    await page.screenshot({path:test.info().outputPath('proactive-settings-desktop.png'),fullPage:true})
  }finally{
    const latest=await(await page.request.get('/api/social-settings/global')).json()
    const csrf=await(await page.request.get('/api/auth/csrf')).json()
    expect((await page.request.patch('/api/social-settings/global',{headers:{'X-XSRF-TOKEN':csrf.token},data:{expectedRevision:latest.revision,values:before.values}})).status()).toBe(200)
  }
})
