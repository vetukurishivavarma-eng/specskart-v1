/** Specskart's mark + wordmark (2026-10). Same paths as the POS app's src/ui/Logo.tsx. */
export function Mark({ className = 'h-9 w-9', tile = true }: { className?: string; tile?: boolean }) {
  return (
    <svg viewBox="0 0 48 48" className={className} aria-hidden="true">
      {tile && <rect width="48" height="48" rx="12" fill="#2342F0" />}
      <g transform="translate(24 24) scale(0.86) translate(-24 -24)" fill="none" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 18.5 H21.5 C21.5 25.5 20 30.5 14.6 30.5 H11.6 C7 30.5 5.6 26 5 18.5 Z" stroke="#fff" strokeWidth="2.6" />
        <path d="M43 18.5 H26.5 C26.5 25.5 28 30.5 33.4 30.5 H36.4 C41 30.5 42.4 26 43 18.5 Z" stroke="#fff" strokeWidth="2.6" />
        <path d="M2.5 18 H45.5" stroke="#fff" strokeWidth="4.2" />
        <path d="M21.5 22.4 Q24 19.8 26.5 22.4" stroke="#fff" strokeWidth="2.6" />
        <path d="M30.4 25.6 Q30.8 22.6 33.6 22" stroke="#C8F04B" strokeWidth="2" />
      </g>
    </svg>
  )
}

export default function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <Mark />
      <span className={`font-display text-[22px] font-extrabold tracking-[-0.04em] ${dark ? 'text-white' : 'text-ink'}`}>
        specskart<span className="text-clay">.</span>
      </span>
    </span>
  )
}
