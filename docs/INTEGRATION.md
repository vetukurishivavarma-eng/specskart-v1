# Specskart v1 — live test with a real Facebook Click‑to‑WhatsApp ad

This is the runbook for taking the Phase 1 funnel from the mock harness to a real
end‑to‑end test: **you tap a Facebook ad on your phone → WhatsApp opens → the bot
replies → you run the Frame Finder → the lead + journey appear in the CRM.**

Everything Meta‑side (creating the app, the access token, accepting terms,
launching the paid ad) is done **by you in the Meta UI** — this document only
tells you what to click and what value goes where.

---

## 0. What you need before starting

| Thing | Notes |
|---|---|
| A Meta (Facebook) Business account | business.facebook.com — with a payment method for ads |
| A phone number for WhatsApp | **not** already registered on the WhatsApp *app* for the same number. A second SIM / spare number is easiest. The Meta *test* number works for the bot but **cannot be used as the destination of a real ad**, so for the ad test you need a real number on the WABA. |
| A place to deploy the backend over HTTPS | Render (you already use it) — plan below |
| A place to host the frontend | Render static site |
| ~ £5–£10 ad budget | you can cap the campaign at a tiny daily budget and stop it after one click |

Time: ~2–3 hours the first time, most of it waiting on Meta review of the WABA /
number.

---

## 1. Deploy the backend

The bot must be reachable at a fixed public HTTPS URL for Meta's webhook.

### 1a. Database — do NOT use H2 for this

`dev`/`mock` profiles use in‑memory H2, which is wiped on every restart (and Render
free dynos restart / sleep constantly). Use Postgres + the `prod` profile.

1. Create a Postgres database (Neon free tier, or Render Postgres). You need two
   connection strings if using Neon: the pooled one and the direct one — for
   Render/Neon set `DB_URL` to the pooled URL.
2. **Validate the Flyway migration against real Postgres before deploying.** The
   `prod` profile runs `ddl-auto: validate`, so if `V1__init.sql` doesn't match
   the JPA model exactly the app will not boot. Locally:
   ```bash
   cd backend
   DB_URL="jdbc:postgresql://<host>/<db>?sslmode=require" \
   DB_USERNAME=<user> DB_PASSWORD=<pass> \
   SPRING_PROFILES_ACTIVE=prod \
   "C:\Users\Shiva\tools\apache-maven-3.9.9\bin\mvn" spring-boot:run
   ```
   Watch for `Schema validation` errors on boot. If it starts cleanly and
   `GET /actuator/health` is `UP`, the migration is good. Fix `V1__init.sql` and
   repeat if not.

### 1b. Render service

- New → Web Service → repo `specskart-v1`, root directory `backend`.
- Runtime: Docker (there is a `backend/Dockerfile`), or Native (Java 21) with
  build `./mvnw -q -DskipTests package` and start
  `java -jar target/*.jar`.
- Health check path: `/actuator/health`.
- Environment variables — set the non‑secret ones now, secrets in step 5:

  | Key | Value |
  |---|---|
  | `SPRING_PROFILES_ACTIVE` | `prod` |
  | `DB_URL` | `jdbc:postgresql://…` (pooled) |
  | `DB_USERNAME` / `DB_PASSWORD` | from the DB |
  | `SESSION_SECRET` | any 32+ random chars |
  | `SPECSKART_SIMULATION_ENABLED` | `false` (turn the sim endpoints off in a public deploy) |
  | `FRONTEND_BASE_URL` | the frontend URL from step 2 (set after) |
  | `SPECSKART_CORS_ORIGINS` | the frontend URL |
  | `SPECSKART_BUSINESS_WA_NUMBER` | your WABA number in `+<country><number>` form |
  | `WHATSAPP_PROVIDER` | leave `mock` for now — flip to `meta` in step 5 |

- Deploy. Confirm `https://<backend>.onrender.com/actuator/health` → `{"status":"UP"}`.

> Free Render dynos sleep after 15 min idle; the first webhook after a sleep takes
> ~30–50 s to cold‑start. Meta retries failed webhook deliveries, so messages are
> not lost, but replies will lag. For a smoother demo use a paid instance or a
> cron ping to keep it warm.

