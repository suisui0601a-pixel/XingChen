import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { InitializationPage } from './InitializationPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('./api', () => ({ api }))
afterEach(() => { cleanup(); api.mockReset(); localStorage.clear() })
const renderPage = () => render(<MemoryRouter><InitializationPage language="zh-CN" /></MemoryRouter>)

it('does not show a credential form when initialization is unavailable', async () => {
  api.mockRejectedValue(new Error('closed'))
  renderPage()
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('初始化入口未开放'))
  expect(screen.queryByLabelText('密码')).toBeNull()
})

it('submits only after explicit user action, clears passwords and grants no session', async () => {
  api.mockResolvedValue({ initializationRequired: true })
  renderPage()
  const password = 'frontend-fixture-only-password-2037'
  await screen.findByLabelText('管理员账号')
  expect(api).toHaveBeenCalledTimes(1)
  fireEvent.change(screen.getByLabelText('管理员账号'), { target: { value: 'fixture-admin' } })
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: password } })
  fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: '创建首位管理员' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('管理员初始化已完成'))
  expect(api).toHaveBeenLastCalledWith('/api/auth/initialize', { method: 'POST', body: JSON.stringify({ username: 'fixture-admin', password, confirmation: password }) })
  expect(screen.queryByLabelText('密码')).toBeNull()
  expect(localStorage.length).toBe(0)
  expect(api.mock.calls.some(([path]) => path === '/api/auth/login')).toBe(false)
})

it('checks confirmation and does not retry a failed mutation or persist the input', async () => {
  api.mockImplementation(async (_path: string, options?: RequestInit) => {
    if (options?.method === 'POST') throw new Error('fixture failure')
    return { initializationRequired: true }
  })
  renderPage()
  await screen.findByLabelText('管理员账号')
  fireEvent.change(screen.getByLabelText('管理员账号'), { target: { value: 'fixture-admin' } })
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'fixture-only-strong-password' } })
  fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'different-password' } })
  fireEvent.click(screen.getByRole('button', { name: '创建首位管理员' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('请输入有效账号')
  expect(api).toHaveBeenCalledTimes(1)
  fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'fixture-only-strong-password' } })
  fireEvent.click(screen.getByRole('button', { name: '创建首位管理员' }))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('初始化未完成'))
  expect(screen.getByLabelText('密码')).toHaveValue('')
  expect(screen.getByLabelText('确认密码')).toHaveValue('')
  expect(api).toHaveBeenCalledTimes(2)
})
