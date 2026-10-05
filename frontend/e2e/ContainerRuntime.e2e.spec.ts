import { expect, test } from './auditFixtures'

// Opt-in against an already running, isolated real Docker container; never starts a jar or production adapter.
test.skip(process.env.XINGCHEN_CONTAINER_E2E !== '1', 'requires explicit isolated container target')

test('real container login, Overview, Memory, Operations, reload and logout', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('管理员账号').fill(process.env.XINGCHEN_E2E_ADMIN_USER!)
  await page.getByLabel('密码').fill(process.env.XINGCHEN_E2E_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: '总览' })).toBeVisible()
  for (const [path, heading] of [['/memory', '长期记忆'], ['/operations', '运行维护']] as const) {
    const response = await page.goto(path)
    expect(response?.status()).toBe(200)
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
  }
  await page.getByRole('button', { name: '退出登录' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect((await page.request.get('/api/console/overview')).status()).toBe(401)
})

test('real container API authentication, CSRF, unknown routes and safe error bodies', async ({ page }) => {
  expect((await page.request.get('/health')).status()).toBe(200)
  expect((await page.request.get('/api/security/posture')).status()).toBe(401)
  await page.goto('/login')
  await page.getByLabel('管理员账号').fill(process.env.XINGCHEN_E2E_ADMIN_USER!)
  await page.getByLabel('密码').fill(process.env.XINGCHEN_E2E_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL('/')
  const responses = [
    await page.request.get('/api/container-route-that-does-not-exist'),
    await page.request.patch('/api/pricing', { data: { revision: 0, rows: [] } }),
    await page.request.patch('/api/pricing', { headers: { 'X-XSRF-TOKEN': 'invalid-fixture' }, data: { revision: 0, rows: [] } }),
  ]
  expect(responses.map(response => response.status())).toEqual([404, 403, 403])
  expect((await page.request.get('/container-page-that-does-not-exist')).status()).toBe(404)
  for (const response of responses) {
    expect(response.headers()['content-type']).not.toContain('text/html')
    const body = await response.json()
    expect(Object.keys(body).sort()).toEqual(['code', 'message', 'traceId'])
    expect(JSON.stringify(body)).not.toMatch(/SELECT|<html>|stack|Exception|password|token=/)
  }
  const cookies = await page.context().cookies()
  expect(cookies.find(cookie => cookie.name === 'JSESSIONID')?.httpOnly).toBe(true)
  expect(cookies.find(cookie => cookie.name === 'JSESSIONID')?.sameSite).toBe('Strict')
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => !['xingchen.language', 'xingchen.theme'].includes(key)))).toEqual([])
})
