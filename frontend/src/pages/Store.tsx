import { useEffect, useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { shop, money, assetUrl, adoptCartFromUrl, type ProductCard } from '../lib/shop'

const CATEGORIES = [
  ['', 'All frames'], ['WAYFARER', 'Wayfarer'], ['AVIATOR', 'Aviator'], ['ROUND_FRAME', 'Round'],
  ['GEOMETRIC', 'Geometric'], ['CATEYE', 'Cat-eye'], ['BROWLINE', 'Browline'],
  ['RECTANGLE', 'Rectangle'], ['OVERSIZED', 'Oversized'], ['THIN_RIM', 'Thin-rim'], ['OVAL_FRAME', 'Oval'],
]

export default function Store() {
  const [params, setParams] = useSearchParams()
  useEffect(() => { adoptCartFromUrl() }, [])

  const face = params.get('face') ?? ''
  const category = params.get('category') ?? ''
  const gender = params.get('gender') ?? ''
  const sort = params.get('sort') ?? ''

  const set = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v); else next.delete(k)
    setParams(next, { replace: true })
  }

  const { data: cfg } = useQuery({ queryKey: ['store-config'], queryFn: shop.storeConfig })
  const { data: products, isLoading } = useQuery({
    queryKey: ['products', face, category, gender, sort],
    queryFn: () => shop.products({ faceShape: face || undefined, category: category || undefined, gender: gender || undefined, sort: sort || undefined }),
  })

  const faceLabel = useMemo(() => face ? face.charAt(0) + face.slice(1).toLowerCase() : '', [face])

  return (
    <div className="container-x py-12">
      <p className="label">Online store</p>
      <h1 className="mt-2 text-4xl">{cfg?.heroTitle ?? 'Frames matched to your face.'}</h1>
      <p className="mt-3 max-w-xl text-ink/65">{cfg?.heroSubtitle}</p>

      {face && (
        <div className="mt-4 inline-flex items-center gap-2 rounded-full bg-moss/15 px-3 py-1 text-sm">
          Showing frames for a <strong>{faceLabel}</strong> face
          <button className="text-ink/50 hover:text-ink" onClick={() => set('face', '')}>clear ✕</button>
        </div>
      )}

      <div className="mt-8 flex flex-wrap items-center gap-2">
        {CATEGORIES.map(([code, label]) => (
          <button
            key={code}
            onClick={() => set('category', code)}
            className={`rounded-full border px-3 py-1 text-sm ${category === code ? 'border-ink bg-ink text-bone' : 'border-ink/20 hover:border-ink/50'}`}
          >{label}</button>
        ))}
        <select value={gender} onChange={(e) => set('gender', e.target.value)} className="ml-auto rounded-lg border border-ink/20 px-2 py-1 text-sm">
          <option value="">Everyone</option><option value="MEN">Men</option><option value="WOMEN">Women</option>
        </select>
        <select value={sort} onChange={(e) => set('sort', e.target.value)} className="rounded-lg border border-ink/20 px-2 py-1 text-sm">
          <option value="">Featured</option><option value="price_asc">Price ↑</option><option value="price_desc">Price ↓</option>
        </select>
      </div>

      {isLoading ? (
        <p className="mt-10 text-ink/50">Loading frames…</p>
      ) : !products?.length ? (
        <p className="mt-10 text-ink/50">No frames match those filters yet.</p>
      ) : (
        <div className="mt-8 grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
          {products.map((p) => <Tile key={p.id} p={p} />)}
        </div>
      )}
    </div>
  )
}

function Tile({ p }: { p: ProductCard }) {
  return (
    <Link to={`/store/${p.slug}`} className="group block">
      <div className="aspect-[4/3] overflow-hidden rounded-2xl border border-ink/10 bg-white">
        {p.imageUrl
          ? <img src={assetUrl(p.imageUrl)} alt={p.name} className="h-full w-full object-cover transition group-hover:scale-105" />
          : <div className="flex h-full items-center justify-center text-ink/30">No image</div>}
      </div>
      <div className="mt-3">
        <div className="flex items-baseline justify-between gap-2">
          <h3 className="text-base">{p.name}</h3>
          <span className="shrink-0 text-sm font-medium">{money(p.priceMinor, p.currency)}</span>
        </div>
        <p className="text-xs text-ink/50">
          {p.colour}{p.colour && p.material ? ' · ' : ''}{p.material}
          {!p.inStock && <span className="text-clay"> · sold out</span>}
        </p>
      </div>
    </Link>
  )
}
