import { describe, expect, it } from 'vitest'
import { memoryFilterInstant, memoryLocalInput, memoryTestWindow } from './memoryTime'

describe('Memory local input / UTC half-open time range', () => {
  for (const boundary of ['2040-12-31T23:59:00Z', '2041-01-01T00:00:00Z', '2041-01-01T00:01:00Z']) {
    it(`keeps relative records in range at ${boundary}, including a creation across midnight`, () => {
      const base = new Date(boundary)
      const range = memoryTestWindow(base)
      const from = Date.parse(memoryFilterInstant(range.from)), to = Date.parse(memoryFilterInstant(range.to))
      for (const offset of [-2 * 60 * 60_000, -24 * 60 * 60_000, 2 * 60_000]) {
        const record = base.getTime() + offset
        expect(record >= from && record < to).toBe(true)
      }
      expect(Date.parse(memoryFilterInstant(memoryLocalInput(base)))).toBe(base.getTime())
      const utcRange = memoryTestWindow(base, true)
      expect(Date.parse(`${utcRange.from}Z`)).toBe(from)
      expect(Date.parse(`${utcRange.to}Z`)).toBe(to)
    })
  }
})
