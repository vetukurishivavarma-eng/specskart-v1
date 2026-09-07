import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

type Cfg = {
  heroTitle: string; heroSubtitle: string; heroImageUrl: string | null
  shippingFeeMinor: number; freeShippingOverMinor: number | null
  deliveryEta: string; currency: string
}

export default function StoreSettings() {
  const qc = useQueryClient()
  const { data } = useQuery({ queryKey: ['store-config'], queryFn: () => api<Cfg>('/public/store-config') })
  const [f, setF] = useState({ heroTitle: '', heroSubtitle: '', heroImageUrl: '', shippingK: '', freeOverK: '', deliveryEta: '', currency: 'ZMW' })

  useEffect(() => {
    if (!data) return
    setF({
      heroTitle: data.heroTitle, heroSubtitle: data.heroSubtitle, heroImageUrl: data.heroImageUrl ?? '',
      shippingK: String(data.shippingFeeMinor / 100),
      freeOverK: data.freeShippingOverMinor ? String(data.freeShippingOverMinor / 100) : '',
      deliveryEta: data.deliveryEta, currency: data.currency,
    })
  }, [data])

  const save = useMutation({
    mutationFn: () => api('/admin/catalog/store-config', {
      method: 'PUT', auth: true,
      body: JSON.stringify({
        heroTitle: f.heroTitle, heroSubtitle: f.heroSubtitle, heroImageUrl: f.heroImageUrl || null,
        shippingFeeMinor: Math.round(Number(f.shippingK || 0) * 100),
        freeShippingOverMinor: f.freeOverK ? Math.round(Number(f.freeOverK) * 100) : null,
        deliveryEta: f.deliveryEta, currency: f.currency,
      }),
    }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['store-config'] }),
  })

  const s = (k: keyof typeof f, v: string) => setF({ ...f, [k]: v })

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl">Storefront settings</h1>
      <form className="mt-6 space-y-4" onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
        <label className="block"><span className="lbl">Hero title</span>
          <input value={f.heroTitle} onChange={(e) => s('heroTitle', e.target.value)} className="fld" /></label>
        <label className="block"><span className="lbl">Hero subtitle</span>
          <textarea value={f.heroSubtitle} onChange={(e) => s('heroSubtitle', e.target.value)} rows={2} className="fld" /></label>
        <label className="block"><span className="lbl">Hero image URL</span>
          <input value={f.heroImageUrl} onChange={(e) => s('heroImageUrl', e.target.value)} className="fld" /></label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block"><span className="lbl">Delivery fee (K)</span>
            <input value={f.shippingK} onChange={(e) => s('shippingK', e.target.value)} className="fld" /></label>
          <label className="block"><span className="lbl">Free delivery over (K)</span>
            <input value={f.freeOverK} onChange={(e) => s('freeOverK', e.target.value)} className="fld" placeholder="—" /></label>
          <label className="block"><span className="lbl">Delivery ETA text</span>
            <input value={f.deliveryEta} onChange={(e) => s('deliveryEta', e.target.value)} className="fld" /></label>
          <label className="block"><span className="lbl">Currency</span>
            <input value={f.currency} onChange={(e) => s('currency', e.target.value.toUpperCase())} className="fld" /></label>
        </div>
        <button className="btn-primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button>
        {save.isSuccess && <span className="ml-3 text-sm text-moss">Saved.</span>}
      </form>
    </div>
  )
}
