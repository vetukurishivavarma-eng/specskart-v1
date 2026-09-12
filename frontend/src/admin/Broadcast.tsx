import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../lib/api'

type Req = {
  faceShape?: string; excludeConverted: boolean
  templateName: string; headerImageUrl?: string; bodyParams: string[]; limit: number
}

export default function Broadcast() {
  const [faceShape, setFaceShape] = useState('')
  const [excludeConverted, setExcludeConverted] = useState(true)
  const [templateName, setTemplateName] = useState('')
  const [headerImageUrl, setHeaderImageUrl] = useState('')
  const [paramsText, setParamsText] = useState('')
  const [limit, setLimit] = useState(200)
  const [previewed, setPreviewed] = useState<number | null>(null)

  const body = (): Req => ({
    faceShape: faceShape || undefined,
    excludeConverted,
    templateName: templateName.trim(),
    headerImageUrl: headerImageUrl.trim() || undefined,
    bodyParams: paramsText.split('\n').map((s) => s.trim()).filter(Boolean),
    limit,
  })

  const preview = useMutation({
    mutationFn: () => api<{ eligible: number }>('/admin/whatsapp/broadcast/preview', {
      method: 'POST', auth: true, body: JSON.stringify(body()),
    }),
    onSuccess: (r) => setPreviewed(r.eligible),
  })

  const send = useMutation({
    mutationFn: () => api<{ sent: number; failed: number; skippedOverCap: number }>('/admin/whatsapp/broadcast/send', {
      method: 'POST', auth: true, body: JSON.stringify(body()),
    }),
  })

  const canSend = templateName.trim() && previewed !== null

  return (
    <div className="max-w-xl space-y-6">
      <div>
        <h1 className="text-2xl">WhatsApp broadcast</h1>
        <p className="mt-1 text-sm text-ink/55">
          Send an approved WhatsApp template to a lead segment at once — new arrivals, a sale.
          Opted-out leads are always excluded. Preview the count before you send.
        </p>
      </div>

      <div className="card space-y-3 p-4">
        <label className="block">
          <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Approved template name</span>
          <input value={templateName} onChange={(e) => { setTemplateName(e.target.value); setPreviewed(null) }}
            placeholder="e.g. weekend_sale_v1" className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
          <p className="mt-1 text-xs text-ink/45">Must already be approved in Meta Business Manager.</p>
        </label>

        <label className="block">
          <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Header image URL (optional)</span>
          <input value={headerImageUrl} onChange={(e) => setHeaderImageUrl(e.target.value)}
            placeholder="https://…" className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
        </label>

        <label className="block">
          <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Body parameters — one per line, in {'{{1}}, {{2}}…'} order</span>
          <textarea value={paramsText} onChange={(e) => setParamsText(e.target.value)} rows={4}
            placeholder={'e.g.\nFrames\n20% off this weekend\nhttps://specskart.../store'}
            className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
        </label>

        <div className="flex gap-3">
          <label className="block flex-1">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Face shape (optional)</span>
            <input value={faceShape} onChange={(e) => { setFaceShape(e.target.value); setPreviewed(null) }}
              placeholder="e.g. OVAL" className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
          </label>
          <label className="block w-28">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Max sends</span>
            <input type="number" min={1} max={500} value={limit}
              onChange={(e) => setLimit(Math.min(500, Math.max(1, Number(e.target.value) || 1)))}
              className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
          </label>
        </div>

        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={excludeConverted}
            onChange={(e) => { setExcludeConverted(e.target.checked); setPreviewed(null) }} />
          Skip leads who've already bought something
        </label>
      </div>

      <div className="flex items-center gap-3">
        <button className="btn-ghost" disabled={preview.isPending} onClick={() => preview.mutate()}>
          {preview.isPending ? 'Counting…' : 'Preview reach'}
        </button>
        {previewed !== null && <span className="text-sm text-ink/60">Reaches <b>{previewed}</b> lead(s)</span>}
      </div>

      {canSend && (
        <button className="btn-primary" disabled={send.isPending} onClick={() => { if (confirm(`Send to up to ${limit} of ${previewed} matching leads?`)) send.mutate() }}>
          {send.isPending ? 'Sending…' : `Send to up to ${Math.min(limit, previewed ?? 0)} leads`}
        </button>
      )}

      {send.isError && <p className="text-sm text-clay">{(send.error as Error).message}</p>}
      {send.isSuccess && (
        <p className="text-sm text-moss">
          Sent {send.data.sent}{send.data.failed > 0 ? `, ${send.data.failed} failed` : ''}
          {send.data.skippedOverCap > 0 ? `, ${send.data.skippedOverCap} skipped (over the cap — raise "Max sends" and run again)` : ''}.
        </p>
      )}
    </div>
  )
}
