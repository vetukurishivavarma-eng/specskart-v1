# Phase 2 — deferred scope & integration points

**Nothing here is built yet.** Phase 1 deliberately stops at the Lead. This file records the client's
expanded vision and how the current architecture is meant to absorb it without a rewrite.

## Client vision (from the brief + follow-up comments)

1. **Online store** — browse frames; buy frames / lenses / frame+lens combos.
2. **In-store eye check** — customer goes to the nearest optician, gets eyes checked, buys from us.
3. **Delivery** — free within 50 km; charged beyond 50 km.
4. **Fitting** — free for some within 50 km; otherwise a minimum charge.
5. **Minimal/no human intervention** across the ordering process.
6. **Continuous WhatsApp messaging** — from lead-gen right through to "goods received", i.e. the same
   conversation thread carries order confirmations, dispatch, delivery and fitting updates.
7. Phone-number + WhatsApp OTP sign-in **only at purchase time**.

## Where it plugs into the Phase 1 code

| Phase 2 need | Phase 1 seam already in place |
|---|---|
| `Customer` entity | `Lead` holds identity + attribution; add `Customer` with `leadId` FK, promote on first purchase. `Lead.status` already has room for a future `CONVERTED`/`PURCHASED`. |
| Product catalog | `recommendation.FrameCategory` is the taxonomy; add `Frame`, `Lens`, `FrameLensCombo` referencing category codes. Frame Finder results already speak in category codes. |
| Cart / Order / Payment | New `order` feature module. `LeadEvent` analytics stream extends with `ORDER_*` events. |
| WhatsApp order updates | `WhatsAppProvider` + `WhatsAppBotService` already own the conversation and message log; add template messages for dispatch/delivery/fitting. `whatsapp_messages` already stores the full thread per lead. |
| Distance-based delivery/fitting fees | New `fulfilment` module: `ServiceArea` (radius rules), `DeliveryQuote`, `FittingBooking`. Optician locations as a `Store` entity. |
| WhatsApp OTP sign-in at purchase | New `auth` flow for customers, separate from the existing staff JWT auth. WhatsApp number already verified via the inbound webhook for existing leads. |
| Provider-specific ad data | `Lead.providerMetadata` / `Campaign.metadata` are JSON — no schema change needed for new ad-platform fields. |

## Explicitly NOT to do now

Cart, checkout, payment gateway, inventory reservation, order tracking, customer OTP auth, shipping.
Keep domain boundaries clean so these arrive as new feature modules, not edits to lead/framefinder/whatsapp.
