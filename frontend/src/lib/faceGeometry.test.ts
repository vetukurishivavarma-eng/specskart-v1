import { describe, it, expect } from 'vitest'
import { geometryFromLandmarks, syntheticGeometry } from './faceGeometry'

function mesh(overrides: Record<number, [number, number]> = {}) {
  // 468-point stub; every point at origin unless overridden
  const pts = Array.from({ length: 468 }, () => ({ x: 0.5, y: 0.5, z: 0 }))
  for (const [i, [x, y]] of Object.entries(overrides)) pts[Number(i)] = { x, y, z: 0 }
  return pts
}

describe('geometryFromLandmarks', () => {
  it('produces scale-invariant ratios from contour points', () => {
    const g = geometryFromLandmarks(mesh({
      10: [0.5, 0.1],   // forehead top
      152: [0.5, 0.9],  // chin bottom  -> face height 0.8
      234: [0.2, 0.5], 454: [0.8, 0.5], // cheeks -> width 0.6
      21: [0.25, 0.2], 251: [0.75, 0.2], // forehead width 0.5
      172: [0.28, 0.7], 397: [0.72, 0.7], // jaw width 0.44
      136: [0.3, 0.75], 365: [0.7, 0.75],
      1: [0.5, 0.6],
    }))
    expect(g.cheekboneWidthRatio).toBeCloseTo(0.75, 1)
    expect(g.foreheadWidthRatio).toBeCloseTo(0.625, 1)
    expect(g.jawWidthRatio).toBeGreaterThan(0)
    expect(g.jawAngleDeg).toBeGreaterThan(0)
    expect(g.jawAngleDeg).toBeLessThan(200)
  })

  it('corrects for a non-square frame so length vs width is not warped', () => {
    // same face, delivered in a 9:16 portrait frame: x is normalised over the
    // narrow width so horizontal spans look bigger. Without the dims arg the
    // cheekbone ratio is inflated; with it, it matches the square reading.
    const overrides = {
      10: [0.5, 0.1], 152: [0.5, 0.9],
      234: [0.2, 0.5], 454: [0.8, 0.5],
      21: [0.25, 0.2], 251: [0.75, 0.2],
      172: [0.28, 0.7], 397: [0.72, 0.7],
      136: [0.3, 0.75], 365: [0.7, 0.75],
      1: [0.5, 0.6],
    } as Record<number, [number, number]>
    const square = geometryFromLandmarks(mesh(overrides))
    const naive = geometryFromLandmarks(mesh(overrides)) // no dims == assume square
    const corrected = geometryFromLandmarks(mesh(overrides), { width: 720, height: 1280 })
    expect(naive.cheekboneWidthRatio).toBeCloseTo(square.cheekboneWidthRatio, 5)
    // 9:16 correction squashes x by 720/1280 -> the width ratio drops
    expect(corrected.cheekboneWidthRatio).toBeLessThan(square.cheekboneWidthRatio * 0.75)
  })
})

describe('syntheticGeometry', () => {
  it('always returns a well-formed geometry for the offline path', () => {
    for (const seed of [0, 0.2, 0.5, 0.99]) {
      const g = syntheticGeometry(seed)
      for (const v of Object.values(g)) expect(Number.isFinite(v)).toBe(true)
      expect(g.jawAngleDeg).toBeGreaterThan(60)
    }
  })
})
