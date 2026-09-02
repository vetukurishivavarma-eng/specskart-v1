import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../lib/api'
import { auth } from '../lib/auth'

const PLATFORMS = ['FACEBOOK', 'INSTAGRAM', 'GOOGLE', 'TIKTOK', 'WHATSAPP', 'WEBSITE', 'OTHER']

type Campaign = {
  id: string; name: string; platform: string; externalCampaignId: string | null; status: string
  budget: number | null; destination: string | null; utm: Record<string, string | null>
}

export default function Campaigns() {
  const qc = useQueryClient()
  const isAdmin = auth.user?.role === 'ADMIN'
  const { data } = useQuery({ queryKey: ['campaigns'], queryFn: () => api<Campaign[]>('/admin/campaigns', { auth: true }) })
  const [form, setForm] = useState({ name: '', platform: 'FACEBOOK', externalCampaignId: '', budget: '', utmSource: '', utmCampaign: '' })

  const create = useMutation({
    mutationFn: () => api('/admin/campaigns', {
      method: 'POST', auth: true,
      body: JSON.stringify({ ...form, budget: form.budget ? Number(form.budget) : null }),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['campaigns'] }); setForm({ name: '', platform: 'FACEBOOK', externalCampaignId: '', budget: '', utmSource: '', utmCampaign: '' }) },
  })

  return (
    <div>
      <h1 className="text-2xl">Campaigns</h1>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-ink/50"><tr>{['Name', 'Platform', 'External ID', 'utm_campaign', 'Budget', 'Status'].map((h) => <th key={h} className="py-2 pr-4">{h}</th>)}</tr></thead>
          <tbody>
            {data?.map((c) => (
              <tr key={c.id} className="border-t border-ink/10">
                <td className="py-2 pr-4">{c.name}</td><td className="py-2 pr-4">{c.platform}</td>
                <td className="py-2 pr-4">{c.externalCampaignId ?? '—'}</td><td className="py-2 pr-4">{c.utm?.campaign ?? '—'}</td>
                <td className="py-2 pr-4">{c.budget ?? '—'}</td><td className="py-2 pr-4">{c.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {isAdmin && (
        <div className="card mt-8 max-w-lg p-5">
          <div className="label">New campaign</div>
          <div className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <input className="col-span-2 rounded border border-ink/20 px-2 py-1.5" placeholder="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <select className="rounded border border-ink/20 px-2 py-1.5" value={form.platform} onChange={(e) => setForm({ ...form, platform: e.target.value })}>
              {PLATFORMS.map((p) => <option key={p}>{p}</option>)}
            </select>
            <input className="rounded border border-ink/20 px-2 py-1.5" placeholder="External campaign ID" value={form.externalCampaignId} onChange={(e) => setForm({ ...form, externalCampaignId: e.target.value })} />
            <input className="rounded border border-ink/20 px-2 py-1.5" placeholder="utm_source" value={form.utmSource} onChange={(e) => setForm({ ...form, utmSource: e.target.value })} />
            <input className="rounded border border-ink/20 px-2 py-1.5" placeholder="utm_campaign" value={form.utmCampaign} onChange={(e) => setForm({ ...form, utmCampaign: e.target.value })} />
            <input className="rounded border border-ink/20 px-2 py-1.5" placeholder="Budget" value={form.budget} onChange={(e) => setForm({ ...form, budget: e.target.value })} />
          </div>
          {create.isError && <p className="mt-2 text-xs text-clay">{(create.error as Error).message}</p>}
          <button className="btn-primary mt-3 !py-2 text-xs" disabled={!form.name.trim() || create.isPending} onClick={() => create.mutate()}>Create campaign</button>
        </div>
      )}
    </div>
  )
}
