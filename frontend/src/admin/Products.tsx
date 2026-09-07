import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { money, assetUrl } from '../lib/shop'

type Row = {
  id: string; slug: string; name: string; status: string; priceMinor: number; currency: string
  stockQty: number; featured: boolean; frameCategoryCode: string | null
  images: { url: string }[]
}

export default function Products() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin-products'],
    queryFn: () => api<Row[]>('/admin/catalog/products', { auth: true }),
  })

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl">Products</h1>
        <Link to="/admin/products/new" className="btn-primary !px-4 !py-2 text-xs">New product</Link>
      </div>

      {isLoading ? <p className="mt-6 text-ink/50">Loading…</p> : (
        <table className="mt-6 w-full text-sm">
          <thead className="text-left text-ink/50">
            <tr><th className="py-2">Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Status</th></tr>
          </thead>
          <tbody>
            {data?.map((p) => (
              <tr key={p.id} className="border-t border-ink/10">
                <td className="py-2">
                  <Link to={`/admin/products/${p.id}`} className="flex items-center gap-3 hover:underline">
                    <span className="h-9 w-11 overflow-hidden rounded bg-ink/5">
                      {p.images[0] && <img src={assetUrl(p.images[0].url)} alt="" className="h-full w-full object-cover" />}
                    </span>
                    <span>{p.name}{p.featured ? ' ★' : ''}</span>
                  </Link>
                </td>
                <td className="text-ink/60">{p.frameCategoryCode ?? '—'}</td>
                <td>{money(p.priceMinor, p.currency)}</td>
                <td className={p.stockQty === 0 ? 'text-clay' : ''}>{p.stockQty}</td>
                <td><span className={`rounded px-2 py-0.5 text-xs ${p.status === 'ACTIVE' ? 'bg-moss/20' : 'bg-ink/10'}`}>{p.status}</span></td>
              </tr>
            ))}
            {!data?.length && <tr><td colSpan={5} className="py-6 text-ink/50">No products yet — add your first frame.</td></tr>}
          </tbody>
        </table>
      )}
    </div>
  )
}
