export default function Store() {
  const items = [
    ['Frames', 'Acetate and metal frames across every shape in the Frame Finder.'],
    ['Lenses', 'Single-vision, progressive and blue-light, cut to your prescription.'],
    ['Frames + Lenses', 'Buy them together — fitting benefits apply on eligible combos.'],
  ]
  return (
    <div className="container-x py-16">
      <p className="label">Online store</p>
      <h1 className="mt-3 text-4xl">Coming soon.</h1>
      <p className="mt-4 max-w-xl text-ink/70">
        We're building online ordering for frames, lenses and frame + lens combos — including free fitting on
        eligible combos. For now, browse with our expert on WhatsApp.
      </p>
      <div className="mt-10 grid gap-5 md:grid-cols-3">
        {items.map(([t, d]) => (
          <div key={t} className="card p-6">
            <div className="label">Coming soon</div>
            <h3 className="mt-2 text-2xl">{t}</h3>
            <p className="mt-2 text-sm text-ink/65">{d}</p>
            <button disabled className="btn mt-6 w-full cursor-not-allowed border border-ink/15 text-ink/40">Coming Soon</button>
          </div>
        ))}
      </div>
    </div>
  )
}
