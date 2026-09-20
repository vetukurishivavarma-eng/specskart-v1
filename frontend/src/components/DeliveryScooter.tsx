/** The Specskart mark, lifted from public/favicon.svg — drawn small on the delivery box. */
const LOGO_PATH =
  'M25.946 44.938c-.664.845-2.021.375-2.021-.698V33.937a2.26 2.26 0 0 0-2.262-2.262H10.287' +
  'c-.92 0-1.456-1.04-.92-1.788l7.48-10.471c1.07-1.497 0-3.578-1.842-3.578H1.237c-.92 0-1.456-1.04-.92-1.788' +
  'L10.013.474c.214-.297.556-.474.92-.474h28.894c.92 0 1.456 1.04.92 1.788l-7.48 10.471c-1.07 1.498 0 3.579 1.842 3.579' +
  'h11.377c.943 0 1.473 1.088.89 1.83L25.947 44.94z'

/**
 * A rider carrying the order along the progress rail.
 *
 * It rides to wherever the order actually is, so the picture and the status can never disagree:
 * parked at the shop while the lenses are made, moving once it is out for delivery, stopped at
 * the end. Everything is inline SVG and CSS — no library, no sprite, nothing to load.
 *
 * @param moving true once the order is actually on the road, which is the only time the wheels
 *               should turn. A scooter spinning its wheels beside "being made" is a lie.
 */
export function DeliveryScooter({ moving }: { moving: boolean }) {
  const wheel = moving ? 'animate-scoot-wheel' : ''

  return (
    <svg
      viewBox="0 0 64 44"
      className={`h-10 w-14 ${moving ? 'animate-scoot-bob' : ''}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      role="img"
      aria-label={moving ? 'Your order is on its way' : 'Your order is with the shop'}
    >
      {/* Speed lines, only while it is actually going somewhere. */}
      {moving && (
        <g className="animate-scoot-dash" stroke="currentColor" strokeWidth="1.5" opacity="0.5">
          <line x1="1" y1="24" x2="7" y2="24" />
          <line x1="0" y1="30" x2="4" y2="30" />
        </g>
      )}

      {/* Top box, with the mark on its side. */}
      <rect x="7" y="13" width="13" height="11" rx="2.5" />
      <g transform="translate(11.2 15.1) scale(0.135)" stroke="none">
        <path d={LOGO_PATH} fill="#863bff" />
      </g>

      {/* Body panel: seat, down behind the rider, along the floorboard. Without this it
          reads as a bicycle rather than a scooter. */}
      <path d="M20 24h10c1.6 0 2.6 1.2 2.6 2.8v2.4c0 1.6-1 2.8-2.6 2.8h-6l-1 3h16" />
      {/* Leg shield sweeping up to the column, and the bars. */}
      <path d="M39 35c4 0 6.5-2.5 7.5-6l1.5-5" />
      <path d="M44 17h8" />
      <path d="M48 20l-1.5-3" />

      {/* Rider: head, back, arm to the bars, leg down to the board. */}
      <circle cx="30" cy="8.5" r="3.6" />
      <path d="M30.5 12.3l1.5 10" />
      <path d="M31.2 15.5 45 16.6" />
      <path d="M32 22.5l7.5 2.2-1.5 7.5" />

      {/* Wheels. The hub line is what makes the spin readable at this size. */}
      <g className={wheel}>
        <circle cx="16" cy="35" r="6" />
        <line x1="16" y1="30" x2="16" y2="40" opacity="0.4" />
      </g>
      <g className={wheel}>
        <circle cx="51" cy="35" r="6" />
        <line x1="46" y1="35" x2="56" y2="35" opacity="0.4" />
      </g>
    </svg>
  )
}
