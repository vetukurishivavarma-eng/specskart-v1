import { useState } from 'react'
import { auth } from '../lib/auth'

const BASE = import.meta.env.VITE_API_BASE ?? '/api'

/** ADMIN-only: downloads the whole database as one zip (see AdminBackupController). Fetched with
 *  the session token -- a plain link can't carry the Authorization header. */
export default function BackupButton() {
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<string | null>(null)
  if (auth.user?.role !== 'ADMIN') return null

  async function download() {
    setBusy(true)
    setMsg(null)
    try {
      const res = await fetch(`${BASE}/admin/backup`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('specskart_token') ?? ''}` },
      })
      if (!res.ok) throw new Error(res.status === 403 ? 'Only an admin can download a backup.' : `Backup failed (${res.status}).`)
      const blob = await res.blob()
      const name = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? 'specskart-backup.zip'
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = name
      a.click()
      URL.revokeObjectURL(url)
      setMsg(`Saved ${name} (${(blob.size / 1024 / 1024).toFixed(1)} MB)`)
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Backup failed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex items-center gap-3">
      <button onClick={download} disabled={busy} className="btn-dark !px-4 !py-2 text-xs disabled:opacity-50">
        {busy ? 'Preparing backup…' : 'Download database backup'}
      </button>
      {msg && <span className="text-xs text-ink/60">{msg}</span>}
    </div>
  )
}
