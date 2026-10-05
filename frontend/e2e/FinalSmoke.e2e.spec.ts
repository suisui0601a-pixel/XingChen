import { expect, test } from './auditFixtures'

// Jar-served smoke only. It is not a substitute for the full visual/a11y/error-state audit.
test('latest jar serves Console routes and keeps management APIs protected', async ({ page }) => {
  test.setTimeout(120_000)
  expect((await page.request.get('/health')).status()).toBe(200)
  expect((await page.request.get('/api/security/posture')).status()).toBe(401)
  await page.goto('/login')
  await page.getByLabel('管理员账号').fill(process.env.XINGCHEN_E2E_ADMIN_USER ?? 'e2e-admin')
  await page.getByLabel('密码').fill(process.env.XINGCHEN_E2E_ADMIN_PASSWORD ?? 'local-e2e-console-password')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL('/')
  const unknown = await page.request.get('/api/audit-route-that-does-not-exist')
  expect(unknown.status()).toBe(404)
  expect(unknown.headers()['content-type']).not.toContain('text/html')
  const csrfFailure = await page.request.patch('/api/pricing', {data:{revision:0,rows:[]}})
  expect(csrfFailure.status()).toBe(403)
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const policy = await (await page.request.get('/api/access/policy')).json()
  const conflict = await page.request.patch('/api/access/rules', {headers:{'X-XSRF-TOKEN':csrf.token},data:{revision:policy.revision-1,rules:policy.rules}})
  expect(conflict.status()).toBe(409)
  const internalFailure = await page.request.get('/api/fake/conversations/internal-failure')
  expect(internalFailure.status()).toBe(500)
  for(const response of [unknown,csrfFailure,conflict,internalFailure]){
    const body=await response.json()
    expect(Object.keys(body).sort()).toEqual(['code','message','traceId'])
    expect(JSON.stringify(body)).not.toMatch(/SELECT|synthetic-private|absolute|<html>|stack|Exception/)
  }
  for (const path of ['/', '/gateway', '/conversations', '/people', '/relationships', '/memory',
    '/persona', '/simulation', '/social-settings', '/models', '/stickers', '/slang', '/voice',
    '/usage', '/access', '/security', '/logs', '/operations', '/settings']) {
    const response = await page.goto(path)
    expect(response?.status(), path).toBe(200)
    await expect(page.locator('h1'), path).toBeVisible()
    await page.reload()
    await expect(page.locator('h1'), `${path} refresh`).toBeVisible()
  }
  const cookies = await page.context().cookies()
  expect(cookies.find(cookie => cookie.name === 'JSESSIONID')?.httpOnly).toBe(true)
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => !['xingchen.language', 'xingchen.theme'].includes(key)))).toEqual([])
})
