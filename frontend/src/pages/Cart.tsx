import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { shop, money, adoptCartFromUrl } from '../lib/shop'

export default function Cart() {
  const qc = useQueryClient()
  const nav = useNavigate()
  const [promo, setPromo] = useState('')
  useEffect(() => { adoptCartFromUrl() }, [])

  const { data: cart, isLoading } = useQuery({ queryKey: ['cart'], queryFn: shop.cart })
  const refresh = () => qc.invalidateQueries({ queryKey: ['cart'] })

  const setQty = useMutation({ mutationFn: (v: { id: string; qty: number }) => shop.setQty(v.id, v.qty), onSuccess: refresh })
  const applyPromo = useMutation({ mutationFn: () => shop.applyPromo(promo), onSuccess: () => { setPromo(''); refresh() } })
  const clearPromo = useMutation({ mutationFn: () => shop.applyPromo(''), onSuccess: refresh })

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
        <ul className="mt-6 divide-y divide-ink/10">
          {cart.lines.map((l) => (
            <li key={l.productId} className="flex gap-4 py-4">
              <Link to={`/store/${l.slug}`} className="h-20 w-24 shrink-0 overflow-hidden rounded-xl border border-ink/10 bg-white">
                {l.imageUrl && <img src={l.imageUrl} alt={l.name} className="h-full w-full object-cover" />}
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
      </div>

      <aside className="h-fit card p-5">
        <h2 className="text-lg">Summary</h2>
        <dl className="mt-4 space-y-2 text-sm">
          <Row k="Subtotal" v={money(cart.subtotalMinor, cart.currency)} />
          {cart.discountMinor > 0 && <Row k={`Discount (${cart.promoCode})`} v={`− ${money(cart.discountMinor, cart.currency)}`} accent />}
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
