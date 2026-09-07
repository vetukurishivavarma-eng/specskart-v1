import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { shop } from '../lib/shop'

/** A quiet, rotating "others are shopping" line. Renders nothing until there's real activity. */
export default function SocialProof({ className = '' }: { className?: string }) {
  const { data } = useQuery({ queryKey: ['social-proof'], queryFn: shop.socialProof, staleTime: 60_000 })
  const [i, setI] = useState(0)

  const lines: string[] = []
  if (data && Array.isArray(data.recent)) {
    for (const r of data.recent) lines.push(`${r.name} in ${r.city} got ${indefinite(r.item)} · ${r.ago}`)
    if (data.analysesThisWeek >= 5) lines.push(`${data.analysesThisWeek} people found their frame shape this week`)
    if (data.ordersThisWeek >= 3) lines.push(`${data.ordersThisWeek} orders placed this week`)
  }

  useEffect(() => {
    if (lines.length < 2) return
    const t = setInterval(() => setI((x) => (x + 1) % lines.length), 4000)
    return () => clearInterval(t)
  }, [lines.length])

  if (!lines.length) return null
  return (
    <div className={`text-xs text-ink/50 ${className}`}>
      <span className="mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-moss align-middle" />
      {lines[i % lines.length]}
    </div>
  )
}

function indefinite(item: string) {
  return /^[aeiou]/i.test(item) ? `an ${item}` : `a ${item}`
}
