# Specskart v1 — build progress

_Last updated: 2026-09-08 (Phase 2 complete). Session: session_01Tne6a6brsdoue2fDNsWxqQ_

## Status: PHASE 1 + PHASE 2 COMPLETE. Real WhatsApp funnel live on +260 97 2809599. Automated
online shop live (mock payments until Flutterwave keys added). HEAD `59f2c76`.

### Phase 2 — all 12 engagement features shipped (2026-09-08), migrations V7–V13
Built partly by 3 parallel worktree subagents (style quiz, back-in-stock+drops, AR try-on),
merged + full-suite-tested one at a time. Backend 45 tests green, frontend 12 green.

1. **Expiring discount after face analysis** (`V7`, `PromoIssuer`) — a personal `FIT-XXXXX` code,
   one live per lead, 10% / 48h, shown on the result page + in the WhatsApp follow-up; `?promo=`
   auto-applies to the cart.
2. **Personalised store/home** — face shape remembered (localStorage) after Frame Finder; store
   pre-filters, home shows a "picked for your face" row.
3. **Shareable result** — "Share my result" → WhatsApp share (`wa.me/?text=`).
4. **Social-proof ticker** (`GET /api/public/social-proof`) — rotating "Grace in Lusaka got a
   Wayfarer · 2h ago" / weekly counts; renders nothing until real activity.
5. **Loyalty points** (`V12`, `LoyaltyService`) — 1 pt/K1 on paid orders; redeem at checkout
   (100 pts = K10, `specskart.loyalty.*`); reversed on cancel/refund.
6. **Referrals** (`V12`) — `SPECS-XXXXX` per lead; friend gets 10% off, referrer gets 200 pts once
   the friend's order is paid. Shown in WhatsApp + on the order tracker (share button).
7. **Style quiz** (`V8`, subagent) — 4 taps after analysis → `RecommendationService.forFaceShapeWithStyle`
   re-ranks (transparent additive boost); answers persist on `leads.style_*`.
8. **Back-in-stock alerts** (`V10`, subagent, `StockAlertJob` @5min) — "notify me" on a sold-out
   / upcoming product; WhatsApp when stock returns. `// ponytail:` outside-24h needs a template.
9. **Countdown drops / limited editions** (`V11`, subagent) — `products.drops_at` hides a product
   from the store until the time passes; PDP shows a live countdown + notify-me; `limited_edition` badge.
10. **AR virtual try-on** (`V9`, subagent) — `products.try_on_image_url` (transparent PNG, admin
    uploads, alpha kept); `frontend/src/lib/tryOn.ts` composites the frame onto the captured selfie
    aligned to MediaPipe eye landmarks; swipe between recommended frames. **Tuning knobs in
    `tryOn.ts` `TRY_ON_TUNING` — needs real-device calibration.**
11. **Prescription lenses** (`V13`) — pick Non-prescription / Blue-light / Single-vision /
    Progressive in the bag; per-pair add-on (`specskart.lenses.*`) on every lensable frame; choice
    + optional Rx JSON copy to the order, shown on the tracker + admin ("Rx not yet collected").
12. **"Complete the look" bundles** (`V13`) — `products.kind` FRAME|ACCESSORY; cart suggests up to
    3 accessories once a frame is in the bag and none added. DataSeeder seeds 3 demo accessories.

New env vars (all optional / have defaults): `PROMO_FACE_ANALYSIS_*`, `LOYALTY_*`, `LENS_*`,
`WHATSAPP_POST_PURCHASE_TEMPLATE`.

---

## (earlier) Status: Phase 1 COMPLETE + walked in Chrome + **deployed to Render + REAL WhatsApp funnel proven (2026-09-03)**. A real "Hi" to the Meta test number drove lead → bot welcome buttons → "Find Frames" tap → Frame Finder link, all confirmed in prod logs.

