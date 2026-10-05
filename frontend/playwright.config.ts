import { defineConfig, devices } from '@playwright/test'

const systemChromiumPath = process.env.XINGCHEN_E2E_CHROMIUM_PATH

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  use: { baseURL: process.env.XINGCHEN_E2E_BASE_URL ?? 'http://127.0.0.1:3200', timezoneId: 'UTC', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], ...(systemChromiumPath ? { launchOptions: { executablePath: systemChromiumPath } } : { channel: 'chromium' }) } }],
  timeout: 30_000,
})
