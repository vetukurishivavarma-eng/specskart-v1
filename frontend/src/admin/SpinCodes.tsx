import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { auth } from '../lib/auth'

type SpinView = {
  code: string; prize: string; customer: string | null; phone: string | null; spunAt: string
  expiresAt: string; redeemedAt: string | null; redeemedBy: string | null; state: 'VALID' | 'USED' | 'EXPIRED' | 'WAITING_FRIEND'
}
type Prize = { label: string; weight: number; active: boolean }

const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
const TONE = { VALID: 'text-moss', USED: 'text-ink/50', EXPIRED: 'text-coral', WAITING_FRIEND: 'text-coral' }
const STATE = { VALID: 'valid', USED: 'used', EXPIRED: 'expired', WAITING_FRIEND: 'waiting for friend' }

/** Spin & Win at the shop: check a customer's code and mark it used (once); the owner edits the wheel. */
export default function SpinCodes() {
  const qc = useQueryClient()
  const [code, setCode] = useState('')
  const [found, setFound] = useState<SpinView | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [resendAt, setResendAt] = useState(0) // the server allows another code after this (ms)
  const [, tick] = useState(0)
  const wait = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000))
  useEffect(() => {
    if (!wait) return
    const t = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [wait > 0])
  const { data: recent } = useQuery({ queryKey: ['admin-spins'], queryFn: () => api<SpinView[]>('/admin/spins', { auth: true }) })

  const look = useMutation({
    mutationFn: () => api<SpinView>(`/admin/spins/code/${encodeURIComponent(code.trim())}`, { auth: true }),
    onSuccess: (v) => { setFound(v); setMsg(null); setSentTo(null); setOtp('') },
    onError: (e) => { setFound(null); setMsg((e as Error).message) },
  })
  const send = useMutation({
    mutationFn: (c: string) => api<{ sentTo: string; resendInSeconds: number }>(`/admin/spins/code/${encodeURIComponent(c)}/send-otp`,
      { method: 'POST', auth: true, body: JSON.stringify({ phone }) }),
    onSuccess: (r) => { setSentTo(r.sentTo); setResendAt(Date.now() + r.resendInSeconds * 1000); setMsg(null) },
    onError: (e) => setMsg((e as Error).message),
  })
  const use = useMutation({
    mutationFn: (c: string) => api<SpinView>(`/admin/spins/code/${encodeURIComponent(c)}/redeem`,
      { method: 'POST', auth: true, body: JSON.stringify({ phone, otp }) }),
    onSuccess: (v) => { setFound(v); setSentTo(null); setOtp(''); setPhone(''); setMsg('✅ Marked as used.'); qc.invalidateQueries({ queryKey: ['admin-spins'] }) },
    onError: (e) => setMsg((e as Error).message),
  })

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl">Spin &amp; Win</h1>
        <p className="mt-1 text-sm text-ink/55">Customers spin from WhatsApp (they type “spin” or pick it in the menu). One prize at a time per number: a customer can spin again once their prize's 2 weeks are over (a friend code blocks spinning too). Prizes are valid for 2 weeks at the shop. A bring-a-friend prize only unlocks once a new friend messages us with the code; the friend gets their own code.</p>
      </div>

      <section className="card p-4">
        <div className="label">Customer's code</div>
        <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (code.trim()) look.mutate() }}>
          <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="SPIN-XXXXX" className="fld !mt-0 max-w-xs" />
          <button className="btn-primary !px-4 !py-2 text-xs" disabled={!code.trim() || look.isPending}>Check</button>
        </form>
        {found && (
          <div className="mt-4 rounded-lg bg-bone p-3 text-sm">
            <p><b>{found.prize}</b> · <span className={TONE[found.state]}>{found.state === 'WAITING_FRIEND' ? 'locked: no new friend has messaged us with this code yet' : found.state === 'VALID' ? `valid until ${day(found.expiresAt)}` : found.state === 'USED' ? `used ${day(found.redeemedAt!)}${found.redeemedBy ? ` by ${found.redeemedBy}` : ''}` : `expired ${day(found.expiresAt)}`}</span></p>
            <p className="text-ink/60">{found.customer ?? 'Customer'}</p>
            {found.state === 'VALID' && (
              <div className="mt-3 space-y-2">
                <p className="text-xs text-ink/60">1. Ask for the WhatsApp number they won with. 2. Send them a verification code. 3. Type the code they got.</p>
                <div className="flex flex-wrap gap-2">
                  <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Their WhatsApp number" inputMode="tel" className="fld !mt-0 max-w-xs" />
                  <button className="btn-ghost !px-4 !py-2 text-xs" disabled={!phone.trim() || send.isPending || wait > 0} onClick={() => send.mutate(found.code)}>{wait ? `Send again in ${wait}s` : sentTo ? 'Send again' : 'Send code'}</button>
                </div>
                {sentTo && (
                  <div className="flex flex-wrap gap-2">
                    <input value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder={`6-digit code sent to ${sentTo}`} inputMode="numeric" className="fld !mt-0 max-w-xs" />
                    <button className="btn-primary !px-4 !py-2 text-xs" disabled={otp.length !== 6 || use.isPending} onClick={() => use.mutate(found.code)}>Give the prize & mark used</button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
        {msg && <p className="mt-2 text-sm">{msg}</p>}
      </section>

      {auth.user?.role === 'ADMIN' && <PrizeEditor />}

      <section className="card p-4">
        <div className="label">Latest spins</div>
        <table className="mt-3 w-full text-sm">
          <thead className="text-left text-ink/50"><tr><th>Code</th><th>Prize</th><th>Customer</th><th>Spun</th><th>Status</th></tr></thead>
          <tbody>
            {(recent ?? []).map((s) => (
              <tr key={s.code} className="border-t border-ink/5">
                <td className="py-1 font-mono">{s.code}</td><td>{s.prize}</td>
                <td>{s.customer ?? s.phone}</td><td>{day(s.spunAt)}</td>
                <td className={TONE[s.state]}>{STATE[s.state]}</td>
              </tr>
            ))}
            {recent?.length === 0 && <tr><td colSpan={5} className="py-3 text-ink/50">No spins yet.</td></tr>}
          </tbody>
        </table>
      </section>
    </div>
  )
}

function PrizeEditor() {
  const qc = useQueryClient()
  const { data } = useQuery({ queryKey: ['admin-spin-prizes'], queryFn: () => api<Prize[]>('/admin/spins/prizes', { auth: true }) })
  const [rows, setRows] = useState<Prize[]>([])
  useEffect(() => { if (data) setRows(data.map(({ label, weight, active }) => ({ label, weight, active }))) }, [data])
  const total = rows.filter((r) => r.active).reduce((s, r) => s + (r.weight || 0), 0)
  const save = useMutation({
    mutationFn: () => api('/admin/spins/prizes', { method: 'PUT', auth: true, body: JSON.stringify(rows) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-spin-prizes'] }),
  })
  const set = (i: number, p: Partial<Prize>) => setRows(rows.map((r, k) => (k === i ? { ...r, ...p } : r)))

  return (
    <section className="card p-4">
      <div className="label">The wheel</div>
      <p className="mt-1 text-xs text-ink/50">A prize's chance is its weight out of the total. Its slice on the wheel is the same size, so what customers see is the real chance.</p>
      <div className="mt-3 space-y-2">
        {rows.map((r, i) => (
          <div key={i} className="flex items-center gap-2 text-sm">
            <input value={r.label} onChange={(e) => set(i, { label: e.target.value })} className="fld !mt-0 flex-1" maxLength={80} />
            <input type="number" min={0} value={r.weight} onChange={(e) => set(i, { weight: Number(e.target.value) })} className="fld !mt-0 w-20" />
            <span className="w-12 text-right text-ink/50">{r.active && total ? Math.round((r.weight / total) * 100) : 0}%</span>
            <label className="flex items-center gap-1 text-xs"><input type="checkbox" checked={r.active} onChange={(e) => set(i, { active: e.target.checked })} /> on</label>
            <button className="text-xs text-coral" onClick={() => setRows(rows.filter((_, k) => k !== i))}>remove</button>
          </div>
        ))}
      </div>
      <div className="mt-3 flex gap-2">
        <button className="btn-ghost !px-3 !py-1 text-xs" onClick={() => setRows([...rows, { label: '', weight: 10, active: true }])}>Add prize</button>
        <button className="btn-primary !px-3 !py-1 text-xs" disabled={save.isPending} onClick={() => save.mutate()}>Save wheel</button>
      </div>
      {save.isError && <p className="mt-2 text-xs text-coral">{(save.error as Error).message}</p>}
      {save.isSuccess && <p className="mt-2 text-xs text-moss">Saved.</p>}
    </section>
  )
}
