import * as THREE from 'three'
import { FilesetResolver, FaceLandmarker, type FaceLandmarkerResult } from '@mediapipe/tasks-vision'
import { buildGlasses, colourToHex, type Glasses } from './glassesModel'
import { solveGlassesMatrix, TRY_ON_3D_TUNING, type TryOn3DTuning } from './tryOnMatrix'

export { TRY_ON_3D_TUNING, solveGlassesMatrix } from './tryOnMatrix'

const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm'
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'

type Opts = { onFace?: (visible: boolean) => void; tuning?: Partial<TryOn3DTuning> }

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
  private glasses: Glasses
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

    this.glasses = buildGlasses()
    this.glasses.matrixAutoUpdate = false
    this.glasses.visible = false
    this.scene.add(this.glasses)
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

  setColour(name: string | null | undefined) {
    this.glasses.setColour(colourToHex(name))
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
    if (matrix && matrix.length === 16) {
      this.glasses.matrix.copy(solveGlassesMatrix(Array.from(matrix), this.tuning))
      this.glasses.matrixWorldNeedsUpdate = true
      if (!this.glasses.visible) { this.glasses.visible = true; this.onFace(true) }
    } else if (this.glasses.visible) {
      this.glasses.visible = false
      this.onFace(false)
    }
    this.render()
  }

  private render() {
    this.renderer.render(this.scene, this.camera)
  }
}