## Live deployment (2026-09-03)
- API https://specskart-api.onrender.com (Render Docker, prod profile, Frankfurt, svc `srv-dacpjcjl550s73d6g2eg`)
- Web https://specskart-web.onrender.com (static, svc `srv-dacpjcjl550s73d6g2dg`)
- DB Render Postgres `specskart-db` (`dpg-dacpf0f10e5c73bhu6l0-a`) — **free, deleted ~2026-10-03**
- Repo on GitHub: `github.com/vetukurishivavarma-eng/specskart-v1` (private)
- Meta app `specskart-v1` (ID 908294765394610) **Published/Live**, WABA `2033874723918528`,
  test number `+1 555 669 9655`, phone-number-id `1291647124029558`, `WHATSAPP_PROVIDER=meta`
- Webhook: `https://specskart-api.onrender.com/api/webhooks/whatsapp`, verify token `specskart-wh-live-2026`

### Three things that had to be fixed for real webhooks to arrive
1. `/api/admin/leads` 500 on Postgres — `LeadRepository.search` binds q=null untyped → `lower(bytea)`.
   Fixed: `cast(:q as string)` in the query (`aa0fff1`).
2. **App must be Published** — Meta withholds real webhooks from dev-mode apps. Publishing needed only
   a **privacy policy URL** (set to `/privacy`) — NOT Business Verification.
3. **App must be subscribed to the WABA** via `POST /{waba-id}/subscribed_apps` — subscribing the
   `messages` field in the dashboard is not enough and the UI doesn't do it. `MetaWhatsAppProvider`
   now does it on startup (`1cc40c9`). This was the real blocker; Meta's "Recent webhook activity"
   panel showed the events but never POSTed them.

### 2026-09-04 additions (committed, redeploying)
- **Face classifier bug fixed** (`f99af61`): `geometryFromLandmarks` mixed x (norm to frame width)
  and y (norm to frame height) in its distance math → on any non-square phone frame the face
  length:width ratio was warped by the aspect ratio → every real face read as ROUND at floor
  confidence. Now the pixel dims flow through and x is rescaled. Re-test on a phone.
- **V2 migration + archive/delete** (`63aede0`): V1 had zero FKs. V2 cleans orphans, adds FK
  cascades (hard children) / SET NULL (campaign, assignee), and an `archived_at` column. CRM lead
  detail now has Archive/Unarchive (soft) and Delete permanently (ADMIN-only, GDPR erasure);
  Leads page has an "Archived" toggle. **The prod deploy runs V2 automatically on boot.**
- vitest pool threads→forks (threads hangs on this box with vitest 4). 26 backend + 8 frontend green.

### 2026-09-07 — WhatsApp re-engagement templates (committed, NOT deployed)
- `WhatsAppProvider.sendTemplate(to, name, lang, bodyParams)` — third transport method.
  Mock records it in the outbox; `MetaWhatsAppProvider` POSTs `type:template` with a body
  component. This is the only message type Meta accepts outside the 24h window.
- Config: `WHATSAPP_FOLLOW_UP_TEMPLATE` (+ `_LANG`, default `en`). Blank = feature off.
  {{1}} = lead first name, {{2}} = store name.
- `WhatsAppBotService.sendManualFollowUp(lead)` → sends the template, moves lead to
  FOLLOW_UP, logs it in the WA thread (`type=template`) + timeline (`WHATSAPP_FOLLOW_UP_SENT`,
  new LeadEventType). 400 `FOLLOW_UP_TEMPLATE_NOT_CONFIGURED` if unset.
- `POST /api/admin/leads/{id}/whatsapp/follow-up` + a "Send WhatsApp follow-up" button on
  the CRM lead-detail WhatsApp panel.
- No DB migration (`lead_events.event_type` is `varchar(60)`). Backend 27 tests green
  (+1), frontend 8 green, `npm run build` clean. Docs: INTEGRATION.md §5a + appendix.

### 2026-09-07 — REAL NUMBER LIVE
- Registered **+260 97 2809599** on the existing (published) Meta app. New WABA `2285018372331628`,
  phone-number-id `1326122450579129`. Permanent system-user token + app secret in Render.
