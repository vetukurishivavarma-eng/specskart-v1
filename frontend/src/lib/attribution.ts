/**
 * Which ad sent this visitor, read off the URL they landed on.
 *
 * Meta, Google and TikTok each tag their outbound clicks (`fbclid`, `gclid`, `ttclid`) and
 * anyone can add `utm_*`. The backend already has a provider per platform that reads exactly
 * these keys — until now nothing ever handed them any, so every website lead was recorded as
 * an untraceable "WEBSITE".
 *
 * First touch wins and is kept: the click that brought someone to the site is rarely the visit
 * where they finally order, so the params are stored on arrival and replayed later. The backend
 * applies attribution only when it creates the lead, so replaying a stale one is harmless.
 */
const KEY = 'sk_attribution'

/** The only params worth keeping. Anything else on the URL is noise or someone else's tracking. */
const PARAMS = [
  'fbclid', 'gclid', 'ttclid', 'ctwa_clid',
  'utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term',
]

export type Attribution = Record<string, string>

/**
 * Call once per page load, before anything rewrites the URL. Records the landing params if this
 * visit carries any and nothing was stored before.
 */
export function captureAttribution() {
  try {
    if (localStorage.getItem(KEY)) return // first touch already recorded
    const q = new URLSearchParams(window.location.search)
    const found: Attribution = {}
    for (const p of PARAMS) {
      const v = q.get(p)
      if (v) found[p] = v
    }
    if (!Object.keys(found).length) return // organic visit — leave it unattributed
    found.landing_page = window.location.pathname + window.location.search
    if (document.referrer) found.referrer = document.referrer
    localStorage.setItem(KEY, JSON.stringify(found))
  } catch {
    // Private mode, or storage disabled. An unattributed lead is worth more than a crash.
  }
}

/** What was captured on arrival, or undefined for an organic visitor. */
export function attribution(): Attribution | undefined {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Attribution) : undefined
  } catch {
    return undefined
  }
}
