import { describe, expect, it } from 'vitest'
import { cleanCc } from '../lib/phone'

describe('cleanCc', () => {
  it('keeps a plain code and tolerates how it is typed', () => {
    expect(cleanCc('260')).toBe('+260')
    expect(cleanCc('+260')).toBe('+260')
    expect(cleanCc(' +26 0 ')).toBe('+260')
  })

  it('accepts codes other than the Zambian default', () => {
    expect(cleanCc('91')).toBe('+91')
    expect(cleanCc('+1')).toBe('+1')
  })

  it('caps at four digits and survives empty input', () => {
    expect(cleanCc('1234567')).toBe('+1234')
    expect(cleanCc('')).toBe('+')
  })
})
