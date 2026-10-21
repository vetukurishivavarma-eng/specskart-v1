import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import { WA } from '../lib/wa'

type Slice = { label: string; weight: number }
type Result = { prize: string; code: string; expiresAt: string; redeemed: boolean; sliceIndex: number; shareLink: string | null }
type Page = { firstName: string | null; slices: Slice[]; result: Result | null }

// two tones per slice: a light-to-deep radial fill reads as a real, lit wheel
const COLORS: [string, string][] = [
  ['#4B65FF', '#1F35C7'], ['#2BC4B4', '#0B7C74'], ['#FF7A59', '#D2391C'],
  ['#8B5CF6', '#5B2BC4'], ['#F5B83D', '#C98407'], ['#3FA7FF', '#1767C2'],
]
const SPIN_MS = 6000

/** An icon per prize, from its name: the wheel shows pictures, the list below says what each is. */
function icon(label: string) {
  const l = label.toLowerCase()
  if (l.includes('friend')) return '🤝'
  if (l.includes('sunglass')) return '🕶️'
  if (l.includes('eye check') || l.includes('eye test')) return '👁️'
  if (l.includes('second') || l.includes('frame')) return '👓'
  if (l.includes('lens') || l.includes('photochrom')) return '✨'
  return '🎁'
}

/** Point on the wheel's edge, `deg` clockwise from the top. */
function at(deg: number, r: number) {
  const a = ((deg - 90) * Math.PI) / 180
  return [100 + r * Math.cos(a), 100 + r * Math.sin(a)]
}

/**
 * Spin & Win, opened from the link the WhatsApp bot sends. The server decides the prize (and
 * refuses a second spin); this page only turns the wheel to it. Each slice's size is its real chance
 * (no numbers shown, but the wheel isn't drawn fairer than it is).
 */
