/**
 * The "message us on WhatsApp" link, in one place.
 *
 * Seven pages each carried their own copy of this with a placeholder number as the fallback,
 * and because VITE_WA_LINK was never set on the static site, every one of them shipped to
 * production pointing at +260 000 000 000 — a number that does not exist. Nobody notices a
 * dead link until a customer taps it.
 *
 * The fallback is now the real business number, so a missing env var degrades to something
 * that works rather than something that silently doesn't. VITE_WA_LINK still wins, which is
 * what a staging or demo deployment needs.
 *
 * Vite inlines this at build time, so changing the variable on Render requires a rebuild.
 */
export const WA_NUMBER = '260972809599'

export const WA = import.meta.env.VITE_WA_LINK ?? `https://wa.me/${WA_NUMBER}`
