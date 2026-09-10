import { describe, it, expect } from 'vitest'
import { findLensCentres } from './tryOnScene'

/** Build an opaque mask: 1 = rim, 0 = transparent. Two rectangular rims with
 *  hollow centres on a transparent field. */
function twoRimFrame(w: number, h: number) {
  const m = new Uint8Array(w * h)
  const rect = (x0: number, y0: number, x1: number, y1: number) => {
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const edge = x < x0 + 3 || x >= x1 - 3 || y < y0 + 3 || y >= y1 - 3
      if (edge) m[y * w + x] = 1
    }
  }
  rect(10, 20, 45, 55)   // left lens rim
  rect(55, 20, 90, 55)   // right lens rim
  for (let y = 30; y < 40; y++) for (let x = 45; x < 55; x++) m[y * w + x] = 1 // bridge
  return m
}

describe('findLensCentres', () => {
  it('locates both enclosed lens openings and orders them left→right', () => {
    const { lc, rc } = findLensCentres(twoRimFrame(100, 75), 100, 75)
    expect(lc.x).toBeLessThan(rc.x)
    expect(lc.x).toBeCloseTo(0.275, 1)   // centre of the left rect ~ (27,37)
    expect(rc.x).toBeCloseTo(0.725, 1)
    expect(lc.y).toBeCloseTo(0.5, 1)
  })

  it('falls back to a symmetric guess when the rim does not close', () => {
    const m = new Uint8Array(100 * 75) // nothing opaque → no enclosed holes
    const { lc, rc } = findLensCentres(m, 100, 75)
    expect(lc.x).toBe(0.27)
    expect(rc.x).toBe(0.73)
  })
})
