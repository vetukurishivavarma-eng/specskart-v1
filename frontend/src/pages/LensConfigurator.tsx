import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { lens, type LensDetails, type LensInquiry } from '../lib/lens'
import { money } from '../lib/shop'

const STORAGE_KEY = 'specskart_lens_inquiry'
const COUNTRY_CODE = '+260'
const DIOPTERS = Array.from({ length: 25 }, (_, i) => (i * 0.25).toFixed(2)) // 0.00 .. 6.00
const ADDS = Array.from({ length: 17 }, (_, i) => (1 + i * 0.25).toFixed(2)) // 1.00 .. 5.00

type Sign = '+' | '-'
function signed(sign: Sign, magnitude: string): number {
  const m = parseFloat(magnitude)
  return sign === '-' ? -m : m
}
function splitSigned(v: number | null): { sign: Sign; magnitude: string } {
  if (v == null) return { sign: '+', magnitude: '0.00' }
  return { sign: v < 0 ? '-' : '+', magnitude: Math.abs(v).toFixed(2) }
}

export default function LensConfigurator() {
  const [params] = useSearchParams()
  const [q, setQ] = useState<LensInquiry | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // step 1 (pre-verification) local picks
  const [lensType, setLensType] = useState<'CLEAR' | 'PHOTOCHROMATIC' | ''>('')
  const [blueBlock, setBlueBlock] = useState(false)
  const [phone, setPhone] = useState('')
  const [sending, setSending] = useState(false)

  const pollRef = useRef<number | null>(null)

  // Resume an inquiry: ?resume= (the follow-up nudge link) beats whatever's in local storage.
  useEffect(() => {
    const resumeId = params.get('resume') || localStorage.getItem(STORAGE_KEY)
    if (!resumeId) { setLoading(false); return }
    lens.status(resumeId).then((v) => {
      localStorage.setItem(STORAGE_KEY, v.id)
      setQ(v)
      if (!v.verified) startPolling(v.id)
    }).catch(() => localStorage.removeItem(STORAGE_KEY)).finally(() => setLoading(false))
    return () => stopPolling()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function startPolling(id: string) {
    stopPolling()
    pollRef.current = window.setInterval(async () => {
      try {
        const v = await lens.status(id)
        if (v.verified) { stopPolling(); setQ(v) }
      } catch { /* keep polling */ }
    }, 3000)
  }
  function stopPolling() {
    if (pollRef.current) { window.clearInterval(pollRef.current); pollRef.current = null }
  }

  async function sendVerification() {
    const digits = phone.replace(/\D/g, '')
    if (digits.length < 8 || !lensType) return
    setSending(true)
    setError(null)
    try {
      const { inquiryId } = await lens.start(COUNTRY_CODE + digits, lensType, blueBlock)
      localStorage.setItem(STORAGE_KEY, inquiryId)
      const v = await lens.status(inquiryId)
      setQ(v)
      startPolling(inquiryId)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSending(false)
    }
  }

  async function patch(details: LensDetails) {
    if (!q) return
    const v = await lens.update(q.id, details)
    setQ(v)
  }

  if (loading) return <div className="container-x py-16 text-ink/50">Loading…</div>

  return (
    <div className="container-x max-w-2xl py-12">
      <p className="label">Lenses</p>
      <h1 className="mt-2 text-3xl">Describe your lens specs</h1>
      <p className="mt-2 flex items-center gap-2 text-sm text-moss">🔒 Your browsing is safe.</p>

      {!q && (
        <div className="mt-8 space-y-6">
          <section>
            <h2 className="text-lg">1. Choose your lens</h2>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <LensOption
                selected={lensType === 'CLEAR'} onClick={() => setLensType('CLEAR')}
                title="Clear" desc="Standard everyday lenses."
                swatch={<div className="h-16 w-16 rounded-full border-2 border-ink/20 bg-white/40" />}
              />
              <LensOption
                selected={lensType === 'PHOTOCHROMATIC'} onClick={() => setLensType('PHOTOCHROMATIC')}
                title="Photochromatic" desc="Clear indoors, tints in sunlight."
                swatch={<div className="h-16 w-16 rounded-full border-2 border-ink/20"
                  style={{ background: 'linear-gradient(135deg, rgba(255,255,255,.4), rgba(20,17,15,.75))' }} />}
              />
            </div>
            <label className="mt-4 flex items-center gap-2 text-sm">
              <input type="checkbox" checked={blueBlock} onChange={(e) => setBlueBlock(e.target.checked)} />
              Add blue-light block (screen protection)
            </label>
          </section>

          {lensType && (
            <section>
              <h2 className="text-lg">2. Verify your WhatsApp number</h2>
              <p className="mt-1 text-xs text-ink/50">We'll send a link to confirm it's really you — tap it to continue.</p>
              <div className="mt-3 flex gap-2">
                <span className="flex items-center rounded-lg border border-ink/20 px-3 py-2 text-sm text-ink/60">{COUNTRY_CODE}</span>
                <input value={phone} onChange={(e) => setPhone(e.target.value)}
                  onBlur={() => phone.replace(/\D/g, '').length >= 8 && !sending && sendVerification()}
                  placeholder="977123456" inputMode="tel"
                  className="flex-1 rounded-lg border border-ink/20 px-3 py-2 text-sm" />
              </div>
              {sending && <p className="mt-2 text-xs text-ink/50">Sending your verification link…</p>}
              {error && <p className="mt-2 text-sm text-clay">{error}</p>}
            </section>
          )}
        </div>
      )}

      {q && !q.verified && (
        <div className="mt-8 card p-5 text-sm">
          <p>We sent a WhatsApp message to <b>{COUNTRY_CODE}{phone.replace(/\D/g, '')}</b>.</p>
          <p className="mt-2 text-ink/60">Tap the link in that message to verify — this page updates automatically, no need to refresh.</p>
          <p className="mt-3 text-xs text-ink/45">Please verify your WhatsApp number before continuing.</p>
        </div>
      )}

      {q && q.verified && q.status !== 'SUBMITTED' && (
        <DetailsForm q={q} onPatch={patch} onQuoted={setQ} />
      )}

      {q && q.status === 'SUBMITTED' && (
        <div className="mt-8 card p-5">
          <h2 className="text-lg text-moss">Thanks — we've got it! ✅</h2>
          <p className="mt-2 text-sm text-ink/60">
            We'll message you on WhatsApp to confirm your prescription and arrange delivery.
          </p>
        </div>
      )}
    </div>
  )
}

function LensOption({ selected, onClick, title, desc, swatch }: {
  selected: boolean; onClick: () => void; title: string; desc: string; swatch: React.ReactNode
}) {
  return (
    <button onClick={onClick}
      className={`flex flex-col items-center gap-2 rounded-xl border p-4 text-center ${selected ? 'border-ink bg-ink/5' : 'border-ink/20'}`}>
      {swatch}
      <span className="font-medium">{title}</span>
      <span className="text-xs text-ink/50">{desc}</span>
    </button>
  )
}

function DetailsForm({ q, onPatch, onQuoted }: {
  q: LensInquiry; onPatch: (d: LensDetails) => Promise<void>; onQuoted: (v: LensInquiry) => void
}) {
  const nameRef = useRef<HTMLInputElement>(null)
  const ageRef = useRef<HTMLInputElement>(null)
  const genderRef = useRef<HTMLSelectElement>(null)
  const [sphR, setSphR] = useState(splitSigned(q.sphRight))
  const [sphL, setSphL] = useState(splitSigned(q.sphLeft))
  const [cylR, setCylR] = useState(splitSigned(q.cylRight))
  const [cylL, setCylL] = useState(splitSigned(q.cylLeft))
  const [axisR, setAxisR] = useState(q.axisRight?.toString() ?? '')
  const [axisL, setAxisL] = useState(q.axisLeft?.toString() ?? '')
  const [addPower, setAddPower] = useState(q.addPower?.toString() ?? '')
  const [structure, setStructure] = useState<'BIFOCAL' | 'PROGRESSIVE'>(q.lensStructure ?? 'BIFOCAL')
  const [quoting, setQuoting] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const needsAxisR = parseFloat(cylR.magnitude) > 0
  const needsAxisL = parseFloat(cylL.magnitude) > 0
  const needsStructure = addPower !== '' && parseFloat(addPower) > 0

  async function checkPrice() {
    setQuoting(true)
    try {
      await onPatch({
        customerName: nameRef.current?.value || undefined,
        age: Number(ageRef.current?.value) || undefined,
        gender: genderRef.current?.value || undefined,
        sphRight: signed(sphR.sign, sphR.magnitude), sphLeft: signed(sphL.sign, sphL.magnitude),
        cylRight: signed(cylR.sign, cylR.magnitude), cylLeft: signed(cylL.sign, cylL.magnitude),
        axisRight: needsAxisR && axisR ? Number(axisR) : undefined,
        axisLeft: needsAxisL && axisL ? Number(axisL) : undefined,
        addPower: addPower ? Number(addPower) : undefined,
        lensStructure: needsStructure ? structure : undefined,
      })
      const v = await lens.quote(q.id)
      onQuoted(v)
    } finally {
      setQuoting(false)
    }
  }

  async function buyNow() {
    setSubmitting(true)
    try { onQuoted(await lens.submit(q.id)) } finally { setSubmitting(false) }
  }

  return (
    <div className="mt-8 space-y-6">
      <section className="grid grid-cols-2 gap-3">
        <Field inputRef={nameRef} label="Full name" defaultValue={q.customerName ?? ''} />
        <Field inputRef={ageRef} label="Age" type="number" defaultValue={q.age?.toString() ?? ''} />
        <label className="block">
          <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Gender</span>
          <select ref={genderRef} defaultValue={q.gender ?? ''} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm">
            <option value="">Select</option>
            <option value="MALE">Male</option>
            <option value="FEMALE">Female</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
      </section>

      <section>
        <h2 className="text-lg">Prescription</h2>
        <div className="mt-3 grid grid-cols-2 gap-4">
          <DiopterField label="Sph — Right (OD)" value={sphR} onChange={setSphR} />
          <DiopterField label="Sph — Left (OS)" value={sphL} onChange={setSphL} />
          <DiopterField label="Cyl — Right (OD)" value={cylR} onChange={setCylR} />
          <DiopterField label="Cyl — Left (OS)" value={cylL} onChange={setCylL} />
          {needsAxisR && <NumberField label="Axis — Right" value={axisR} onChange={setAxisR} max={180} />}
          {needsAxisL && <NumberField label="Axis — Left" value={axisL} onChange={setAxisL} max={180} />}
        </div>
        {q.specialAxis && (
          <p className="mt-3 rounded-lg bg-clay/10 p-3 text-xs text-clay">
            This axis needs a special (non-stock) lens — we'll confirm feasibility and pricing with you on WhatsApp.
          </p>
        )}

        <div className="mt-4">
          <label className="block max-w-xs">
            <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Add (near-vision power)</span>
            <select value={addPower} onChange={(e) => setAddPower(e.target.value)}
              className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm">
              <option value="">None</option>
              {ADDS.map((v) => <option key={v} value={v}>+{v}</option>)}
            </select>
          </label>
          {needsStructure && (
            <label className="mt-3 block max-w-xs">
              <span className="text-xs font-medium uppercase tracking-widest text-ink/50">Lens structure</span>
              <select value={structure} onChange={(e) => setStructure(e.target.value as 'BIFOCAL' | 'PROGRESSIVE')}
                className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm">
                <option value="BIFOCAL">Bifocal</option>
                <option value="PROGRESSIVE">Progressive</option>
              </select>
            </label>
          )}
        </div>
      </section>

      {q.status === 'DRAFT' || q.status === 'VERIFIED' ? (
        <button className="btn-primary" disabled={quoting} onClick={checkPrice}>
          {quoting ? 'Checking…' : 'Check price'}
        </button>
      ) : (
        <div className="card p-5">
          <p className="text-lg font-medium">{money(q.priceMinor ?? 0, q.currency ?? 'ZMW')}</p>
          <div className="mt-3 flex gap-3">
            <button className="btn-ghost" onClick={checkPrice} disabled={quoting}>Re-check price</button>
            <button className="btn-primary" onClick={buyNow} disabled={submitting}>{submitting ? 'Placing…' : 'Buy now'}</button>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ inputRef, label, type = 'text', defaultValue }: {
  inputRef: React.RefObject<HTMLInputElement | null>; label: string; type?: string; defaultValue: string
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium uppercase tracking-widest text-ink/50">{label}</span>
      <input ref={inputRef} type={type} defaultValue={defaultValue} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
    </label>
  )
}

function NumberField({ label, value, onChange, max }: { label: string; value: string; onChange: (v: string) => void; max: number }) {
  return (
    <label className="block">
      <span className="text-xs font-medium uppercase tracking-widest text-ink/50">{label}</span>
      <input type="number" min={0} max={max} value={value} onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
    </label>
  )
}

function DiopterField({ label, value, onChange }: {
  label: string; value: { sign: Sign; magnitude: string }; onChange: (v: { sign: Sign; magnitude: string }) => void
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium uppercase tracking-widest text-ink/50">{label}</span>
      <div className="mt-1 flex gap-1">
        <select value={value.sign} onChange={(e) => onChange({ ...value, sign: e.target.value as Sign })}
          className="w-16 rounded-lg border border-ink/20 px-2 py-2 text-sm">
          <option value="+">+</option>
          <option value="-">−</option>
        </select>
        <select value={value.magnitude} onChange={(e) => onChange({ ...value, magnitude: e.target.value })}
          className="flex-1 rounded-lg border border-ink/20 px-2 py-2 text-sm">
          {DIOPTERS.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
      </div>
    </label>
  )
}
