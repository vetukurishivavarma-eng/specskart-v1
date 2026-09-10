import { FilesetResolver, FaceLandmarker, type FaceLandmarkerResult } from '@mediapipe/tasks-vision'

const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1/wasm'
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'

export type FrameSpec = { colour?: string | null; tryOnImageUrl?: string | null }

/** Fit knobs the "Adjust fit" panel writes. offsetForwardCm is kept for storage
 *  compatibility with older saved prefs but is unused in the 2D compositor. */
export type TryOnTuning = { scale: number; offsetUpCm: number; offsetForwardCm?: number }
export const TRY_ON_TUNING: TryOnTuning = { scale: 1, offsetUpCm: 0 }

type Opts = { onFace?: (visible: boolean) => void; tuning?: Partial<TryOnTuning> }
type LM = { x: number; y: number }

// MediaPipe face-mesh landmark indices.
const IRIS_A = 468        // one iris centre; the other is IRIS_B — we order by screen x
const IRIS_B = 473
const NOSE_TIP = 1
const PD_CM = 6.3         // average inter-pupillary distance, for cm→pixel conversion
const REST_DROP = 0.045   // seat the frame this fraction of the eye span below the pupils
const YAW_FADE_START = 0.16  // |nose offset / eye span| where the frame starts fading
const YAW_FADE_END = 0.34    // …and where it is fully gone (head turned too far for a flat frame)
const SMOOTH = 0.45      // ponytail: plain EMA on the eye points; swap for one-euro if lag shows

const FACE_OVAL = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
  378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
  162, 21, 54, 103, 67, 109,
]

/** Two lens-opening centres as fractions of the cut-out, plus a dark silhouette
 *  of the whole frame for the contact shadow. */
type PreparedFrame = { img: CanvasImageSource; w: number; h: number; lc: LM; rc: LM; shadow: HTMLCanvasElement }

/**
 * Two lens-opening centres (fractions) from an opaque/transparent mask: flood the
 * exterior in from the border through transparent pixels; the transparent blobs
 * it can't reach are the enclosed lens openings. Falls back to a symmetric guess
 * when the rim doesn't fully close in the photo.
 */
export function findLensCentres(opaque: Uint8Array, w: number, h: number): { lc: LM; rc: LM } {
  const N = w * h
  const outside = new Uint8Array(N)
  const stack: number[] = []
  for (let x = 0; x < w; x++) stack.push(x, x + (h - 1) * w)
  for (let y = 0; y < h; y++) stack.push(y * w, w - 1 + y * w)
  while (stack.length) {
    const p = stack.pop()!
    if (outside[p] || opaque[p]) continue
    outside[p] = 1
    const x = p % w, y = (p / w) | 0
    if (x > 0) stack.push(p - 1)
    if (x < w - 1) stack.push(p + 1)
    if (y > 0) stack.push(p - w)
    if (y < h - 1) stack.push(p + w)
  }

  const seen = new Uint8Array(N)
  const blobs: { area: number; sx: number; sy: number }[] = []
  for (let p0 = 0; p0 < N; p0++) {
    if (seen[p0] || opaque[p0] || outside[p0]) continue
    let area = 0, sx = 0, sy = 0
    const q = [p0]; seen[p0] = 1
    while (q.length) {
      const p = q.pop()!
      area++; sx += p % w; sy += (p / w) | 0
      const x = p % w, y = (p / w) | 0
      for (const n of [x > 0 ? p - 1 : -1, x < w - 1 ? p + 1 : -1, y > 0 ? p - w : -1, y < h - 1 ? p + w : -1]) {
        if (n < 0 || seen[n] || opaque[n] || outside[n]) continue
        seen[n] = 1; q.push(n)
      }
    }
    if (area > 0.004 * N) blobs.push({ area, sx: sx / area, sy: sy / area })
  }
  blobs.sort((a, b) => b.area - a.area)

  if (blobs.length >= 2) {
    const [a, b] = blobs.slice(0, 2).sort((p, q) => p.sx - q.sx)
    return { lc: { x: a.sx / w, y: a.sy / h }, rc: { x: b.sx / w, y: b.sy / h } }
  }
  return { lc: { x: 0.27, y: 0.48 }, rc: { x: 0.73, y: 0.48 } }
}

function analyzeFrame(bitmap: CanvasImageSource, W: number, H: number): { lc: LM; rc: LM } {
  const scale = Math.min(1, 360 / W)
  const w = Math.max(1, Math.round(W * scale))
  const h = Math.max(1, Math.round(H * scale))
  const c = document.createElement('canvas')
  c.width = w; c.height = h
  const cx = c.getContext('2d', { willReadFrequently: true })!
  cx.drawImage(bitmap, 0, 0, w, h)
  const data = cx.getImageData(0, 0, w, h).data
  const opaque = new Uint8Array(w * h)
  for (let i = 0; i < w * h; i++) opaque[i] = data[i * 4 + 3] > 60 ? 1 : 0
  return findLensCentres(opaque, w, h)
}

