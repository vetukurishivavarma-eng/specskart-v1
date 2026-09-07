import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

type Promo = {
  id: string; code: string; discountType: string; discountValue: number
  minSubtotalMinor: number; maxRedemptions: number | null; redeemedCount: number
  expiresAt: string | null; autoIssue: boolean; active: boolean
}

export default function Promos() {
  const qc = useQueryClient()
  const { data } = useQuery({ queryKey: ['admin-promos'], queryFn: () => api<Promo[]>('/admin/catalog/promos', { auth: true }) })
  const [nw, setNw] = useState({ code: '', discountType: 'PERCENT', discountValue: '10', minKwacha: '', autoIssue: false })
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-promos'] })

  const create = useMutation({
    mutationFn: () => api('/admin/catalog/promos', {
      method: 'POST', auth: true,
      body: JSON.stringify({
        code: nw.code, discountType: nw.discountType, discountValue: Number(nw.discountValue),
        minSubtotalMinor: nw.minKwacha ? Math.round(Number(nw.minKwacha) * 100) : 0,
        autoIssue: nw.autoIssue, active: true,
      }),
    }),
    onSuccess: () => { setNw({ code: '', discountType: 'PERCENT', discountValue: '10', minKwacha: '', autoIssue: false }); refresh() },
  })

  const toggle = useMutation({
    mutationFn: (p: Promo) => api(`/admin/catalog/promos/${p.id}`, { method: 'PUT', auth: true, body: JSON.stringify({ active: !p.active }) }),
    onSuccess: refresh,
  })
  const setAuto = useMutation({
    mutationFn: (p: Promo) => api(`/admin/catalog/promos/${p.id}`, { method: 'PUT', auth: true, body: JSON.stringify({ autoIssue: !p.autoIssue }) }),
    onSuccess: refresh,
  })
  const del = useMutation({
    mutationFn: (id: string) => api(`/admin/catalog/promos/${id}`, { method: 'DELETE', auth: true }),
    onSuccess: refresh,
  })

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl">Promo codes</h1>

      <form className="card mt-5 flex flex-wrap items-end gap-3 p-4" onSubmit={(e) => { e.preventDefault(); if (nw.code.trim()) create.mutate() }}>
        <label className="block"><span className="lbl">Code</span>
          <input value={nw.code} onChange={(e) => setNw({ ...nw, code: e.target.value.toUpperCase() })} className="fld w-32" /></label>
        <label className="block"><span className="lbl">Type</span>
          <select value={nw.discountType} onChange={(e) => setNw({ ...nw, discountType: e.target.value })} className="fld">
            <option value="PERCENT">% off</option><option value="FIXED">K off</option>
          </select></label>
        <label className="block"><span className="lbl">Value</span>
          <input value={nw.discountValue} onChange={(e) => setNw({ ...nw, discountValue: e.target.value })} className="fld w-20" /></label>
        <label className="block"><span className="lbl">Min spend (K)</span>
          <input value={nw.minKwacha} onChange={(e) => setNw({ ...nw, minKwacha: e.target.value })} className="fld w-24" /></label>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input type="checkbox" checked={nw.autoIssue} onChange={(e) => setNw({ ...nw, autoIssue: e.target.checked })} /> Auto-issue in funnel
        </label>
        <button className="btn-primary !py-2 text-xs" disabled={create.isPending || !nw.code.trim()}>Add</button>
        {create.isError && <span className="text-xs text-clay">{(create.error as Error).message}</span>}
      </form>

      <table className="mt-6 w-full text-sm">
        <thead className="text-left text-ink/50"><tr><th className="py-2">Code</th><th>Discount</th><th>Used</th><th>Auto-issue</th><th>Active</th><th></th></tr></thead>
        <tbody>
          {data?.map((p) => (
            <tr key={p.id} className="border-t border-ink/10">
              <td className="py-2 font-medium">{p.code}</td>
              <td>{p.discountType === 'PERCENT' ? `${p.discountValue}%` : `K${p.discountValue / 100}`}{p.minSubtotalMinor ? ` · min K${p.minSubtotalMinor / 100}` : ''}</td>
              <td>{p.redeemedCount}{p.maxRedemptions ? ` / ${p.maxRedemptions}` : ''}</td>
              <td><button onClick={() => setAuto.mutate(p)} className="underline">{p.autoIssue ? 'yes' : 'no'}</button></td>
              <td><button onClick={() => toggle.mutate(p)} className="underline">{p.active ? 'on' : 'off'}</button></td>
              <td><button onClick={() => { if (confirm(`Delete ${p.code}?`)) del.mutate(p.id) }} className="text-ink/40 hover:text-clay">✕</button></td>
            </tr>
          ))}
          {!data?.length && <tr><td colSpan={6} className="py-6 text-ink/50">No codes yet.</td></tr>}
        </tbody>
      </table>
    </div>
  )
}
