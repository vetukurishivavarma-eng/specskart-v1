export const auth = {
  get token() { return localStorage.getItem('specskart_token') },
  get user() {
    const raw = localStorage.getItem('specskart_user')
    return raw ? JSON.parse(raw) as { email: string; name: string; role: string } : null
  },
  login(token: string, user: { email: string; name: string; role: string }) {
    localStorage.setItem('specskart_token', token)
    localStorage.setItem('specskart_user', JSON.stringify(user))
  },
  logout() {
    localStorage.removeItem('specskart_token')
    localStorage.removeItem('specskart_user')
  },
}