---

## 2. Deploy the frontend

- New → Static Site → repo `specskart-v1`, root `frontend`.
- Build: `npm ci && npm run build`  ·  Publish directory: `dist`.
- Environment variable: `VITE_API_BASE = https://<backend>.onrender.com/api`
  (the SPA calls `/api` relative by default; on a separate static host it must be
  absolute).
- SPA routing: add a rewrite rule `/*  →  /index.html` (200) so deep links like
  `/frame-finder?s=…` and `/admin` load.
- Deploy, then go back and set the backend's `FRONTEND_BASE_URL` and
  `SPECSKART_CORS_ORIGINS` to this URL and redeploy the backend.
- Open the site, `/admin/login`, sign in with `admin@specskart.local` /
  `admin12345`, and **change that password path is not built yet** — so at least
  rotate `SESSION_SECRET` and keep the URL private. (Phase 1 has a single seeded
  admin; treat the CRM URL as a secret.)

---

## 3. WhatsApp Business Cloud API setup (Meta side)

All at **developers.facebook.com** and **business.facebook.com**.

1. **Create a Meta app**: developers.facebook.com → My Apps → Create App → type
   **Business** → name it (e.g. "Specskart CRM").
2. **Add the WhatsApp product** to the app. This creates a test WABA and a test
   number automatically.
3. **Add your real number**: WhatsApp → API Setup → "Add phone number" → register
   the business number, verify by SMS/call. It must not be active on the WhatsApp
   consumer app. Give the WABA a display name (goes through Meta review, minutes–days).
4. Collect these four values (WhatsApp → API Setup, and App → Settings → Basic):
   | Value | Where | Goes to env var |
   |---|---|---|
   | **Phone number ID** | API Setup, under the number | `WHATSAPP_PHONE_NUMBER_ID` |
   | **WhatsApp Business Account ID** | API Setup | `WHATSAPP_BUSINESS_ACCOUNT_ID` |
   | **App Secret** | App → Settings → Basic → "Show" | `WHATSAPP_APP_SECRET` |
   | **Access token** | see next step | `WHATSAPP_ACCESS_TOKEN` |
5. **Permanent access token** (the 24‑h token in API Setup is only for the first
   smoke test): business.facebook.com → Business settings → Users → **System
   users** → Add → assign the app with `whatsapp_business_messaging` and
   `whatsapp_business_management` → **Generate token** → no expiry → copy it.
6. **Verify token**: pick any random string (e.g. `specskart-<random>`), you'll
   paste the same value into Meta and into `WHATSAPP_WEBHOOK_VERIFY_TOKEN`.

---

## 4. Wire the webhook

The endpoint is:

```
https://<backend>.onrender.com/api/webhooks/whatsapp
```

- `GET` handles Meta's verification handshake (checks `hub.verify_token` against
  `WHATSAPP_WEBHOOK_VERIFY_TOKEN`).
- `POST` receives messages; validated with `X-Hub-Signature-256` against
  `WHATSAPP_APP_SECRET`.

Steps:

1. Set the backend env vars from step 3 first (step 5) and redeploy, so the verify
   token is live.
