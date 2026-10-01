import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { shop, money, assetUrl, rememberedFace, rememberedName } from '../lib/shop'
import SocialProof from '../components/SocialProof'
import { WA } from '../lib/wa'
import { Mark } from '../components/Logo'


export function ForYou() {
  const face = rememberedFace()
  const { data } = useQuery({
    queryKey: ['for-you', face],
    queryFn: () => shop.products({ faceShape: face || undefined }),
    enabled: !!face,
  })
  if (!face || !data?.length) return null
  const name = rememberedName()
  return (
    <section className="border-t border-ink/10 bg-white py-16">
      <div className="container-x">
        <div className="flex items-baseline justify-between">
          <p className="label">{name ? `${name}, picked for your` : 'Picked for your'} {face.toLowerCase()} face</p>
          <Link to={`/store?face=${face}`} className="text-sm underline">See all</Link>
        </div>
        <div className="mt-6 grid grid-cols-2 gap-5 md:grid-cols-4">
          {data.slice(0, 4).map((p) => (
            <Link key={p.id} to={`/store/${p.slug}`} className="group block">
              <div className="aspect-[4/3] overflow-hidden rounded-2xl border border-ink/10 bg-white">
                {p.imageUrl && <img src={assetUrl(p.imageUrl)} alt={p.name} className="h-full w-full object-cover transition group-hover:scale-105" />}
              </div>
              <div className="mt-2 flex items-baseline justify-between gap-2">
                <span className="text-sm">{p.name}</span>
                <span className="shrink-0 text-sm font-medium">{money(p.priceMinor, p.currency)}</span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}

function Featured() {
  const { data } = useQuery({ queryKey: ['featured'], queryFn: shop.featured })
  if (!data?.length) return null
  return (
    <section className="container-x py-20">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="label">Frames</p>
          <h2 className="mt-2 text-4xl">In the store now</h2>
        </div>
        <Link to="/store" className="btn-ghost !py-2">Shop all frames</Link>
      </div>
      <div className="mt-8 grid grid-cols-2 gap-5 md:grid-cols-4">
        {data.slice(0, 8).map((p) => (
          <Link key={p.id} to={`/store/${p.slug}`} className="group card-lift block overflow-hidden">
            <div className="aspect-[4/3] bg-bone">
              {p.imageUrl && <img src={assetUrl(p.imageUrl)} alt={p.name} className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
            </div>
            <div className="flex items-baseline justify-between gap-2 p-4">
              <span className="truncate text-sm font-medium">{p.name}</span>
              <span className="shrink-0 font-display text-base font-bold">{money(p.priceMinor, p.currency)}</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  )
}

export default function Home() {
  return (
    <div>
      <section className="relative overflow-hidden bg-ink text-white">
        <div aria-hidden className="pointer-events-none absolute -right-40 -top-40 h-[520px] w-[520px] rounded-full bg-clay/40 blur-3xl" />
        <div className="container-x relative grid gap-12 py-20 md:grid-cols-[1.15fr_1fr] md:items-center md:py-28">
          <div>
            <p className="inline-flex items-center gap-2 rounded-full border border-white/15 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-signal">
              <span className="h-1.5 w-1.5 rounded-full bg-signal" /> Frames + lenses, Zambia
            </p>
            <h1 className="mt-6 text-5xl leading-[0.98] md:text-7xl">
              See sharp.<br />
              <span className="text-white/45">Look sharper.</span>
            </h1>
            <p className="mt-6 max-w-md text-lg text-white/70">
              Pick a frame that suits your face, add prescription lenses priced in seconds, and we confirm everything on WhatsApp.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link to="/store" className="btn bg-white font-semibold text-ink hover:bg-signal">Shop frames</Link>
              <Link to="/lens" className="btn-primary">Get lenses</Link>
            </div>
            <SocialProof className="mt-6" dark />
            <a href={WA} target="_blank" rel="noreferrer" className="mt-3 inline-block text-sm text-white/50 underline underline-offset-4 hover:text-white">Or chat with us on WhatsApp</a>
          </div>

          <div className="grid min-w-0 gap-4">
            <Link to="/store" className="group relative overflow-hidden rounded-3xl bg-clay p-8 transition hover:-translate-y-1">
              <Mark className="absolute -right-6 -top-6 h-40 w-40 opacity-30 transition group-hover:rotate-[-6deg]" tile={false} />
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-white/70">Frames</p>
              <p className="mt-3 font-display text-4xl font-bold">K300 – K3,000</p>
              <p className="mt-2 text-white/75">Every shape, in store and online.</p>
              <span className="mt-6 inline-block text-sm font-semibold text-signal">Browse frames →</span>
            </Link>
            <div className="grid grid-cols-2 gap-4">
              <Link to="/lens" className="min-w-0 rounded-3xl border border-white/10 bg-white/[0.06] p-5 md:p-6 transition hover:bg-white/[0.1]">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-white/50">Lenses</p>
                <p className="mt-3 font-display text-lg font-bold leading-tight md:text-2xl">Clear or photo&shy;chromatic</p>
                <span className="mt-4 inline-block text-sm font-semibold text-signal">Price my Rx →</span>
              </Link>
              <Link to="/frame-finder" className="min-w-0 rounded-3xl border border-white/10 bg-white/[0.06] p-5 md:p-6 transition hover:bg-white/[0.1]">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-white/50">Frame Finder</p>
                <p className="mt-3 font-display text-lg font-bold leading-tight md:text-2xl">What suits my face?</p>
                <span className="mt-4 inline-block text-sm font-semibold text-signal">Try it →</span>
              </Link>
            </div>
          </div>
        </div>
      </section>

      <ForYou />
      <Featured />

      <section className="border-y border-ink/[0.08] bg-white py-20">
        <div className="container-x">
          <p className="label">How it works</p>
          <h2 className="mt-2 text-4xl">Three steps to new glasses</h2>
          <div className="mt-10 grid gap-6 md:grid-cols-3">
            {[
              ['01', 'Choose your frame', 'Browse the store or let Frame Finder match one to your face shape.'],
              ['02', 'Add your lenses', 'Clear or photochromatic, blue-light block optional — priced from your prescription.'],
              ['03', 'Confirm on WhatsApp', 'We verify your order, then you collect in store or we deliver.'],
            ].map(([n, t, d]) => (
              <div key={n} className="rounded-2xl bg-bone p-7">
                <div className="font-display text-5xl font-extrabold text-clay">{n}</div>
                <h3 className="mt-4 text-xl">{t}</h3>
                <p className="mt-2 text-ink/65">{d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="container-x py-20">
        <div className="flex flex-col items-start gap-6 rounded-3xl bg-clay p-10 text-white md:flex-row md:items-center md:justify-between">
          <div>
            <h3 className="text-3xl">Already have a prescription?</h3>
            <p className="mt-2 text-white/75">Get your lens price in a couple of minutes — we'll confirm on WhatsApp.</p>
          </div>
          <Link to="/lens" className="btn bg-white font-semibold text-ink hover:bg-signal">Price my lenses</Link>
        </div>
      </section>
    </div>
  )
}
