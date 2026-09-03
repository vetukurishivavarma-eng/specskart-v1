# Specskart v1 — build progress

_Last updated: 2026-09-03 (session 2 — browser walkthrough). Session: session_01NXPW5vtn6ZsgH2XDQA96hE_

## Status: Phase 1 vertical slice COMPLETE, proven end-to-end (backend) AND now walked in a real browser (Chrome). Every page renders; CRM writes persist; Frame Finder Simulate path works. Real-webcam MediaPipe path still untested (no camera in this env).

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