- Set on `specskart-api`: `WHATSAPP_PROVIDER=meta`, `WHATSAPP_PHONE_NUMBER_ID`,
  `WHATSAPP_BUSINESS_ACCOUNT_ID`, `WHATSAPP_APP_SECRET`, `WHATSAPP_ACCESS_TOKEN`,
  `SPECSKART_BUSINESS_WA_NUMBER=+260972809599`.
- Backend auto-subscribed the new WABA on boot (log: `subscribed app to WABA 2285018372331628`).
- **Smoke test PASSED**: a real "Hi" → webhook → lead `6b63c639…` → bot GREETING → welcome sent, no errors.
- Commit `34b9ee5` (templates) pushed + auto-deployed.
- ⚠️ Token + app secret went through the chat — rotate after ad testing.

### 2026-09-07 — PHASE 1 AUTOMATED SHOP (committed, not yet deployed)
Goal: no human in the sales conversation. Browse → recommend → cart → pay → confirm →
track → status updates → abandoned-cart nudge, all automatic. New backend packages
`catalog`, `order`, `payment`; `V3__commerce.sql` (products, product_images, promo_codes,
carts, cart_items, orders, order_items, order_events, store_config). Money = minor units.
- **Catalog**: `Product` + images + `PromoCode` + `StoreConfig`; `/api/public/products*`,
  face-shape filter reuses the recommendation rules (category codes for that shape).
- **Cart**: server-side, `X-Cart-Token` header (localStorage on the SPA); `?c=` link adopts
  a token (WhatsApp / nudge links); `?s=` (frame-finder token) links the cart to the lead.
- **Checkout**: `CheckoutService.start` → reserve stock, redeem promo, create order,
  `PaymentProvider.start` → hosted checkout URL. `confirmPayment` re-verifies with the
  gateway (idempotent) → PAID → lead CONVERTED + auto WhatsApp confirmation.
- **Payments**: `PaymentProvider` iface. `MockPaymentProvider` (default, `?mockPaid=1`
  return page) + `FlutterwaveProvider` (`PAYMENTS_PROVIDER=flutterwave`, `FLW_SECRET_KEY`,
  `FLW_SECRET_HASH`). Webhook `/api/webhooks/payment` (verif-hash checked, then API-verified).
- **Order lifecycle**: PENDING_PAYMENT→PAID→PACKED→SHIPPED→DELIVERED (+CANCELLED/REFUNDED);
  every transition emits an OrderEvent + an automatic **personalised** WhatsApp message
  (buyer's first name + item list on the PAID confirmation, tracker link on each). CANCELLED restocks.
- **Every order is tied to a lead:** funnel buyers already are (cart `?s=`), a website buyer is
  found-or-created by phone (`LeadService.onWebOrder`, digits = the WhatsApp-id shape so a later
  "Hi" merges) — so confirmations always have a recipient and the buyer appears in the CRM.
- **Post-purchase (`PostPurchaseJob`, @Scheduled hourly, V6__order_follow_up.sql):** 3 days after
  DELIVERED, one message per order — thanks by name + tracker link + an auto-issue discount code
  + a lead-linked shop link. Uses `WHATSAPP_POST_PURCHASE_TEMPLATE` if set (works outside the
  24h window); else a plain message (delivered only while the service window is open).
  `orders.followed_up_at` guards against repeats.
- **Inventory holds (movie-ticket model, V4__stock_holds.sql):** `products.stock_qty` = "available
  right now". Add-to-cart does an **atomic** `UPDATE ... WHERE stock_qty >= :n` (`ProductRepository.reserve`,
  returns rows-affected) and stamps `cart_items.held_until = now + 15min`. Cart view/edit renews the
  hold; `StockHoldJob` (@Scheduled 2min) releases lapsed holds on un-ordered carts; removing a line or
  cancelling an order calls `release`. Checkout no longer decrements — it re-grabs only if the hold
  lapsed, rolling the whole checkout back if the unit is gone. No stock read-modify-write anywhere.
