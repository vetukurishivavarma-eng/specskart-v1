import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { TryOnScene } from '../lib/tryOnScene'

export type TryOnFrame = { slug: string; name: string; colour?: string | null }

type Phase = 'starting' | 'live' | 'posed' | 'blocked'

/** Full-screen 3D virtual try-on: a tracked glasses mesh over the live camera. */
export default function TryOn({ frames, onClose }: { frames: TryOnFrame[]; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('starting')
  const [idx, setIdx] = useState(0)
  const [seesFace, setSeesFace] = useState(false)
  const [modelLoading, setModelLoading] = useState(true)
  const [err, setErr] = useState<string | null>(null)

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

        const scene = new TryOnScene(stage, v, { onFace: setSeesFace })
        sceneRef.current = scene
        scene.setColour(current?.colour)
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

  // Swap the frame colour when the selection changes.
  useEffect(() => { sceneRef.current?.setColour(current?.colour) }, [idx]) // eslint-disable-line react-hooks/exhaustive-deps

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
                className="btn-primary mt-4 w-full !bg-bone !text-ink disabled:opacity-50"
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
