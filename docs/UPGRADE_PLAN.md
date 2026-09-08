# Specskart v1 — upgrade plan

_Written 2026-09-09. A review of what to change next in design, chat interaction,
and features. Ordered by value / effort. Nothing here is built yet unless it says
"DONE"._

---

## 0. Done in this pass (2026-09-09)

- **Staff order alert.** `WHATSAPP_STAFF_NUMBERS` (comma-separated) → every listed
  number gets a plain WhatsApp the moment an order is paid: order no, items,
  total, customer, and a deep link to `/admin/orders/{id}`. Best-effort per
  number. Off when the var is blank. (`OrderNotificationService.notifyNewOrder`,
  called from `CheckoutService.confirmPayment`.)
- **Customer status updates** — confirmed already complete: every
  `updateStatus` transition (PAID/PACKED/SHIPPED/DELIVERED/CANCELLED/REFUNDED)
  fires a personalised customer WhatsApp via `OrderNotificationService.onStatus`.
  No change needed.
- **AR try-on accuracy rewrite.** `computeFramePlacement` no longer scales a
  magic multiple of the eye-corner span. It now:
  - takes width from the **temple-to-temple silhouette** (landmarks 234 / 454),
    which is what a frame front actually spans and is self-foreshortening when
    the head turns; falls back to the eye span only when temples aren't visible;
  - centres on the **nose bridge** (168) — its projected position already
    carries yaw and pitch — rolling with the **eye-centre line** (4 points per
    eye, far less jittery than one corner);
  - new `smoothPlacement` EMA so a live overlay tracks instead of vibrating.
  `TryOn.tsx` is now a **live overlay** (camera + canvas, ~8 fps detection via
  the new non-throwing `useFaceLandmarker().track`), "Freeze this look" to
  compare/share. Still one tuning knob (`TRY_ON_TUNING`) but the defaults are
  anatomy-derived, so a real-device pass is polish not a prerequisite.

---

## 1. Chat interaction (WhatsApp bot)

The bot is a deterministic intent map (`WhatsAppBotService.classify`) with
two-button replies. It gets a lead from "Hi" to a Frame Finder link to a product
list. Gaps:

| # | Change | Why | Effort |
|---|--------|-----|--------|
| 1.1 | **"Track my order" intent.** Keyword/button → look up the lead's latest order → reply status + tracker link. Today a customer who asks "where's my order" gets the welcome menu. | Highest-frequency post-purchase question; already have `OrderQueryService`. | S |
| 1.2 | **List messages instead of button triples.** WhatsApp `interactive.list` allows up to 10 rows with descriptions — use it for "Show Me Frames" (list the 3–5 recommended products with prices) and a top-level menu (Find frames / Shop / Track order / Talk to us). | Buttons cap at 3; the product list is currently plain text. | S |
| 1.3 | **Cart in chat.** "Add the [name]" / tapping a product row → `CartService.freshCartForLead` + a checkout link. Partly there (`sendRecommendedProducts` links a lead-linked cart) — extend to per-product. | Shorter path to purchase from the funnel. | M |
| 1.4 | **Human handoff.** A "Talk to a person" row that sets the lead to a `NEEDS_AGENT` flag + alerts staff (reuse `WHATSAPP_STAFF_NUMBERS`), and mutes the bot for that thread for N hours. | The "Talk to an expert" button was removed in `1b12bd6` because nothing alerted staff — now staff alerting exists. | M |
| 1.5 | **Quick replies after each bot message** (persistent menu): always offer "Menu" so a user is never stuck. Add a global `MENU` intent. | Dead ends today if the user types something off-script twice. | S |
| 1.6 | **Post-analysis re-rank in chat.** Offer the 4-tap style quiz as a WhatsApp list right after the analysis follow-up, not only on the web result page. | The quiz already re-ranks recs server-side; chat users never see it. | M |
| 1.7 | **Delivery-address capture in chat** for a funnel buyer who never opens the web checkout — a short scripted flow (name → phone → address → confirm) → create order → payment link. | Some customers will only ever use WhatsApp. | L |