export default function Spin() {
  const { leadId } = useParams()
  const [params] = useSearchParams()
  const q = `?exp=${params.get('exp') ?? ''}&sig=${encodeURIComponent(params.get('sig') ?? '')}`
  const [page, setPage] = useState<Page | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [turn, setTurn] = useState(0)
  const [spinning, setSpinning] = useState(false)
  const [result, setResult] = useState<Result | null>(null)
  const [party, setParty] = useState(false)

  useEffect(() => {
    api<Page>(`/public/spin/${leadId}${q}`)
      .then((p) => { setPage(p); if (p.result) { setResult(p.result); setTurn(landOn(p.slices, p.result.sliceIndex, 0)) } })
      .catch((e) => setError(e.message))
  }, [leadId, q])

  async function spin() {
    if (!page || spinning || result) return
    setSpinning(true)
    try {
      const r = await api<Result>(`/public/spin/${leadId}${q}`, { method: 'POST' })
      setTurn(landOn(page.slices, r.sliceIndex, 8))
      setTimeout(() => { setResult(r); setSpinning(false); setParty(true) }, SPIN_MS + 150)
    } catch (e) {
      setError((e as Error).message)
      setSpinning(false)
    }
  }

  if (error) return (
    <div className="container-x max-w-md py-20 text-center">
      <h1 className="text-2xl">Spin & Win</h1>
      <p className="mt-3 text-ink/60">{error}</p>
      <a href={`${WA}?text=${encodeURIComponent('Spin')}`} className="btn-primary mt-6 inline-block">Message us on WhatsApp</a>
    </div>
  )
  if (!page) return <div className="container-x py-20 text-center text-ink/50">Loading…</div>

  const total = page.slices.reduce((s, x) => s + x.weight, 0)
  let from = 0
  const arcs = page.slices.map((s, i) => {
    const sweep = (s.weight / total) * 360
    const a = { i, from, to: from + sweep }
    from += sweep
    return a
  })

  return (
    <div className="relative min-h-screen overflow-hidden px-4 py-10 text-white"
      style={{ background: 'radial-gradient(120% 80% at 50% 0%, #2342F0 0%, #121a4a 45%, #0A0F1F 100%)' }}>
      <style>{`
        @keyframes sk-bulb { 0%,100% { opacity: 1 } 50% { opacity: .25 } }
        @keyframes sk-pop { from { transform: scale(.85) translateY(12px); opacity: 0 } to { transform: none; opacity: 1 } }
        @keyframes sk-fall { to { transform: translate3d(var(--dx), 110vh, 0) rotate(720deg) } }
        @keyframes sk-pulse { 0%,100% { box-shadow: 0 0 0 0 rgba(200,240,75,.55) } 50% { box-shadow: 0 0 0 14px rgba(200,240,75,0) } }
      `}</style>
      {party && <Confetti />}

      <div className="relative mx-auto max-w-md text-center">
        <p className="label !text-signal">Specskart Opticians</p>
        <h1 className="mt-2 text-4xl font-bold">{page.firstName ? `${page.firstName}, spin & win!` : 'Spin & win!'}</h1>
        <p className="mt-2 text-sm text-white/70">Every spin wins a prize 🎁</p>

        <div className="relative mx-auto mt-8 aspect-square w-full max-w-[340px]">
          {/* static glow + rim with blinking lights (never rotated, so the spin stays smooth) */}
          <div className="absolute inset-0 rounded-full" style={{ boxShadow: '0 0 60px 10px rgba(75,101,255,.45), inset 0 0 0 10px #0A0F1F' }} />
          {/* the pointer */}
          <div className="absolute left-1/2 top-[-10px] z-20 -translate-x-1/2" style={{ filter: 'drop-shadow(0 3px 6px rgba(0,0,0,.5))' }}>
            <svg width="34" height="42" viewBox="0 0 34 42"><path d="M17 42 L2 10 A16 16 0 1 1 32 10 Z" fill="#C8F04B" /><circle cx="17" cy="15" r="6" fill="#0A0F1F" /></svg>
          </div>
          <div className="absolute inset-0" style={{
            transform: `rotate(${turn}deg)`,
            transition: spinning ? `transform ${SPIN_MS}ms cubic-bezier(0.15, 0.85, 0.2, 1)` : 'none',
            willChange: 'transform',
          }}>
            <svg viewBox="0 0 200 200" className="h-full w-full">
              <defs>
                {COLORS.map(([light, deep], k) => (
                  <radialGradient key={k} id={`sk-g${k}`} cx="50%" cy="50%" r="50%">
                    <stop offset="0%" stopColor={light} /><stop offset="100%" stopColor={deep} />
                  </radialGradient>
                ))}
              </defs>
              <circle cx="100" cy="100" r="99" fill="#0A0F1F" />
              {arcs.map(({ i, from, to }) => {
                const [x1, y1] = at(from, 90), [x2, y2] = at(to, 90)
                const d = arcs.length === 1
                  ? 'M100 10 A90 90 0 1 1 99.99 10 Z'
                  : `M100 100 L${x1} ${y1} A90 90 0 ${to - from > 180 ? 1 : 0} 1 ${x2} ${y2} Z`
                const mid = (from + to) / 2
                const [tx, ty] = at(mid, 60)
                return (
                  <g key={i}>
                    <path d={d} fill={`url(#sk-g${i % COLORS.length})`} stroke="#ffffff" strokeOpacity=".85" strokeWidth="1.2" />
                    <text x={tx} y={ty} fontSize={to - from < 45 ? 15 : 20} textAnchor="middle" dominantBaseline="central"
                      transform={`rotate(${mid} ${tx} ${ty})`}>{icon(page.slices[i].label)}</text>
                  </g>
                )
              })}
              {/* lights on the rim */}
              {Array.from({ length: 16 }, (_, k) => {
                const [bx, by] = at(k * 22.5, 95)
                return <circle key={k} cx={bx} cy={by} r="2.4" fill={k % 2 ? '#C8F04B' : '#ffffff'}
                  style={{ animation: `sk-bulb 1.${k % 2 ? 2 : 6}s ease-in-out infinite`, animationDelay: `${(k % 4) * 0.15}s` }} />
              })}
              <circle cx="100" cy="100" r="17" fill="#0A0F1F" stroke="#C8F04B" strokeWidth="3" />
              <text x="100" y="100" fontSize="13" textAnchor="middle" dominantBaseline="central">👓</text>
            </svg>
          </div>
        </div>

        {!result && (
          <button onClick={spin} disabled={spinning}
            className="mt-9 w-full rounded-full bg-signal py-4 text-lg font-extrabold tracking-[0.2em] text-ink transition active:scale-95 disabled:opacity-70"
            style={spinning ? undefined : { animation: 'sk-pulse 1.8s ease-in-out infinite' }}>
            {spinning ? 'GOOD LUCK…' : 'SPIN'}
          </button>
        )}

        {result && (
          <div className="mt-8 rounded-3xl bg-white p-6 text-left text-ink shadow-2xl" style={{ animation: 'sk-pop .5s cubic-bezier(.2,1.4,.4,1) both' }}>
            <p className="text-sm font-medium text-ink/60">{result.redeemed ? 'You used your prize:' : 'You won 🎉'}</p>
            <p className="mt-1 flex items-center gap-2 text-2xl font-bold"><span>{icon(result.prize)}</span>{result.prize}</p>
            {!result.redeemed && result.shareLink && (
              <>
                <p className="mt-4 text-sm text-ink/70">
                  To unlock it, a friend who is new to Specskart must message us with your code <b>{result.code}</b>.
                  As soon as they do, you both get a code for the shop.
                </p>
                <a href={`https://wa.me/?text=${encodeURIComponent(`I won a prize at Specskart Opticians 🎁 Message them with my code so we both get it: ${result.shareLink}`)}`}
                  className="btn-primary mt-4 block text-center">Send to a friend on WhatsApp</a>
              </>
            )}
            {!result.redeemed && !result.shareLink && (
              <>
                <div className="mt-4 rounded-2xl border-2 border-dashed border-ink/20 bg-bone p-4 text-center">
                  <p className="text-xs uppercase tracking-widest text-ink/50">Your code</p>
                  <p className="mt-1 font-display text-3xl tracking-[0.2em]">{result.code}</p>
                </div>
                <p className="mt-3 text-sm text-ink/70">
                  Show it at Specskart Opticians by <b>{new Date(result.expiresAt).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })}</b>.
                  We’ve also sent it to you on WhatsApp.
                </p>
                <a href={`${WA}?text=${encodeURIComponent('Book my visit')}`} className="btn-primary mt-4 block text-center">Book my free eye test</a>
              </>
            )}
          </div>
        )}

        <div className="mt-8 rounded-2xl bg-white/5 p-4 text-left backdrop-blur">
          <p className="text-xs font-semibold uppercase tracking-widest text-white/50">Prizes on the wheel</p>
          <ul className="mt-3 space-y-2.5 text-sm">
            {page.slices.map((s, i) => (
              <li key={i} className="flex items-center gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-base"
                  style={{ background: `linear-gradient(135deg, ${COLORS[i % COLORS.length][0]}, ${COLORS[i % COLORS.length][1]})` }}>{icon(s.label)}</span>
                <span>{s.label}</span>
              </li>
            ))}
          </ul>
        </div>
        <p className="mt-6 text-xs text-white/40">Prizes are collected at our shop within 2 weeks. You can spin again once those 2 weeks are over.</p>
      </div>
    </div>
  )
}

