# Specskart v1 — Phase 1

Lead generation · WhatsApp automation · AI Frame Finder · CRM · Campaign analytics.
**No e-commerce in this phase** — the Store section is a "Coming Soon" teaser by design.

> This is a fresh, standalone build. It is unrelated to any earlier project in this workspace.

---

## What Phase 1 does

```
Ad / campaign (FB · IG · Google · TikTok · WhatsApp)
   → customer opens WhatsApp
   → webhook → backend creates a Lead + captures attribution
   → deterministic WhatsApp bot replies with options
   → "Find Frames For My Face" → secure opaque Frame Finder link (no phone number in the URL)
   → customer opens link (no sign-up / OTP) → photo-processing consent
   → browser-side MediaPipe face landmarks → scale-invariant ratios → server classifies face shape
   → rules engine returns recommended frame categories → saved against the Lead
   → automated WhatsApp follow-up with the result
   → Admin CRM shows the whole journey, dashboard, campaign performance
```

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC / Security / Data JPA, Bean Validation, Flyway |
| DB | PostgreSQL (prod) · H2 in-memory (dev/mock) |
| Frontend | React 19 + TypeScript + Vite + Tailwind + React Router + TanStack Query |
| Face analysis | `@mediapipe/tasks-vision` FaceLandmarker in the browser; deterministic geometric classifier on the server |
| WhatsApp | Official WhatsApp Business Cloud API (`MetaWhatsAppProvider`) with a `MockWhatsAppProvider` default |
| Packaging | Docker Compose (postgres + redis + backend + frontend) |

Redis is wired into compose but not yet used — reserved for the Phase 2 short-lived session cache.

---

## Run locally (no Docker, no paid APIs)

Backend (H2 + mock WhatsApp + simulation endpoints enabled):

```bash
cd backend
./mvnw spring-boot:run           # http://localhost:8080  (profile: dev)
```

Frontend:

```bash
cd frontend
npm install
npm run dev                      # http://localhost:5173  (proxies /api to :8080)
```

Default logins (dev/mock only): `admin@specskart.local / admin12345`, `agent@specskart.local / agent12345`.

## Run with Docker

```bash
cp .env.example .env             # edit SESSION_SECRET etc.
docker compose up --build
# frontend  http://localhost:8081
# backend   http://localhost:8080
```

Compose runs the backend on the `prod` profile → PostgreSQL + Flyway migrations.

---

## Mock testing procedure (the full funnel, no Meta account)

With the backend running on `dev`/`mock`:

```bash
# 1. login
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@specskart.local","password":"admin12345"}' | jq -r .token)

# 2. a Facebook click-to-WhatsApp customer arrives
curl -s localhost:8080/api/sim/whatsapp/inbound -H 'Content-Type: application/json' -d '{
  "waId":"260977123456","name":"Chanda Mwansa","text":"Hi",
  "referral":{"source_id":"fb-sep-frames","utm_source":"facebook","utm_campaign":"sep_frames_fb","ctwa_clid":"abc"}
}'

# 3. customer taps "Find Frames For My Face"
curl -s localhost:8080/api/sim/whatsapp/inbound -H 'Content-Type: application/json' \
  -d '{"waId":"260977123456","buttonId":"FIND_FRAMES"}'
#    -> response.outbox contains the personalized /frame-finder?s=<token> link

# 4. open that link in the browser, OR drive it by API:
TOK=<token from the link>
curl -s localhost:8080/api/frame-finder/session/$TOK
curl -s localhost:8080/api/frame-finder/session/$TOK/consent -H 'Content-Type: application/json' \
  -d '{"cameraConsent":true,"photoProcessingConsent":true}'
curl -s localhost:8080/api/frame-finder/session/$TOK/analysis -H 'Content-Type: application/json' -d '{
  "geometry":{"faceWidthRatio":0.82,"foreheadWidthRatio":0.70,"cheekboneWidthRatio":0.82,
              "jawWidthRatio":0.72,"jawAngleDeg":150,"chinRatio":0.20}}'

# 5. see the lead + full journey in the CRM
curl -s "localhost:8080/api/admin/leads?q=Chanda" -H "Authorization: Bearer $TOKEN"
```

`scripts/e2e` (in the scratchpad during development) exercises exactly this.

---

## APIs

**Public / customer**
- `GET|POST /api/webhooks/whatsapp` — Meta verification + inbound messages (HMAC-SHA256 signature check when `WHATSAPP_APP_SECRET` set; idempotent by wa message id)
- `GET /api/frame-finder/session/{token}` · `/consent` · `/analysis` · `/event` · `/send-to-whatsapp`
- `GET /api/frame-recommendations/{faceShape}`
- `POST /api/sim/whatsapp/inbound` · `GET /api/sim/whatsapp/outbox` — simulation, only when `specskart.simulation.enabled=true`

**Admin** (`Authorization: Bearer <jwt>`; `ADMIN` or `AGENT`)
- `POST /api/auth/login`, `GET /api/admin/me`
- `GET /api/admin/leads` (page, `status`, `campaignId`, `q`) · `GET /api/admin/leads/{id}` · `PATCH /{id}/status` · `POST /{id}/notes` · `POST /{id}/assign`
- `GET /api/admin/campaigns` · `POST /api/admin/campaigns` (ADMIN) · `PATCH /{id}/status`
- `GET /api/admin/analytics/dashboard` · `GET /api/admin/analytics/campaigns`
- `GET|POST|DELETE /api/admin/recommendations/rules` (ADMIN) — the face-shape → frame rules are data, not hard-coded

Errors are `{ code, message, timestamp, traceId }`.

## Face analysis approach

The **image never leaves the browser.** MediaPipe FaceLandmarker produces a 468-point mesh; the frontend
reduces it to six scale-invariant ratios (face width, forehead, cheekbone, jaw widths ÷ face height, jaw
angle, chin ratio) and posts only those. `FaceShapeClassifier` scores the seven shapes
(Oval/Round/Square/Rectangle/Heart/Diamond/Triangle) with transparent geometric rules and returns a shape +
bounded confidence + the rule trace. It is **face shape estimation, not biometric identity recognition.**
`FACE_RETAIN_IMAGES=false` by default; the frontend has an offline "Simulate" path that submits synthetic geometry.

## Security notes

- Frame Finder links are `?s=<44-char URL-safe random>`; only the SHA-256 hash is stored; configurable expiry.
- Admin auth is real JWT + Spring Security method/route rules — not a frontend-only gate.
- Webhook: verify-token handshake + optional HMAC signature verification + idempotency ledger + safe logging (no tokens/secrets).
- Consent records (camera, photo-processing) are stored with timestamp + policy version before analysis is allowed.

## Known limitations

- Frontend is compiled and type-checked but not yet exercised in a real browser session; the backend funnel is covered by tests + a scripted end-to-end run.
- The geometric classifier is intentionally simple; confidence values are modest and meant to read as "closest to".
- `MetaWhatsAppProvider` sends real messages but has not been run against a live WhatsApp number in this environment.
- Prod `V1__init.sql` is hand-written to match the JPA model; verify with `flyway validate` on first prod deploy.
- No Redis usage yet; no rate limiting on public endpoints.

See `docs/PHASE2.md` for the deferred e-commerce scope and where it plugs in.
