const WA = import.meta.env.VITE_WA_LINK ?? 'https://wa.me/260000000000'
export default function Contact() {
  return (
    <div className="container-x max-w-xl py-16">
      <p className="label">Contact</p>
      <h1 className="mt-3 text-4xl">Talk to an eyewear expert.</h1>
      <p className="mt-4 text-ink/70">
        The fastest way to reach us is WhatsApp — our assistant answers immediately and hands you to a person when you need one.
      </p>
      <a href={WA} target="_blank" rel="noreferrer" className="btn-primary mt-8">Open WhatsApp</a>
    </div>
  )
}
