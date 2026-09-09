import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { money, type OrderView } from '../lib/shop'

const NEXT: Record<string, string[]> = {
  PENDING_PAYMENT: ['PAID', 'CANCELLED'],
  PAID: ['PACKED', 'CANCELLED', 'REFUNDED'],
  PACKED: ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED: ['DELIVERED', 'REFUNDED'],
  DELIVERED: ['REFUNDED'],
}

export default function OrderDetail() {
  const { id } = useParams()
  const qc = useQueryClient()
  const { data: o, isLoading } = useQuery({
    queryKey: ['admin-order', id],
    queryFn: () => api<OrderView>(`/admin/orders/${id}`, { auth: true }),
  })

  const move = useMutation({
    mutationFn: (status: string) => api(`/admin/orders/${id}/status`, {
      method: 'PATCH', auth: true, body: JSON.stringify({ status, note: null }),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-order', id] }); qc.invalidateQueries({ queryKey: ['admin-orders'] }) },
  })

  if (isLoading || !o) return <p className="text-ink/50">Loading…</p>
  const nexts = NEXT[o.status] ?? []

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 space-y-6">
        <div>
          <h1 className="text-2xl">{o.orderNo}</h1>
          <p className="text-ink/55">{o.status} · placed {new Date(o.createdAt).toLocaleString()}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {nexts.map((s) => (
              <button key={s} disabled={move.isPending} onClick={() => move.mutate(s)}
                className="btn-ghost !px-3 !py-1 text-xs">→ {s}</button>
            ))}
          </div>
          {move.isError && <p className="mt-2 text-xs text-clay">{(move.error as Error).message}</p>}
          <p className="mt-2 text-xs text-ink/45">Each change fires an automatic WhatsApp update to the customer.</p>
        </div>

        <section className="card p-4">
          <div className="label">Items</div>
          <ul className="mt-3 space-y-2 text-sm">
            {o.lines.map((l, i) => (
              <li key={i} className="flex justify-between"><span>{l.qty} × {l.productName}</span><span>{money(l.lineTotalMinor, o.currency)}</span></li>
            ))}
          </ul>
          {o.lensType && (
            <p className="mt-3 rounded bg-ink/5 p-2 text-xs">
              Lenses: <strong>{o.lensType.replace('_', ' ')}</strong>{o.lensAddMinor > 0 ? ` (+${money(o.lensAddMinor, o.currency)})` : ''}
              {o.rxJson ? <><br />Rx: {o.rxJson}</> : <><br /><span className="text-clay">Prescription not yet collected — contact the customer on WhatsApp.</span></>}
            </p>
          )}
          <dl className="mt-3 space-y-1 border-t border-ink/10 pt-3 text-sm">
            <div className="flex justify-between"><dt className="text-ink/55">Subtotal</dt><dd>{money(o.subtotalMinor, o.currency)}</dd></div>
            {o.discountMinor > 0 && <div className="flex justify-between"><dt className="text-ink/55">Discount {o.promoCode ? `(${o.promoCode})` : ''}</dt><dd>− {money(o.discountMinor, o.currency)}</dd></div>}
            <div className="flex justify-between"><dt className="text-ink/55">Delivery</dt><dd>{money(o.shippingMinor, o.currency)}</dd></div>
            <div className="flex justify-between font-medium"><dt>Total</dt><dd>{money(o.totalMinor, o.currency)}</dd></div>
          </dl>
        </section>

        <section className="card p-4 text-sm">
          <div className="label">Timeline</div>
          <ol className="mt-3 space-y-1">
            {o.timeline.map((e, i) => {
              const alert = e.note?.startsWith('⚠')
              return (
                <li key={i} className="flex gap-3">
                  <span className="text-ink/40">{new Date(e.at).toLocaleString()}</span>
                  <span className={alert ? 'text-clay' : undefined}>{e.status}{e.note ? ` — ${e.note}` : ''}</span>
                </li>
              )
            })}
          </ol>
        </section>
      </div>

      <aside className="card h-fit p-4 text-sm">
        <div className="label">Customer</div>
        <p className="mt-2">{o.customerName}<br />{o.customerPhone}<br />{o.customerEmail ?? '—'}</p>
        <div className="label mt-4">Deliver to</div>
        <p className="mt-2">{o.shipAddress}<br />{o.shipCity}</p>
      </aside>
    </div>
  )
}
