import { expect, test } from './auditFixtures'

test('gateway status polling keeps an unsaved endpoint draft without sending a configuration mutation', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('管理员账号').fill(process.env.XINGCHEN_E2E_ADMIN_USER ?? 'e2e-admin')
  await page.getByLabel('密码').fill(process.env.XINGCHEN_E2E_ADMIN_PASSWORD ?? 'local-e2e-console-password')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL('/')
  await page.goto('/gateway')
  const endpoint = page.locator('#onebot-http')
  await expect(endpoint).toBeVisible()
  let mutations = 0
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/gateway/config' && request.method() === 'PATCH') mutations++
  })
  await endpoint.fill('http://127.0.0.1:3499')
  await page.waitForResponse(response => new URL(response.url()).pathname === '/api/gateway/status' && response.status() === 200)
  await expect(endpoint).toHaveValue('http://127.0.0.1:3499')
  expect(mutations).toBe(0)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '总览' })).toBeVisible()
})
