import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useFaceLandmarker } from '../lib/useFaceLandmarker'
import { drawTryOn, loadImage, snapshot } from '../lib/tryOn'
import { assetUrl } from '../lib/shop'

export type TryOnFrame = { slug: string; name: string; tryOnImageUrl: string | null }

type Phase = 'starting' | 'ready' | 'posed' | 'blocked' | 'noframes'

/** Full-screen virtual try-on: capture a selfie, then swipe recommended frames onto your face. */
export default function TryOn({ frames, onClose }: { frames: TryOnFrame[]; onClose: () => void }) {
  const withImages = frames.filter((f) => f.tryOnImageUrl)
  const [phase, setPhase] = useState<Phase>(withImages.length ? 'starting' : 'noframes')
  const [idx, setIdx] = useState(0)
  const [err, setErr] = useState<string | null>(null)

  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const stillRef = useRef<HTMLImageElement | null>(null)
  const landmarksRef = useRef<{ x: number; y: number }[] | null>(null)
  const frameCache = useRef<Map<string, HTMLImageElement>>(new Map())
  const { analyse, loading: modelLoading } = useFaceLandmarker()

  useEffect(() => {
    if (phase !== 'starting') return
    let cancelled = false
    ;(async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
        if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return }
        streamRef.current = stream
        if (videoRef.current) { videoRef.current.srcObject = stream; await videoRef.current.play() }
        setPhase('ready')
      } catch {
        setPhase('blocked')
      }
    })()
    return () => { cancelled = true }
  }, [phase])

  useEffect(() => () => stopCamera(), [])

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  async function frameImg(url: string): Promise<HTMLImageElement | null> {
    const key = assetUrl(url)
    if (frameCache.current.has(key)) return frameCache.current.get(key)!
    try {
      const img = await loadImage(key)
      frameCache.current.set(key, img)
      return img
    } catch { return null }
  }

  async function render() {
    const canvas = canvasRef.current
    const still = stillRef.current
    const lm = landmarksRef.current
    if (!canvas || !still || !lm) return
    const cur = withImages[idx]
    const img = cur?.tryOnImageUrl ? await frameImg(cur.tryOnImageUrl) : null
    drawTryOn(canvas, still, lm as any, img, { mirror: true })
  }

  useEffect(() => { if (phase === 'posed') void render() }, [idx, phase]) // eslint-disable-line react-hooks/exhaustive-deps

  async function capture() {
    if (!videoRef.current) return
    setErr(null)
    try {
      const { landmarks } = await analyse(videoRef.current)
      landmarksRef.current = landmarks
      stillRef.current = await snapshot(videoRef.current)
      stopCamera()
      setPhase('posed')
    } catch (e) {
      setErr((e as Error).message)
    }
  }

  function retake() {
    stillRef.current = null
    landmarksRef.current = null
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
            <p className="text-bone/70">Try-on images are still being added for these frames.</p>
            <button onClick={onClose} className="btn-primary mt-5 !bg-bone !text-ink">Back</button>
          </Centered>
        )}

        {phase === 'blocked' && (
          <Centered>
            <p className="text-bone/70">We need camera access for try-on. Allow it in your browser and reopen, or keep browsing.</p>
            <button onClick={onClose} className="btn-primary mt-5 !bg-bone !text-ink">Back</button>
          </Centered>
        )}

        {(phase === 'starting' || phase === 'ready') && (
          <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center px-5">
            <div className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-bone/15 bg-black">
              <video ref={videoRef} playsInline muted className="h-full w-full object-cover [transform:scaleX(-1)]" />
            </div>
            {modelLoading && <p className="mt-3 text-sm text-bone/50">Loading the face model…</p>}
            {err && <p className="mt-3 text-sm text-clay">{err}</p>}
            <button className="btn-primary mt-4 w-full !bg-bone !text-ink disabled:opacity-50"
              disabled={phase !== 'ready'} onClick={capture}>
              {phase === 'ready' ? 'Capture' : 'Starting camera…'}
            </button>
          </div>
        )}

        {phase === 'posed' && (
          <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center px-5">
            <div className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-bone/15 bg-black">
              <canvas ref={canvasRef} className="h-full w-full object-cover" />
            </div>
            <div className="mt-2 text-center text-sm text-bone/60">{current?.name}</div>
            <button onClick={retake} className="mt-1 text-xs text-bone/45 underline">Retake photo</button>
          </div>
        )}
      </div>

      {phase === 'posed' && (
        <div className="border-t border-bone/10 px-4 py-4">
          <div className="flex gap-3 overflow-x-auto">
            {withImages.map((f, i) => (
              <button key={f.slug} onClick={() => setIdx(i)}
                className={`shrink-0 rounded-xl border px-3 py-2 text-sm ${i === idx ? 'border-bone bg-bone text-ink' : 'border-bone/25 text-bone/70'}`}>
                {f.name}
              </button>
            ))}
          </div>
          {current && (
            <Link to={`/store/${current.slug}`} onClick={() => { stopCamera(); onClose() }}
              className="btn-primary mt-4 block w-full !bg-bone !text-ink text-center">
              Shop {current.name}
            </Link>
          )}
        </div>
      )}
    </div>
  )
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto flex h-full max-w-sm flex-col items-center justify-center px-6 text-center">{children}</div>
}
