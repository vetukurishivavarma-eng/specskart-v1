import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useFaceLandmarker } from '../lib/useFaceLandmarker'
import { syntheticGeometry, type Geometry } from '../lib/faceGeometry'
import { assetUrl, rememberFace, shop } from '../lib/shop'
import type { TryOnFrame } from '../components/TryOn'

// Pulls in Three.js + MediaPipe — kept out of the initial bundle.
const TryOn = lazy(() => import('../components/TryOn'))

const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'

function hoursTo(iso: string) {
  return Math.max(1, Math.round((new Date(iso).getTime() - Date.now()) / 3_600_000))
}

const QUIZ = [
  { key: 'vibe', label: 'Overall vibe', options: [
    { value: 'classic', label: 'Classic' }, { value: 'bold', label: 'Bold' }, { value: 'minimal', label: 'Minimal' } ] },
  { key: 'colour', label: 'Colour tones', options: [
    { value: 'warm', label: 'Warm' }, { value: 'cool', label: 'Cool' }, { value: 'neutral', label: 'Neutral' } ] },
  { key: 'budget', label: 'Budget', options: [
    { value: 'low', label: 'Under K700' }, { value: 'mid', label: 'K700–1000' }, { value: 'high', label: 'K1000+' } ] },
  { key: 'screenHours', label: 'Screen time', options: [
    { value: 'high', label: 'A lot' }, { value: 'some', label: 'Some' }, { value: 'low', label: 'Not much' } ] },
] as const

type SessionView = {
  status: string; storeName: string; leadFirstName: string | null
  expired: boolean; consentPolicyVersion: string
}
type Frame = { code: string; displayName: string; description: string; reason: string }
type Result = {
  faceShape: string; faceShapeDisplay: string; confidence: number; message: string
  recommended: Frame[]; avoidOrUseCarefully: Frame[]
  promoCode: string | null; promoPercent: number; promoExpiresAt: string | null
}

type Stage = 'loading' | 'invalid' | 'intro' | 'consent' | 'capture' | 'analysing' | 'result'

