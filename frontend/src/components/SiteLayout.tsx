import { Link, NavLink, Outlet } from 'react-router-dom'
import { WA } from '../lib/wa'
import CartIcon from './CartIcon'
import Logo from './Logo'


export default function SiteLayout() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-30 border-b border-ink/[0.08] bg-white/85 backdrop-blur">
        <div className="container-x flex h-16 items-center justify-between gap-4">
          <Link to="/" aria-label="Specskart home"><Logo /></Link>
          {/* Frames and lenses are both on sale -- the store is back in the nav (2026-10). */}
          <nav className="hidden items-center gap-7 text-sm font-medium md:flex">
            {[['/store', 'Frames'], ['/lens', 'Lenses'], ['/frame-finder', 'Frame Finder'], ['/how-it-works', 'How it works'], ['/contact', 'Contact']].map(([to, label]) => (
              <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'text-clay' : 'text-ink/60 hover:text-ink'}>{label}</NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <CartIcon />
            <Link to="/lens" className="btn-primary !px-4 !py-2 text-xs">Get lenses</Link>
          </div>
        </div>
        <nav className="container-x flex gap-5 overflow-x-auto pb-3 text-sm font-medium md:hidden">
          {[['/store', 'Frames'], ['/lens', 'Lenses'], ['/frame-finder', 'Frame Finder'], ['/contact', 'Contact']].map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => `shrink-0 ${isActive ? 'text-clay' : 'text-ink/60'}`}>{label}</NavLink>
          ))}
        </nav>
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="bg-ink py-12 text-sm text-white/60">
        <div className="container-x flex flex-wrap items-center justify-between gap-6">
          <div className="flex flex-col gap-2">
            <Logo dark />
            <span>© {new Date().getFullYear()} Specskart · Frames &amp; lenses, Zambia</span>
          </div>
          <div className="flex gap-6">
            <Link to="/terms">Terms</Link>
            <Link to="/privacy">Privacy</Link>
            <a href={WA} target="_blank" rel="noreferrer">WhatsApp</a>
            <Link to="/admin/login" className="text-white/35">Staff</Link>
          </div>
        </div>
        <p className="container-x mt-8 text-xs text-white/40">
          Offers and prices are subject to availability and may change without notice. T&amp;C apply.
          Information on this site is general guidance, not medical advice, and is not a substitute for an eye
          examination. Final lens prices depend on your prescription. See our <Link to="/terms" className="underline">Terms &amp; Conditions</Link>.
        </p>
      </footer>
    </div>
  )
}
