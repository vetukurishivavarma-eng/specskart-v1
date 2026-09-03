// MediaPipe FaceLandmarker index reference (468-point mesh subset).
// We only need a handful of contour points to build scale-invariant ratios.
type P = { x: number; y: number; z: number }

const IDX = {
  chinBottom: 152,
  foreheadTop: 10,
  leftCheek: 234,
  rightCheek: 454,
  leftJaw: 172,
  rightJaw: 397,
  leftForehead: 21,
  rightForehead: 251,
  leftJawAngle: 136,
  rightJawAngle: 365,
  noseTip: 1,
}

const dist = (a: P, b: P) => Math.hypot(a.x - b.x, a.y - b.y)

export type Geometry = {
  faceWidthRatio: number
  foreheadWidthRatio: number
  cheekboneWidthRatio: number
  jawWidthRatio: number
  jawAngleDeg: number
  chinRatio: number
}

/**
 * @param lm   468 MediaPipe landmarks with x,y NORMALISED to the source frame
 *             (x to frame width, y to frame height).
 * @param dims pixel size of the frame the landmarks came from. Required for a
 *             correct result on any non-square frame — x and y are otherwise in
 *             different units, which warps every width/height ratio (most badly
 *             face length vs width, so every face collapses toward one shape
 *             with near-floor confidence). Omit only for already-square input.
 */
export function geometryFromLandmarks(lm: P[], dims?: { width: number; height: number }): Geometry {
  const aspect = dims && dims.width > 0 && dims.height > 0 ? dims.width / dims.height : 1
  // Put x into the same unit as y ("frame heights") so Euclidean distances are
  // comparable. Ratios stay scale-invariant because everything is over faceHeight.
  const p = (i: number): P => ({ x: lm[i].x * aspect, y: lm[i].y, z: lm[i].z })

  const faceHeight = dist(p(IDX.foreheadTop), p(IDX.chinBottom)) || 1
  const cheek = dist(p(IDX.leftCheek), p(IDX.rightCheek))
  const forehead = dist(p(IDX.leftForehead), p(IDX.rightForehead))
  const jaw = dist(p(IDX.leftJaw), p(IDX.rightJaw))

  // jaw angle: angle at the jaw corner between the jawline and the line to the chin
  const corner = p(IDX.leftJawAngle)
  const chin = p(IDX.chinBottom)
  const ear = p(IDX.leftJaw)
  const v1 = { x: chin.x - corner.x, y: chin.y - corner.y }
  const v2 = { x: ear.x - corner.x, y: ear.y - corner.y }
  const dot = v1.x * v2.x + v1.y * v2.y
  const mag = Math.hypot(v1.x, v1.y) * Math.hypot(v2.x, v2.y) || 1
  const jawAngleDeg = (Math.acos(Math.max(-1, Math.min(1, dot / mag))) * 180) / Math.PI

  const chinRatio = dist(p(IDX.noseTip), p(IDX.chinBottom)) / faceHeight

  return {
    faceWidthRatio: cheek / faceHeight,
    foreheadWidthRatio: forehead / faceHeight,
    cheekboneWidthRatio: cheek / faceHeight,
    jawWidthRatio: jaw / faceHeight,
    jawAngleDeg,
    chinRatio,
  }
}

// Deterministic synthetic geometry for the "simulate" path (no camera / offline).
export function syntheticGeometry(seed = Math.random()): Geometry {
  const shapes: Geometry[] = [
    { faceWidthRatio: 0.72, foreheadWidthRatio: 0.68, cheekboneWidthRatio: 0.72, jawWidthRatio: 0.66, jawAngleDeg: 150, chinRatio: 0.22 }, // oval
    { faceWidthRatio: 0.83, foreheadWidthRatio: 0.72, cheekboneWidthRatio: 0.83, jawWidthRatio: 0.74, jawAngleDeg: 150, chinRatio: 0.19 }, // round
    { faceWidthRatio: 0.80, foreheadWidthRatio: 0.79, cheekboneWidthRatio: 0.80, jawWidthRatio: 0.79, jawAngleDeg: 118, chinRatio: 0.20 }, // square
    { faceWidthRatio: 0.60, foreheadWidthRatio: 0.58, cheekboneWidthRatio: 0.60, jawWidthRatio: 0.58, jawAngleDeg: 120, chinRatio: 0.20 }, // rectangle
    { faceWidthRatio: 0.80, foreheadWidthRatio: 0.82, cheekboneWidthRatio: 0.80, jawWidthRatio: 0.64, jawAngleDeg: 140, chinRatio: 0.17 }, // heart
  ]
  return shapes[Math.floor(seed * shapes.length) % shapes.length]
}
