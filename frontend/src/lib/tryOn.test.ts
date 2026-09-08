import { describe, it, expect } from 'vitest'
import { computeFramePlacement, smoothPlacement, TRY_ON_TUNING, type FramePlacement } from './tryOn'

// 468-point stub. Eyes are small clusters around a centre; the mesh indices used
// by computeFramePlacement are filled in, everything else is off-face.
type P = { x: number; y: number; z?: number }

function face(opts: {
  rEye: [number, number]
  lEye: [number, number]
  bridge?: [number, number]
  temples?: { r: [number, number]; l: [number, number] }
}): P[] {
  const pts: P[] = Array.from({ length: 468 }, () => ({ x: -1, y: -1 }))
  const put = (i: number, [x, y]: [number, number]) => { pts[i] = { x, y } }
  // right-eye ring 33,133,159,145 clustered on rEye; left-eye ring 263,362,386,374 on lEye
  for (const i of [33, 133, 159, 145]) put(i, opts.rEye)
  for (const i of [263, 362, 386, 374]) put(i, opts.lEye)
  if (opts.bridge) put(168, opts.bridge)
  if (opts.temples) { put(234, opts.temples.r); put(454, opts.temples.l) }
  return pts
}

describe('computeFramePlacement', () => {
  it('spans temple to temple, centred on the bridge, level when the eyes are level', () => {
    const p = computeFramePlacement(
      face({
        rEye: [0.38, 0.5], lEye: [0.62, 0.5],
        bridge: [0.5, 0.5],
        temples: { r: [0.2, 0.5], l: [0.8, 0.5] },
      }),
      1000, 800, 0.4,
    )!
    expect(p.cx).toBeCloseTo(500)
    expect(p.angle).toBeCloseTo(0)
    expect(p.w).toBeCloseTo(0.6 * 1000 * TRY_ON_TUNING.widthScale) // temple span
    expect(p.h).toBeCloseTo(p.w * 0.4)
    expect(p.cy).toBeCloseTo(0.5 * 800 + p.h * TRY_ON_TUNING.bridgeBiasFrac)
  })

  it('falls back to the eye span when temple points are absent', () => {
    const p = computeFramePlacement(
      face({ rEye: [0.4, 0.5], lEye: [0.6, 0.5], bridge: [0.5, 0.5] }),
      1000, 1000, 0.4,
    )!
    const eyeSpan = 0.2 * 1000
    expect(p.w).toBeCloseTo(eyeSpan * TRY_ON_TUNING.eyeFallbackScale)
  })

  it('follows head tilt via the eye centres', () => {
    const p = computeFramePlacement(
      face({ rEye: [0.4, 0.45], lEye: [0.6, 0.55], bridge: [0.5, 0.5] }),
      1000, 1000, 0.4,
    )!
    expect(p.angle).toBeGreaterThan(0)
    expect(p.angle).toBeCloseTo(Math.atan2(0.1 * 1000, 0.2 * 1000))
  })

  it('centres on the bridge, which already carries the yaw', () => {
    const turned = computeFramePlacement(
      face({
        rEye: [0.45, 0.5], lEye: [0.75, 0.5],
        bridge: [0.52, 0.5],
        temples: { r: [0.35, 0.5], l: [0.85, 0.5] },
      }),
      1000, 1000, 0.4,
    )!
    expect(turned.cx).toBeCloseTo(520)
  })

  it('returns null when the eye landmarks are missing or coincident', () => {
    expect(computeFramePlacement([], 100, 100, 0.4)).toBeNull()
    expect(
      computeFramePlacement(face({ rEye: [0.5, 0.5], lEye: [0.5, 0.5] }), 100, 100, 0.4),
    ).toBeNull()
  })
})

describe('smoothPlacement', () => {
  const at = (cx: number): FramePlacement => ({ cx, cy: 100, angle: 0, w: 200, h: 80 })

  it('returns the first placement unchanged', () => {
    expect(smoothPlacement(null, at(500))).toEqual(at(500))
  })

  it('eases toward the target for small moves', () => {
    const out = smoothPlacement(at(500), at(520), 0.5)
    expect(out.cx).toBeCloseTo(510)
  })

  it('snaps straight to a large re-pose', () => {
    const out = smoothPlacement(at(100), at(900), 0.35) // jump > width
    expect(out.cx).toBe(900)
  })
})
