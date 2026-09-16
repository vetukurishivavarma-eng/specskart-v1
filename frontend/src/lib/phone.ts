export const DEFAULT_COUNTRY_CODE = '+260'

/** Digits only, '+'-prefixed — the shape the backend's PhoneNumbers.normalize()
 *  treats as "already has a country code" and passes through untouched. Without
 *  the '+', a foreign number is sent to Meta as typed: accepted, never delivered. */
export const cleanCc = (v: string) => '+' + v.replace(/\D/g, '').slice(0, 4)
