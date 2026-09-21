import { api } from './api'

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
  deliveryName: string | null
  deliveryAddress: string | null
  deliveryArea: string | null
  deliveryLandmark: string | null
  fulfilment: 'ORDERED' | 'PACKED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED' | null
  paid: boolean
}

export type Delivery = { name?: string; address: string; area: string; landmark?: string }

export type LensDetails = {
  customerName?: string; age?: number; gender?: string
  sphRight?: number; sphLeft?: number; cylRight?: number; cylLeft?: number
  axisRight?: number; axisLeft?: number
  addPower?: number; lensStructure?: string
}

export const lens = {
  start: (phone: string, lensType: string, blueBlock: boolean) =>
    api<{ inquiryId: string }>('/public/lens/start', {
      method: 'POST', body: JSON.stringify({ phone, lensType, blueBlock }),
    }),
  status: (id: string) => api<LensInquiry>(`/public/lens/${id}`),
  verify: (token: string) => api<{ verified: boolean }>(`/public/lens/verify/${token}`, { method: 'POST' }),
  update: (id: string, details: LensDetails) =>
    api<LensInquiry>(`/public/lens/${id}`, { method: 'PATCH', body: JSON.stringify(details) }),
  delivery: (id: string, d: Delivery) =>
    api<LensInquiry>(`/public/lens/${id}/delivery`, { method: 'PUT', body: JSON.stringify(d) }),
  quote: (id: string) => api<LensInquiry>(`/public/lens/${id}/quote`, { method: 'POST' }),
  pay: (id: string) =>
    api<{ checkoutUrl: string; amountMinor: number; currency: string }>(
      `/public/lens/${id}/pay`, { method: 'POST' }),
  submit: (id: string) => api<LensInquiry>(`/public/lens/${id}/submit`, { method: 'POST' }),
}
