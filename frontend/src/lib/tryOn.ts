// Virtual try-on: composite a transparent frame PNG onto a selfie, aligned to the face.
//
// MediaPipe FaceLandmarker 468-mesh indices used here:
//   33 / 263   outer eye corners (right / left)
//   133 / 362  inner eye corners
//   159 / 386  upper eyelids
//   145 / 374  lower eyelids
//   168        nose bridge, between the eyes — where a real frame's bridge rests
//   234 / 454  face silhouette at ear level (right / left) — a frame front spans
//              roughly this width, not the narrower eye span

const RIGHT_EYE = [33, 133, 159, 145]
const LEFT_EYE = [263, 362, 386, 374]
const RIGHT_EYE_OUTER = 33
const LEFT_EYE_OUTER = 263
const NOSE_BRIDGE = 168
const RIGHT_TEMPLE = 234
const LEFT_TEMPLE = 454

export type TryOnTuning = {
  /** frame front width as a multiple of the temple-to-temple span */
  widthScale: number
  /** width multiple of the outer-eye span, used only when temple points are unavailable */
  eyeFallbackScale: number
  /** vertical nudge from the nose bridge as a fraction of frame height (negative = up) */
  bridgeBiasFrac: number
}

// ponytail: alignment tuning — the numbers that want a real-device pass.
// Frames too narrow -> raise widthScale. Sitting low -> make bridgeBiasFrac more negative.
export const TRY_ON_TUNING: TryOnTuning = { widthScale: 1.0, eyeFallbackScale: 2.1, bridgeBiasFrac: -0.02 }

type LM = { x: number; y: number; z?: number }

export type FramePlacement = { cx: number; cy: number; angle: number; w: number; h: number }

function centroid(landmarks: LM[], idx: number[], pxW: number, pxH: number): { x: number; y: number } | null {
  let x = 0, y = 0, n = 0
  for (const i of idx) {
    const p = landmarks[i]
    if (!p || !Number.isFinite(p.x) || !Number.isFinite(p.y)) continue
    x += p.x * pxW
    y += p.y * pxH
    n++
  }
  return n ? { x: x / n, y: y / n } : null
}

/**
 * Pixel placement of the frame given normalised landmarks and the pixel size of
 * the image they were detected on. `frameAspect` = frame PNG height / width.
 *
 * Width comes from the face silhouette (temple to temple), which is what a frame
 * front actually spans and is naturally foreshortened when the head is turned.
 * The frame is centred on the nose bridge — its projected position already
 * carries the head's yaw — and rolled with the eye line. Only when the bridge
 * point is unusable does it fall back to the eye midpoint, nudged toward the
 * near side to approximate the same thing.
 */
export function computeFramePlacement(
  landmarks: LM[],
  pxW: number,
  pxH: number,
  frameAspect: number,
  tuning: TryOnTuning = TRY_ON_TUNING,
): FramePlacement | null {
  const rEye = centroid(landmarks, RIGHT_EYE, pxW, pxH)
  const lEye = centroid(landmarks, LEFT_EYE, pxW, pxH)
  if (!rEye || !lEye) return null

  const eyeSpan = Math.hypot(lEye.x - rEye.x, lEye.y - rEye.y)
  if (eyeSpan < 1) return null

  // Roll from the (stable) eye centres.
  const angle = Math.atan2(lEye.y - rEye.y, lEye.x - rEye.x)

  // Width: temple to temple when we have both, else fall back to the eye span.
  const rT = landmarks[RIGHT_TEMPLE]
  const lT = landmarks[LEFT_TEMPLE]
  const haveTemples =
    rT && lT && Number.isFinite(rT.x) && Number.isFinite(lT.x) &&
    Math.abs(lT.x - rT.x) * pxW > eyeSpan // sanity: temples must be wider than the eyes
  const w = haveTemples
    ? Math.hypot((lT!.x - rT!.x) * pxW, (lT!.y - rT!.y) * pxH) * tuning.widthScale
    : eyeSpan * tuning.eyeFallbackScale
  const h = w * frameAspect

  // Centre on the nose bridge — its projected x/y already carries the head's
  // yaw and pitch. Fall back to the eye midpoint, nudged toward the near side.
  const bridge = landmarks[NOSE_BRIDGE]
  let cx: number
  let cy: number
  if (bridge && Number.isFinite(bridge.x)) {
    cx = bridge.x * pxW
    cy = bridge.y * pxH
  } else {
    cx = (rEye.x + lEye.x) / 2
    cy = (rEye.y + lEye.y) / 2
    const ro = landmarks[RIGHT_EYE_OUTER]
    const lo = landmarks[LEFT_EYE_OUTER]
    if (ro && lo && Number.isFinite(ro.x) && Number.isFinite(lo.x)) {
      const dR = Math.abs(cx - ro.x * pxW)
      const dL = Math.abs(lo.x * pxW - cx)
      cx += (dR - dL) * 0.5 // toward the compressed (near) side
    }
  }

  return { cx, cy: cy + h * tuning.bridgeBiasFrac, angle, w, h }
}

