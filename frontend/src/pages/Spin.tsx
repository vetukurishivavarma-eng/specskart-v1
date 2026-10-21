import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import { WA } from '../lib/wa'

type Slice = { label: string; weight: number }
type Result = { prize: string; code: string; expiresAt: string; redeemed: boolean; sliceIndex: number; shareLink: string | null }
type Page = { firstName: string | null; slices: Slice[]; result: Result | null }

const COLORS = ['#2342F0', '#0E8C8C', '#E8492E', '#33415C', '#4B65FF', '#2BA7A7']

/** Point on the wheel's edge, `deg` clockwise from the top. */
function at(deg: number, r: number) {
  const a = ((deg - 90) * Math.PI) / 180
  return [100 + r * Math.cos(a), 100 + r * Math.sin(a)]
}

/**
 * Spin & Win, opened from the link the WhatsApp bot sends. The server decides the prize (and
 * refuses a second spin); this page only turns the wheel to it. Each slice's size is its real chance.
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
      setTurn(landOn(page.slices, r.sliceIndex, 6))
      setTimeout(() => { setResult(r); setSpinning(false) }, 4700)
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
    <div className="min-h-screen bg-ink px-4 py-10 text-white">
      <div className="mx-auto max-w-md text-center">
        <p className="label !text-signal">Specskart Opticians</p>
        <h1 className="mt-2 text-3xl">{page.firstName ? `${page.firstName}, spin & win!` : 'Spin & win!'}</h1>
        <p className="mt-2 text-sm text-white/60">Every spin wins a prize. One spin per customer.</p>

        <div className="relative mx-auto mt-8 aspect-square w-full max-w-[320px]">
          {/* the pointer, at the top */}
          <div className="absolute left-1/2 top-[-6px] z-10 h-0 w-0 -translate-x-1/2 border-x-[12px] border-t-[22px] border-x-transparent border-t-signal" />
          <svg viewBox="0 0 200 200" className="h-full w-full drop-shadow-xl"
            style={{ transform: `rotate(${turn}deg)`, transition: spinning ? 'transform 4.5s cubic-bezier(0.17, 0.67, 0.12, 0.99)' : 'none' }}>
            {arcs.map(({ i, from, to }) => {
              const [x1, y1] = at(from, 96), [x2, y2] = at(to, 96)
              const d = arcs.length === 1
                ? 'M100 4 A96 96 0 1 1 99.99 4 Z'
                : `M100 100 L${x1} ${y1} A96 96 0 ${to - from > 180 ? 1 : 0} 1 ${x2} ${y2} Z`
              const [tx, ty] = at((from + to) / 2, 62)
              return (
                <g key={i}>
                  <path d={d} fill={COLORS[i % COLORS.length]} stroke="#0A0F1F" strokeWidth="1.5" />
                  <text x={tx} y={ty} fill="white" fontSize="16" fontWeight="700" textAnchor="middle" dominantBaseline="middle">{i + 1}</text>
                </g>
              )
            })}
            <circle cx="100" cy="100" r="14" fill="#0A0F1F" stroke="#C8F04B" strokeWidth="2" />
          </svg>
        </div>

        {!result && (
          <button onClick={spin} disabled={spinning} className="btn mt-8 w-full bg-signal font-bold text-ink disabled:opacity-60">
            {spinning ? 'Spinning…' : 'SPIN'}
          </button>
        )}

        {result && (
          <div className="mt-8 rounded-2xl bg-white p-5 text-left text-ink">
            <p className="text-sm text-ink/60">{result.redeemed ? 'You used your prize:' : 'You won 🎉'}</p>
            <p className="mt-1 text-xl font-semibold">{result.prize}</p>
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
                <p className="mt-4 text-sm text-ink/60">Your code</p>
                <p className="font-display text-2xl tracking-widest">{result.code}</p>
                <p className="mt-2 text-sm text-ink/70">
                  Show it at Specskart Opticians by <b>{new Date(result.expiresAt).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })}</b>.
                  We’ve also sent it to you on WhatsApp.
                </p>
                <a href={`${WA}?text=${encodeURIComponent('Book my visit')}`} className="btn-primary mt-4 block text-center">Book my free eye test</a>
              </>
            )}
          </div>
        )}

        <ul className="mt-8 space-y-2 text-left text-sm">
          {page.slices.map((s, i) => (
            <li key={i} className="flex items-center gap-3">
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold" style={{ background: COLORS[i % COLORS.length] }}>{i + 1}</span>
              <span className="flex-1">{s.label}</span>
              <span className="text-white/50">{Math.round((s.weight / total) * 100)}%</span>
            </li>
          ))}
        </ul>
        <p className="mt-6 text-xs text-white/40">Prizes are collected at our shop within a week. One spin per WhatsApp number.</p>
      </div>
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
