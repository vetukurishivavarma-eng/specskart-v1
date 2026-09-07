import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { money } from '../lib/shop'

const FILTERS = ['', 'PENDING_PAYMENT', 'PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED']

type Row = { id: string; orderNo: string; status: string; customerName: string; totalMinor: number; currency: string; createdAt: string; paidAt: string | null }

export default function Orders() {
  const [status, setStatus] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['admin-orders', status],
    queryFn: () => api<Row[]>(`/admin/orders${status ? `?status=${status}` : ''}`, { auth: true }),
    refetchInterval: 20_000,
  })

  return (
    <div>
      <h1 className="text-2xl">Orders</h1>
      <div className="mt-4 flex flex-wrap gap-2">
        {FILTERS.map((s) => (
          <button key={s} onClick={() => setStatus(s)}
            className={`rounded-full border px-3 py-1 text-xs ${status === s ? 'border-ink bg-ink text-bone' : 'border-ink/20'}`}>
            {s || 'All'}
          </button>
        ))}
      </div>

      {isLoading ? <p className="mt-6 text-ink/50">Loading…</p> : (
        <table className="mt-6 w-full text-sm">
          <thead className="text-left text-ink/50"><tr><th className="py-2">Order</th><th>Customer</th><th>Total</th><th>Status</th><th>Placed</th></tr></thead>
          <tbody>
            {data?.map((o) => (
              <tr key={o.id} className="border-t border-ink/10">
                <td className="py-2"><Link to={`/admin/orders/${o.id}`} className="hover:underline">{o.orderNo}</Link></td>
                <td className="text-ink/70">{o.customerName}</td>
                <td>{money(o.totalMinor, o.currency)}</td>
                <td><span className="rounded bg-ink/10 px-2 py-0.5 text-xs">{o.status}</span></td>
                <td className="text-ink/50">{new Date(o.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
            {!data?.length && <tr><td colSpan={5} className="py-6 text-ink/50">No orders {status ? `in ${status}` : 'yet'}.</td></tr>}
          </tbody>
        </table>
      )}
    </div>
  )
}
