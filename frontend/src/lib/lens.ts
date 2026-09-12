import { api } from './api'

export type LensInquiry = {
  id: string
  status: 'DRAFT' | 'VERIFIED' | 'PRICED' | 'SUBMITTED'
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
}

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
  quote: (id: string) => api<LensInquiry>(`/public/lens/${id}/quote`, { method: 'POST' }),
  submit: (id: string) => api<LensInquiry>(`/public/lens/${id}/submit`, { method: 'POST' }),
}
