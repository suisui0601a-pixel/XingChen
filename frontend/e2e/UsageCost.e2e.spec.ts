import {expect,test} from './auditFixtures'
test('mixed partial usage stays visible; adding exact pricing completes coverage without combining currencies',async({page})=>{
 await page.goto('/login');await page.getByLabel('管理员账号').fill('e2e-admin');await page.getByLabel('密码').fill('local-e2e-console-password');await page.getByRole('button',{name:'登录'}).click();await expect(page).toHaveURL('/')
 await page.goto('/usage');await expect(page.getByTestId('pricing-coverage')).toHaveText('定价覆盖率: 部分')
 await expect(page.getByTestId('cost-USD')).toBeVisible();await expect(page.getByTestId('cost-CNY')).toBeVisible();await expect(page.getByTestId('unpriced-usage')).toContainText('未定价用量: 1');await expect(page.getByTestId('unpriced-usage')).toContainText('140');await expect(page.getByTestId('estimated-total')).toHaveCount(0);await expect(page.getByText(/多币种费用不可直接合并/)).toBeVisible()
 await expect(page.getByRole('cell',{name:'无匹配价格',exact:true})).toBeVisible()
 for(const width of [375,430,1440]){await page.setViewportSize({width,height:900});await expect(page.getByTestId('cost-CNY')).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)}
 const pricing=await (await page.request.get('/api/pricing')).json();const csrf=await (await page.request.get('/api/auth/csrf')).json()
 const response=await page.request.patch('/api/pricing',{headers:{'X-XSRF-TOKEN':csrf.token},data:{revision:pricing.revision,rows:[...pricing.rows,{provider:'deepseek',model:'fixture-model-2',input:'1.3',cacheHit:'0.2',cacheMiss:'1',output:'2.3',reasoning:'2.3',currency:'USD'}]}});expect(response.status()).toBe(200)
 await page.reload();await expect(page.getByTestId('pricing-coverage')).toHaveText('定价覆盖率: 完整');await expect(page.getByTestId('estimated-total')).toHaveCount(0);await expect(page.getByTestId('unpriced-usage')).toHaveCount(0);await expect(page.getByTestId('cost-USD')).toBeVisible();await expect(page.getByTestId('cost-CNY')).toBeVisible();await expect(page.getByText(/多币种费用不可直接合并/)).toBeVisible()
 const summary=await (await page.request.get('/api/usage/summary')).json();expect(summary.cost.pricingCoverage).toBe('COMPLETE');expect(summary.cost.estimatedCost).toBeNull();expect(summary.cost.currencyCount).toBe(2)
 await expect(page.locator('body')).not.toContainText(/fixture message|private fixture memory|hidden reasoning|sk-/)
})
