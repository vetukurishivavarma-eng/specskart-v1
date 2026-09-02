import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

const STATUSES = ['', 'NEW', 'CONTACTED', 'ENGAGED', 'FACE_ANALYSIS_STARTED', 'FACE_ANALYSIS_COMPLETED', 'INTERESTED', 'FOLLOW_UP', 'CONVERTED', 'LOST']

type Row = {
  id: string; name: string | null; whatsappNumber: string; source: string; campaignName: string | null
  faceShape: string | null; recommendedFrames: string[]; status: string; createdAt: string; lastContactAt: string | null
}
type Page = { content: Row[]; page: number; totalPages: number; totalElements: number }

export default function Leads() {
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useQuery({
    queryKey: ['leads', status, q, page],
    queryFn: () => api<Page>(`/admin/leads?page=${page}&size=20${status ? `&status=${status}` : ''}${q ? `&q=${encodeURIComponent(q)}` : ''}`, { auth: true }),
  })

  return (
    <div>
      <h1 className="text-2xl">Leads</h1>
      <div className="mt-4 flex flex-wrap gap-3">
        <input placeholder="Search name or number" className="rounded-lg border border-ink/20 px-3 py-2 text-sm"
          value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} />
        <select className="rounded-lg border border-ink/20 px-3 py-2 text-sm" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          {STATUSES.map((s) => <option key={s} value={s}>{s || 'All statuses'}</option>)}
        </select>
      </div>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-ink/50">
            <tr>{['Customer', 'WhatsApp', 'Source', 'Campaign', 'Face shape', 'Recommended', 'Status', 'Created'].map((h) => <th key={h} className="py-2 pr-4">{h}</th>)}</tr>
          </thead>
          <tbody>
            {isLoading && <tr><td className="py-4">Loading…</td></tr>}
            {data?.content.map((r) => (
              <tr key={r.id} className="border-t border-ink/10 hover:bg-white">
                <td className="py-2 pr-4"><Link className="underline" to={`/admin/leads/${r.id}`}>{r.name ?? 'Unknown'}</Link></td>
                <td className="py-2 pr-4">{r.whatsappNumber}</td>
                <td className="py-2 pr-4">{r.source}</td>
                <td className="py-2 pr-4">{r.campaignName ?? '—'}</td>
                <td className="py-2 pr-4">{r.faceShape ?? '—'}</td>
                <td className="py-2 pr-4 text-ink/60">{r.recommendedFrames?.slice(0, 3).join(', ') || '—'}</td>
                <td className="py-2 pr-4"><span className="rounded-full bg-ink/10 px-2 py-0.5 text-xs">{r.status}</span></td>
                <td className="py-2 pr-4 text-ink/50">{new Date(r.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex items-center gap-3 text-sm">
        <button className="btn-ghost !px-3 !py-1" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Prev</button>
        <span>Page {page + 1} / {Math.max(1, data?.totalPages ?? 1)} · {data?.totalElements ?? 0} leads</span>
        <button className="btn-ghost !px-3 !py-1" disabled={(data && page + 1 >= data.totalPages) ?? true} onClick={() => setPage((p) => p + 1)}>Next</button>
      </div>
    </div>
  )
}
