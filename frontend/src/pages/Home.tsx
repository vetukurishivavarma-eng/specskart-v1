import { Link } from 'react-router-dom'

const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'

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
            <a href={WA} target="_blank" rel="noreferrer" className="btn-ghost">Chat on WhatsApp</a>
          </div>
        </div>
        <div className="aspect-[4/5] rounded-3xl bg-gradient-to-br from-moss/20 via-clay/10 to-ink/10 border border-ink/10 grid place-items-center">
          <span className="font-display text-2xl text-ink/40">Eyewear, considered.</span>
        </div>
      </section>

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
            <h3 className="mt-2 text-2xl">Frames, lenses & fitting — coming soon.</h3>
            <p className="mt-1 text-ink/65">Frame + lens combos with fitting benefits arrive when online ordering launches.</p>
          </div>
          <Link to="/store" className="btn-ghost">Preview the store</Link>
        </div>
      </section>
    </div>
  )
}
