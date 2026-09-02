import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

type Stats = {
  totalLeads: number; todayLeads: number; faceAnalysesCompleted: number; analysisConversionPct: number
  whatsappConversations: number; hotLeads: number; newLeads: number; followUpsDue: number
  leadsBySource: Record<string, number>; leadsByStatus: Record<string, number>
}
type Perf = {
  campaignId: string; name: string; platform: string; leads: number; faceAnalyses: number
  analysisConversionPct: number; qualified: number; converted: number; budget: number | null; costPerLead: number | null
}

export default function Dashboard() {
  const stats = useQuery({ queryKey: ['dash'], queryFn: () => api<Stats>('/admin/analytics/dashboard', { auth: true }) })
  const perf = useQuery({ queryKey: ['perf'], queryFn: () => api<Perf[]>('/admin/analytics/campaigns', { auth: true }) })
  const sys = useQuery({ queryKey: ['sys'], queryFn: () => api<{ whatsappMode: string; whatsappConfigured: boolean; simulationEnabled: boolean; frameRetainImages: boolean }>('/admin/system/status', { auth: true }) })

  if (stats.isLoading) return <p>Loading…</p>
  const s = stats.data!

  const tiles: [string, number | string][] = [
    ['Total leads', s.totalLeads], ['Today', s.todayLeads], ['Face analyses', s.faceAnalysesCompleted],
    ['Analysis conversion', `${s.analysisConversionPct}%`], ['WhatsApp conversations', s.whatsappConversations],
    ['Hot leads', s.hotLeads], ['New leads', s.newLeads], ['Follow-ups due', s.followUpsDue],
  ]

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl">Dashboard</h1>
        {sys.data && (
          <div className="flex gap-3 text-xs text-ink/55">
            <span className={`rounded-full px-2 py-1 ${sys.data.whatsappConfigured ? 'bg-moss/15' : 'bg-clay/15 text-clay'}`}>
              WhatsApp: {sys.data.whatsappMode}{sys.data.whatsappConfigured ? '' : ' — not configured'}
            </span>
            {sys.data.simulationEnabled && <span className="rounded-full bg-ink/10 px-2 py-1">Simulation on</span>}
            <span className="rounded-full bg-ink/10 px-2 py-1">Image retention: {sys.data.frameRetainImages ? 'on' : 'off'}</span>
          </div>
        )}
      </div>
      <div className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-4">
        {tiles.map(([k, v]) => (
          <div key={k} className="card p-4">
            <div className="label">{k}</div>
            <div className="mt-1 font-display text-3xl">{v}</div>
          </div>
        ))}
      </div>

      <div className="mt-8 grid gap-6 md:grid-cols-2">
        <Breakdown title="Leads by source" data={s.leadsBySource} />
        <Breakdown title="Leads by status" data={s.leadsByStatus} />
      </div>

      <h2 className="mt-10 text-xl">Campaign performance</h2>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-ink/50">
            <tr>{['Campaign', 'Platform', 'Leads', 'Analyses', 'Conv %', 'Qualified', 'Converted', 'Cost / lead'].map((h) => <th key={h} className="py-2 pr-4">{h}</th>)}</tr>
          </thead>
          <tbody>
            {perf.data?.map((p) => (
              <tr key={p.campaignId} className="border-t border-ink/10">
                <td className="py-2 pr-4">{p.name}</td><td className="py-2 pr-4">{p.platform}</td>
                <td className="py-2 pr-4">{p.leads}</td><td className="py-2 pr-4">{p.faceAnalyses}</td>
                <td className="py-2 pr-4">{p.analysisConversionPct}%</td><td className="py-2 pr-4">{p.qualified}</td>
                <td className="py-2 pr-4">{p.converted}</td>
                <td className="py-2 pr-4">{p.costPerLead != null ? p.costPerLead : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Breakdown({ title, data }: { title: string; data: Record<string, number> }) {
  const total = Object.values(data).reduce((a, b) => a + b, 0) || 1
  return (
    <div className="card p-4">
      <div className="label">{title}</div>
      <div className="mt-3 space-y-2">
        {Object.entries(data).map(([k, v]) => (
          <div key={k}>
            <div className="flex justify-between text-sm"><span>{k}</span><span className="text-ink/50">{v}</span></div>
            <div className="mt-1 h-1.5 rounded bg-ink/10"><div className="h-full rounded bg-clay" style={{ width: `${(v / total) * 100}%` }} /></div>
          </div>
        ))}
      </div>
    </div>
  )
}
