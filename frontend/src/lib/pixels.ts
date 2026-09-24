/**
 * Meta and TikTok browser pixels — PageView only.
 *
 * Deliberately narrow. The pixel is here to build retargeting audiences (people who looked at
 * the lens page and didn't order), which needs nothing more than a page view. Conversions are
 * reported server-side by AdEventService, where they can't be blocked and where they actually
 * happen — the number is verified in WhatsApp's browser, and the sale is billed in the POS.
 *
 * Because the two never send the same event, there is no deduplication to get wrong. If a
 * conversion is ever added here, it must carry the same event_id the server uses or the
 * platform will count it twice.
 *
 * Both are off unless their id is configured, so local and preview builds send nothing.
 */
const META_ID = import.meta.env.VITE_META_PIXEL_ID as string | undefined
const TIKTOK_ID = import.meta.env.VITE_TIKTOK_PIXEL_ID as string | undefined

/** What Meta's own snippet builds: a callable that queues until fbevents.js replaces it. */
type Fbq = ((...args: unknown[]) => void) & {
  queue: unknown[][]
  loaded: boolean
  version: string
  callMethod?: (...args: unknown[]) => void
}

declare global {
  interface Window {
    fbq?: Fbq
    _fbq?: Fbq
    ttq?: { page: () => void }
    TiktokAnalyticsObject?: string
  }
}

function script(src: string) {
  const s = document.createElement('script')
  s.async = true
  s.src = src
  document.head.appendChild(s)
}

/** Meta's snippet, transcribed rather than pasted — the official one is a minified one-liner. */
function initMeta(id: string) {
  const queue: unknown[][] = []
  const fbq = ((...args: unknown[]) => {
    if (fbq.callMethod) fbq.callMethod(...args)
    else queue.push(args)
  }) as Fbq
  fbq.queue = queue
  fbq.loaded = true
  fbq.version = '2.0'
  window.fbq = fbq
  window._fbq = fbq
  script('https://connect.facebook.net/en_US/fbevents.js')
  fbq('init', id)
  fbq('track', 'PageView')
}

/**
 * TikTok's snippet, same idea. The queue matters here: `page()` is called before the SDK has
 * loaded, so the stub has to record the call for the real SDK to replay. A no-op stub silently
 * drops every page view.
 */
function initTikTok(id: string) {
  const METHODS = ['page', 'track', 'identify', 'instances', 'debug', 'on', 'off', 'once',
    'ready', 'alias', 'group', 'enableCookie', 'disableCookie']
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const ttq: any = []
  for (const m of METHODS) {
    ttq[m] = (...args: unknown[]) => { ttq.push([m, ...args]) }
  }
  ttq._i = { [id]: [] }
  ttq._t = { [id]: +new Date() }
  ttq._o = { [id]: {} }
  window.TiktokAnalyticsObject = 'ttq'
  window.ttq = ttq
  script(`https://analytics.tiktok.com/i18n/pixel/events.js?sdkid=${id}&lib=ttq`)
  ttq.page()
}

/** Call once, on load. A missing id means that platform simply isn't set up yet. */
export function initPixels() {
  try {
    if (META_ID) initMeta(META_ID)
    if (TIKTOK_ID) initTikTok(TIKTOK_ID)
  } catch {
    // A pixel is never worth breaking the page over.
  }
}
