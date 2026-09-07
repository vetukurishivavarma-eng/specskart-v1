import { api } from './api'

const CART_KEY = 'specskart_cart'

export function cartToken(): string | null {
  try { return localStorage.getItem(CART_KEY) } catch { return null }
}

export function setCartToken(token: string | null) {
  try {
    if (token) localStorage.setItem(CART_KEY, token)
    else localStorage.removeItem(CART_KEY)
  } catch { /* private mode */ }
}

/** Adopt a cart token handed to us in a WhatsApp / abandoned-cart link (?c=…). Call once on load. */
export function adoptCartFromUrl() {
  const c = new URLSearchParams(window.location.search).get('c')
  if (c) setCartToken(c)
}

const FACE_KEY = 'specskart_face'
const NAME_KEY = 'specskart_name'

/** Remember the shopper's face shape after Frame Finder so the store personalises itself. */
export function rememberFace(shape: string, name?: string | null) {
  try {
    if (shape) localStorage.setItem(FACE_KEY, shape)
    if (name) localStorage.setItem(NAME_KEY, name)
  } catch { /* private mode */ }
}
export function rememberedFace(): string | null {
  try { return localStorage.getItem(FACE_KEY) } catch { return null }
}
export function rememberedName(): string | null {
  try { return localStorage.getItem(NAME_KEY) } catch { return null }
}
export function forgetFace() {
  try { localStorage.removeItem(FACE_KEY) } catch { /* */ }
}

export type SocialProof = {
  analysesThisWeek: number
  ordersThisWeek: number
  recent: { name: string; city: string; item: string; ago: string }[]
}

/** Frame Finder session token from the URL (?s=…) — lets a purchase attribute back to the lead. */
function sParam(): string {
  const s = new URLSearchParams(window.location.search).get('s')
  return s ? `?s=${encodeURIComponent(s)}` : ''
}

/** Resolve an image URL: uploaded photos come back as "/api/..." and must hit the API origin. */
export function assetUrl(url: string | null | undefined): string {
  if (!url) return ''
  if (!url.startsWith('/api/')) return url
  const base = (import.meta.env.VITE_API_BASE as string) ?? '/api'
  return base.replace(/\/api\/?$/, '') + url
}

export function money(minor: number, currency = 'ZMW'): string {
  const n = (minor / 100).toLocaleString(undefined, { minimumFractionDigits: 2 })
  return currency === 'ZMW' ? `K${n}` : `${currency} ${n}`
}

function headers(): Record<string, string> {
  const t = cartToken()
  return t ? { 'X-Cart-Token': t } : {}
}

export type ProductCard = {
  id: string; slug: string; name: string; frameCategoryCode: string | null
  colour: string | null; material: string | null; gender: string
  priceMinor: number; compareAtMinor: number | null; currency: string
  inStock: boolean; featured: boolean; imageUrl: string | null
}

export type ProductDetail = ProductCard & {
  description: string | null; stockQty: number; lensable: boolean
  images: { id: string; url: string; alt: string | null }[]
}

export type CartLine = {
  productId: string; slug: string; name: string; imageUrl: string | null
  qty: number; unitPriceMinor: number; lineTotalMinor: number; inStock: boolean; stockQty: number
  heldUntil: string | null
}

export type CartView = {
  token: string; lines: CartLine[]; promoCode: string | null
  subtotalMinor: number; discountMinor: number; shippingMinor: number; totalMinor: number
  currency: string; deliveryEta: string; holdExpiresAt: string | null
  pointsAvailable: number; pointValueMinor: number
}

export type OrderView = {
  orderNo: string; status: string; customerName: string; customerPhone: string; customerEmail: string | null
  shipAddress: string; shipCity: string
  subtotalMinor: number; discountMinor: number; shippingMinor: number; totalMinor: number; currency: string
  promoCode: string | null; paidAt: string | null; createdAt: string
  lines: { productName: string; productSlug: string | null; qty: number; unitPriceMinor: number; lineTotalMinor: number }[]
  timeline: { status: string; note: string | null; at: string }[]
  pointsEarned: number; pointsRedeemed: number; pointsBalance: number; referralCode: string | null
}

async function cartCall<T = CartView>(path: string, opts: RequestInit = {}): Promise<T> {
  const view = await api<T>(path, { ...opts, headers: { ...headers(), ...(opts.headers ?? {}) } })
  if ((view as { token?: string }).token) setCartToken((view as { token: string }).token)
  return view
}

export const shop = {
  products: (params: Record<string, string | undefined>) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v) q.set(k, v) })
    return api<ProductCard[]>(`/public/products?${q.toString()}`)
  },
  featured: () => api<ProductCard[]>('/public/products/featured'),
  product: (slug: string) => api<ProductDetail>(`/public/products/${slug}`),
  storeConfig: () => api<{ heroTitle: string; heroSubtitle: string; heroImageUrl: string | null; deliveryEta: string; currency: string; shippingFeeMinor: number; freeShippingOverMinor: number | null }>('/public/store-config'),
  socialProof: () => api<SocialProof>('/public/social-proof'),

  cart: () => cartCall(`/public/cart${sParam()}`),
  addItem: (productId: string, qty = 1) =>
    cartCall('/public/cart/items', { method: 'POST', body: JSON.stringify({ productId, qty }) }),
  setQty: (productId: string, qty: number) =>
    cartCall(`/public/cart/items/${productId}`, { method: 'PATCH', body: JSON.stringify({ qty }) }),
  applyPromo: (code: string) =>
    cartCall('/public/cart/promo', { method: 'POST', body: JSON.stringify({ code }) }),

  checkout: (body: Record<string, string | number | undefined>) =>
    cartCall<{ orderNo: string; checkoutUrl: string; totalMinor: number; currency: string }>(
      `/public/checkout${sParam()}`, { method: 'POST', body: JSON.stringify(body) }),
  order: (orderNo: string) => api<OrderView>(`/public/orders/${orderNo}`),
  confirmOrder: (orderNo: string) =>
    api<OrderView>(`/public/orders/${orderNo}/confirm`, { method: 'POST' }),
}
