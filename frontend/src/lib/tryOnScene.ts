import * as THREE from 'three'
import { FilesetResolver, FaceLandmarker, type FaceLandmarkerResult } from '@mediapipe/tasks-vision'
import { buildGlasses, buildTexturedFrame, colourToHex, loadFrameTexture } from './glassesModel'
import { solveGlassesMatrix, TRY_ON_3D_TUNING, type TryOn3DTuning } from './tryOnMatrix'

export { TRY_ON_3D_TUNING, solveGlassesMatrix } from './tryOnMatrix'

const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm'
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'

export type FrameSpec = { colour?: string | null; tryOnImageUrl?: string | null }

type Opts = { onFace?: (visible: boolean) => void; tuning?: Partial<TryOn3DTuning> }

type LM = { x: number; y: number; z: number }

// MediaPipe face-mesh outline (ordered ring, 36 points closing back to 10).
const FACE_OVAL = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
  378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
  162, 21, 54, 103, 67, 109,
]

/**
 * Live 3D try-on: MediaPipe Face Landmarker (VIDEO mode, head-pose matrix on)
 * drives a Three.js glasses mesh rendered on a transparent canvas over the
 * <video>. The caller mirrors both layers with CSS for the selfie view, so the
 * scene itself stays in true camera space.
 */
export class TryOnScene {
  private renderer: THREE.WebGLRenderer
  private scene = new THREE.Scene()
  private camera: THREE.PerspectiveCamera
  private frame: THREE.Object3D
  private frameToken = 0
  private occluder: THREE.Mesh
  private landmarker: FaceLandmarker | null = null
  private raf = 0
  private running = false
  private lastTs = -1
  private tuning: TryOn3DTuning
  private readonly onFace: (visible: boolean) => void
  private container: HTMLElement
  private video: HTMLVideoElement

