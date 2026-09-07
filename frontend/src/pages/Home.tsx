import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { shop, money, assetUrl, rememberedFace, rememberedName } from '../lib/shop'
import SocialProof from '../components/SocialProof'

const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'

function ForYou() {
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

export default function Home() {
  return (
    <div>
      <section className="container-x grid gap-10 py-20 md:grid-cols-2 md:items-center">
        <div>
          <p className="label">AI Frame Finder</p>
          <h1 className="mt-3 text-5xl leading-[1.05] md:text-6xl">Find frames that fit more than your face.</h1>
          <p className="mt-5 max-w-md text-lg text-ink/70">
            Take a quick selfie. We read your facial proportions and recommend frame styles that genuinely complement you.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/frame-finder" className="btn-primary">Find My Frame</Link>
            <Link to="/store" className="btn-ghost">Shop frames</Link>
          </div>
          <SocialProof className="mt-5" />
          <a href={WA} target="_blank" rel="noreferrer" className="mt-3 inline-block text-sm text-ink/50 underline">Or chat with us on WhatsApp</a>
        </div>
        <div className="aspect-[4/5] rounded-3xl bg-gradient-to-br from-moss/20 via-clay/10 to-ink/10 border border-ink/10 grid place-items-center">
          <span className="font-display text-2xl text-ink/40">Eyewear, considered.</span>
        </div>
      </section>

      <ForYou />

      <section className="border-y border-ink/10 bg-white py-16">
        <div className="container-x">
          <p className="label">How the Frame Finder works</p>
          <div className="mt-8 grid gap-8 md:grid-cols-3">
            {[
              ['01', 'Take a selfie', 'In your browser — the photo never leaves your device.'],
              ['02', 'We read the proportions', 'Forehead, cheekbones, jaw and face length become simple ratios.'],
              ['03', 'Discover your styles', 'A shortlist of frame shapes chosen for your face, ready on WhatsApp.'],
            ].map(([n, t, d]) => (
              <div key={n}>
                <div className="font-display text-3xl text-clay">{n}</div>
                <h3 className="mt-2 text-xl">{t}</h3>
                <p className="mt-2 text-ink/65">{d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="container-x py-16">
        <div className="card flex flex-col items-start gap-4 p-8 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="label">Online store</p>
            <h3 className="mt-2 text-2xl">Shop frames, fitted with your lenses.</h3>
            <p className="mt-1 text-ink/65">Delivered across Zambia. Pay by card or mobile money.</p>
          </div>
          <Link to="/store" className="btn-primary">Shop now</Link>
        </div>
      </section>
    </div>
  )
}
