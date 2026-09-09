import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useFaceLandmarker } from '../lib/useFaceLandmarker'
import {
  computeFramePlacement,
  drawTryOn,
  loadImage,
  smoothPlacement,
  snapshot,
  type FramePlacement,
} from '../lib/tryOn'
import { assetUrl } from '../lib/shop'

export type TryOnFrame = { slug: string; name: string; tryOnImageUrl: string | null }

type Phase = 'starting' | 'live' | 'posed' | 'blocked' | 'noframes'

const TRACK_MS = 120 // ~8 detections/sec — reads as tracking, keeps the GPU light

/** Full-screen virtual try-on: a live frame overlay on the camera, freeze to compare and share. */
export default function TryOn({ frames, onClose }: { frames: TryOnFrame[]; onClose: () => void }) {
  const withImages = frames.filter((f) => f.tryOnImageUrl)
  const [phase, setPhase] = useState<Phase>(withImages.length ? 'starting' : 'noframes')
  const [idx, setIdx] = useState(0)
  const [err, setErr] = useState<string | null>(null)
  const [seesFace, setSeesFace] = useState(false)

  const videoRef = useRef<HTMLVideoElement>(null)
  const liveCanvasRef = useRef<HTMLCanvasElement>(null)
  const posedCanvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const stillRef = useRef<HTMLImageElement | null>(null)
  const posedLandmarksRef = useRef<{ x: number; y: number; z: number }[] | null>(null)
  const placementRef = useRef<FramePlacement | null>(null)
  const frameCache = useRef<Map<string, HTMLImageElement>>(new Map())
  const idxRef = useRef(0)
  idxRef.current = idx

  const { track, loading: modelLoading } = useFaceLandmarker()

  const frameImg = useCallback(async (url: string | null | undefined): Promise<HTMLImageElement | null> => {
    if (!url) return null
    const key = assetUrl(url)
    const hit = frameCache.current.get(key)
    if (hit) return hit
    try {
      const img = await loadImage(key)
      frameCache.current.set(key, img)
      return img
    } catch {
      return null
    }
  }, [])

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  // Open the camera.
  useEffect(() => {
    if (phase !== 'starting') return
    let cancelled = false
    ;(async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
        if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return }
        streamRef.current = stream
        if (videoRef.current) { videoRef.current.srcObject = stream; await videoRef.current.play() }
        setPhase('live')
      } catch {
        setPhase('blocked')
      }
    })()
    return () => { cancelled = true }
  }, [phase])

  useEffect(() => () => stopCamera(), [])

  // Live tracking + overlay loop.
  useEffect(() => {
    if (phase !== 'live') return
    let stop = false
    let timer: number | undefined
    placementRef.current = null

    const tick = async () => {
      const video = videoRef.current
      const canvas = liveCanvasRef.current
      if (stop || !video || !canvas || video.readyState < 2) {
        if (!stop) timer = window.setTimeout(tick, TRACK_MS)
        return
      }
      const lm = await track(video)
      if (stop) return
      setSeesFace(!!lm)

      const img = await frameImg(withImages[idxRef.current]?.tryOnImageUrl)
      if (stop) return

      if (lm && img) {
        const raw = computeFramePlacement(lm, video.videoWidth, video.videoHeight, img.naturalHeight / img.naturalWidth)
        const next = raw ? smoothPlacement(placementRef.current, raw) : null
        placementRef.current = next
        drawTryOn(canvas, video, lm, img, { mirror: true, placement: next })
      } else {
        // Still paint the mirrored camera so the <canvas> can sit on top of a hidden <video>.
        drawTryOn(canvas, video, [], null, { mirror: true })
      }
      if (!stop) timer = window.setTimeout(tick, TRACK_MS)
    }
    void tick()
    return () => { stop = true; if (timer) window.clearTimeout(timer) }
  }, [phase, track, frameImg, withImages])

  // Re-render the frozen shot when the selected frame changes.
  useEffect(() => {
    if (phase !== 'posed') return
    ;(async () => {
      const canvas = posedCanvasRef.current
      const still = stillRef.current
      const lm = posedLandmarksRef.current
      if (!canvas || !still || !lm) return
      const img = await frameImg(withImages[idx]?.tryOnImageUrl)
      drawTryOn(canvas, still, lm, img, { mirror: true })
    })()
  }, [idx, phase, frameImg, withImages])

  async function capture() {
    const video = videoRef.current
    if (!video) return
    setErr(null)
    const lm = await track(video)
    if (!lm) { setErr('We could not see your face clearly. Move into better light and try again.'); return }
    posedLandmarksRef.current = lm
    stillRef.current = await snapshot(video)
    stopCamera()
    setPhase('posed')
  }

  function retake() {
    stillRef.current = null
    posedLandmarksRef.current = null
    placementRef.current = null
    setPhase('starting')
  }

  const current = withImages[idx]

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-ink text-bone">
      <div className="flex items-center justify-between px-5 py-4">
        <span className="font-display text-lg">Virtual try-on</span>
        <button onClick={() => { stopCamera(); onClose() }} className="text-bone/60 hover:text-bone" aria-label="Close">✕</button>
      </div>

      <div className="relative flex-1 overflow-hidden">
        {phase === 'noframes' && (
          <Centered>
            <p className="text-bone/70">Virtual try-on isn't ready for these frames yet — the cut-out images are still being added.</p>
            <button onClick={onClose} className="btn-primary mt-5 !bg-bone !text-ink">Keep browsing</button>
          </Centered>
        )}

        {phase === 'blocked' && (
          <Centered>
            <p className="text-bone/70">We need camera access for try-on. Allow it in your browser and reopen, or keep browsing.</p>
            <button onClick={onClose} className="btn-primary mt-5 !bg-bone !text-ink">Back</button>
          </Centered>
        )}

        {(phase === 'starting' || phase === 'live') && (
          <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center px-5">
            <div className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-bone/15 bg-black">
              {/* video feeds the tracker; the canvas shows the mirrored feed + overlay */}
              <video ref={videoRef} playsInline muted className="absolute inset-0 h-full w-full object-cover opacity-0" />
              <canvas ref={liveCanvasRef} className="absolute inset-0 h-full w-full object-cover" />
              {phase === 'live' && !seesFace && (
                <div className="absolute inset-x-0 bottom-3 text-center text-xs text-bone/70">
                  Line your face up in the frame
                </div>
              )}
            </div>
            {modelLoading && <p className="mt-3 text-sm text-bone/50">Loading the face model…</p>}
            {err && <p className="mt-3 text-sm text-clay">{err}</p>}
            {phase === 'live' && withImages.length > 1 && (
              <FrameStrip frames={withImages} idx={idx} onPick={setIdx} />
            )}
            <button
              className="btn-primary mt-4 w-full !bg-bone !text-ink disabled:opacity-50"
              disabled={phase !== 'live' || !seesFace}
              onClick={capture}
            >
              {phase !== 'live' ? 'Starting camera…' : seesFace ? 'Freeze this look' : 'Looking for your face…'}
            </button>
          </div>
        )}

        {phase === 'posed' && (
          <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center px-5">
            <div className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-bone/15 bg-black">
              <canvas ref={posedCanvasRef} className="h-full w-full object-cover" />
            </div>
            <div className="mt-2 text-center text-sm text-bone/60">{current?.name}</div>
            <button onClick={retake} className="mt-1 text-xs text-bone/45 underline">Retake photo</button>
          </div>
        )}
      </div>

      {phase === 'posed' && (
        <div className="border-t border-bone/10 px-4 py-4">
          <FrameStrip frames={withImages} idx={idx} onPick={setIdx} />
          {current && (
            <Link
              to={`/store/${current.slug}`}
              onClick={() => { stopCamera(); onClose() }}
              className="btn-primary mt-4 block w-full !bg-bone !text-ink text-center"
            >
              Shop {current.name}
            </Link>
          )}
        </div>
      )}
    </div>
  )
}

function FrameStrip({ frames, idx, onPick }: { frames: TryOnFrame[]; idx: number; onPick: (i: number) => void }) {
  return (
    <div className="mt-3 flex w-full gap-3 overflow-x-auto">
      {frames.map((f, i) => (
        <button
          key={f.slug}
          onClick={() => onPick(i)}
          className={`shrink-0 rounded-xl border px-3 py-2 text-sm ${i === idx ? 'border-bone bg-bone text-ink' : 'border-bone/25 text-bone/70'}`}
        >
          {f.name}
        </button>
      ))}
    </div>
  )
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto flex h-full max-w-sm flex-col items-center justify-center px-6 text-center">{children}</div>
}
