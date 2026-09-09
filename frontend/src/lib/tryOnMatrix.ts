import * as THREE from 'three'

export type TryOn3DTuning = {
  /** Vertical FOV (deg) of the camera MediaPipe assumes when it solves the head matrix. */
  fovDeg: number
  /** Overall size multiplier for the glasses mesh (the head matrix is already ~metric). */
  scale: number
  /** Push the lenses toward the camera, off the eye plane, in cm. */
  offsetForwardCm: number
  /** Raise (+) or lower (−) the glasses on the nose, in cm. */
  offsetUpCm: number
  /** Some MediaPipe builds hand back the face pose with Z into the screen — flip it if the
   *  glasses vanish or turn inside-out. */
  flipZ: boolean
}

// ponytail: 3D alignment tuning — the numbers that want a real-device pass.
// Glasses too big/small -> scale. Sitting on the eyeballs -> raise offsetForwardCm.
// Riding low on the nose -> raise offsetUpCm. Gone / inverted -> toggle flipZ.
// The "Adjust fit" panel in the try-on writes scale/offsetUpCm/offsetForwardCm live.
export const TRY_ON_3D_TUNING: TryOn3DTuning = {
  fovDeg: 63, scale: 1.0, offsetForwardCm: 2.2, offsetUpCm: 2.7, flipZ: false,
}

/**
 * Turn MediaPipe's 4x4 facial-transformation matrix (column-major, face-local →
 * camera space, ~centimetres) into the matrix for the glasses group: head pose,
 * then a small local nudge forward/up and the size multiplier.
 */
export function solveGlassesMatrix(mp: number[], t: TryOn3DTuning = TRY_ON_3D_TUNING): THREE.Matrix4 {
  const head = new THREE.Matrix4().fromArray(mp)
  if (t.flipZ) head.multiply(new THREE.Matrix4().makeScale(1, 1, -1))
  const local = new THREE.Matrix4()
    .makeTranslation(0, t.offsetUpCm, t.offsetForwardCm)
    .multiply(new THREE.Matrix4().makeScale(t.scale, t.scale, t.scale))
  return head.multiply(local)
}
