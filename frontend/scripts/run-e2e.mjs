import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createServer } from 'node:net'
import { setTimeout as delay } from 'node:timers/promises'
import { deflateSync } from 'node:zlib'

function pngChunk(type, data) {
  const name = Buffer.from(type)
  const payload = Buffer.concat([name, data])
  let crc = 0xffffffff
  for (const byte of payload) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
  }
  const chunk = Buffer.alloc(12 + data.length)
  chunk.writeUInt32BE(data.length, 0)
  name.copy(chunk, 4)
  data.copy(chunk, 8)
  chunk.writeUInt32BE((crc ^ 0xffffffff) >>> 0, 8 + data.length)
  return chunk
}

function onePixelPng() {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(1, 0)
  ihdr.writeUInt32BE(1, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), pngChunk('IHDR', ihdr), pngChunk('IDAT', deflateSync(Buffer.from([0, 32, 96, 192, 255]))), pngChunk('IEND', Buffer.alloc(0))])
}

const jarArgument = process.argv[2]?.toLowerCase().endsWith('.jar') ? process.argv[2] : null
const jar = resolve(jarArgument ?? '../build/libs/xingchen-core-0.1.0-SNAPSHOT.jar')
const playwrightArguments = process.argv.slice(jarArgument ? 3 : 2)
const server = createServer()
const requestedPort = process.env.XINGCHEN_E2E_PORT ? Number(process.env.XINGCHEN_E2E_PORT) : 0
const port = await new Promise((resolvePort, reject) => server.listen(requestedPort, '127.0.0.1', () => { const address = server.address(); server.close(() => resolvePort(address.port)) }).on('error', reject))
// Java's canonical-path checks may be denied for Windows AppData temp roots;
// keep all fake-profile state under the disposable project build directory.
const temp = await mkdtemp(join(resolve('../build'), 'xingchen-console-e2e-'))
const stickerRoot = join(temp, 'assets', 'stickers')
const stickerFixture = join(stickerRoot, 'nested', 'fixture.png')
await mkdir(join(stickerRoot, 'nested'), { recursive: true })
await writeFile(stickerFixture, onePixelPng())
const inherited = process.env
const env = {
  PATH: inherited.PATH ?? '', Path: inherited.Path ?? inherited.PATH ?? '',
  SystemRoot: inherited.SystemRoot ?? '', COMSPEC: inherited.COMSPEC ?? '', TEMP: inherited.TEMP ?? '', TMP: inherited.TMP ?? '',
  USERPROFILE: inherited.USERPROFILE ?? '', HOME: inherited.HOME ?? '', JAVA_HOME: inherited.JAVA_HOME ?? '',
  PLAYWRIGHT_BROWSERS_PATH: inherited.PLAYWRIGHT_BROWSERS_PATH ?? '',
  XINGCHEN_E2E_CHROMIUM_PATH: inherited.XINGCHEN_E2E_CHROMIUM_PATH ?? '',
  XINGCHEN_E2E_ADMIN_USER: inherited.XINGCHEN_E2E_ADMIN_USER ?? 'e2e-admin',
  XINGCHEN_E2E_ADMIN_PASSWORD: inherited.XINGCHEN_E2E_ADMIN_PASSWORD ?? 'local-e2e-console-password',
  XINGCHEN_CONSOLE_BIND: '127.0.0.1', XINGCHEN_CONSOLE_PORT: String(port), XINGCHEN_CONSOLE_ALLOW_REMOTE: 'false',
  SPRING_PROFILES_ACTIVE: 'gateway-fake-e2e,conversation-fake-e2e,model-fake-e2e',
  XINGCHEN_OWNER_ID: 'e2e-owner',
  XINGCHEN_PUBLIC_BASE_URL: '', XINGCHEN_CONSOLE_COOKIE_SECURE: 'false', XINGCHEN_DB_PATH: join(temp, 'console.db'), XINGCHEN_SECRET_DIR: join(temp, 'secrets'),
  XINGCHEN_CONSOLE_ADMIN_USER: 'e2e-admin', XINGCHEN_CONSOLE_ADMIN_PASSWORD: 'local-e2e-console-password',
  XINGCHEN_E2E_SECONDARY_USER: 'e2e-rotation-admin', XINGCHEN_E2E_SECONDARY_PASSWORD: 'local-e2e-secondary-password',
  XINGCHEN_SOCIAL_ENABLED: 'false', DSH_ENABLED: 'false', ONEBOT_ENABLED: 'false', MODEL_ENABLED: 'false',
  XINGCHEN_STICKER_ROOT: stickerRoot,
  XINGCHEN_LIVE_ENABLED: 'false', XINGCHEN_LIVE_TEST: '', XINGCHEN_DEEPSEEK_API_KEY: '', DEEPSEEK_API_KEY: '',
  ONEBOT_ACCESS_TOKEN: '', ONEBOT_TOKEN: '', DSH_API_KEY: '',
}
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java'
const app = spawn(java, ['-jar', jar], { cwd: process.cwd(), env, stdio: ['ignore', 'pipe', 'pipe'] })
let appLog = ''
app.stdout.on('data', (chunk) => { appLog += chunk.toString().replace(/(password|token|secret)\s*[:=]\s*\S+/ig, '$1=[REDACTED]') })
app.stderr.on('data', (chunk) => { appLog += chunk.toString().replace(/(password|token|secret)\s*[:=]\s*\S+/ig, '$1=[REDACTED]') })

try {
  const baseURL = `http://127.0.0.1:${port}`
  let ready = false
  for (let i = 0; i < 240; i++) {
    if (app.exitCode !== null) throw new Error(`Temporary application exited early. ${appLog.slice(-3000)}`)
    try { if ((await fetch(`${baseURL}/health`)).ok) { ready = true; break } } catch { /* waiting for local startup */ }
    await delay(500)
  }
  if (!ready) throw new Error(`Temporary application did not become ready. ${appLog.slice(-3000)}`)
  const csrfResponse = await fetch(`${baseURL}/api/auth/csrf`)
  const csrfBody = await csrfResponse.json()
  const csrfSetCookies = csrfResponse.headers.getSetCookie?.() ?? [csrfResponse.headers.get('set-cookie') ?? '']
  const xsrfSetCookie = csrfSetCookies.find((header) => header.startsWith('XSRF-TOKEN='))
  const xsrfCookie = xsrfSetCookie?.split(';', 1)[0]
  if (!xsrfCookie) throw new Error('Temporary application did not issue the CSRF cookie.')
  const credentialCheck = await fetch(`${baseURL}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-xsrf-token': csrfBody.token, cookie: xsrfCookie ?? '' },
    body: JSON.stringify({ username: env.XINGCHEN_CONSOLE_ADMIN_USER, password: env.XINGCHEN_CONSOLE_ADMIN_PASSWORD }),
  })
  if (!credentialCheck.ok) throw new Error(`Temporary E2E admin login preflight failed (HTTP ${credentialCheck.status}).`)
  const runner = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', ...playwrightArguments], { cwd: process.cwd(), env: { ...env, XINGCHEN_E2E_BASE_URL: baseURL }, stdio: 'inherit' })
  const code = await new Promise((resolveCode) => runner.on('exit', (value) => resolveCode(value ?? 1)))
  if (code !== 0) process.exitCode = code
} finally {
  app.kill('SIGTERM')
  await Promise.race([new Promise((resolveExit) => app.once('exit', resolveExit)), delay(10_000)])
  if (app.exitCode === null) app.kill('SIGKILL')
  await rm(temp, { recursive: true, force: true })
}