- **WhatsApp bot**: "Show Me Frames" now sends the 3 matched products w/ prices + a shop
  link on a cart pre-linked to the lead. "Explore Frames" → shop link. New events
  PRODUCTS_SHOWN / ORDER_PLACED / ORDER_PAID / ORDER_DELIVERED.
- **Abandoned cart**: `@Scheduled` every 15 min — cart w/ items + lead + idle 1–24h + not
  nudged → one WhatsApp reminder incl. an auto-issue promo code (seeded `FRAME10`, 10%).
- **Storefront** (`/store`, `/store/:slug`, `/cart`, `/checkout`, `/order/:orderNo`) +
  admin screens (Products/editor, Orders/detail w/ status buttons, Promo codes, Storefront
  settings) under `/admin`. `AdminLayout` nav extended.
- **Photo upload (V5__product_image_files.sql):** admin uploads a JPG/PNG in the product
  editor — no URL pasting. Bytes stored in Postgres (`product_image_files`, own table so
  listings never load them; Render free tier has no disk/object storage), downscaled to
  1400px + re-encoded JPEG via JDK `ImageIO` (no dependency). `product_images.url` holds a
  relative `/api/public/product-images/{fileId}` path; the SPA resolves it against the API
  origin (`shop.ts assetUrl`). `GET` is public + cached 1yr immutable. Editor redirects
  new → edit after first save so photos can be added. External-URL images still supported
  via the upsert API.
- **Seed** (dev/mock only): 10 demo frames + `FRAME10`. Prod starts empty — admin adds real
  products. `SPRING_PROFILES_ACTIVE=prod` won't seed products.
- Tests: backend 30 green (`CommerceFlowTest` = cart→promo→checkout→pay→status→cancel/restock,
  `FlywayMigrationTest` validates V3), frontend 9 green, `npm run build` clean.
- **Deferred to Phase 2** (engagement): virtual try-on, style quiz, loyalty, referrals,
  shareable result card, social proof, bundles, back-in-stock alerts, prescription-lens flow.
- Physical fulfilment (pick/pack/courier hand-off) stays a back-office human step.

### Still open for the actual ad
- FB Page (Ads Manager needs one).
- Create the CTWA ad + a CRM Campaign whose **External ID = the FB Ad ID** — §7–§8.
- (optional) approved re-engagement template → set `WHATSAPP_FOLLOW_UP_TEMPLATE` (§5a).
- MediaPipe real-webcam path: exercised for the first time when the user opens the Frame Finder link
  on a phone during the live test.

---

## (superseded) earlier status: Phase 1 vertical slice COMPLETE, proven end-to-end (backend) AND walked in a real browser (Chrome). Frame Finder Simulate path works.

## Browser walkthrough — 2026-09-03 (Chrome, dev server localhost:5173 + backend :8080 dev/H2)
Walked: Home, How it works, Store (Coming soon), Contact, Privacy — all render, editorial serif design consistent.
Frame Finder: no/expired `?s=` token → graceful "This link isn't active"; with a real sim-generated token → personalised intro → consent screen (policy version shown) → camera view (black, no webcam here) with Upload / **Simulate**. Simulate → result "Heart · 56%" + recommended styles + "Use carefully: Browline" + "Send results to WhatsApp" (turns to "Sent ✓") + "View recommended frames" (→ /store).
Admin CRM: login (email pre-filled in dev) → Dashboard (tiles, source/status breakdown bars, campaign-performance table, WhatsApp/Simulation/retention status strip) → Leads list + status filter → Lead detail (16-event journey, face-analysis block, WhatsApp thread, attribution, consent w/ policy version, notes). Verified writes persist: status change (→ INTERESTED), assignee (assign + unassign, both 200), add note (shows author + timestamp), create campaign (new row appears).

