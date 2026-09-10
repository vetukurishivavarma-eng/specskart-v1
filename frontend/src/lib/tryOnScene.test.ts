import { describe, it, expect } from 'vitest'
import { findLensCentres, repaintLenses } from './tryOnScene'

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

  it('reports not-found and a symmetric guess when the rim does not close', () => {
    const m = new Uint8Array(100 * 75) // nothing opaque → no enclosed holes
    const { lc, rc, found } = findLensCentres(m, 100, 75)
    expect(found).toBe(false)
    expect(lc.x).toBe(0.27)
    expect(rc.x).toBe(0.73)
  })
})

describe('repaintLenses', () => {
  it('clears a bright/opaque lens fill to faint glass and returns both centres', () => {
    const w = 100, h = 60
    const data = new Uint8ClampedArray(w * h * 4)
    const opaque = new Uint8Array(w * h)
    // two solid opaque lens blocks, bright interior
    for (const [x0, x1] of [[10, 45], [55, 90]]) {
      for (let y = 15; y < 45; y++) for (let x = x0; x < x1; x++) {
        const i = y * w + x
        opaque[i] = 1
        data[i * 4] = data[i * 4 + 1] = data[i * 4 + 2] = 200 // bright fill
        data[i * 4 + 3] = 255
      }
    }
    const out = repaintLenses(data, w, h, opaque)!
    expect(out).not.toBeNull()
    expect(out.lc.x).toBeCloseTo(0.275, 1)
    expect(out.rc.x).toBeCloseTo(0.725, 1)
    const mid = (30 * w + 27) * 4 // inside the left lens
    expect(data[mid + 3]).toBeLessThanOrEqual(30) // alpha knocked down
    expect(data[mid]).toBe(150)                   // repainted glass grey
  })
})
