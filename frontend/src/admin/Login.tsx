import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { auth } from '../lib/auth'

export default function Login() {
  const nav = useNavigate()
  const [email, setEmail] = useState('admin@specskart.local')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true); setErr(null)
    try {
      const r = await api<{ token: string; email: string; name: string; role: string }>('/auth/login', {
        method: 'POST', body: JSON.stringify({ email, password }),
      })
      auth.login(r.token, { email: r.email, name: r.name, role: r.role })
      nav('/admin')
    } catch (e) { setErr((e as ApiError).message) } finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen place-items-center bg-bone">
      <form onSubmit={submit} className="card w-full max-w-sm p-8">
        <div className="font-display text-2xl">Specskart CRM</div>
        <p className="mt-1 text-sm text-ink/55">Staff sign in</p>
        <label className="label mt-6 block">Email</label>
        <input className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2" value={email} onChange={(e) => setEmail(e.target.value)} />
        <label className="label mt-4 block">Password</label>
        <input type="password" className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2" value={password} onChange={(e) => setPassword(e.target.value)} />
        {err && <p className="mt-3 text-sm text-clay">{err}</p>}
        <button className="btn-primary mt-6 w-full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>
  )
}
