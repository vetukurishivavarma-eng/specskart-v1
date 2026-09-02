import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../lib/api'

const STATUSES = ['NEW', 'CONTACTED', 'ENGAGED', 'FACE_ANALYSIS_STARTED', 'FACE_ANALYSIS_COMPLETED', 'INTERESTED', 'FOLLOW_UP', 'CONVERTED', 'LOST']

type Detail = {
  lead: any
  attribution: Record<string, unknown>
  timeline: { at: string; type: string; source: string | null }[]
  notes: { id: string; body: string; authorEmail: string; createdAt: string }[]
  whatsappMessages: { at: string; direction: string; type: string; body: string }[]
  faceAnalyses: any[]
  consents: { at: string; type: string; status: string; policyVersion: string }[]
}

export default function LeadDetail() {
  const { id } = useParams()
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const { data, isLoading } = useQuery({ queryKey: ['lead', id], queryFn: () => api<Detail>(`/admin/leads/${id}`, { auth: true }) })
  const staff = useQuery({ queryKey: ['staff'], queryFn: () => api<{ id: string; name: string }[]>('/admin/users', { auth: true }) })
  const assign = useMutation({
    mutationFn: (userId: string) => api(`/admin/leads/${id}/assign`, { method: 'POST', auth: true, body: JSON.stringify({ userId: userId || null }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lead', id] }),
  })

  const setStatus = useMutation({
    mutationFn: (status: string) => api(`/admin/leads/${id}/status`, { method: 'PATCH', auth: true, body: JSON.stringify({ status }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lead', id] }),
  })
  const addNote = useMutation({
    mutationFn: () => api(`/admin/leads/${id}/notes`, { method: 'POST', auth: true, body: JSON.stringify({ body: note }) }),
    onSuccess: () => { setNote(''); qc.invalidateQueries({ queryKey: ['lead', id] }) },
  })

  if (isLoading || !data) return <p>Loading…</p>
  const l = data.lead

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 space-y-6">
        <div>
          <h1 className="text-2xl">{l.name ?? 'Unknown'}</h1>
          <p className="text-ink/55">{l.whatsappNumber} · {l.source}{l.campaignName ? ` · ${l.campaignName}` : ''}</p>
          <div className="mt-3 flex items-center gap-2">
            <select className="rounded-lg border border-ink/20 px-2 py-1 text-sm" value={l.status} onChange={(e) => setStatus.mutate(e.target.value)}>
              {STATUSES.map((s) => <option key={s}>{s}</option>)}
            </select>
            {setStatus.isError && <span className="text-xs text-clay">{(setStatus.error as Error).message}</span>}
            <select className="rounded-lg border border-ink/20 px-2 py-1 text-sm" value={l.assignedToUserId ?? ''} onChange={(e) => assign.mutate(e.target.value)}>
              <option value="">Unassigned</option>
              {staff.data?.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </div>
        </div>

        <section className="card p-4">
          <div className="label">Journey</div>
          <ol className="mt-3 space-y-2">
            {data.timeline.map((t, i) => (
              <li key={i} className="flex gap-3 text-sm">
                <span className="text-ink/40">{new Date(t.at).toLocaleString()}</span>
                <span>{t.type.replaceAll('_', ' ').toLowerCase()}</span>
              </li>
            ))}
          </ol>
        </section>

        {data.faceAnalyses.length > 0 && (
          <section className="card p-4">
            <div className="label">Face analysis</div>
            {data.faceAnalyses.map((a, i) => (
              <div key={i} className="mt-2 text-sm">
                <div className="font-display text-lg">{a.faceShape} · {Math.round(a.confidence * 100)}%</div>
                <div className="text-ink/60">{a.summary}</div>
                <div className="text-ink/40">Recommended: {a.recommended?.join(', ')}</div>
                <div className="text-ink/40">Image retained: {a.imageRetained ? 'yes' : 'no'} · {a.analysisVersion}</div>
              </div>
            ))}
          </section>
        )}

        <section className="card p-4">
          <div className="label">WhatsApp conversation</div>
          <div className="mt-3 space-y-2">
            {data.whatsappMessages.map((m, i) => (
              <div key={i} className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${m.direction === 'INBOUND' ? 'bg-ink/5' : 'ml-auto bg-moss/15'}`}>
                {m.body}
                <div className="mt-1 text-[10px] text-ink/40">{m.direction} · {new Date(m.at).toLocaleTimeString()}</div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="space-y-6">
        <section className="card p-4">
          <div className="label">Attribution</div>
          <dl className="mt-3 space-y-1 text-sm">
            {Object.entries(data.attribution).filter(([, v]) => v != null && v !== '').map(([k, v]) => (
              <div key={k} className="flex justify-between gap-3">
                <dt className="text-ink/45">{k}</dt><dd className="truncate text-right">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="card p-4">
          <div className="label">Consent</div>
          <ul className="mt-2 space-y-1 text-sm">
            {data.consents.map((c, i) => <li key={i}>{c.type}: <strong>{c.status}</strong> <span className="text-ink/40">v{c.policyVersion}</span></li>)}
          </ul>
        </section>

        <section className="card p-4">
          <div className="label">Notes</div>
          <div className="mt-2 space-y-2">
            {data.notes.map((n) => (
              <div key={n.id} className="text-sm"><p>{n.body}</p><p className="text-[10px] text-ink/40">{n.authorEmail} · {new Date(n.createdAt).toLocaleString()}</p></div>
            ))}
          </div>
          <textarea className="mt-3 w-full rounded-lg border border-ink/20 p-2 text-sm" rows={3} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Add a note…" />
          <button className="btn-primary mt-2 w-full !py-2 text-xs" disabled={!note.trim() || addNote.isPending} onClick={() => addNote.mutate()}>Add note</button>
        </section>
      </div>
    </div>
  )
}
