import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { auth } from '../lib/auth'

export default function AdminLayout() {
  const nav = useNavigate()
  const user = auth.user
  const link = ({ isActive }: { isActive: boolean }) =>
    `block rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-ink text-bone' : 'text-ink/65 hover:bg-ink/5'}`
  return (
    <div className="min-h-screen bg-bone">
      <div className="mx-auto flex max-w-7xl gap-6 px-5 py-6">
        <aside className="w-52 shrink-0">
          <div className="font-display text-xl">Specskart CRM</div>
          <nav className="mt-6 space-y-1">
            <NavLink end to="/admin" className={link}>Dashboard</NavLink>
            <NavLink to="/admin/leads" className={link}>Leads</NavLink>
            <NavLink to="/admin/campaigns" className={link}>Campaigns</NavLink>
          </nav>
          <div className="mt-8 text-xs text-ink/50">
            {user?.name} · {user?.role}
            <button className="mt-2 block underline" onClick={() => { auth.logout(); nav('/admin/login') }}>Sign out</button>
          </div>
        </aside>
        <main className="min-w-0 flex-1"><Outlet /></main>
      </div>
    </div>
  )
}
