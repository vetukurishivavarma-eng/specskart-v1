import { describe, it, expect } from 'vitest'
import * as THREE from 'three'
import { solveGlassesMatrix, TRY_ON_3D_TUNING } from './tryOnMatrix'

// A head matrix: identity rotation, 40 cm in front of the camera (−Z), no roll.
function headMatrix(tx = 0, ty = 0, tz = -40): number[] {
  const m = new THREE.Matrix4().makeTranslation(tx, ty, tz)
  return m.toArray() // column-major, matching MediaPipe
}

describe('solveGlassesMatrix', () => {
  it('places the glasses at the head position plus the forward/up nudge', () => {
    const out = solveGlassesMatrix(headMatrix(0, 0, -40))
    const pos = new THREE.Vector3().setFromMatrixPosition(out)
    expect(pos.x).toBeCloseTo(0, 5)
    expect(pos.y).toBeCloseTo(TRY_ON_3D_TUNING.offsetUpCm, 5)
    expect(pos.z).toBeCloseTo(-40 + TRY_ON_3D_TUNING.offsetForwardCm, 5)
  })

  it('carries head translation through', () => {
    const out = solveGlassesMatrix(headMatrix(5, -3, -50))
    const pos = new THREE.Vector3().setFromMatrixPosition(out)
    expect(pos.x).toBeCloseTo(5, 5)
    expect(pos.y).toBeCloseTo(-3 + TRY_ON_3D_TUNING.offsetUpCm, 5)
  })

  it('applies the scale multiplier to the mesh', () => {
    const out = solveGlassesMatrix(headMatrix(), { ...TRY_ON_3D_TUNING, scale: 2 })
    const s = new THREE.Vector3().setFromMatrixScale(out)
    expect(s.x).toBeCloseTo(2, 5)
  })

  it('flipZ inverts the face-space Z axis', () => {
    const straight = solveGlassesMatrix(headMatrix(0, 0, -40), { ...TRY_ON_3D_TUNING, flipZ: false })
    const flipped = solveGlassesMatrix(headMatrix(0, 0, -40), { ...TRY_ON_3D_TUNING, flipZ: true })
    const zs = new THREE.Vector3().setFromMatrixPosition(straight).z
    const zf = new THREE.Vector3().setFromMatrixPosition(flipped).z
    expect(zf).not.toBeCloseTo(zs, 3)
  })
})