/**
 * Exponential-moving-average smoothing between placements across frames.
 * Kills the per-detection jitter that makes a live overlay look unstable.
 * `alpha` in (0,1]: lower = smoother/laggier. Returns `next` unchanged when
 * there is no previous placement or the two are far apart (a real re-pose).
 */
export function smoothPlacement(
  prev: FramePlacement | null,
  next: FramePlacement,
  alpha = 0.35,
): FramePlacement {
  if (!prev) return next
  const jump = Math.hypot(next.cx - prev.cx, next.cy - prev.cy)
  if (jump > next.w) return next
  const mix = (a: number, b: number) => a + (b - a) * alpha
  // Shortest-path angle blend.
  let da = next.angle - prev.angle
  while (da > Math.PI) da -= 2 * Math.PI
  while (da < -Math.PI) da += 2 * Math.PI
  return {
    cx: mix(prev.cx, next.cx),
    cy: mix(prev.cy, next.cy),
    angle: prev.angle + da * alpha,
    w: mix(prev.w, next.w),
    h: mix(prev.h, next.h),
  }
}

type Source = CanvasImageSource & Partial<{ videoWidth: number; videoHeight: number; naturalWidth: number; naturalHeight: number }>

function sourceSize(src: Source): { w: number; h: number } {
  return {
    w: src.videoWidth || src.naturalWidth || 0,
    h: src.videoHeight || src.naturalHeight || 0,
  }
}

/**
 * Draw the selfie then the frame overlay onto `canvas`. `mirror` flips the whole
 * thing horizontally (to match a mirrored `<video>` preview) — landmarks and
 * frame flip together so alignment is preserved. Pass `placement` to draw a
 * pre-computed (e.g. smoothed) placement instead of recomputing from landmarks.
 */
export function drawTryOn(
  canvas: HTMLCanvasElement,
  source: Source,
  landmarks: LM[],
  frame: HTMLImageElement | null,
  opts: { mirror?: boolean; tuning?: TryOnTuning; placement?: FramePlacement | null } = {},
): FramePlacement | null {
  const { w, h } = sourceSize(source)
  if (!w || !h) return null
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return null

  ctx.save()
  if (opts.mirror) { ctx.translate(w, 0); ctx.scale(-1, 1) }
  ctx.drawImage(source, 0, 0, w, h)

  let placed: FramePlacement | null = null
  if (frame && frame.complete && frame.naturalWidth > 0) {
    const p = opts.placement !== undefined
      ? opts.placement
      : computeFramePlacement(landmarks, w, h, frame.naturalHeight / frame.naturalWidth, opts.tuning)
    if (p) {
      ctx.translate(p.cx, p.cy)
      ctx.rotate(p.angle)
      ctx.drawImage(frame, -p.w / 2, -p.h / 2, p.w, p.h)
      placed = p
    }
  }
  ctx.restore()
  return placed
}

/** Load an <img> and resolve when it's decodable (rejects on error). */
export function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image failed to load'))
    img.src = src
  })
}

/** Freeze a video frame into a still <img> so switching frames doesn't need the camera. */
export function snapshot(video: HTMLVideoElement): Promise<HTMLImageElement> {
  const c = document.createElement('canvas')
  c.width = video.videoWidth
  c.height = video.videoHeight
  c.getContext('2d')!.drawImage(video, 0, 0)
  return loadImage(c.toDataURL('image/jpeg', 0.9))
}
