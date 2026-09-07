import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { shop, money, assetUrl, adoptCartFromUrl } from '../lib/shop'

function HoldTimer({ until, onExpire }: { until: string; onExpire: () => void }) {
  const [left, setLeft] = useState(() => Math.max(0, new Date(until).getTime() - Date.now()))
  useEffect(() => {
    const id = setInterval(() => {
      const ms = Math.max(0, new Date(until).getTime() - Date.now())
      setLeft(ms)
      if (ms === 0) onExpire()
    }, 1000)
    return () => clearInterval(id)
  }, [until, onExpire])
  const m = Math.floor(left / 60000)
  const s = Math.floor((left % 60000) / 1000)
  return (
    <div className="mt-4 rounded-lg bg-moss/10 px-3 py-2 text-sm">
      Your frames are held for <strong>{m}:{String(s).padStart(2, '0')}</strong>. Check out before then to keep them.
    </div>
  )
}

export default function Cart() {
  const qc = useQueryClient()
  const nav = useNavigate()
  const [promo, setPromo] = useState('')
  useEffect(() => { adoptCartFromUrl() }, [])

  // refetch every minute — the GET renews the server-side hold while the shopper sits here
  const { data: cart, isLoading } = useQuery({ queryKey: ['cart'], queryFn: shop.cart, refetchInterval: 60_000 })
  const refresh = () => qc.invalidateQueries({ queryKey: ['cart'] })

  // auto-apply a code carried from the Frame Finder result, once
  useEffect(() => {
    if (!cart || cart.promoCode || !cart.lines.length) return
    let pending: string | null = null
    try { pending = localStorage.getItem('specskart_pending_promo') } catch { /* */ }
    if (pending) {
      try { localStorage.removeItem('specskart_pending_promo') } catch { /* */ }
      shop.applyPromo(pending).then(refresh).catch(() => {})
    }
  }, [cart]) // eslint-disable-line react-hooks/exhaustive-deps

  const setQty = useMutation({ mutationFn: (v: { id: string; qty: number }) => shop.setQty(v.id, v.qty), onSuccess: refresh })
  const applyPromo = useMutation({ mutationFn: () => shop.applyPromo(promo), onSuccess: () => { setPromo(''); refresh() } })
  const clearPromo = useMutation({ mutationFn: () => shop.applyPromo(''), onSuccess: refresh })
  const setLens = useMutation({ mutationFn: (t: string | null) => shop.setLens(t), onSuccess: refresh })
  const addSuggestion = useMutation({ mutationFn: (id: string) => shop.addItem(id, 1), onSuccess: refresh })

  if (isLoading) return <div className="container-x py-16 text-ink/50">Loading your bag…</div>

  if (!cart?.lines.length) return (
    <div className="container-x py-16">
      <h1 className="text-3xl">Your bag is empty</h1>
      <Link to="/store" className="btn-primary mt-6 inline-block">Browse frames</Link>
    </div>
  )

  return (
    <div className="container-x grid gap-10 py-12 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <h1 className="text-3xl">Your bag</h1>
        {cart.holdExpiresAt && <HoldTimer until={cart.holdExpiresAt} onExpire={refresh} />}
        <ul className="mt-6 divide-y divide-ink/10">
          {cart.lines.map((l) => (
            <li key={l.productId} className="flex gap-4 py-4">
              <Link to={`/store/${l.slug}`} className="h-20 w-24 shrink-0 overflow-hidden rounded-xl border border-ink/10 bg-white">
                {l.imageUrl && <img src={assetUrl(l.imageUrl)} alt={l.name} className="h-full w-full object-cover" />}
              </Link>
              <div className="flex flex-1 flex-col">
                <div className="flex justify-between gap-3">
                  <Link to={`/store/${l.slug}`} className="font-medium hover:underline">{l.name}</Link>
                  <span className="shrink-0">{money(l.lineTotalMinor, cart.currency)}</span>
                </div>
                <span className="text-sm text-ink/50">{money(l.unitPriceMinor, cart.currency)} each</span>
                <div className="mt-auto flex items-center gap-2">
                  <select
                    value={l.qty}
                    onChange={(e) => setQty.mutate({ id: l.productId, qty: Number(e.target.value) })}
                    className="rounded-lg border border-ink/20 px-2 py-1 text-sm"
                  >
                    {Array.from({ length: Math.max(l.qty, Math.min(l.stockQty, 10)) }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>{n}</option>
                    ))}
                  </select>
                  <button className="text-sm text-ink/50 hover:text-clay" onClick={() => setQty.mutate({ id: l.productId, qty: 0 })}>Remove</button>
                  {!l.inStock && <span className="text-sm text-clay">out of stock</span>}
                </div>
              </div>
            </li>
          ))}
        </ul>

        {cart.lines.some((l) => l.lensable) && (
          <div className="mt-6 card p-4">
            <h3 className="text-base">Prescription lenses</h3>
            <p className="text-xs text-ink/50">Applied to each prescription-ready frame in your bag.</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {[
                ['', 'No lenses / frame only'],
                ['NON_PRESCRIPTION', 'Non-prescription'],
                ['BLUE_LIGHT', 'Blue-light'],
                ['SINGLE_VISION', 'Single vision'],
                ['PROGRESSIVE', 'Progressive'],
              ].map(([v, label]) => (
                <button key={v} onClick={() => setLens.mutate(v || null)}
                  className={`rounded-full border px-3 py-1 text-sm ${(cart.lensType ?? '') === v ? 'border-ink bg-ink text-bone' : 'border-ink/20'}`}>
                  {label}
                </button>
              ))}
            </div>
            {cart.lensAddMinor > 0 && (
              <p className="mt-2 text-xs text-ink/60">+ {money(cart.lensAddMinor, cart.currency)} for lenses. We’ll confirm your prescription by WhatsApp after you order.</p>
            )}
          </div>
        )}

        {cart.suggestions.length > 0 && (
          <div className="mt-6">
            <h3 className="text-base">Complete the look</h3>
            <div className="mt-3 grid grid-cols-3 gap-3">
              {cart.suggestions.map((s) => (
                <div key={s.productId} className="card p-2 text-center">
                  <div className="aspect-square overflow-hidden rounded-lg bg-white">
                    {s.imageUrl && <img src={assetUrl(s.imageUrl)} alt={s.name} className="h-full w-full object-cover" />}
                  </div>
                  <div className="mt-1 text-xs">{s.name}</div>
                  <div className="text-xs text-ink/50">{money(s.priceMinor, cart.currency)}</div>
                  <button className="btn-ghost mt-1 !px-2 !py-1 text-[11px]" disabled={addSuggestion.isPending}
                    onClick={() => addSuggestion.mutate(s.productId)}>Add</button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <aside className="h-fit card p-5">
        <h2 className="text-lg">Summary</h2>
        <dl className="mt-4 space-y-2 text-sm">
          <Row k="Subtotal" v={money(cart.subtotalMinor, cart.currency)} />
          {cart.lensAddMinor > 0 && <Row k="Prescription lenses" v={`+ ${money(cart.lensAddMinor, cart.currency)}`} />}
          {cart.discountMinor > 0 && <Row k={cart.promoCode ? `Discount (${cart.promoCode})` : 'Discount'} v={`− ${money(cart.discountMinor, cart.currency)}`} accent />}
          <Row k="Delivery" v={cart.shippingMinor === 0 ? 'Free' : money(cart.shippingMinor, cart.currency)} />
          <div className="flex justify-between border-t border-ink/10 pt-2 text-base font-medium">
            <span>Total</span><span>{money(cart.totalMinor, cart.currency)}</span>
          </div>
        </dl>

        {cart.promoCode ? (
          <button className="mt-3 text-xs text-ink/50 underline" onClick={() => clearPromo.mutate()}>Remove code</button>
        ) : (
          <div className="mt-3 flex gap-2">
            <input value={promo} onChange={(e) => setPromo(e.target.value)} placeholder="Promo code"
              className="w-full rounded-lg border border-ink/20 px-2 py-1 text-sm" />
            <button className="btn-ghost !px-3 !py-1 text-xs" disabled={!promo.trim() || applyPromo.isPending} onClick={() => applyPromo.mutate()}>Apply</button>
          </div>
        )}
        {applyPromo.isError && <p className="mt-1 text-xs text-clay">{(applyPromo.error as Error).message}</p>}

        <button className="btn-primary mt-5 w-full" onClick={() => nav('/checkout')}>Checkout</button>
        <p className="mt-2 text-center text-xs text-ink/45">Delivery in {cart.deliveryEta}</p>
      </aside>
    </div>
  )
}

function Row({ k, v, accent }: { k: string; v: string; accent?: boolean }) {
  return (
    <div className="flex justify-between">
      <dt className="text-ink/55">{k}</dt>
      <dd className={accent ? 'text-moss' : ''}>{v}</dd>
    </div>
  )
}
