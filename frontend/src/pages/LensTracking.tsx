import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { lens } from '../lib/lens'
import { money } from '../lib/shop'
import { WA } from '../lib/wa'

/** The collection ladder, in the order staff move it along in the POS app. */
const STEPS = ['ORDERED', 'READY', 'DELIVERED']
/**
 * Where a step's dot actually sits, as a percentage of the rail.
 *
 * The steps are equal flex cells and the dot is centred in its own cell, so with three of them
 * the dots land at 16.7/50/83.3% — not 0/50/100%. Using the latter made the filled rail
 * overshoot the dot it was supposed to stop at.
 */
const stepCentre = (i: number) => ((i + 0.5) / STEPS.length) * 100

const LABEL: Record<string, string> = {
  ORDERED: 'Order placed',
  READY: 'Ready to collect',
  DELIVERED: 'Collected',
  CANCELLED: 'Cancelled',
}

/** What the customer's "track your order" link opens: the state of their own lens order.
 *
 *  Before this page that link pointed at /lens, the configurator, so a customer following it
 *  was handed a blank form and invited to order a second pair. */
export default function LensTracking() {
  const { id } = useParams()

  const { data: q, isLoading, isError } = useQuery({
    queryKey: ['lens-track', id],
    queryFn: () => lens.status(id!),
    // Someone watching this page is waiting on the shop to move it along; a slow poll costs
    // nothing and saves them refreshing. Stops once there is nowhere left to go.
    refetchInterval: (r) => (['DELIVERED', 'CANCELLED'].includes(r.state.data?.fulfilment ?? '') ? false : 30_000),
  })

  if (isLoading) return <div className="container-x py-16 text-ink/50">Loading your order…</div>
  if (isError || !q) return (
    <div className="container-x py-16">
      <p className="text-ink/60">We couldn’t find that order.</p>
      <a href={WA} className="mt-3 inline-block underline">Message us on WhatsApp</a>
    </div>
  )

  const ref = 'LENS-' + (q.id ?? '').slice(0, 8).toUpperCase()
  const stage = q.fulfilment ?? 'ORDERED'
  const stepIdx = STEPS.indexOf(stage)
  const delivered = stage === 'DELIVERED'

  return (
    <div className="container-x max-w-2xl py-12">
      <div className="animate-track-rise">
        <p className="label">Order {ref}</p>
        <h1 className="mt-2 text-3xl">{LABEL[stage] ?? stage}</h1>

        {stage === 'CANCELLED' ? (
          <p className="mt-3 text-ink/60">
            This order was cancelled.{q.paid ? ' Your online payment will be refunded.' : ''} Questions?{' '}
            <a href={WA} className="underline">Message us on WhatsApp</a>
          </p>
        ) : delivered ? (
          <p className="mt-3 text-ink/60">
            These lenses were handed over. If anything isn’t right with the fit, tell us and we’ll
            sort it out.
          </p>
        ) : stage === 'READY' ? (
          <p className="mt-3 text-ink/60">
            Your lenses are waiting at the shop. Come in and collect them, or send someone to pick
            them up for you — bring this reference.
          </p>
        ) : (
          <p className="mt-3 text-ink/60">This page updates on its own — no need to refresh.</p>
        )}
      </div>

      {/* The ladder is a progress bar while there is progress left to show. Once collected it
          is just three ticks, so the end state says so in words instead. */}
      {!delivered && stepIdx >= 0 && (
        <div className="animate-track-rise relative mt-14" style={{ '--d': '90ms' } as React.CSSProperties}>
          {/* The rail sits behind the dots and fills to the step the order is on. */}
          <div className="absolute left-0 right-0 top-1.5 h-px bg-ink/15" aria-hidden />
          <div
            className="animate-track-fill absolute left-0 top-1.5 h-px bg-ink"
            style={{ width: `${stepCentre(stepIdx)}%` }}
            aria-hidden
          />
          <ol className="relative flex justify-between">
            {STEPS.map((s, i) => (
              <li key={s} className="flex flex-1 flex-col items-center text-center">
                <span
                  className={`h-3 w-3 rounded-full ${i <= stepIdx ? 'bg-ink' : 'bg-ink/20'} ${
                    i === stepIdx ? 'animate-track-pulse' : ''
                  }`}
                />
                <span className={`mt-2 text-xs ${i <= stepIdx ? 'text-ink' : 'text-ink/40'}`}>
                  {LABEL[s]}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}

      <div className="card animate-track-rise mt-8 p-5" style={{ '--d': '180ms' } as React.CSSProperties}>
        <h2 className="text-lg">Your lenses</h2>
        <dl className="mt-3 space-y-1 text-sm">
          <div className="flex justify-between">
            <dt className="text-ink/55">Type</dt>
            <dd>
              {q.lensType === 'PHOTOCHROMATIC' ? 'Photochromatic' : 'Clear'}
              {q.blueBlock ? ' + blue-light block' : ''}
            </dd>
          </div>
          {q.lensStructure && (
            <div className="flex justify-between">
              <dt className="text-ink/55">Structure</dt>
              <dd>{q.lensStructure === 'PROGRESSIVE' ? 'Progressive' : 'Bifocal'}</dd>
            </div>
          )}
          {q.priceMinor != null && (
            <div className="flex justify-between border-t border-ink/10 pt-2">
              <dt className="text-ink/55">Total</dt>
              <dd>
                {money(q.priceMinor, q.currency ?? 'ZMW')}
                {q.paid ? '' : ' — payable when you collect'}
              </dd>
            </div>
          )}
        </dl>
      </div>

      <div className="animate-track-rise mt-8 flex flex-wrap gap-3" style={{ '--d': '340ms' } as React.CSSProperties}>
        <a href={WA} className="btn-primary">Message us on WhatsApp</a>
        {delivered && <Link to="/lens" className="btn-ghost">Order another pair</Link>}
      </div>
    </div>
  )
}