function makeShadow(bitmap: CanvasImageSource, w: number, h: number): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = w; c.height = h
  const cx = c.getContext('2d')!
  cx.drawImage(bitmap, 0, 0, w, h)
  cx.globalCompositeOperation = 'source-in'
  cx.fillStyle = '#000'
  cx.fillRect(0, 0, w, h)
  return c
}

/**
 * Live 2D virtual try-on. MediaPipe Face Landmarker (VIDEO mode) gives the eye
 * and face-outline points every frame; we similarity-fit the real product
 * cut-out onto the two pupils, drop a soft contact shadow, add a faint live
 * reflection in the lenses, clip the arms to the head, and fade out when the
 * head turns too far for a flat photo to hold up. Drawn on a transparent canvas
 * over the <video>; the caller CSS-mirrors both for the selfie view.
 */
export class TryOnScene {
  private canvas: HTMLCanvasElement
  private ctx: CanvasRenderingContext2D
  private landmarker: FaceLandmarker | null = null
  private frame: PreparedFrame | null = null
  private frameToken = 0
  private raf = 0
  private running = false
  private lastTs = -1
  private tuning: TryOnTuning
  private readonly onFace: (visible: boolean) => void
  private container: HTMLElement
  private video: HTMLVideoElement
  private sL: LM | null = null   // smoothed eye points
  private sR: LM | null = null
  private faceShown = false

  constructor(container: HTMLElement, video: HTMLVideoElement, opts: Opts = {}) {
    this.container = container
    this.video = video
    this.tuning = { ...TRY_ON_TUNING, ...opts.tuning }
    this.onFace = opts.onFace ?? (() => {})

    this.canvas = document.createElement('canvas')
    Object.assign(this.canvas.style, {
      position: 'absolute', inset: '0', width: '100%', height: '100%', objectFit: 'cover',
    } as CSSStyleDeclaration)
    container.appendChild(this.canvas)
    this.ctx = this.canvas.getContext('2d')!
  }

