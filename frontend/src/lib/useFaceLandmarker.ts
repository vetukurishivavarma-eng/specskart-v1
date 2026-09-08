import { useCallback, useRef, useState } from 'react'
import { FilesetResolver, FaceLandmarker } from '@mediapipe/tasks-vision'
import { geometryFromLandmarks, type Geometry } from './faceGeometry'

const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm'
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'

export function useFaceLandmarker() {
  const ref = useRef<FaceLandmarker | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const ensure = useCallback(async () => {
    if (ref.current) return ref.current
    setLoading(true)
    try {
      const fileset = await FilesetResolver.forVisionTasks(WASM)
      ref.current = await FaceLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL, delegate: 'GPU' },
        numFaces: 1,
        runningMode: 'IMAGE',
      })
      return ref.current
    } catch (e) {
      setError('Face model could not be loaded. Check your connection or use “Simulate”.')
      throw e
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * One detection for a live-preview loop: the raw landmarks or null. Never
   * throws (a missing or doubled face is just "no overlay this frame"), and
   * skips the geometry maths the try-on overlay doesn't need.
   */
  const track = useCallback(async (source: HTMLVideoElement | HTMLImageElement): Promise<{ x: number; y: number; z: number }[] | null> => {
    const fl = ref.current ?? (await ensure())
    try {
      const res = fl.detect(source)
      if ((res.faceLandmarks?.length ?? 0) !== 1) return null
      return res.faceLandmarks[0] as { x: number; y: number; z: number }[]
    } catch {
      return null
    }
  }, [ensure])

  const analyse = useCallback(async (source: HTMLVideoElement | HTMLImageElement): Promise<{ geometry: Geometry; faces: number; landmarks: { x: number; y: number; z: number }[] }> => {
    const fl = await ensure()
    const res = fl.detect(source)
    const faces = res.faceLandmarks?.length ?? 0
    if (faces !== 1) {
      throw new Error(faces === 0
        ? 'We could not see a face. Make sure your whole face is visible and well lit.'
        : 'We see more than one face. Please make sure only you are in frame.')
    }
    // Landmark x/y are normalised to the FRAME's width/height. Pass the real
    // pixel size so the geometry can undo the aspect-ratio distortion — without
    // it every face reads as the same shape at floor confidence.
    const dims = source instanceof HTMLVideoElement
      ? { width: source.videoWidth, height: source.videoHeight }
      : { width: source.naturalWidth, height: source.naturalHeight }
    const landmarks = res.faceLandmarks[0] as { x: number; y: number; z: number }[]
    return { geometry: geometryFromLandmarks(landmarks as any, dims), faces, landmarks }
  }, [ensure])

  return { analyse, track, loading, error }
}
