import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Store from './Store'
import { money } from '../lib/shop'

const cfg = { heroTitle: 'Frames for your face', heroSubtitle: 'Shop the shapes made for you.', heroImageUrl: null, deliveryEta: '2-4 days', currency: 'ZMW', shippingFeeMinor: 0, freeShippingOverMinor: null }
const products = [
  { id: '1', slug: 'aria', name: 'Aria Wayfarer', frameCategoryCode: 'WAYFARER', colour: 'Black', material: 'Acetate', gender: 'UNISEX', priceMinor: 78000, compareAtMinor: null, currency: 'ZMW', inStock: true, featured: true, imageUrl: null },
]

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    const u = String(url)
    const body = u.includes('store-config') ? cfg
      : u.includes('social-proof') ? { analysesThisWeek: 0, ordersThisWeek: 0, recent: [] }
      : products
    return Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve(JSON.stringify(body)) } as Response)
  }))
})

function renderStore() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/store']}><Store /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Store page', () => {
  it('renders a live product grid with prices', async () => {
    renderStore()
    expect(await screen.findByText('Aria Wayfarer')).toBeInTheDocument()
    expect(screen.getByText(money(78000, 'ZMW'))).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText(/coming soon/i)).not.toBeInTheDocument())
  })

  it('money() formats Kwacha', () => {
    expect(money(78000, 'ZMW')).toBe('K780.00')
    expect(money(150000, 'USD')).toBe('USD 1,500.00')
  })
})