export default function FrameFinder() {
  const [params] = useSearchParams()
  const token = params.get('s')
  const [stage, setStage] = useState<Stage>('loading')
  const [session, setSession] = useState<SessionView | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [result, setResult] = useState<Result | null>(null)
  const [sentToWa, setSentToWa] = useState(false)
  const [quizOpen, setQuizOpen] = useState(false)
  const [refined, setRefined] = useState(false)
  const [quiz, setQuiz] = useState<Record<string, string>>({})
  const [quizBusy, setQuizBusy] = useState(false)
  const [tryOnFrames, setTryOnFrames] = useState<TryOnFrame[] | null>(null)
  const [tryOnLoading, setTryOnLoading] = useState(false)
  const [tryOnNote, setTryOnNote] = useState<string | null>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const { analyse, loading: modelLoading } = useFaceLandmarker()

  useEffect(() => {
    if (!token) { setStage('invalid'); return }
    api<SessionView>(`/frame-finder/session/${token}`)
      .then((s) => { setSession(s); setStage(s.expired ? 'invalid' : 'intro') })
      .catch((e: ApiError) => { setErr(e.message); setStage('invalid') })
  }, [token])

  useEffect(() => () => stopCamera(), [])

  function track(event: string, metadata?: Record<string, unknown>) {
    if (!token) return
    api(`/frame-finder/session/${token}/event`, { method: 'POST', body: JSON.stringify({ event, metadata: metadata ?? {} }) }).catch(() => {})
  }

  async function giveConsent() {
    try {
      await api(`/frame-finder/session/${token}/consent`, {
        method: 'POST',
        body: JSON.stringify({ cameraConsent: true, photoProcessingConsent: true, policyVersion: session?.consentPolicyVersion }),
      })
      setStage('capture')
      startCamera()
    } catch (e) { setErr((e as ApiError).message) }
  }

  async function startCamera() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
      streamRef.current = stream
      if (videoRef.current) { videoRef.current.srcObject = stream; await videoRef.current.play() }
      track('CAMERA_PERMISSION_GRANTED')
    } catch {
      track('CAMERA_PERMISSION_DENIED')
      setErr('Camera access was blocked. You can upload a photo or use “Simulate” instead.')
    }
  }

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  async function runAnalysis(geometry: Geometry) {
    setStage('analysing')
    setErr(null)
    track('PHOTO_CAPTURED')
    try {
      const r = await api<Result>(`/frame-finder/session/${token}/analysis`, {
        method: 'POST', body: JSON.stringify({ geometry }),
      })
      setResult(r)
      rememberFace(r.faceShape, session?.leadFirstName)
      stopCamera()
      setStage('result')
    } catch (e) {
      setErr((e as ApiError).message)
      setStage('capture')
    }
  }

  async function captureFromVideo() {
    if (!videoRef.current) return
    try {
      const { geometry } = await analyse(videoRef.current)
      await runAnalysis(geometry)
    } catch (e) {
      setErr((e as Error).message)
    }
  }

  async function onUpload(file: File) {
    const img = new Image()
    img.src = URL.createObjectURL(file)
    await img.decode()
    try {
      const { geometry } = await analyse(img)
      await runAnalysis(geometry)
    } catch (e) {
      setErr((e as Error).message)
    }
  }

  async function sendToWhatsApp() {
    await api(`/frame-finder/session/${token}/send-to-whatsapp`, { method: 'POST' })
    setSentToWa(true)
  }

  async function submitQuiz() {
    setQuizBusy(true)
    try {
      const r = await api<Result>(`/frame-finder/session/${token}/style-quiz`, {
        method: 'POST', body: JSON.stringify(quiz),
      })
      setResult(r)
      setRefined(true)
      setQuizOpen(false)
    } catch (e) {
      setErr((e as ApiError).message)
    } finally {
      setQuizBusy(false)
    }
  }

  async function openTryOn() {
    if (!result) return
    setTryOnLoading(true)
    setTryOnNote(null)
    try {
      const products = await shop.products({ faceShape: result.faceShape })
      setTryOnFrames(products.map((p) => ({
        slug: p.slug, name: p.name, colour: p.colour, tryOnImageUrl: assetUrl(p.tryOnImageUrl) || null,
      })))
    } catch {
      setTryOnNote('Could not load the try-on. Please try again in a moment.')
    } finally {
      setTryOnLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-ink text-bone">
      <div className="mx-auto max-w-md px-5 py-10">
        <div className="font-display text-lg">{session?.storeName ?? 'Specskart'} · Frame Finder</div>

        {stage === 'loading' && <p className="mt-20 text-center text-bone/60">Loading your session…</p>}

        {stage === 'invalid' && (
          <div className="mt-16 text-center">
            <h1 className="text-3xl">This link isn't active</h1>
            <p className="mt-3 text-bone/70">{err ?? 'Your Frame Finder link may have expired. Message us on WhatsApp for a fresh one.'}</p>
          </div>
        )}

        {stage === 'intro' && (
          <div className="mt-10">
            <h1 className="text-4xl leading-tight">Find frames made for your face{session?.leadFirstName ? `, ${session.leadFirstName}` : ''}.</h1>
            <p className="mt-4 text-bone/70">Take a quick selfie and we'll recommend frame styles that complement your face.</p>
            <button className="btn-primary mt-8 w-full !bg-bone !text-ink" onClick={() => setStage('consent')}>Start Face Analysis</button>
          </div>
        )}

        {stage === 'consent' && (
          <div className="mt-10">
            <h2 className="text-2xl">Before we start</h2>
            <p className="mt-3 text-bone/70">
              Your photo is used only to analyse facial proportions and recommend frame styles. It is processed on your
              device, is not uploaded by default, and is not shared with advertising platforms.
            </p>
            <p className="mt-2 text-xs text-bone/45">Policy version {session?.consentPolicyVersion}</p>
            <button className="btn-primary mt-6 w-full !bg-bone !text-ink" onClick={giveConsent}>I agree — turn on the camera</button>
            <a href={import.meta.env.VITE_WA_LINK ?? '#'} className="mt-3 block text-center text-sm text-bone/50">Not now</a>
          </div>
        )}

        {(stage === 'capture' || stage === 'analysing') && (
          <div className="mt-8">
            <div className="relative aspect-[3/4] overflow-hidden rounded-2xl border border-bone/15 bg-black">
              <video ref={videoRef} playsInline muted className="h-full w-full object-cover [transform:scaleX(-1)]" />
              {stage === 'analysing' && <div className="absolute inset-0 grid place-items-center bg-black/50 text-sm">Analysing…</div>}
            </div>
            {err && <p className="mt-3 text-sm text-clay">{err}</p>}
            {modelLoading && <p className="mt-3 text-sm text-bone/50">Loading the face model…</p>}
            <button className="btn-primary mt-4 w-full !bg-bone !text-ink" onClick={captureFromVideo} disabled={stage === 'analysing'}>
              Capture & analyse
            </button>
            <div className="mt-3 flex items-center justify-between text-sm text-bone/55">
              <label className="cursor-pointer underline">
                Upload a photo
                <input type="file" accept="image/*" hidden onChange={(e) => e.target.files && onUpload(e.target.files[0])} />
              </label>
              <button className="underline" onClick={() => runAnalysis(syntheticGeometry())}>Simulate</button>
            </div>
          </div>
        )}

        {stage === 'result' && result && (
          <div className="mt-8">
            <p className="label !text-bone/50">Your face match</p>
            <h1 className="mt-1 text-5xl">{result.faceShapeDisplay}</h1>
            <p className="mt-1 text-bone/60">{Math.round(result.confidence * 100)}% match confidence · {result.message}</p>
            {refined && <p className="mt-2 text-sm text-bone/60">Refined for your style ✨</p>}

            <p className="mt-8 label !text-bone/50">Recommended styles</p>
            <div className="mt-3 space-y-2">
              {result.recommended.map((f) => (
                <div key={f.code} className="rounded-xl border border-bone/15 p-3">
                  <div className="font-display text-lg">{f.displayName}</div>
                  <div className="text-sm text-bone/60">{f.description}</div>
                </div>
              ))}
            </div>
            {result.avoidOrUseCarefully.length > 0 && (
              <p className="mt-4 text-sm text-bone/45">
                Use carefully: {result.avoidOrUseCarefully.map((f) => f.displayName).join(', ')}
              </p>
            )}

            {!refined && !quizOpen && (
              <button
                className="mt-6 w-full rounded-xl border border-bone/20 bg-bone/5 p-3 text-left text-sm text-bone/70"
                onClick={() => setQuizOpen(true)}
              >
                ✨ Refine with a 30-second style quiz →
              </button>
            )}

            {quizOpen && (
              <div className="mt-6 rounded-xl border border-bone/20 bg-bone/5 p-4">
                <div className="font-display text-lg">Your style</div>
                {QUIZ.map((q) => (
                  <div key={q.key} className="mt-3">
                    <div className="text-sm text-bone/60">{q.label}</div>
                    <div className="mt-1.5 flex flex-wrap gap-2">
                      {q.options.map((o) => (
                        <button
                          key={o.value}
                          onClick={() => setQuiz((s) => ({ ...s, [q.key]: o.value }))}
                          className={`rounded-full border px-3 py-1 text-xs ${
                            quiz[q.key] === o.value ? 'border-bone bg-bone text-ink' : 'border-bone/25 text-bone/70'
                          }`}
                        >
                          {o.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
                <button
                  className="btn-primary mt-4 w-full !bg-bone !text-ink"
                  disabled={quizBusy || Object.keys(quiz).length === 0}
                  onClick={submitQuiz}
                >
                  {quizBusy ? 'Refining…' : 'See my refined picks'}
                </button>
                <button className="mt-2 block w-full text-center text-xs text-bone/45" onClick={() => setQuizOpen(false)}>
                  Skip
                </button>
              </div>
            )}

            {result.promoCode && (
              <div className="mt-6 rounded-xl border border-bone/20 bg-bone/5 p-4 text-center">
                <div className="text-sm text-bone/60">🎁 {result.promoPercent}% off your first pair</div>
                <div className="mt-1 font-display text-2xl tracking-wide">{result.promoCode}</div>
                {result.promoExpiresAt && (
                  <div className="mt-1 text-xs text-bone/45">Ends {hoursTo(result.promoExpiresAt)}h from now</div>
                )}
              </div>
            )}

            <div className="mt-8 space-y-3">
              <button className="btn-primary block w-full !bg-bone !text-ink text-center disabled:opacity-60"
                disabled={tryOnLoading} onClick={openTryOn}>
                {tryOnLoading ? 'Loading try-on…' : 'Try them on 👓'}
              </button>
              {tryOnNote && <p className="text-center text-xs text-bone/60">{tryOnNote}</p>}
              <a href={`/store?face=${result.faceShape}${result.promoCode ? `&promo=${result.promoCode}` : ''}`}
                 className="btn-ghost block w-full !border-bone/25 !text-bone text-center">Shop my frames</a>
              <button className="btn-ghost w-full !border-bone/25 !text-bone" onClick={sendToWhatsApp} disabled={sentToWa}>
                {sentToWa ? 'Sent to WhatsApp ✓' : 'Send results to WhatsApp'}
              </button>
              <a
                href={`https://wa.me/?text=${encodeURIComponent(
                  `I did the Specskart Frame Finder — my face shape is ${result.faceShapeDisplay}! 👓 Find yours: ${WA}`)}`}
                target="_blank" rel="noreferrer"
                className="block w-full text-center text-sm text-bone/60 underline">Share my result</a>
              <button className="block w-full text-center text-sm text-bone/50" onClick={() => { setResult(null); setStage('capture'); startCamera() }}>
                Try another photo
              </button>
            </div>
          </div>
        )}
      </div>

      {tryOnFrames && (
        <Suspense fallback={null}>
          <TryOn frames={tryOnFrames} onClose={() => setTryOnFrames(null)} />
        </Suspense>
      )}
    </div>
  )
}