  async start() {
    if (!this.landmarker) {
      const fileset = await FilesetResolver.forVisionTasks(WASM)
      this.landmarker = await FaceLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL, delegate: 'GPU' },
        numFaces: 1,
        runningMode: 'VIDEO',
      })
    }
    this.running = true
    this.resize()
    this.loop()
  }

  async setFrame(spec: FrameSpec) {
    const token = ++this.frameToken
    if (!spec.tryOnImageUrl) { this.frame = null; this.clear(); return }
    try {
      const img = await loadImage(spec.tryOnImageUrl)
      if (token !== this.frameToken) return
      const w = img.naturalWidth, h = img.naturalHeight
      const { lc, rc } = analyzeFrame(img, w, h)
      this.frame = { img, w, h, lc, rc, shadow: makeShadow(img, w, h) }
    } catch {
      this.frame = null
    }
  }

  setTuning(patch: Partial<TryOnTuning>) { this.tuning = { ...this.tuning, ...patch } }
  getTuning(): TryOnTuning { return { ...this.tuning } }

  freeze() { this.running = false; cancelAnimationFrame(this.raf) }
  resume() {
    if (this.running) return
    this.running = true
    this.lastTs = -1
    this.loop()
  }

  resize() {
    const w = this.video.videoWidth || this.container.clientWidth || 1
    const h = this.video.videoHeight || this.container.clientHeight || 1
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w
      this.canvas.height = h
    }
  }

  dispose() {
    this.running = false
    cancelAnimationFrame(this.raf)
    this.canvas.remove()
    this.landmarker?.close()
    this.landmarker = null
  }

  private clear() { this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height) }

  private setFaceShown(v: boolean) {
    if (v === this.faceShown) return
    this.faceShown = v
    this.onFace(v)
  }

  private loop = () => {
    if (!this.running) return
    this.raf = requestAnimationFrame(this.loop)
    const v = this.video
    if (!this.landmarker || v.readyState < 2 || v.videoWidth === 0) return
    this.resize()

    let ts = performance.now()
    if (ts <= this.lastTs) ts = this.lastTs + 1
    this.lastTs = ts

    let res: FaceLandmarkerResult | undefined
    try { res = this.landmarker.detectForVideo(v, ts) } catch { return }

    const lms = res?.faceLandmarks?.[0] as LM[] | undefined
    this.clear()
    if (!lms || !this.frame) {
      this.sL = this.sR = null
      this.setFaceShown(false)
      return
    }
    this.draw(lms)
    this.setFaceShown(true)
  }

  private draw(lms: LM[]) {
    const { ctx, canvas: cv } = this
    const W = cv.width, H = cv.height
    const px = (l: LM): LM => ({ x: l.x * W, y: l.y * H })

    // order the two irises by screen x, then low-pass them
    let a = px(lms[IRIS_A]), b = px(lms[IRIS_B])
    if (b.x < a.x) [a, b] = [b, a]
    this.sL = ema(this.sL, a)
    this.sR = ema(this.sR, b)
    const L = this.sL!, R = this.sR!

    const span = Math.hypot(R.x - L.x, R.y - L.y)
    if (span < 8) return
    const roll = Math.atan2(R.y - L.y, R.x - L.x)
    // unit vector pointing "up the face" (toward the brow) on screen
    const upx = (R.y - L.y) / span
    const upy = -(R.x - L.x) / span

    // yaw proxy: how far the nose tip has slid off the eye mid-line
    const nose = px(lms[NOSE_TIP])
    const midEye = { x: (L.x + R.x) / 2, y: (L.y + R.y) / 2 }
    const yaw = Math.abs((nose.x - midEye.x) / span)
    const fade = 1 - clamp01((yaw - YAW_FADE_START) / (YAW_FADE_END - YAW_FADE_START))
    if (fade <= 0.01) return

    const f = this.frame!
    const lensMid = { x: (f.lc.x + f.rc.x) / 2 * f.w, y: (f.lc.y + f.rc.y) / 2 * f.h }
    const lensSpanFrac = f.rc.x - f.lc.x
    const s = (span / Math.max(0.05, lensSpanFrac * f.w)) * this.tuning.scale

    // + toward the brow, − toward the nose; default sits it slightly onto the nose
    const k = ((this.tuning.offsetUpCm || 0) / PD_CM - REST_DROP) * span
    const cx = midEye.x + upx * k
    const cy = midEye.y + upy * k

    // head-outline clip so the temple arms don't float past the face
    const oval = new Path2D()
    let ovx = 0, ovy = 0
    for (const i of FACE_OVAL) { ovx += lms[i].x * W; ovy += lms[i].y * H }
    ovx /= FACE_OVAL.length; ovy /= FACE_OVAL.length
    FACE_OVAL.forEach((i, k) => {
      const x = ovx + (lms[i].x * W - ovx) * 1.22
      const y = ovy + (lms[i].y * H - ovy) * 1.22
      k === 0 ? oval.moveTo(x, y) : oval.lineTo(x, y)
    })
    oval.closePath()

    const place = () => { ctx.translate(cx, cy); ctx.rotate(roll); ctx.scale(s, s); ctx.translate(-lensMid.x, -lensMid.y) }

    // 1. contact shadow — dark frame silhouette, blurred, dropped a touch
    ctx.save()
    ctx.clip(oval)
    ctx.globalAlpha = 0.3 * fade
    ctx.filter = `blur(${Math.max(1, span * 0.02)}px)`
    ctx.translate(0, span * 0.04)
    place()
    ctx.drawImage(f.shadow, 0, 0)
    ctx.restore()

    // 2. faint live reflection inside the lenses
    ctx.save()
    place()
    const lensPath = new Path2D()
    for (const c of [f.lc, f.rc]) {
      lensPath.ellipse(c.x * f.w, c.y * f.h, lensSpanFrac * f.w * 0.42, lensSpanFrac * f.h * 0.34, 0, 0, Math.PI * 2)
    }
    ctx.clip(lensPath)
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.globalAlpha = 0.12 * fade
    ctx.globalCompositeOperation = 'lighter'
    ctx.filter = `blur(${Math.max(2, span * 0.06)}px) brightness(1.35)`
    ctx.drawImage(this.video, 0, 0, W, H)
    ctx.restore()

    // 3. the frame itself
    ctx.save()
    ctx.clip(oval)
    ctx.globalAlpha = fade
    place()
    ctx.drawImage(f.img, 0, 0)
    ctx.restore()
  }
}

function ema(prev: LM | null, next: LM): LM {
  if (!prev) return next
  return { x: prev.x + SMOOTH * (next.x - prev.x), y: prev.y + SMOOTH * (next.y - prev.y) }
}

function clamp01(v: number) { return v < 0 ? 0 : v > 1 ? 1 : v }

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => (img.naturalWidth ? resolve(img) : reject(new Error('empty image')))
    img.onerror = () => reject(new Error('image load failed'))
    img.src = url
  })
}
