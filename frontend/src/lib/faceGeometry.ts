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

export function geometryFromLandmarks(lm: P[]): Geometry {
  const faceHeight = dist(lm[IDX.foreheadTop], lm[IDX.chinBottom]) || 1
  const cheek = dist(lm[IDX.leftCheek], lm[IDX.rightCheek])
  const forehead = dist(lm[IDX.leftForehead], lm[IDX.rightForehead])
  const jaw = dist(lm[IDX.leftJaw], lm[IDX.rightJaw])

  // jaw angle: angle at the jaw corner between the jawline and the vertical to the chin
  const corner = lm[IDX.leftJawAngle]
  const chin = lm[IDX.chinBottom]
  const ear = lm[IDX.leftJaw]
  const v1 = { x: chin.x - corner.x, y: chin.y - corner.y }
  const v2 = { x: ear.x - corner.x, y: ear.y - corner.y }
  const dot = v1.x * v2.x + v1.y * v2.y
  const mag = Math.hypot(v1.x, v1.y) * Math.hypot(v2.x, v2.y) || 1
  const jawAngleDeg = (Math.acos(Math.max(-1, Math.min(1, dot / mag))) * 180) / Math.PI

  const chinRatio = dist(lm[IDX.noseTip], lm[IDX.chinBottom]) / faceHeight

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