/** A short burst of confetti when the prize lands. */
function Confetti() {
  const bits = Array.from({ length: 70 }, (_, k) => ({
    left: Math.random() * 100,
    dx: `${(Math.random() - 0.5) * 30}vw`,
    delay: Math.random() * 0.6,
    dur: 2.4 + Math.random() * 1.6,
    color: ['#C8F04B', '#4B65FF', '#FF7A59', '#2BC4B4', '#F5B83D', '#ffffff'][k % 6],
    w: 6 + Math.random() * 6,
  }))
  return (
    <div className="pointer-events-none fixed inset-0 z-30 overflow-hidden" aria-hidden>
      {bits.map((b, k) => (
        <span key={k} className="absolute top-[-20px] block rounded-sm" style={{
          left: `${b.left}%`, width: b.w, height: b.w * 0.45, background: b.color,
          ['--dx' as string]: b.dx,
          animation: `sk-fall ${b.dur}s cubic-bezier(.25,.6,.4,1) ${b.delay}s forwards`,
        }} />
      ))}
    </div>
  )
}

/** Rotation that brings a random point inside slice `index` under the top pointer, after `laps` full turns. */
function landOn(slices: Slice[], index: number, laps: number) {
  const total = slices.reduce((s, x) => s + x.weight, 0)
  let from = 0
  for (let i = 0; i < index; i++) from += (slices[i].weight / total) * 360
  const sweep = (slices[index].weight / total) * 360
  const point = from + sweep * (0.2 + Math.random() * 0.6)
  return laps * 360 + (360 - point)
}
