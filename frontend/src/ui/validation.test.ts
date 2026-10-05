import { describe, expect, it } from 'vitest'
import { z } from 'zod'

const loginSchema = z.object({ username: z.string().trim().min(1), password: z.string().min(14) })
const publicUrlSchema = z.string().refine((value) => !value || /^https?:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/i.test(value))

describe('admin form validation', () => {
  it('requires a username and suitably long password', () => {
    expect(loginSchema.safeParse({ username: 'admin', password: 'short' }).success).toBe(false)
    expect(loginSchema.safeParse({ username: 'admin', password: 'local-console-password' }).success).toBe(true)
  })
  it('accepts only http(s) public origins or an empty value', () => {
    expect(publicUrlSchema.safeParse('https://bot.example.test').success).toBe(true)
    expect(publicUrlSchema.safeParse('').success).toBe(true)
    expect(publicUrlSchema.safeParse('javascript:alert(1)').success).toBe(false)
  })
})