### Issues found in the walkthrough
1. **Vite DEV optimizer hangs on first run** — log stops at "[optimizer] bundling dependencies..." and never finishes; the page then reloads every ~14s and the renderer freezes to a blank screen. Fix that worked: `rm -rf frontend/node_modules/.vite` then restart `npm run dev`. Almost certainly the heavy `@mediapipe/tasks-vision` dep — add it to `optimizeDeps` in vite.config.ts (include or exclude) so this doesn't bite on a clean machine. NOT an app bug: `npm run build` is clean (455 kB / 139 kB gzip, 4.5s) and the built app is fine.
2. **Brief white flash on route transitions** between the dark Frame Finder layout and the light marketing SiteLayout — unstyled paint during the layout swap / web-font load. Minor polish (give the root a base background, preload/`font-display` the Fraunces/Inter faces).
3. Production `vite build` emits **no separate chunk for `@mediapipe/tasks-vision`** (single 455 kB index.js, 87 modules) — confirm the real-webcam path's dynamic import actually resolves in the built bundle. The Simulate path does not exercise MediaPipe, so the camera path remains completely untested (needs a device with a camera).
4. Minor: the Leads table name link needs a precise click (two-line name wrap); direct URL nav to `/admin/leads/:id` works fine.

Repo: `C:\Users\Shiva\specskart-v1` — its own git repo, branch `main`, 8 commits (HEAD `4de3883`).
Backend: 24 tests green (incl. MockMvc API-contract + rate limiter). Frontend: 7 Vitest tests green. `mock-funnel.sh` re-run clean on a fresh DB.
Fresh standalone build; NOT related to any other workspace project.

## Toolchain notes (important for next session)
- No Maven or Docker on this box. Maven was downloaded to `C:\Users\Shiva\tools\apache-maven-3.9.9\bin\mvn`.
  The project also has `backend/mvnw` (wrapper) which will self-download Maven on first run.
- Java 21 (Temurin) present. Node 22 present.
- Spring Boot **4.1.0** (4.1.1.RELEASE from Initializr metadata does NOT exist in Central; 4.1.0 does).
  Boot 4 uses **Jackson 3** (`tools.jackson.*`), split starters (`spring-boot-starter-webmvc`,
  `spring-boot-starter-json`), and `org.springframework.boot.health.contributor.*` for health indicators.
- Frontend: React 19 / Vite 8 / TS 6 / Tailwind 3.

## Done
- [x] Backend modular monolith, package-by-feature (lead, campaign, attribution, whatsapp,
      framefinder, faceanalysis, recommendation, analytics, admin, auth, config, shared, simulation)
- [x] Lead domain + find-or-create from WhatsApp identity + soft/enforced status transitions
- [x] Multi-channel attribution: adapter `LeadSourceProvider` (Meta/Google/TikTok/WhatsApp/Website),
      UTM capture, `providerMetadata` JSON, campaign resolution by externalId / utm_campaign
- [x] WhatsApp: `WhatsAppProvider` iface, `MockWhatsAppProvider` (default), `MetaWhatsAppProvider` (real
      Cloud API, `@ConditionalOnProperty provider=meta`), deterministic `WhatsAppBotService`,
      webhook (verify handshake + HMAC sig + idempotency ledger), message log
- [x] Frame Finder: opaque token `?s=` (44-char), only SHA-256 hash stored, configurable expiry,
      status lifecycle, consent recording
- [x] Face analysis: browser posts 6 scale-invariant ratios only; `FaceShapeClassifier` (7 shapes,
      transparent geometric scoring, bounded confidence); `FaceAnalysis` persisted; snapshot on Lead
- [x] Recommendation engine: DB-backed `FaceShapeFrameRecommendation` rules, seeded, admin-editable
- [x] Analytics: `LeadEvent` stream (all 16 event types), dashboard aggregation, campaign performance
- [x] Admin CRM API: JWT (ADMIN/AGENT), leads list/detail(timeline+WA thread+attribution+consent)/
      status/notes/assign, campaign CRUD, recommendation rule editor
- [x] Consent records, global `{code,message,timestamp,traceId}` errors, WhatsApp health indicator
- [x] DB: H2 create-drop (dev/mock), Postgres + Flyway `V1__init.sql` (prod); `DataSeeder` (users,
      face shapes, frame categories, rules, demo campaigns)
