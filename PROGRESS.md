# Specskart v1 — build progress

_Last updated: 2026-09-03 (session 1, cont.). Session: session_014CLssw1cRChm9HbQQHjUZy_

## Status: Phase 1 vertical slice COMPLETE and proven end-to-end (backend). Frontend compiles + unit-tested, not yet walked in a real browser (no Chrome extension in this env).

Repo: `C:\Users\Shiva\specskart-v1` — its own git repo, branch `main`, 5 commits (HEAD `82d6bd2`).
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

## Added since first checkpoint (commits e646c65, 82d6bd2)
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

## Not done / next session
- [ ] Walk the frontend in a real browser (Frame Finder webcam + MediaPipe model download; CRM
      screenshots). Not possible in this env — no Claude Chrome extension connected.
- [ ] Verify `V1__init.sql` against a real Postgres (`flyway validate`) — H2/PG-mode test passes but
      column names still assume Hibernate snake_case physical naming.
- [ ] `docker compose up` has never been run here (no Docker on box).
- [ ] Playwright E2E (WhatsApp-sim → session → analysis → admin visibility).
- [ ] Rate limiting on public endpoints; Redis actually used (currently unused).
- [ ] WhatsApp: template-message support for the follow-up (currently plain interactive/text, which is
      fine inside the 24h window / mock, but real prod needs approved templates for re-engagement).
- [ ] `MetaWhatsAppProvider` never exercised against a live number.
- [ ] Admin: "follow-ups due" is a status count, not a real date-based reminder; no assignable-users list endpoint.
- [ ] Consider `analysisConversionPct` / dashboard "today" timezone (fixed to system zone; revisit for prod UTC).

## How to resume
```
cd C:\Users\Shiva\specskart-v1\backend && "C:\Users\Shiva\tools\apache-maven-3.9.9\bin\mvn" spring-boot:run
cd C:\Users\Shiva\specskart-v1\frontend && npm run dev
# admin: admin@specskart.local / admin12345   (dev/mock only)
```
Generator scripts used to create the code live in the session scratchpad (not committed) — the code
itself is all committed, so they are not needed.
