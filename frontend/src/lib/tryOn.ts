// Virtual try-on: composite a transparent frame PNG onto a selfie, aligned to the eyes.
// MediaPipe FaceLandmarker 468-mesh: outer eye corners are 33 (right eye) and 263 (left eye).

const RIGHT_EYE_OUTER = 33
const LEFT_EYE_OUTER = 263

export type TryOnTuning = {
  /** frame width as a multiple of the outer-eye-corner span (frames extend past the eyes) */
  widthScale: number
  /** vertical nudge as a fraction of frame height (positive = down toward the nose) */
  yOffsetFrac: number
}

// ponytail: alignment tuning knob — the only numbers that need real-device calibration.
// Frames look narrow -> raise widthScale. Riding high on the brow -> raise yOffsetFrac.
export const TRY_ON_TUNING: TryOnTuning = { widthScale: 2.15, yOffsetFrac: 0.06 }

type LM = { x: number; y: number }

export type FramePlacement = { cx: number; cy: number; angle: number; w: number; h: number }

/**
 * Pixel placement of the frame given normalised landmarks and the pixel size of the
 * image they were detected on. `frameAspect` = frame PNG height / width.
 */
export function computeFramePlacement(
  landmarks: LM[],
  pxW: number,
  pxH: number,
  frameAspect: number,
  tuning: TryOnTuning = TRY_ON_TUNING,
): FramePlacement | null {
  const r = landmarks[RIGHT_EYE_OUTER]
  const l = landmarks[LEFT_EYE_OUTER]
  if (!r || !l) return null
  const rx = r.x * pxW, ry = r.y * pxH
  const lx = l.x * pxW, ly = l.y * pxH
  const span = Math.hypot(lx - rx, ly - ry)
  if (span < 1) return null
  const angle = Math.atan2(ly - ry, lx - rx)
  const w = span * tuning.widthScale
  const h = w * frameAspect
  return {
    cx: (rx + lx) / 2,
    cy: (ry + ly) / 2 + h * tuning.yOffsetFrac,
    angle,
    w,
    h,
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
 * Draw the selfie then the frame overlay onto `canvas`. `mirror` flips the whole thing
 * horizontally (to match a mirrored `<video>` preview) — landmarks and frame flip together
 * so alignment is preserved.
 */
export function drawTryOn(
  canvas: HTMLCanvasElement,
  source: Source,
  landmarks: LM[],
  frame: HTMLImageElement | null,
  opts: { mirror?: boolean; tuning?: TryOnTuning } = {},
): boolean {
  const { w, h } = sourceSize(source)
  if (!w || !h) return false
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return false

  ctx.save()
  if (opts.mirror) { ctx.translate(w, 0); ctx.scale(-1, 1) }
  ctx.drawImage(source, 0, 0, w, h)

  let placed = false
  if (frame && frame.complete && frame.naturalWidth > 0) {
    const p = computeFramePlacement(landmarks, w, h, frame.naturalHeight / frame.naturalWidth, opts.tuning)
    if (p) {
      ctx.translate(p.cx, p.cy)
      ctx.rotate(p.angle)
      ctx.drawImage(frame, -p.w / 2, -p.h / 2, p.w, p.h)
      placed = true
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