- [x] Simulation endpoints (`/api/sim/whatsapp/inbound|outbox`) gated by `specskart.simulation.enabled`
- [x] 13 backend tests green (classifier x4, tokens x2, attribution x3, funnel integration x4
      incl. webhook idempotency + opaque-link-has-no-phone-number)
- [x] End-to-end funnel proven via curl script: campaign → sim click-to-WhatsApp → lead+attribution →
      autoreply → FIND_FRAMES → session link → consent → analysis → ROUND + recs → saved to lead →
      WA follow-up → admin dashboard + lead journey (16 timeline events)
- [x] Frontend: customer site (Home/HowItWorks/Contact/Store "Coming Soon"/Privacy), Frame Finder
      (consent → camera / upload / offline "Simulate" → MediaPipe landmarks → result → send-to-WA),
      admin (Login/Dashboard/Leads/LeadDetail/Campaigns). `npm run build` passes (tsc + vite).
- [x] docker-compose (postgres/redis/backend/frontend), Dockerfiles, nginx, `.env.example`
- [x] README (setup, mock test procedure, API list, security notes, limitations), `docs/PHASE2.md`

## Added since first checkpoint (commits e646c65 · 82d6bd2 · 4e3a2d6 · 4de3883)
- [x] Frontend Vitest + RTL suite (7 green): api.ts error mapping / 401 token clear / bearer header,
      faceGeometry ratio extraction + offline synthetic path, Store "Coming Soon" component test.
      Vitest needs `pool: threads` on this Windows box (baked into vitest.config.ts).
- [x] `FlywayMigrationTest` runs V1 against H2/PostgreSQL mode (catches gross SQL); `timestamptz` →
      portable `timestamp with time zone`.
- [x] `LeadStatusTransitionTest` (illegal transition rejected, soft-advance guard).
- [x] `GET /api/admin/users` staff directory + lead-assign dropdown wired in LeadDetail.
- [x] `GET /api/admin/system/status` (WhatsApp mode/configured, simulation flag, retention, expiry,
      policy version) + dashboard status strip — satisfies spec §27 "WhatsApp integration status".
- [x] `.gitattributes` (LF), tsconfig.app excludes test files from the prod build.
- [x] In-memory rate limiter (60/min per IP+prefix) on webhooks/frame-finder/sim → 429 RATE_LIMITED.
- [x] MockMvc `ApiContractTest` — locks the public error shape (401/404/410 + code + traceId).

## Not done / next session
- [x] Walk the frontend in a real browser — done 2026-09-03 (see "Browser walkthrough" above). Every
      page + the CRM writes verified. STILL open: the real-webcam MediaPipe capture path (no camera
      in this env) and the vite.config `optimizeDeps` fix for mediapipe (issue 1 above).
- [ ] Verify `V1__init.sql` against a real Postgres (`flyway validate`) — H2/PG-mode test passes but
      column names still assume Hibernate snake_case physical naming.
- [ ] `docker compose up` has never been run here (no Docker on box).
- [ ] Playwright E2E (WhatsApp-sim → session → analysis → admin visibility).
- [x] Rate limiting on public endpoints (in-memory fixed window). Redis still unused (Phase 2).
- [ ] WhatsApp: template-message support for the follow-up (currently plain interactive/text, which is
      fine inside the 24h window / mock, but real prod needs approved templates for re-engagement).
- [ ] `MetaWhatsAppProvider` never exercised against a live number.
- [ ] Admin: "follow-ups due" is a status count, not a real date-based reminder (needs `followUpAt` on Lead + migration).
- [ ] Consider `analysisConversionPct` / dashboard "today" timezone (fixed to system zone; revisit for prod UTC).

## How to resume
```
cd C:\Users\Shiva\specskart-v1\backend && "C:\Users\Shiva\tools\apache-maven-3.9.9\bin\mvn" spring-boot:run
cd C:\Users\Shiva\specskart-v1\frontend && npm run dev
# admin: admin@specskart.local / admin12345   (dev/mock only)
```
Generator scripts used to create the code live in the session scratchpad (not committed) — the code
itself is all committed, so they are not needed.
