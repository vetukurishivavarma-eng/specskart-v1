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
const REST_DROP = 0.12    // seat the frame this fraction of the eye span below the pupils
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
type PreparedFrame = { img: HTMLCanvasElement; w: number; h: number; lc: LM; rc: LM; shadow: HTMLCanvasElement }

/**
 * Two lens-opening centres (fractions) from an opaque/transparent mask: flood the
 * exterior in from the border through transparent pixels; the transparent blobs
 * it can't reach are the enclosed lens openings. Falls back to a symmetric guess
 * when the rim doesn't fully close in the photo.
 */
export function findLensCentres(opaque: Uint8Array, w: number, h: number): { lc: LM; rc: LM; found: boolean } {
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
    return { lc: { x: a.sx / w, y: a.sy / h }, rc: { x: b.sx / w, y: b.sy / h }, found: true }
  }
  return { lc: { x: 0.27, y: 0.48 }, rc: { x: 0.73, y: 0.48 }, found: false }
}

/**
 * A catalogue photo may have tinted or opaque lenses (or, on a bad photo, the
 * folded temple arms lying across them). If the lens openings aren't already
 * transparent, repaint them as faint glass in place — left/right halves of the
 * frame, an inscribed ellipse per lens. Mutates `data`. Returns the lens centres
 * when it acted, else null (caller then uses hole detection).
 */
export function repaintLenses(data: Uint8ClampedArray, w: number, h: number, opaque: Uint8Array): { lc: LM; rc: LM } | null {
  const N = w * h
  let ox = 0, on = 0, minY = h, maxY = 0
  const bright = new Uint8Array(N)
  for (let i = 0; i < N; i++) {
    if (!opaque[i]) continue
    const r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2]
    const mx = Math.max(r, g, b)
    ox += i % w; on++
    const y = (i / w) | 0
    if (y < minY) minY = y
    if (y > maxY) maxY = y
    if (mx > 115) bright[i] = 1                          // tinted / clear-ish lens fill
  }
  if (on < 0.02 * N) return null
  const midX = ox / on
  const box = (x0: number, x1: number) => {
    let bx0 = w, by0 = h, bx1 = 0, by1 = 0, cnt = 0
    for (let i = 0; i < N; i++) {
      if (!bright[i]) continue
      const x = i % w
      if (x < x0 || x >= x1) continue
      const y = (i / w) | 0
      if (x < bx0) bx0 = x; if (x > bx1) bx1 = x
      if (y < by0) by0 = y; if (y > by1) by1 = y
      cnt++
    }
    return cnt > 0.01 * N ? { bx0, by0, bx1, by1 } : null
  }
  const lb = box(0, midX), rb = box(midX, w)
  if (!lb || !rb) return null
  const paint = (b: { bx0: number; by0: number; bx1: number; by1: number }): LM => {
    const cx = (b.bx0 + b.bx1) / 2, cy = (b.by0 + b.by1) / 2
    const rx = Math.max(1, (b.bx1 - b.bx0) / 2 * 0.94), ry = Math.max(1, (b.by1 - b.by0) / 2 * 0.94)
    for (let y = b.by0; y <= b.by1; y++) for (let x = b.bx0; x <= b.bx1; x++) {
      const dx = (x - cx) / rx, dy = (y - cy) / ry
      if (dx * dx + dy * dy > 1) continue
      const i = (y * w + x) * 4
      data[i] = 150; data[i + 1] = 150; data[i + 2] = 148
      data[i + 3] = Math.min(data[i + 3], 14)   // near-clear so the eyes read through
    }
    return { x: cx / w, y: cy / h }
  }
  return { lc: paint(lb), rc: paint(rb) }
}

type Prepared = { source: HTMLCanvasElement; w: number; h: number; lc: LM; rc: LM }

function prepareFrame(bitmap: CanvasImageSource, W: number, H: number): Prepared {
  const scale = Math.min(1, 480 / W)
  const w = Math.max(1, Math.round(W * scale))
  const h = Math.max(1, Math.round(H * scale))
  const c = document.createElement('canvas')
  c.width = w; c.height = h
  const cx = c.getContext('2d', { willReadFrequently: true })!
  cx.drawImage(bitmap, 0, 0, w, h)
  const imgData = cx.getImageData(0, 0, w, h)
  const data = imgData.data
  const opaque = new Uint8Array(w * h)
  for (let i = 0; i < w * h; i++) opaque[i] = data[i * 4 + 3] > 60 ? 1 : 0

  const holes = findLensCentres(opaque, w, h)
  let { lc, rc } = holes
  if (!holes.found) {
    const glass = repaintLenses(data, w, h, opaque)
    if (glass) { cx.putImageData(imgData, 0, 0); lc = glass.lc; rc = glass.rc }
  }
  return { source: c, w, h, lc, rc }
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
      const raw = await loadImage(spec.tryOnImageUrl)
      if (token !== this.frameToken) return
      const { source, w, h, lc, rc } = prepareFrame(raw, raw.naturalWidth, raw.naturalHeight)
      this.frame = { img: source, w, h, lc, rc, shadow: makeShadow(source, w, h) }
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

    // 1. contact shadow — dark frame silhouette, heavily blurred, barely dropped
    ctx.save()
    ctx.clip(oval)
    ctx.globalAlpha = 0.16 * fade
    ctx.filter = `blur(${Math.max(3, span * 0.05)}px)`
    ctx.translate(0, span * 0.025)
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
    ctx.globalAlpha = 0.07 * fade
    ctx.globalCompositeOperation = 'lighter'
    ctx.filter = `blur(${Math.max(2, span * 0.06)}px) brightness(1.2)`
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
