import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { assetUrl } from '../lib/shop'

const API = (import.meta.env.VITE_API_BASE as string) ?? '/api'
const CATEGORIES = ['WAYFARER', 'AVIATOR', 'ROUND_FRAME', 'GEOMETRIC', 'CATEYE', 'BROWLINE', 'RECTANGLE', 'SQUARE_FRAME', 'OVERSIZED', 'THIN_RIM', 'OVAL_FRAME']

type Form = {
  name: string; slug: string; description: string; frameCategoryCode: string
  material: string; colour: string; gender: string
  priceKwacha: string; compareAtKwacha: string; stockQty: string
  lensable: boolean; featured: boolean; status: string
  dropsAt: string; limitedEdition: boolean; kind: string
}

const EMPTY: Form = {
  name: '', slug: '', description: '', frameCategoryCode: '', material: '', colour: '', gender: 'UNISEX',
  priceKwacha: '', compareAtKwacha: '', stockQty: '0', lensable: true, featured: false, status: 'ACTIVE',
  dropsAt: '', limitedEdition: false, kind: 'FRAME',
}

// ISO instant <-> value for <input type="datetime-local"> (local time, no seconds/zone)
const toLocalInput = (iso: string | null) => iso ? new Date(iso).toISOString().slice(0, 16) : ''
const fromLocalInput = (v: string) => v ? new Date(v).toISOString() : null

