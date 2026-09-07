import { describe, it, expect } from 'vitest'
import { computeFramePlacement, TRY_ON_TUNING } from './tryOn'

// 468-point stub; outer eye corners are indices 33 (right) and 263 (left).
function mesh(right: [number, number], left: [number, number]) {
  const pts = Array.from({ length: 468 }, () => ({ x: 0.5, y: 0.5 }))
  pts[33] = { x: right[0], y: right[1] }
  pts[263] = { x: left[0], y: left[1] }
  return pts
}

describe('computeFramePlacement', () => {
  it('centres the frame between the eyes, scaled to the eye span', () => {
    // eyes at x=0.3 and 0.7 on a 1000px-wide, 800px-tall image, level
    const p = computeFramePlacement(mesh([0.3, 0.5], [0.7, 0.5]), 1000, 800, 0.4)!
    expect(p.cx).toBeCloseTo(500)          // midpoint x
    expect(p.angle).toBeCloseTo(0)         // level eyes
    const span = 0.4 * 1000                // 400px
    expect(p.w).toBeCloseTo(span * TRY_ON_TUNING.widthScale)
    expect(p.h).toBeCloseTo(p.w * 0.4)     // frameAspect
    // y sits at eye line plus the downward nudge
    expect(p.cy).toBeCloseTo(400 + p.h * TRY_ON_TUNING.yOffsetFrac)
  })

  it('follows head tilt', () => {
    const p = computeFramePlacement(mesh([0.3, 0.45], [0.7, 0.55]), 1000, 1000, 0.4)!
    expect(p.angle).toBeGreaterThan(0)     // left eye lower on screen -> positive rotation
    expect(p.angle).toBeCloseTo(Math.atan2(0.10 * 1000, 0.40 * 1000))
  })

  it('returns null when the eye landmarks are missing or coincident', () => {
    expect(computeFramePlacement([], 100, 100, 0.4)).toBeNull()
    expect(computeFramePlacement(mesh([0.5, 0.5], [0.5, 0.5]), 100, 100, 0.4)).toBeNull()
  })
})
