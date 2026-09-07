import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

const CATEGORIES = ['WAYFARER', 'AVIATOR', 'ROUND_FRAME', 'GEOMETRIC', 'CATEYE', 'BROWLINE', 'RECTANGLE', 'SQUARE_FRAME', 'OVERSIZED', 'THIN_RIM', 'OVAL_FRAME']

type Form = {
  name: string; slug: string; description: string; frameCategoryCode: string
  material: string; colour: string; gender: string
  priceKwacha: string; compareAtKwacha: string; stockQty: string
  lensable: boolean; featured: boolean; status: string
  images: { url: string; alt: string }[]
}

const EMPTY: Form = {
  name: '', slug: '', description: '', frameCategoryCode: '', material: '', colour: '', gender: 'UNISEX',
  priceKwacha: '', compareAtKwacha: '', stockQty: '0', lensable: true, featured: false, status: 'ACTIVE', images: [{ url: '', alt: '' }],
}

export default function ProductEditor() {
  const { id } = useParams()
  const isNew = !id
  const nav = useNavigate()
  const qc = useQueryClient()
  const [f, setF] = useState<Form>(EMPTY)

  const existing = useQuery({
    queryKey: ['admin-product', id],
    queryFn: () => api<any>(`/admin/catalog/products/${id}`, { auth: true }),
    enabled: !isNew,
  })

  useEffect(() => {
    const p = existing.data
    if (!p) return
    setF({
      name: p.name, slug: p.slug, description: p.description ?? '', frameCategoryCode: p.frameCategoryCode ?? '',
      material: p.material ?? '', colour: p.colour ?? '', gender: p.gender,
      priceKwacha: String(p.priceMinor / 100), compareAtKwacha: p.compareAtMinor ? String(p.compareAtMinor / 100) : '',
      stockQty: String(p.stockQty), lensable: p.lensable, featured: p.featured, status: p.status,
      images: p.images.length ? p.images.map((i: any) => ({ url: i.url, alt: i.alt ?? '' })) : [{ url: '', alt: '' }],
    })
  }, [existing.data])

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: f.name, slug: f.slug || undefined, description: f.description, frameCategoryCode: f.frameCategoryCode || undefined,
        material: f.material, colour: f.colour, gender: f.gender,
        priceMinor: Math.round(Number(f.priceKwacha) * 100),
        compareAtMinor: f.compareAtKwacha ? Math.round(Number(f.compareAtKwacha) * 100) : null,
        stockQty: Number(f.stockQty), lensable: f.lensable, featured: f.featured, status: f.status,
        images: f.images.filter((i) => i.url.trim()),
      }
      return isNew
        ? api('/admin/catalog/products', { method: 'POST', auth: true, body: JSON.stringify(body) })
        : api(`/admin/catalog/products/${id}`, { method: 'PUT', auth: true, body: JSON.stringify(body) })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-products'] }); nav('/admin/products') },
  })

  const del = useMutation({
    mutationFn: () => api(`/admin/catalog/products/${id}`, { method: 'DELETE', auth: true }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-products'] }); nav('/admin/products') },
  })

  const s = <K extends keyof Form>(k: K, v: Form[K]) => setF({ ...f, [k]: v })

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl">{isNew ? 'New product' : f.name || 'Edit product'}</h1>
      <form className="mt-6 space-y-4" onSubmit={(e) => { e.preventDefault(); if (f.name && f.priceKwacha) save.mutate() }}>
        <Text label="Name" value={f.name} onChange={(v) => s('name', v)} />
        <Text label="Slug (optional)" value={f.slug} onChange={(v) => s('slug', v)} />
        <label className="block">
          <span className="lbl">Description</span>
          <textarea value={f.description} onChange={(e) => s('description', e.target.value)} rows={3} className="fld" />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <Select label="Frame category" value={f.frameCategoryCode} onChange={(v) => s('frameCategoryCode', v)} options={['', ...CATEGORIES]} />
          <Select label="Fit" value={f.gender} onChange={(v) => s('gender', v)} options={['UNISEX', 'MEN', 'WOMEN']} />
          <Text label="Colour" value={f.colour} onChange={(v) => s('colour', v)} />
          <Text label="Material" value={f.material} onChange={(v) => s('material', v)} />
          <Text label="Price (K)" value={f.priceKwacha} onChange={(v) => s('priceKwacha', v)} />
          <Text label="Compare-at (K, optional)" value={f.compareAtKwacha} onChange={(v) => s('compareAtKwacha', v)} />
          <Text label="Stock" value={f.stockQty} onChange={(v) => s('stockQty', v)} />
          <Select label="Status" value={f.status} onChange={(v) => s('status', v)} options={['ACTIVE', 'DRAFT', 'ARCHIVED']} />
        </div>
        <div className="flex gap-6 text-sm">
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.featured} onChange={(e) => s('featured', e.target.checked)} /> Featured</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.lensable} onChange={(e) => s('lensable', e.target.checked)} /> Prescription-ready</label>
        </div>

        <div>
          <span className="lbl">Image URLs</span>
          {f.images.map((img, i) => (
            <div key={i} className="mt-1 flex gap-2">
              <input value={img.url} placeholder="https://…" onChange={(e) => {
                const next = [...f.images]; next[i] = { ...next[i], url: e.target.value }; s('images', next)
              }} className="fld" />
              <button type="button" className="text-ink/40 hover:text-clay" onClick={() => s('images', f.images.filter((_, x) => x !== i))}>✕</button>
            </div>
          ))}
          <button type="button" className="mt-2 text-xs underline" onClick={() => s('images', [...f.images, { url: '', alt: '' }])}>Add image</button>
        </div>

        <div className="flex items-center gap-3 pt-2">
          <button className="btn-primary" disabled={save.isPending || !f.name || !f.priceKwacha}>{save.isPending ? 'Saving…' : 'Save'}</button>
          {!isNew && (
            <button type="button" className="btn-ghost !border-clay/40 text-clay"
              onClick={() => { if (confirm('Delete this product?')) del.mutate() }}>Delete</button>
          )}
          {save.isError && <span className="text-sm text-clay">{(save.error as Error).message}</span>}
        </div>
      </form>
    </div>
  )
}

function Text({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return <label className="block"><span className="lbl">{label}</span>
    <input value={value} onChange={(e) => onChange(e.target.value)} className="fld" /></label>
}
function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: string[] }) {
  return <label className="block"><span className="lbl">{label}</span>
    <select value={value} onChange={(e) => onChange(e.target.value)} className="fld">
      {options.map((o) => <option key={o} value={o}>{o || '—'}</option>)}
    </select></label>
}
