import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { TryOnScene } from '../lib/tryOnScene'
import { TRY_ON_3D_TUNING } from '../lib/tryOnMatrix'

export type TryOnFrame = { slug: string; name: string; colour?: string | null; tryOnImageUrl?: string | null }

type Phase = 'starting' | 'live' | 'posed' | 'blocked'
type Fit = { scale: number; offsetUpCm: number; offsetForwardCm: number }

const FIT_KEY = 'specskart_tryon_fit'
const DEFAULT_FIT: Fit = {
  scale: TRY_ON_3D_TUNING.scale,
  offsetUpCm: TRY_ON_3D_TUNING.offsetUpCm,
  offsetForwardCm: TRY_ON_3D_TUNING.offsetForwardCm,
}

function loadFit(): Fit {
  try {
    const raw = localStorage.getItem(FIT_KEY)
    if (raw) return { ...DEFAULT_FIT, ...JSON.parse(raw) }
  } catch { /* private mode / bad json */ }
  return DEFAULT_FIT
}

/** Full-screen 3D virtual try-on: a head-tracked frame over the live camera. */
export default function TryOn({ frames, onClose }: { frames: TryOnFrame[]; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('starting')
  const [idx, setIdx] = useState(0)
  const [seesFace, setSeesFace] = useState(false)
  const [modelLoading, setModelLoading] = useState(true)
  const [err, setErr] = useState<string | null>(null)
  const [adjust, setAdjust] = useState(false)
  const [fit, setFit] = useState<Fit>(loadFit)

  const stageRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const sceneRef = useRef<TryOnScene | null>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const current = frames[idx]

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  // Open the camera and build the 3D scene once, on mount.
  useEffect(() => {
    let cancelled = false
    let ro: ResizeObserver | undefined
    ;(async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
        if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return }
        streamRef.current = stream
        const v = videoRef.current
        const stage = stageRef.current
        if (!v || !stage) return
        v.srcObject = stream
        await v.play()
        setPhase('live')

        const scene = new TryOnScene(stage, v, { onFace: setSeesFace, tuning: loadFit() })
        sceneRef.current = scene
        scene.setFrame(current ?? {})
        scene.start().then(() => setModelLoading(false))
          .catch(() => setErr('Could not start the face tracker. Check your connection and try again.'))
        ro = new ResizeObserver(() => scene.resize())
        ro.observe(stage)
      } catch {
        setPhase('blocked')
      }
    })()
    return () => {
      cancelled = true
      ro?.disconnect()
      sceneRef.current?.dispose()
      sceneRef.current = null
      stopCamera()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Swap the frame when the selection changes.
  useEffect(() => { sceneRef.current?.setFrame(current ?? {}) }, [idx]) // eslint-disable-line react-hooks/exhaustive-deps

  function updateFit(patch: Partial<Fit>) {
    const next = { ...fit, ...patch }
    setFit(next)
    sceneRef.current?.setTuning(next)
    try { localStorage.setItem(FIT_KEY, JSON.stringify(next)) } catch { /* private mode */ }
  }

  function resetFit() {
    setFit(DEFAULT_FIT)
    sceneRef.current?.setTuning(DEFAULT_FIT)
    try { localStorage.removeItem(FIT_KEY) } catch { /* */ }
  }

  function capture() {
    sceneRef.current?.freeze()
    videoRef.current?.pause()
    setPhase('posed')
  }

  function retake() {
    videoRef.current?.play().catch(() => {})
    sceneRef.current?.resume()
    setPhase('live')
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-ink text-bone">
      <div className="flex items-center justify-between px-5 py-4">
        <span className="font-display text-lg">Virtual try-on</span>
        <button onClick={() => { stopCamera(); onClose() }} className="text-bone/60 hover:text-bone" aria-label="Close">✕</button>
      </div>

      <div className="relative flex-1 overflow-hidden">
        {phase === 'blocked' ? (
          <Centered>
            <p className="text-bone/70">We need camera access for try-on. Allow it in your browser and reopen, or keep browsing.</p>
            <button onClick={onClose} className="btn-primary mt-5 !bg-bone !text-ink">Keep browsing</button>
          </Centered>
        ) : (
          <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center px-5">
            <div
              ref={stageRef}
              className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-bone/15 bg-black"
              style={{ transform: 'scaleX(-1)' }}
            >
              <video ref={videoRef} playsInline muted className="absolute inset-0 h-full w-full object-cover" />
              {/* the Three.js canvas is mounted here by TryOnScene and inherits the mirror */}
            </div>

            {modelLoading && phase !== 'posed' && <p className="mt-3 text-sm text-bone/50">Loading the face model…</p>}
            {err && <p className="mt-3 text-sm text-clay">{err}</p>}

            {frames.length > 1 && <FrameStrip frames={frames} idx={idx} onPick={setIdx} />}

            <div className="mt-2 w-full">
              <button onClick={() => setAdjust((a) => !a)} className="text-xs text-bone/55 underline">
                {adjust ? 'Hide fit controls' : 'Adjust fit'}
              </button>
              {adjust && (
                <div className="mt-2 space-y-2 rounded-xl border border-bone/15 p-3 text-xs">
                  <Slider label="Size" min={0.7} max={1.8} step={0.02} value={fit.scale}
                    onChange={(v) => updateFit({ scale: v })} />
                  <Slider label="Up / down" min={-1} max={5} step={0.1} value={fit.offsetUpCm}
                    onChange={(v) => updateFit({ offsetUpCm: v })} />
                  <Slider label="Depth" min={0} max={4} step={0.1} value={fit.offsetForwardCm}
                    onChange={(v) => updateFit({ offsetForwardCm: v })} />
                  <button onClick={resetFit} className="text-bone/50 underline">Reset</button>
                </div>
              )}
            </div>

            {phase === 'posed' ? (
              <div className="mt-4 flex w-full gap-3">
                <button onClick={retake} className="btn-ghost flex-1 !border-bone/25 !text-bone">Retake</button>
                {current && (
                  <Link to={`/store/${current.slug}`} onClick={() => { stopCamera(); onClose() }}
                    className="btn-primary flex-1 !bg-bone !text-ink text-center">Shop {current.name}</Link>
                )}
              </div>
            ) : (
              <button
                className="btn-primary mt-3 w-full !bg-bone !text-ink disabled:opacity-50"
                disabled={phase !== 'live' || !seesFace}
                onClick={capture}
              >
                {phase !== 'live' ? 'Starting camera…' : seesFace ? 'Freeze this look' : 'Line your face up…'}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function Slider({ label, min, max, step, value, onChange }: {
  label: string; min: number; max: number; step: number; value: number; onChange: (v: number) => void
}) {
  return (
    <label className="flex items-center gap-3">
      <span className="w-20 shrink-0 text-bone/60">{label}</span>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))} className="flex-1 accent-bone" />
    </label>
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
