import { describe, it, expect, vi, beforeEach } from 'vitest'
import { api, ApiError } from './api'

describe('api', () => {
  beforeEach(() => { localStorage.clear(); vi.restoreAllMocks() })

  it('parses a JSON body on success', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })))
    await expect(api('/x')).resolves.toEqual({ ok: true })
  })

  it('throws ApiError with the backend code on failure', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ code: 'FRAME_SESSION_EXPIRED', message: 'expired' }), { status: 410 })))
    await expect(api('/x')).rejects.toMatchObject({ code: 'FRAME_SESSION_EXPIRED', status: 410 })
  })

  it('clears the token on a 401 to an authed call', async () => {
    localStorage.setItem('specskart_token', 't')
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 401 })))
    await expect(api('/x', { auth: true })).rejects.toBeInstanceOf(ApiError)
    expect(localStorage.getItem('specskart_token')).toBeNull()
  })

  it('attaches the bearer header only when auth is requested', async () => {
    localStorage.setItem('specskart_token', 'abc')
    const spy = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', spy)
    await api('/x', { auth: true })
    const headers = (spy.mock.calls[0][1] as RequestInit).headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer abc')
  })
})
