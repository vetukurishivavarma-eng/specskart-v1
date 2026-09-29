const BASE = import.meta.env.VITE_API_BASE ?? '/api'

export class ApiError extends Error {
  code: string
  status: number
  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

function authHeader(): Record<string, string> {
  const t = localStorage.getItem('specskart_token')
  return t ? { Authorization: `Bearer ${t}` } : {}
}

export async function api<T>(path: string, opts: RequestInit & { auth?: boolean } = {}): Promise<T> {
  const { auth, ...rest } = opts
  // A FormData body needs the browser to set its own multipart Content-Type (with boundary) —
  // forcing application/json here would send the file upload with the wrong header and it'd
  // never parse server-side.
  const isFormData = typeof FormData !== 'undefined' && rest.body instanceof FormData
  const res = await fetch(`${BASE}${path}`, {
    ...rest,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(auth ? authHeader() : {}),
      ...(rest.headers ?? {}),
    },
  })
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) {
    const code = body?.code ?? 'ERROR'
    const msg = body?.message ?? res.statusText
    if (res.status === 401 && auth) {
      // The 12h token expired: drop the stale session and send admins back to sign in,
      // instead of leaving forms on screen that silently fail to save.
      localStorage.removeItem('specskart_token')
      localStorage.removeItem('specskart_user')
      if (location.pathname.startsWith('/admin') && location.pathname !== '/admin/login') {
        location.assign('/admin/login')
      }
    }
    throw new ApiError(code, msg, res.status)
  }
  return body as T
}