2. Meta app → WhatsApp → Configuration → **Webhook** → Edit:
   - Callback URL: the URL above
   - Verify token: your `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
   - Click Verify and Save — Meta calls `GET` and expects the challenge echoed.
     If it fails: token mismatch, or the backend is asleep/not deployed.
3. **Subscribe to the `messages` field** (Webhook fields → Manage → toggle
   `messages`). Without this you get nothing.
4. Make sure the webhook is subscribed **for your WABA** (some setups need
   `Configuration → WhatsApp Business Account → Subscribe`).

---

## 5. Flip the backend to the real provider

Set / update on Render and redeploy:

```
WHATSAPP_PROVIDER=meta
WHATSAPP_PHONE_NUMBER_ID=…
WHATSAPP_BUSINESS_ACCOUNT_ID=…
WHATSAPP_ACCESS_TOKEN=…            (the permanent system-user token)
WHATSAPP_APP_SECRET=…
WHATSAPP_WEBHOOK_VERIFY_TOKEN=…    (same string you gave Meta)
WHATSAPP_GRAPH_BASE_URL=https://graph.facebook.com/v21.0   (default; bump if Meta deprecates v21)
```

On boot `MetaWhatsAppProvider` throws if the token or phone‑number‑id is missing —
check the deploy logs say the app started and
`GET /api/admin/system/status` (needs admin auth) shows `whatsappMode: "META"`,
`whatsappConfigured: true`. The dashboard status strip shows the same.

---

## 6. Smoke test WITHOUT an ad (do this first)

1. From your **personal** WhatsApp, send `Hi` to the business number.
2. Expected within a few seconds (longer if the dyno was asleep):
   - the bot replies with a welcome + 3 buttons
     (*Find Frames For My Face* / *Explore Frames* / *Talk To An Expert*).
3. Tap **Find Frames For My Face** → you get a link
   `https://<frontend>/frame-finder?s=<44-char token>`.
4. Open it on the phone → consent → allow camera → take a selfie →
   it analyses in‑browser → result screen with your face shape + recommended
   styles → tap **Send results to WhatsApp**.
5. Back in WhatsApp you get the analysis follow‑up with *Show Me Frames* /
   *Talk To Expert* / *Not Now*.
6. In the CRM (`/admin`):
   - Dashboard: Total leads +1, Face analyses +1, WhatsApp conversations +1,
     source **META**.
   - Leads → your number → full journey timeline, WhatsApp thread, face‑analysis
     block, consent records.

This proves the whole pipeline. Free‑form replies work here because **you messaged
first**, which opens the 24‑hour customer‑service window — no message templates
needed for this flow.

> The real‑camera path uses MediaPipe FaceLandmarker downloaded to the browser
> (~5–10 MB, first load only). On a phone this is the first time that code path is
> exercised — if the result screen hangs, check the browser console; the desktop
> walkthrough only tested the "Simulate" fallback.

---

## 7. Create the Click‑to‑WhatsApp ad

Meta Ads Manager → Create:

1. **Objective**: *Engagement* (or *Traffic*). At ad‑set level set **Conversion
   location = WhatsApp**.
2. **WhatsApp Business Account / number**: select your WABA number.
3. **Budget**: daily budget at the platform minimum (e.g. £2–£5), you'll stop it
   after one test click.
4. **Audience**: narrow it to basically yourself — your city + your age + an
   interest — so you don't spend on strangers. Or use Advantage+ and just stop it
   fast.
5. **Placements**: Facebook Feed + Instagram Feed is fine.
6. **Creative**: any image + primary text + headline. Under the WhatsApp section
   there's an optional **"greeting message"** / welcome message — you can leave
   Meta's default; our bot replies regardless. The important part is the CTA
   button is *Send Message* / *WhatsApp*.
7. Publish → wait for **"Active"** (ad review, usually < 1 h).

When the ad is live, note the **Ad ID** (Ads Manager → columns → Ad ID, a long
number). You need it for step 8.

---

## 8. Attribute the test lead to a campaign in the CRM

A Click‑to‑WhatsApp referral delivers `referral.source_id` on the first inbound
message — that value is the **Facebook Ad ID**. The backend resolves the campaign
by matching that against a Campaign's **External ID** (falls back to
`utm_campaign`, which CTWA ads usually don't send).

So in `/admin/campaigns` → **New campaign**:

| Field | Value |
|---|---|
| Name | e.g. "FB CTWA Live Test" |
| Platform | FACEBOOK |
| External campaign ID | **the Ad ID from step 7** |
| utm_source | `facebook` |
| utm_campaign | anything, e.g. `fb_ctwa_test` |
| Budget | your daily cap |

Do this **before** you tap the ad, so the very first message is attributed. If you
forget, the lead still lands as source META with no campaign — you can't
retroactively link it in Phase 1.

---

## 9. Run the real test

1. On your phone, open Facebook/Instagram, find your ad (search your page, or use
   the ad preview link from Ads Manager → "…" → Share → copy the preview URL and
   open it in the app).
