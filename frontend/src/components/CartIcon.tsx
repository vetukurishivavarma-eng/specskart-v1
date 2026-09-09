import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { shop } from '../lib/shop'

/** Header bag icon with a live item count that bumps whenever the count goes up. */
export default function CartIcon() {
  const { data } = useQuery({ queryKey: ['cart'], queryFn: shop.cart, staleTime: 10_000 })
  const count = data?.lines.reduce((n, l) => n + l.qty, 0) ?? 0

  const prev = useRef(count)
  const [bump, setBump] = useState(false)
  useEffect(() => {
    if (count > prev.current) {
      setBump(true)
      const t = setTimeout(() => setBump(false), 500)
      prev.current = count
      return () => clearTimeout(t)
    }
    prev.current = count
  }, [count])

  return (
    <NavLink
      to="/cart"
      aria-label={`Bag — ${count} item${count === 1 ? '' : 's'}`}
      className={({ isActive }) => `relative inline-flex p-2 ${isActive ? 'text-ink' : 'text-ink/60 hover:text-ink'}`}
    >
      <svg className={bump ? 'animate-cart-bump' : ''} width="22" height="22" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
        <path d="M3 6h18" />
        <path d="M16 10a4 4 0 0 1-8 0" />
      </svg>
      {count > 0 && (
        <span className="absolute right-0 top-0 min-w-[1rem] rounded-full bg-clay px-1 text-center text-[10px] font-semibold leading-4 text-bone">
          {count}
        </span>
      )}
    </NavLink>
  )
}
