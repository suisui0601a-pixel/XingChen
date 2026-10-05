/** datetime-local is browser-local; the API contract is an offset-bearing UTC Instant. */
export function memoryFilterInstant(localValue: string): string {
  return new Date(localValue).toISOString()
}

/** Minute precision matches datetime-local inputs without assuming the browser timezone. */
export function memoryLocalInput(instant: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${instant.getFullYear()}-${pad(instant.getMonth() + 1)}-${pad(instant.getDate())}T${pad(instant.getHours())}:${pad(instant.getMinutes())}`
}

export function memoryTestWindow(base: Date, utc = false): { from: string; to: string } {
  const input = (instant: Date) => utc ? instant.toISOString().slice(0, 16) : memoryLocalInput(instant)
  return {
    from: input(new Date(base.getTime() - 3 * 24 * 60 * 60_000)),
    to: input(new Date(base.getTime() + 60 * 60_000)),
  }
}
