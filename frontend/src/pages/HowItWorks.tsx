export default function HowItWorks() {
  return (
    <div className="container-x max-w-2xl py-16">
      <p className="label">How it works</p>
      <h1 className="mt-3 text-4xl">A store consultation, on your phone.</h1>
      <div className="mt-10 space-y-8 text-ink/75">
        <Step n="1" t="You arrive from an ad or a WhatsApp message">
          Tap the link a campaign or our WhatsApp assistant sends you. No sign-up, no password, no OTP —
          the link already knows who you are.
        </Step>
        <Step n="2" t="You give consent for photo processing">
          We explain exactly what the photo is used for before the camera turns on. You can withdraw at any time.
        </Step>
        <Step n="3" t="Your face becomes a set of ratios">
          Entirely in your browser, we measure face width vs. length, forehead, cheekbones and jaw. The image
          itself is never uploaded.
        </Step>
        <Step n="4" t="We estimate your face shape and matching frames">
          You get a shortlist — e.g. “closest to Oval → Rectangle, Wayfarer, Geometric”. It's an estimate, not a verdict.
        </Step>
        <Step n="5" t="Your results follow you to WhatsApp">
          Ask us to send the results and an expert can pick up the conversation from there.
        </Step>
      </div>
    </div>
  )
}

function Step({ n, t, children }: { n: string; t: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-5">
      <div className="font-display text-2xl text-clay">{n}</div>
      <div>
        <h3 className="text-xl text-ink">{t}</h3>
        <p className="mt-1">{children}</p>
      </div>
    </div>
  )
}