2. Tap the **Send Message / WhatsApp** button → WhatsApp opens with a prefilled
   message → send it.
3. Same flow as the smoke test, but now:
   - Leads → your number shows **Campaign = "FB CTWA Live Test"**.
   - Lead detail → Attribution panel shows `source META`, `providerMetadata`
     containing the `referral` block (headline, body, `ctwa_clid`, `source_id`).
   - Dashboard → "Leads by source" META, and campaign‑performance row for your
     campaign gets Leads 1 / Analyses 1 once you finish the Frame Finder.
4. **Stop the ad** in Ads Manager so it doesn't keep spending.

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Webhook "Verify" fails in Meta | Backend asleep or not deployed; or `WHATSAPP_WEBHOOK_VERIFY_TOKEN` ≠ the token typed in Meta. Hit `GET /api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=<t>&hub.challenge=123` yourself — should return `123`. |
| No reply to "Hi" | (a) `messages` field not subscribed; (b) provider still `mock` — check `/api/admin/system/status`; (c) access token invalid/expired → check backend logs for `WhatsApp send failed` (401/190); (d) dyno cold start — wait 60 s and resend. |
| `401` in backend logs on inbound | `X-Hub-Signature-256` mismatch → `WHATSAPP_APP_SECRET` wrong. Temporarily unset it to bypass the check while debugging. |
| Reply arrives but Frame Finder link 404s | `FRONTEND_BASE_URL` wrong, or the static site has no SPA rewrite → deep link 404. |
| Frame Finder says "This link isn't active" | Token expired (`FRAME_FINDER_SESSION_EXPIRY_HOURS`, default 72) or the DB was wiped (you're on H2 — switch to Postgres). |
| CORS error in the browser on `/admin` | `SPECSKART_CORS_ORIGINS` doesn't include the frontend origin. |
| Lead has no campaign | Campaign's External ID ≠ the Ad ID, or campaign created after the first message. |
| App won't boot on `prod` | `Schema validation failed` → `V1__init.sql` drift; fix and redeploy (see 1a). |
| "Re‑engagement" follow‑up (outside 24 h) never sends | Expected — free‑form messages are blocked outside the 24‑h window; that path needs an approved WhatsApp **message template**, not built yet. |

---

## 11. Cost & cleanup

- WhatsApp: service conversations (user‑initiated) are free within the monthly
  free tier; you'll be well under it for a test.
- Ads: whatever you spent before stopping — cap it low.
- After testing: stop the ad, and either keep the deploy for demos or suspend the
  Render services. The permanent access token stays valid until you delete the
  system user.
- To go back to mock: set `WHATSAPP_PROVIDER=mock` and redeploy.

---

## Appendix — env var reference (backend)

| Var | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` for Postgres + Flyway |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | — | prod datasource |
| `SESSION_SECRET` | insecure dev value | JWT signing key, 32+ chars |
| `JWT_TTL_MINUTES` | `720` | admin session length |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | used to build the Frame Finder link |
| `SPECSKART_CORS_ORIGINS` | localhost:5173,4173 | comma list of allowed browser origins |
| `SPECSKART_BUSINESS_WA_NUMBER` | `+260000000000` | shown to customers |
| `SPECSKART_SIMULATION_ENABLED` | `true` | **set `false` in public deploys** |
| `FRAME_FINDER_SESSION_EXPIRY_HOURS` | `72` | opaque link lifetime |
| `FACE_RETAIN_IMAGES` | `false` | keep `false` — never store selfies |
| `WHATSAPP_PROVIDER` | `mock` | `meta` for real |
| `WHATSAPP_PHONE_NUMBER_ID` | — | from Meta API Setup |
| `WHATSAPP_BUSINESS_ACCOUNT_ID` | — | from Meta API Setup |
| `WHATSAPP_ACCESS_TOKEN` | — | permanent system‑user token |
| `WHATSAPP_APP_SECRET` | — | App → Settings → Basic |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | `dev-verify-token` | must match Meta |
| `WHATSAPP_GRAPH_BASE_URL` | `https://graph.facebook.com/v21.0` | Graph API version |
