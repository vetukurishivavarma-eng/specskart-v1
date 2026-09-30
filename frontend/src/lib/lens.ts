import { api } from './api'
import { attribution } from './attribution'

export type LensInquiry = {
  id: string
  status: 'DRAFT' | 'VERIFIED' | 'PRICED' | 'SUBMITTED' | 'SOLD'
  verified: boolean
  lensType: 'CLEAR' | 'PHOTOCHROMATIC' | null
  blueBlock: boolean
  customerName: string | null
  age: number | null
  gender: string | null
  sphRight: number | null; sphLeft: number | null
  cylRight: number | null; cylLeft: number | null
  axisRight: number | null; axisLeft: number | null
  addPower: number | null
  lensStructure: 'BIFOCAL' | 'PROGRESSIVE' | null
  specialAxis: boolean
  priceMinor: number | null
  currency: string | null
  fulfilment: 'ORDERED' | 'READY' | 'DELIVERED' | 'CANCELLED' | null
  shopName: string | null
  shopAddress: string | null
  shopMapsUrl: string | null
  paid: boolean
}

export type LensDetails = {
  customerName?: string; age?: number; gender?: string
  sphRight?: number; sphLeft?: number; cylRight?: number; cylLeft?: number
  axisRight?: number; axisLeft?: number
  addPower?: number; lensStructure?: string
}

/** The signed personal link the WhatsApp bot sends: /lens?l=&exp=&sig= */
export type PersonalLink = { l: string; exp: number; sig: string }

export function personalLinkFrom(params: URLSearchParams): PersonalLink | null {
  const l = params.get('l'), exp = params.get('exp'), sig = params.get('sig')
  return l && exp && sig ? { l, exp: Number(exp), sig } : null
}

export const lens = {
  // `link` stands in for the phone: the number is already known from the link.
  start: (phone: string | null, lensType: string, blueBlock: boolean, link?: PersonalLink | null) =>
    api<{ inquiryId: string }>('/public/lens/start', {
      // The ad that brought them here, so the lead this creates can be traced back to it.
      method: 'POST',
      body: JSON.stringify({ phone, lensType, blueBlock, attribution: attribution(), link: link ?? undefined }),
    }),
  openLink: (link: PersonalLink) =>
    api<{ name: string | null; maskedNumber: string }>('/public/lens/link', {
      method: 'POST', body: JSON.stringify(link),
    }),
  status: (id: string) => api<LensInquiry>(`/public/lens/${id}`),
  verify: (token: string) => api<{ verified: boolean }>(`/public/lens/verify/${token}`, { method: 'POST' }),
  update: (id: string, details: LensDetails) =>
    api<LensInquiry>(`/public/lens/${id}`, { method: 'PATCH', body: JSON.stringify(details) }),
  quote: (id: string) => api<LensInquiry>(`/public/lens/${id}/quote`, { method: 'POST' }),
  pay: (id: string) =>
    api<{ checkoutUrl: string; amountMinor: number; currency: string }>(
      `/public/lens/${id}/pay`, { method: 'POST' }),
  submit: (id: string) => api<LensInquiry>(`/public/lens/${id}/submit`, { method: 'POST' }),
}