Recommended first slice: **1.1 + 1.2 + 1.5** — one PR, all small, turns the bot
from a launcher into a usable assistant.

---

## 2. Design / UX (web)

| # | Change | Why | Effort |
|---|--------|-----|--------|
| 2.1 | **Storefront empty state.** Prod catalog starts empty; `/store` with no products should show a "Frames landing soon — get notified" capture, not a blank grid. | First real visitors may arrive before products are loaded. | S |
| 2.2 | **Frame Finder → try-on hand-off.** After the result page, a prominent "See them on your face" that opens `TryOn` seeded with the recommended frames (component already takes `frames`). | Try-on is buried; it's the strongest conversion moment. | S |
| 2.3 | **Mobile nav.** `SiteLayout` header hides the nav under `md:` with no hamburger — on a phone there is only the logo, Bag, and "Find My Frame". Add a menu. | Most traffic is a phone tapping a WhatsApp/CTWA link. | S |
| 2.4 | **PDP: lens picker + "complete the look" visible above the fold**, not only in the bag. | The add-on revenue (`specskart.lenses.*`) is easy to miss. | S |
| 2.5 | **Result share card** — render the recommendation as an image (canvas) for a proper WhatsApp share preview, instead of `wa.me/?text=`. | Better viral loop. | M |
| 2.6 | **Consistent loading/skeletons.** Several pages flash empty then pop. | Polish. | S |
| 2.7 | **Try-on tuning pass on a real phone** — walk `TRY_ON_TUNING.widthScale` / `bridgeBiasFrac` with 3–4 real faces + real frame PNGs; confirm the transparent-lens PNGs are cropped to the frame front (no arms) so the width maths holds. | The one thing that needs hardware. | S (once on a device) |

---

## 3. Feature backlog (from PROGRESS + new)

- **`followUpAt` on Lead** + V14 migration + a "remind me" date on the CRM lead
  detail + a real "due today" count on the dashboard (currently the dashboard's
  "follow-ups" is just a count of `FOLLOW_UP`-status leads).
- **Admin password-change flow** (self-service).
- **Playwright E2E**: WhatsApp-sim → session → analysis → CRM visibility → shop →
  checkout. None exists.
- **Order search / filter in admin** (by status, date, customer) — the Orders
  screen is an unfiltered list.
- **Back-in-stock / drops** currently WhatsApp plain-text only — needs an approved
  template for the outside-24h case (there's a `// ponytail:` marker in
  `StockAlertJob`).
- **Analytics "today" timezone** is the system zone; pin to the store's zone for
  prod.

---

## 4. Ops / security debt (blockers for going live for real)

1. **Rotate secrets leaked in chat** — `DB_PASSWORD`, `SESSION_SECRET`, the
   WhatsApp permanent access token, and the app secret. All were pasted into a
   session transcript.
2. **`specskart-db` is Render free and was slated for deletion ~2026-10-03** —
   move to a paid tier or another Postgres host, or the funnel dies.
3. **Move the API + web off Render free** — the ~150s cold start loses CTWA leads
   who tap and wait.
4. **CTWA ad** (the actual go-live): FB Page + Ads Manager + a Click-to-WhatsApp
   ad, then a CRM Campaign whose **External ID = the FB Ad ID** (INTEGRATION.md
   §7–§8). Everything technical for this is already deployed.

---

## Suggested order

1. Rotate secrets (§4.1) — do it before anything else touches prod.
2. Chat slice 1.1 + 1.2 + 1.5.
3. UX 2.1 + 2.2 + 2.3 (empty state, try-on hand-off, mobile nav).
4. Real-device try-on tuning (2.7) whenever a phone is to hand.
5. Then CTWA ad (§4.4) — the business milestone.