export default function ProductEditor() {
  const { id } = useParams()
  const isNew = !id
  const nav = useNavigate()
  const qc = useQueryClient()
  const [f, setF] = useState<Form>(EMPTY)
  const fileRef = useRef<HTMLInputElement>(null)

  const existing = useQuery({
    queryKey: ['admin-product', id],
    queryFn: () => api<any>(`/admin/catalog/products/${id}`, { auth: true }),
    enabled: !isNew,
  })
  const product = existing.data
  const photos: { id: string; url: string; alt: string | null }[] = product?.images ?? []
  const tryOnRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!product) return
    setF({
      name: product.name, slug: product.slug, description: product.description ?? '',
      frameCategoryCode: product.frameCategoryCode ?? '', material: product.material ?? '',
      colour: product.colour ?? '', gender: product.gender,
      priceKwacha: String(product.priceMinor / 100),
      compareAtKwacha: product.compareAtMinor ? String(product.compareAtMinor / 100) : '',
      stockQty: String(product.stockQty), lensable: product.lensable, featured: product.featured, status: product.status,
      dropsAt: toLocalInput(product.dropsAt ?? null), limitedEdition: !!product.limitedEdition,
      kind: product.kind ?? 'FRAME',
    })
  }, [product])

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: f.name, slug: f.slug || undefined, description: f.description,
        frameCategoryCode: f.frameCategoryCode || undefined, material: f.material, colour: f.colour, gender: f.gender,
        priceMinor: Math.round(Number(f.priceKwacha) * 100),
        compareAtMinor: f.compareAtKwacha ? Math.round(Number(f.compareAtKwacha) * 100) : null,
        stockQty: Number(f.stockQty), lensable: f.lensable, featured: f.featured, status: f.status,
        dropsAt: fromLocalInput(f.dropsAt), limitedEdition: f.limitedEdition, kind: f.kind,
      }
      return isNew
        ? api<any>('/admin/catalog/products', { method: 'POST', auth: true, body: JSON.stringify(body) })
        : api<any>(`/admin/catalog/products/${id}`, { method: 'PUT', auth: true, body: JSON.stringify(body) })
    },
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      if (isNew) nav(`/admin/products/${p.id}`, { replace: true }) // now they can add photos
    },
  })

  const del = useMutation({
    mutationFn: () => api(`/admin/catalog/products/${id}`, { method: 'DELETE', auth: true }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-products'] }); nav('/admin/products') },
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${API}/admin/catalog/products/${id}/images`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('specskart_token')}` },
        body: fd,
      })
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message ?? 'Upload failed')
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-product', id] }); if (fileRef.current) fileRef.current.value = '' },
  })

  const removePhoto = useMutation({
    mutationFn: (imageId: string) => api(`/admin/catalog/products/${id}/images/${imageId}`, { method: 'DELETE', auth: true }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-product', id] }),
  })

  const uploadTryOn = useMutation({
    mutationFn: async (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${API}/admin/catalog/products/${id}/try-on-image`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('specskart_token')}` },
        body: fd,
      })
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message ?? 'Upload failed')
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-product', id] }); if (tryOnRef.current) tryOnRef.current.value = '' },
  })

  const removeTryOn = useMutation({
    mutationFn: () => api(`/admin/catalog/products/${id}/try-on-image`, { method: 'DELETE', auth: true }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-product', id] }),
  })

  const genTryOn = useMutation({
    mutationFn: () => api(`/admin/catalog/products/${id}/try-on-image/from-photo`, { method: 'POST', auth: true }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-product', id] }),
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
        <div className="flex flex-wrap gap-6 text-sm">
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.featured} onChange={(e) => s('featured', e.target.checked)} /> Featured</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.lensable} onChange={(e) => s('lensable', e.target.checked)} /> Prescription-ready</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.limitedEdition} onChange={(e) => s('limitedEdition', e.target.checked)} /> Limited edition</label>
          <label className="flex items-center gap-2">Type
            <select value={f.kind} onChange={(e) => s('kind', e.target.value)} className="rounded border border-ink/20 px-1 py-0.5 text-sm">
              <option value="FRAME">Frame</option><option value="ACCESSORY">Accessory</option>
            </select>
          </label>
        </div>
        <label className="block">
          <span className="lbl">Drops at (leave blank for immediate)</span>
          <input type="datetime-local" value={f.dropsAt} onChange={(e) => s('dropsAt', e.target.value)} className="fld" />
        </label>

        <div className="flex items-center gap-3 pt-2">
          <button className="btn-primary" disabled={save.isPending || !f.name || !f.priceKwacha}>{save.isPending ? 'Saving…' : 'Save'}</button>
          {!isNew && (
            <button type="button" className="btn-ghost !border-clay/40 text-clay"
              onClick={() => { if (confirm('Delete this product?')) del.mutate() }}>Delete</button>
          )}
          {save.isError && <span className="text-sm text-clay">{(save.error as Error).message}</span>}
          {save.isSuccess && !isNew && <span className="text-sm text-moss">Saved.</span>}
        </div>
      </form>

      <div className="mt-8">
        <span className="lbl">Photos</span>
        {isNew ? (
          <p className="mt-1 text-sm text-ink/50">Save the product first, then you can add photos here.</p>
        ) : (
          <>
            <div className="mt-2 flex flex-wrap gap-3">
              {photos.map((img) => (
                <div key={img.id} className="relative h-24 w-32 overflow-hidden rounded-lg border border-ink/15">
                  <img src={assetUrl(img.url)} alt={img.alt ?? ''} className="h-full w-full object-cover" />
                  <button
                    className="absolute right-1 top-1 rounded bg-white/90 px-1 text-xs text-clay"
                    onClick={() => removePhoto.mutate(img.id)}
                  >✕</button>
                </div>
              ))}
              {!photos.length && <p className="text-sm text-ink/40">No photos yet.</p>}
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              className="mt-3 text-sm"
              disabled={upload.isPending}
              onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate(file) }}
            />
            {upload.isPending && <p className="mt-1 text-xs text-ink/50">Uploading…</p>}
            {upload.isError && <p className="mt-1 text-xs text-clay">{(upload.error as Error).message}</p>}
            <p className="mt-1 text-xs text-ink/40">JPG or PNG, up to 10 MB. Resized and optimised automatically.</p>

            <div className="mt-6">
              <span className="lbl">Try-on image</span>
              <p className="mt-1 text-xs text-ink/40">
                A front-on, background-removed picture of this frame. The 3D virtual try-on maps it onto a
                head-tracked surface so it looks like the real product. Without one, try-on shows a generic
                tinted frame.
              </p>
              {product?.tryOnImageUrl ? (
                <div className="mt-2 flex items-center gap-3">
                  <div className="h-24 w-32 overflow-hidden rounded-lg border border-ink/15 bg-[repeating-conic-gradient(#e5e5e5_0_25%,#fff_0_50%)] bg-[length:16px_16px]">
                    <img src={assetUrl(product.tryOnImageUrl)} alt="" className="h-full w-full object-contain" />
                  </div>
                  <button type="button" className="text-xs text-clay underline" onClick={() => removeTryOn.mutate()}>Remove</button>
                </div>
              ) : (
                <p className="mt-1 text-xs text-ink/40">None yet.</p>
              )}
              <div className="mt-2 flex flex-wrap items-center gap-3">
                <button type="button" className="btn-ghost !px-3 !py-1 text-xs"
                  disabled={!photos.length || genTryOn.isPending}
                  onClick={() => genTryOn.mutate()}>
                  {genTryOn.isPending ? 'Generating…' : '✨ Generate from main photo'}
                </button>
                {!photos.length && <span className="text-xs text-ink/40">Add a photo first</span>}
              </div>
              {genTryOn.isError && <p className="mt-1 text-xs text-clay">{(genTryOn.error as Error).message}</p>}

              <input
                ref={tryOnRef}
                type="file"
                accept="image/*"
                className="mt-2 text-sm"
                disabled={uploadTryOn.isPending}
                onChange={(e) => { const file = e.target.files?.[0]; if (file) uploadTryOn.mutate(file) }}
              />
              {uploadTryOn.isPending && <p className="mt-1 text-xs text-ink/50">Uploading…</p>}
              {uploadTryOn.isError && <p className="mt-1 text-xs text-clay">{(uploadTryOn.error as Error).message}</p>}
              <p className="mt-1 text-xs text-ink/40">
                Generate pulls the first photo and removes a plain (light, uncluttered) background automatically.{' '}
                Or upload your own: a transparent PNG is kept as-is; a plain photo gets its background removed. Resized to 900px wide.
              </p>
            </div>
          </>
        )}
      </div>
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
