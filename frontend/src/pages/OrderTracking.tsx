import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { shop, money, setCartToken } from '../lib/shop'

const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'

const STEPS = ['PAID', 'PACKED', 'SHIPPED', 'DELIVERED']
const LABEL: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting payment', PAID: 'Payment received', PACKED: 'Packed',
  SHIPPED: 'Out for delivery', DELIVERED: 'Delivered', CANCELLED: 'Cancelled', REFUNDED: 'Refunded',
}

export default function OrderTracking() {
  const { orderNo } = useParams()
  const [params] = useSearchParams()
  const justPaid = params.has('mockPaid') || params.has('status') || params.has('tx_ref')

  // Returning from the payment page: confirm once, then poll a little.
  const { data: order, isLoading, isError } = useQuery({
    queryKey: ['order', orderNo, justPaid],
    queryFn: () => justPaid ? shop.confirmOrder(orderNo!) : shop.order(orderNo!),
    refetchInterval: (q) => (q.state.data && ['PENDING_PAYMENT'].includes(q.state.data.status) ? 3000 : false),
  })

  useEffect(() => { if (order && order.status !== 'PENDING_PAYMENT') setCartToken(null) }, [order])

  if (isLoading) return <div className="container-x py-16 text-ink/50">Loading your order…</div>
  if (isError || !order) return (
    <div className="container-x py-16">
      <p className="text-ink/60">We couldn’t find that order.</p>
      <Link to="/store" className="mt-3 inline-block underline">Back to the store</Link>
    </div>
  )

  const stepIdx = STEPS.indexOf(order.status)
  const terminal = order.status === 'CANCELLED' || order.status === 'REFUNDED'

  return (
    <div className="container-x max-w-2xl py-12">
      <p className="label">Order {order.orderNo}</p>
      <h1 className="mt-2 text-3xl">{LABEL[order.status] ?? order.status}</h1>

      {order.status === 'PENDING_PAYMENT' && (
        <p className="mt-3 text-ink/60">We’re waiting for your payment to confirm. This page updates automatically.</p>
      )}

      {!terminal && stepIdx >= 0 && (
        <ol className="mt-8 flex justify-between">
          {STEPS.map((s, i) => (
            <li key={s} className="flex flex-1 flex-col items-center text-center">
              <span className={`h-3 w-3 rounded-full ${i <= stepIdx ? 'bg-ink' : 'bg-ink/20'}`} />
              <span className={`mt-2 text-xs ${i <= stepIdx ? 'text-ink' : 'text-ink/40'}`}>{LABEL[s]}</span>
            </li>
          ))}
        </ol>
      )}

      <div className="card mt-8 p-5">
        <h2 className="text-lg">Items</h2>
        <ul className="mt-3 space-y-2 text-sm">
          {order.lines.map((l, i) => (
            <li key={i} className="flex justify-between"><span>{l.qty} × {l.productName}</span><span>{money(l.lineTotalMinor, order.currency)}</span></li>
          ))}
        </ul>
        <dl className="mt-4 space-y-1 border-t border-ink/10 pt-3 text-sm">
          <div className="flex justify-between"><dt className="text-ink/55">Subtotal</dt><dd>{money(order.subtotalMinor, order.currency)}</dd></div>
          {order.discountMinor > 0 && <div className="flex justify-between text-moss"><dt>Discount</dt><dd>− {money(order.discountMinor, order.currency)}</dd></div>}
          <div className="flex justify-between"><dt className="text-ink/55">Delivery</dt><dd>{order.shippingMinor === 0 ? 'Free' : money(order.shippingMinor, order.currency)}</dd></div>
          <div className="flex justify-between pt-1 font-medium"><dt>Total</dt><dd>{money(order.totalMinor, order.currency)}</dd></div>
        </dl>
      </div>

      <div className="card mt-4 p-5 text-sm">
        <h2 className="text-lg">Delivery</h2>
        <p className="mt-2 text-ink/70">{order.customerName}<br />{order.shipAddress}, {order.shipCity}<br />{order.customerPhone}</p>
      </div>

      {(order.pointsEarned > 0 || order.pointsBalance > 0 || order.referralCode) && (
        <div className="card mt-4 p-5 text-sm">
          <h2 className="text-lg">Rewards</h2>
          {order.pointsEarned > 0 && <p className="mt-2 text-ink/70">You earned <strong>{order.pointsEarned} points</strong> on this order.</p>}
          {order.pointsRedeemed > 0 && <p className="text-ink/70">You used {order.pointsRedeemed} points.</p>}
          <p className="mt-1 text-ink/70">Points balance: <strong>{order.pointsBalance}</strong></p>
          {order.referralCode && (
            <div className="mt-3">
              <p className="text-ink/70">Share your code — your friend gets a discount, you get points:</p>
              <div className="mt-1 flex items-center gap-3">
                <span className="font-display text-lg tracking-wide">{order.referralCode}</span>
                <a className="text-xs underline" target="_blank" rel="noreferrer"
                   href={`https://wa.me/?text=${encodeURIComponent(`Get a discount on your first Specskart frames with my code ${order.referralCode} 👓 ${WA}`)}`}>
                  Share on WhatsApp
                </a>
              </div>
            </div>
          )}
        </div>
      )}

      <ol className="mt-6 space-y-2 text-sm">
        {order.timeline.map((e, i) => (
          <li key={i} className="flex gap-3">
            <span className="text-ink/40">{new Date(e.at).toLocaleString()}</span>
            <span>{LABEL[e.status] ?? e.status}{e.note ? ` — ${e.note}` : ''}</span>
          </li>
        ))}
      </ol>

      <p className="mt-8 text-xs text-ink/45">Questions about your order? Reply to us on WhatsApp.</p>
    </div>
  )
}