  constructor(container: HTMLElement, video: HTMLVideoElement, opts: Opts = {}) {
    this.container = container
    this.video = video
    this.tuning = { ...TRY_ON_3D_TUNING, ...opts.tuning }
    this.onFace = opts.onFace ?? (() => {})

    this.renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, premultipliedAlpha: false })
    this.renderer.setClearColor(0x000000, 0) // drawing buffer is sized to the video frame in resize()
    const c = this.renderer.domElement
    c.style.position = 'absolute'
    c.style.inset = '0'
    c.style.width = '100%'
    c.style.height = '100%'
    c.style.objectFit = 'cover' // crop to match the object-cover <video> underneath
    container.appendChild(c)

    this.camera = new THREE.PerspectiveCamera(this.tuning.fovDeg, 1, 1, 5000)

    this.scene.add(new THREE.HemisphereLight(0xffffff, 0x404040, 1.1))
    const key = new THREE.DirectionalLight(0xffffff, 1.3)
    key.position.set(1, 2, 3)
    this.scene.add(key)

    this.frame = this.mount(buildGlasses())

    // Invisible depth-only face patch: written to the depth buffer before the
    // frame so the temple arms (which sit behind the face plane) get hidden by
    // the cheeks and ears instead of floating on top — the thing that otherwise
    // makes it read as a flat sticker.
    const occGeo = new THREE.BufferGeometry()
    occGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array((FACE_OVAL.length + 1) * 3), 3))
    const idx: number[] = []
    for (let i = 0; i < FACE_OVAL.length; i++) idx.push(0, i + 1, ((i + 1) % FACE_OVAL.length) + 1)
    occGeo.setIndex(idx)
    this.occluder = new THREE.Mesh(occGeo, new THREE.MeshBasicMaterial({ colorWrite: false }))
    this.occluder.renderOrder = -1
    this.occluder.frustumCulled = false
    this.occluder.visible = false
    this.scene.add(this.occluder)
  }

  /** Rebuild the occluder patch from the live face outline, unprojected to face depth. */
  private updateOccluder(lms: LM[]) {
    const cam = this.camera
    const dist = Math.hypot(this.frame.matrix.elements[12], this.frame.matrix.elements[13], this.frame.matrix.elements[14]) || 40
    const pos = this.occluder.geometry.attributes.position as THREE.BufferAttribute
    const p = new THREE.Vector3()
    let cx = 0, cy = 0
    for (const i of FACE_OVAL) { cx += lms[i].x; cy += lms[i].y }
    cx /= FACE_OVAL.length; cy /= FACE_OVAL.length

    const place = (nx: number, ny: number, slot: number) => {
      p.set(nx * 2 - 1, -(ny * 2 - 1), 0.5).unproject(cam).normalize().multiplyScalar(dist)
      pos.setXYZ(slot, p.x, p.y, p.z)
    }
    place(cx, cy, 0)
    for (let k = 0; k < FACE_OVAL.length; k++) {
      const l = lms[FACE_OVAL[k]]
      // push the ring slightly outward so temples clearing the ears are still covered
      place(cx + (l.x - cx) * 1.12, cy + (l.y - cy) * 1.12, k + 1)
    }
    pos.needsUpdate = true
    this.occluder.geometry.computeBoundingSphere()
    this.occluder.visible = true
  }

  /** Make an object the tracked frame: matrix-driven, hidden until a face is seen. */
  private mount(obj: THREE.Object3D): THREE.Object3D {
    obj.matrixAutoUpdate = false
    obj.visible = false
    this.scene.add(obj)
    return obj
  }

  private disposeFrame() {
    this.scene.remove(this.frame)
    this.frame.traverse((o) => {
      if (o instanceof THREE.Mesh) {
        o.geometry.dispose()
        const m = o.material as THREE.Material & { map?: THREE.Texture }
        m.map?.dispose()
        m.dispose()
      }
    })
  }

  async start() {
    if (!this.landmarker) {
      const fileset = await FilesetResolver.forVisionTasks(WASM)
      this.landmarker = await FaceLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL, delegate: 'GPU' },
        numFaces: 1,
        runningMode: 'VIDEO',
        outputFacialTransformationMatrixes: true,
      })
    }
    this.running = true
    this.resize()
    this.loop()
  }

  /**
   * Show a product frame. If it has a cut-out image, map that onto a face-wrapping
   * curved surface (looks like the real product); otherwise fall back to a generic
   * mesh tinted to the product colour. Async because the texture has to load.
   */
  async setFrame(spec: FrameSpec) {
    const token = ++this.frameToken
    let next: THREE.Object3D | null = null
    if (spec.tryOnImageUrl) {
      try {
        const { texture, aspect } = await loadFrameTexture(spec.tryOnImageUrl)
        if (token !== this.frameToken) { texture.dispose(); return } // superseded by a newer switch
        next = buildTexturedFrame(texture, aspect, spec.colour)
      } catch {
        /* fall through to the generic mesh */
      }
    }
    if (token !== this.frameToken) return
    if (!next) {
      const g = buildGlasses(colourToHex(spec.colour))
      next = g
    }
    const wasVisible = this.frame.visible
    this.disposeFrame()
    this.frame = this.mount(next)
    this.frame.visible = wasVisible
    this.render()
  }

  setTuning(patch: Partial<TryOn3DTuning>) {
    this.tuning = { ...this.tuning, ...patch }
    this.resize() // re-applies fov + re-renders; the loop picks up the rest next frame
  }

  getTuning(): TryOn3DTuning {
    return { ...this.tuning }
  }

  freeze() { this.running = false; cancelAnimationFrame(this.raf) }

  resume() {
    if (this.running) return
    this.running = true
    this.lastTs = -1
    this.loop()
  }

  resize() {
    // Render at the camera feed's own aspect ratio; the canvas is object-cover'd
    // by CSS to match the <video> exactly, so the head matrix (which is relative
    // to the full frame) stays aligned with the face on screen.
    const w = this.video.videoWidth || this.container.clientWidth || 1
    const h = this.video.videoHeight || this.container.clientHeight || 1
    this.renderer.setSize(w, h, false)
    this.camera.aspect = w / h
    this.camera.fov = this.tuning.fovDeg
    this.camera.updateProjectionMatrix()
    this.render()
  }

  dispose() {
    this.running = false
    cancelAnimationFrame(this.raf)
    this.renderer.domElement.remove()
    this.renderer.dispose()
    this.landmarker?.close()
    this.landmarker = null
  }

  private loop = () => {
    if (!this.running) return
    this.raf = requestAnimationFrame(this.loop)
    const v = this.video
    if (!this.landmarker || v.readyState < 2 || v.videoWidth === 0) return

    // Match the render target to the feed once its dimensions are known.
    if (Math.abs(this.camera.aspect - v.videoWidth / v.videoHeight) > 0.01) this.resize()

    // detectForVideo needs a strictly increasing timestamp.
    let ts = performance.now()
    if (ts <= this.lastTs) ts = this.lastTs + 1
    this.lastTs = ts

    let res: FaceLandmarkerResult | undefined
    try {
      res = this.landmarker.detectForVideo(v, ts)
    } catch {
      return
    }

    const matrix = res?.facialTransformationMatrixes?.[0]?.data
    const lms = res?.faceLandmarks?.[0] as LM[] | undefined
    if (matrix && matrix.length === 16) {
      this.frame.matrix.copy(solveGlassesMatrix(Array.from(matrix), this.tuning))
      this.frame.matrixWorldNeedsUpdate = true
      if (lms) this.updateOccluder(lms)
      if (!this.frame.visible) { this.frame.visible = true; this.onFace(true) }
    } else if (this.frame.visible) {
      this.frame.visible = false
      this.occluder.visible = false
      this.onFace(false)
    }
    this.render()
  }

  private render() {
    this.renderer.render(this.scene, this.camera)
  }
}
