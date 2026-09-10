import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { shop, money } from '../lib/shop'

export default function Checkout() {
  const navigate = useNavigate()
  const { data: cart, isLoading } = useQuery({ queryKey: ['cart'], queryFn: shop.cart })
  const { data: cfg } = useQuery({ queryKey: ['storeConfig'], queryFn: shop.storeConfig })
  const [f, setF] = useState({ customerName: '', customerPhone: '', customerEmail: '', shipAddress: '', shipCity: '' })
  const on = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const [usePoints, setUsePoints] = useState(false)
  const [referral, setReferral] = useState('')
  const [method, setMethod] = useState<'ONLINE' | 'COD'>('ONLINE')

  const points = cart?.pointsAvailable ?? 0
  const pointsDiscount = usePoints ? points * (cart?.pointValueMinor ?? 0) : 0
  const cod = method === 'COD'

  const pay = useMutation({
    mutationFn: () => shop.checkout({
      ...f,
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

  const valid = f.customerName.trim() && f.customerPhone.trim() && f.shipAddress.trim() && f.shipCity.trim()

  return (
    <div className="container-x grid gap-10 py-12 lg:grid-cols-2">
      <div>
        <h1 className="text-3xl">Checkout</h1>
        <form className="mt-6 space-y-3" onSubmit={(e) => { e.preventDefault(); if (valid) pay.mutate() }}>
          <Field label="Full name" value={f.customerName} onChange={on('customerName')} />
          <Field label="WhatsApp / phone number" value={f.customerPhone} onChange={on('customerPhone')} />
          <Field label="Email (optional)" value={f.customerEmail} onChange={on('customerEmail')} type="email" />
          <Field label="Delivery address" value={f.shipAddress} onChange={on('shipAddress')} />
          <Field label="City / town" value={f.shipCity} onChange={on('shipCity')} />

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
            <label className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${!cod ? 'border-ink bg-ink/5' : 'border-ink/20'}`}>
              <input type="radio" name="pay" checked={!cod} onChange={() => setMethod('ONLINE')} className="mt-0.5" />
              <span><b>Pay now</b> — card or mobile money (MTN / Airtel). Secure checkout.</span>
            </label>
            {cfg?.codEnabled !== false && (
              <label className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${cod ? 'border-ink bg-ink/5' : 'border-ink/20'}`}>
                <input type="radio" name="pay" checked={cod} onChange={() => setMethod('COD')} className="mt-0.5" />
                <span><b>Pay on delivery</b> — cash to the courier when your frames arrive.</span>
              </label>
            )}
          </fieldset>

          <button className="btn-primary mt-4 w-full disabled:bg-ink/30" disabled={!valid || pay.isPending}>
            {pay.isPending ? 'Placing your order…'
              : cod ? `Place order · pay ${money(cart.totalMinor, cart.currency)} on delivery`
              : (usePoints || referral.trim()) ? 'Continue to payment'
              : `Pay ${money(cart.totalMinor, cart.currency)}`}
          </button>
          {pay.isError && <p className="text-sm text-clay">{(pay.error as Error).message}</p>}
          <p className="text-xs text-ink/45">
            {cod ? 'We’ll get your frames ready and the courier collects the cash on hand-over.'
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
          <div className="flex justify-between"><dt className="text-ink/55">Delivery</dt><dd>{cart.shippingMinor === 0 ? 'Free' : money(cart.shippingMinor, cart.currency)}</dd></div>
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
