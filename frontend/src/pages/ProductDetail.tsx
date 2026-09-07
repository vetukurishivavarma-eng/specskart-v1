import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { shop, money, assetUrl } from '../lib/shop'
import TryOn from '../components/TryOn'

function useCountdown(target: string | null) {
  const [left, setLeft] = useState(() => target ? new Date(target).getTime() - Date.now() : 0)
  useEffect(() => {
    if (!target) return
    const t = setInterval(() => setLeft(new Date(target).getTime() - Date.now()), 1000)
    return () => clearInterval(t)
  }, [target])
  if (left <= 0) return null
  const s = Math.floor(left / 1000)
  return { d: Math.floor(s / 86400), h: Math.floor((s % 86400) / 3600), m: Math.floor((s % 3600) / 60), s: s % 60 }
}

function NotifyMe({ slug, label }: { slug: string; label: string }) {
  const [num, setNum] = useState('')
  const m = useMutation({ mutationFn: () => shop.notifyMe(slug, num) })
  if (m.isSuccess) return <p className="mt-4 text-sm text-moss">We'll message you on WhatsApp when it's available.</p>
  return (
    <form className="mt-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (num.trim()) m.mutate() }}>
      <input value={num} onChange={(e) => setNum(e.target.value)} placeholder="WhatsApp number"
        className="flex-1 rounded-lg border border-ink/20 px-3 py-2 text-sm" />
      <button className="btn-primary !px-4" disabled={m.isPending || !num.trim()}>{m.isPending ? '…' : label}</button>
    </form>
  )
}

export default function ProductDetail() {
  const { slug } = useParams()
  const nav = useNavigate()
  const qc = useQueryClient()
  const [img, setImg] = useState(0)
  const [tryOn, setTryOn] = useState(false)

  const { data: p, isLoading, isError } = useQuery({
    queryKey: ['product', slug],
    queryFn: () => shop.product(slug!),
  })

  const add = useMutation({
    mutationFn: () => shop.addItem(p!.id, 1),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cart'] }); nav('/cart') },
  })

  const countdown = useCountdown(p?.dropsAt ?? null)

  if (isLoading) return <div className="container-x py-16 text-ink/50">Loading…</div>
  if (isError || !p) return (
    <div className="container-x py-16">
      <p className="text-ink/60">That frame isn’t available.</p>
      <Link to="/store" className="mt-3 inline-block underline">Back to the store</Link>
    </div>
  )

  const gallery = p.images.length ? p.images : (p.imageUrl ? [{ url: p.imageUrl, alt: p.name }] : [])
  const upcoming = !!countdown

  return (
    <div className="container-x grid gap-10 py-12 md:grid-cols-2">
      <div>
        <div className="aspect-[4/3] overflow-hidden rounded-2xl border border-ink/10 bg-white">
          {gallery[img]
            ? <img src={assetUrl(gallery[img].url)} alt={gallery[img].alt ?? p.name} className="h-full w-full object-cover" />
            : <div className="flex h-full items-center justify-center text-ink/30">No image</div>}
        </div>
        {gallery.length > 1 && (
          <div className="mt-3 flex gap-2">
            {gallery.map((g, i) => (
              <button key={i} onClick={() => setImg(i)}
                className={`h-16 w-16 overflow-hidden rounded-lg border ${i === img ? 'border-ink' : 'border-ink/15'}`}>
                <img src={assetUrl(g.url)} alt="" className="h-full w-full object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        <Link to="/store" className="text-sm text-ink/50 hover:text-ink">← All frames</Link>
        <div className="mt-2 flex items-center gap-2">
          <h1 className="text-3xl">{p.name}</h1>
          {p.limitedEdition && <span className="rounded-full bg-clay/15 px-2 py-0.5 text-xs text-clay">Limited edition</span>}
        </div>
        <div className="mt-2 flex items-center gap-3">
          <span className="text-xl font-medium">{money(p.priceMinor, p.currency)}</span>
          {p.compareAtMinor && p.compareAtMinor > p.priceMinor && (
            <span className="text-ink/40 line-through">{money(p.compareAtMinor, p.currency)}</span>
          )}
        </div>
        <p className="mt-4 text-ink/70">{p.description}</p>
        <dl className="mt-5 grid grid-cols-2 gap-y-1 text-sm text-ink/60">
          {p.colour && <><dt className="text-ink/40">Colour</dt><dd>{p.colour}</dd></>}
          {p.material && <><dt className="text-ink/40">Material</dt><dd>{p.material}</dd></>}
          <dt className="text-ink/40">Fit</dt><dd>{p.gender === 'UNISEX' ? 'Unisex' : p.gender === 'MEN' ? 'Men' : 'Women'}</dd>
          {p.lensable && <><dt className="text-ink/40">Lenses</dt><dd>Prescription-ready</dd></>}
        </dl>

        {upcoming ? (
          <div className="mt-7 rounded-xl border border-ink/15 p-4">
            <div className="label">Drops in</div>
            <div className="mt-1 font-display text-2xl tabular-nums">
              {countdown.d}d {String(countdown.h).padStart(2, '0')}:{String(countdown.m).padStart(2, '0')}:{String(countdown.s).padStart(2, '0')}
            </div>
            <NotifyMe slug={p.slug} label="Notify me" />
          </div>
        ) : !p.inStock ? (
          <div className="mt-7">
            <button disabled className="btn-primary w-full cursor-not-allowed bg-ink/30">Sold out</button>
            <NotifyMe slug={p.slug} label="Notify me" />
          </div>
        ) : (
          <button
            disabled={add.isPending}
            onClick={() => add.mutate()}
            className="btn-primary mt-7 w-full disabled:cursor-not-allowed disabled:bg-ink/30"
          >
            {add.isPending ? 'Adding…' : 'Add to bag'}
          </button>
        )}
        {p.tryOnImageUrl && !upcoming && (
          <button onClick={() => setTryOn(true)} className="btn-ghost mt-3 w-full">Try it on 👓</button>
        )}
        {add.isError && <p className="mt-2 text-sm text-clay">{(add.error as Error).message}</p>}
        <p className="mt-3 text-xs text-ink/45">Fitted with your lenses and delivered across Zambia. Pay by card or mobile money.</p>
      </div>

      {tryOn && (
        <TryOn
          frames={[{ slug: p.slug, name: p.name, tryOnImageUrl: p.tryOnImageUrl }]}
          onClose={() => setTryOn(false)}
        />
      )}
    </div>
  )
}
