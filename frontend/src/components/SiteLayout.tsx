import { Link, NavLink, Outlet } from 'react-router-dom'

const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'

export default function SiteLayout() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-ink/10">
        <div className="container-x flex h-16 items-center justify-between">
          <Link to="/" className="font-display text-xl font-semibold">Specskart</Link>
          <nav className="hidden gap-8 text-sm md:flex">
            {[['/how-it-works', 'How it works'], ['/store', 'Store'], ['/contact', 'Contact']].map(([to, label]) => (
              <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'text-ink' : 'text-ink/55 hover:text-ink'}>{label}</NavLink>
            ))}
          </nav>
          <Link to="/frame-finder" className="btn-primary !px-4 !py-2 text-xs">Find My Frame</Link>
        </div>
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="border-t border-ink/10 py-10 text-sm text-ink/55">
        <div className="container-x flex flex-wrap items-center justify-between gap-4">
          <span>© {new Date().getFullYear()} Specskart</span>
          <div className="flex gap-6">
            <Link to="/privacy">Privacy</Link>
            <a href={WA} target="_blank" rel="noreferrer">WhatsApp</a>
            <Link to="/admin/login" className="text-ink/35">Staff</Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
