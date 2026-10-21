import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { shop, money } from '../lib/shop'
import { DEFAULT_COUNTRY_CODE, cleanCc } from '../lib/phone'

export default function Checkout() {
  const navigate = useNavigate()
  const { data: cart, isLoading } = useQuery({ queryKey: ['cart'], queryFn: shop.cart })
  const { data: cfg } = useQuery({ queryKey: ['storeConfig'], queryFn: shop.storeConfig })
  const [f, setF] = useState({ customerName: '', customerPhone: '', customerEmail: '' })
  const on = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const [cc, setCc] = useState(DEFAULT_COUNTRY_CODE)
  const [usePoints, setUsePoints] = useState(false)
  const [referral, setReferral] = useState('')
  const [method, setMethod] = useState<'ONLINE' | 'COD'>('COD')

  // No delivery: every order is collected at the shop holding the frames.
  const { data: collectAt } = useQuery({
    queryKey: ['shipsFrom'],
    queryFn: () => shop.shipsFrom(null, ''),
    enabled: !!cart?.lines.length,
  })

  const points = cart?.pointsAvailable ?? 0
  const pointsDiscount = usePoints ? points * (cart?.pointValueMinor ?? 0) : 0
  const cod = method === 'COD'

  const pay = useMutation({
    mutationFn: () => shop.checkout({
      ...f,
      customerPhone: cleanCc(cc) + f.customerPhone.replace(/\D/g, ''),
      redeemPoints: usePoints ? points : undefined,
      referralCode: referral.trim() || undefined,
      payOnDelivery: cod,
    }),
    onSuccess: (r) => { r.checkoutUrl ? (window.location.href = r.checkoutUrl) : navigate(`/order/${r.orderNo}`) },
  })

  if (isLoading) return <div className="container-x py-16 text-ink/50">Loading…</div>
  if (!cart?.lines.length) return (
    <div className="container-x py-16">
      <p>Your bag is empty.</p>
      <Link to="/store" className="mt-3 inline-block underline">Browse frames</Link>
    </div>
  )

  const valid = f.customerName.trim() && f.customerPhone.trim()

  return (
    <div className="container-x grid gap-10 py-12 lg:grid-cols-2">
      <div>
        <h1 className="text-3xl">Checkout</h1>
        <form className="mt-6 space-y-3" onSubmit={(e) => { e.preventDefault(); if (valid) pay.mutate() }}>
          <Field label="Full name" value={f.customerName} onChange={on('customerName')} />
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">WhatsApp / phone number</span>
            <div className="mt-1 flex gap-2">
              <input value={cc} onChange={(e) => setCc(cleanCc(e.target.value))}
                aria-label="Country code" inputMode="tel"
                className="w-20 rounded-lg border border-ink/20 px-3 py-2 text-sm" />
              <input value={f.customerPhone} onChange={on('customerPhone')}
                inputMode="tel" placeholder="977123456"
                className="flex-1 rounded-lg border border-ink/20 px-3 py-2 text-sm" />
            </div>
          </label>
          <Field label="Email (optional)" value={f.customerEmail} onChange={on('customerEmail')} type="email" />

          <div className="rounded-lg bg-ink/5 px-3 py-3 text-sm">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Collect at the shop</span>
            <p className="mt-1">
              🏬 <b>{collectAt?.shopName ?? 'Specskart'}</b>
              {collectAt?.address && <><br /><span className="text-ink/70">{collectAt.address}</span></>}
            </p>
            {collectAt?.mapsUrl && (
              <a href={collectAt.mapsUrl} target="_blank" rel="noreferrer" className="mt-1 inline-block underline">Open in Google Maps</a>
            )}
            <p className="mt-1 text-ink/60">We don’t deliver. We’ll message you on WhatsApp as soon as your frames are ready to collect.</p>
          </div>

          {!cart.promoCode && (
            <label className="block">
              <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Referral code (optional)</span>
              <input value={referral} onChange={(e) => setReferral(e.target.value.toUpperCase())}
                className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" placeholder="SPECS-XXXXX" />
            </label>
          )}
          {points > 0 && (
            <label className="flex items-center gap-2 rounded-lg bg-moss/10 px-3 py-2 text-sm">
              <input type="checkbox" checked={usePoints} onChange={(e) => setUsePoints(e.target.checked)} />
              Use {points} points (−{money(pointsDiscount, cart.currency)})
            </label>
          )}

          <fieldset className="mt-4 space-y-2">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">How would you like to pay?</span>
            {/* Online payment is off until the client goes live with it (same as the lens page).
                Re-enable: restore onChange={() => setMethod('ONLINE')}, drop `disabled`, default to 'ONLINE'. */}
            <label className="flex cursor-not-allowed items-start gap-3 rounded-lg border border-ink/20 p-3 text-sm opacity-40">
              <input type="radio" name="pay" checked={false} disabled className="mt-0.5" />
              <span><b>Pay now</b> — card or mobile money (Coming soon)</span>
            </label>
            {cfg?.codEnabled !== false && (
              <label className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${cod ? 'border-ink bg-ink/5' : 'border-ink/20'}`}>
                <input type="radio" name="pay" checked={cod} onChange={() => setMethod('COD')} className="mt-0.5" />
                <span><b>Pay at the shop</b> — cash or mobile money when you collect.</span>
              </label>
            )}
          </fieldset>

          <button className="btn-primary mt-4 w-full disabled:bg-ink/30" disabled={!valid || pay.isPending}>
            {pay.isPending ? 'Placing your order…'
              : cod ? `Place order · pay ${money(cart.totalMinor, cart.currency)} when you collect`
              : (usePoints || referral.trim()) ? 'Continue to payment'
              : `Pay ${money(cart.totalMinor, cart.currency)}`}
          </button>
          {pay.isError && <p className="text-sm text-clay">{(pay.error as Error).message}</p>}
          <p className="text-xs text-ink/45">
            {cod ? 'We’ll get your frames ready and you pay when you pick them up at the shop.'
                 : 'You’ll be taken to a secure page to pay by card or mobile money (MTN / Airtel).'}
          </p>
          {cfg?.guaranteeNote && <p className="text-xs text-ink/45">✓ {cfg.guaranteeNote}</p>}
        </form>
      </div>

      <aside className="h-fit card p-5">
        <h2 className="text-lg">Order summary</h2>
        <ul className="mt-4 space-y-3 text-sm">
          {cart.lines.map((l) => (
            <li key={l.productId} className="flex justify-between gap-3">
              <span>{l.qty} × {l.name}</span>
              <span>{money(l.lineTotalMinor, cart.currency)}</span>
            </li>
          ))}
        </ul>
        <dl className="mt-4 space-y-1 border-t border-ink/10 pt-3 text-sm">
          <div className="flex justify-between"><dt className="text-ink/55">Subtotal</dt><dd>{money(cart.subtotalMinor, cart.currency)}</dd></div>
          {cart.discountMinor > 0 && <div className="flex justify-between text-moss"><dt>Discount</dt><dd>− {money(cart.discountMinor, cart.currency)}</dd></div>}
          <div className="flex justify-between"><dt className="text-ink/55">Collection</dt><dd>At the shop</dd></div>
          <div className="flex justify-between pt-1 text-base font-medium"><dt>Total</dt><dd>{money(cart.totalMinor, cart.currency)}</dd></div>
        </dl>
      </aside>
    </div>
  )
}

function Field({ label, type = 'text', ...rest }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="text-xs font-medium uppercase tracking-widest text-ink/50">{label}</span>
      <input {...rest} type={type} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
    </label>
  )
}
