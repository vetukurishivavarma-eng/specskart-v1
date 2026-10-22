# Specskart v1 — build progress

_Last updated: 2026-10-20._

## 2026-10-22: broadcast "pick customers" (v1 `6b50a3a`, POS `39c881d` = 1.32.0/vc37, pushed)
- Message many customers -> Everyone / Pick customers (search + tick, max 200). Backend `leadIds` on both broadcast
  requests; STOP, already-had-it and 24h-window rules still apply. 245 tests green, tsc clean. APK via GitHub Actions,
  then release v1.32.0 + app_releases row (37). Not device-tested. Web admin Broadcast has no picker (not asked).

## 2026-10-21 (6) END OF DAY — RESUME HERE
- POS **1.31.0 / vc36 RELEASED** (GitHub v1.31.0, link .../releases/download/v1.31.0/specskart-pos-1.31.0.apk); user adds
  app_releases row (min 36, mandatory). 1.29.0 + 1.30.0 also released today.
- FREE-FIRST rule (v1 `a64dbca`): open 24h chat -> plain text, closed -> paid template, for lens verify, lens + frames
  post-purchase, eye-test recall, ready-message broadcast (freeText), New chat (opens chat if window open).
- Meta: Visa added to WABA "specskart" (2285018372331628); no WhatsApp spend cap exists (offered in-app daily cap, not
  built); business unverified = 250 new chats/day, verify later. Templates specskart_followup, spin_and_win,
  eye_test_recall IN REVIEW; Render env set by user. eye_checkup_reminder deliberately NOT created.
- **TOMORROW (user asked):** broadcast "pick customers" — in app Message many customers, choose specific leads
  (search/tick list) instead of everyone; also lets a 1-person test. App has no Max box now (fixed 200); web admin
  Broadcast still has Max sends.
- App-update link with spaces now stripped server-side (`9929d6c`).
- NOT live-tested yet: spin page redesign, OTP redeem on a device, broadcast sends (need approved templates),
  bring-a-friend flow, follow-up template. Check staff_new_order has a Document header and lens_post_purchase a
  Copy-code button, or those sends fail.
- Amul project (separate repo): live site 404 since its `d37b5c2`; uncommitted fix in its server/src/app.ts; user
  can't push there now.

## 2026-10-21 (5): POS 1.30.0/vc35 RELEASED by Claude (v1.30.0 GitHub release); user adds app_releases row
- Broadcast in app (admin): Ready message (READY list in app/broadcast.tsx = approved templates; spin_and_win) or
  Type your own (free text, only leads with open 24h window, `/broadcast/text/*`). Never same template/text twice.
  Template quick-reply taps read by text (classify fallback). OTP: 1/min, 5 per prize (V75). Prizes 2 weeks.
  App update link spaces stripped (AppRelease getter/setter) after a pasted link with spaces locked tills.
- Meta: billing being added; business UNVERIFIED (250 new chats/day) -> verify later with client's PACRA docs; fix
  portfolio name/time zone (Asia/Kolkata -> Africa/Lusaka) before. Templates in review: specskart_followup,
  spin_and_win, eye_test_recall; Render env set by user. Checkup reminder deliberately OFF.

## 2026-10-21 (4): Spin redeem = WhatsApp number + prize code + WhatsApp'd verification code (V74, 238 tests)
- Till flow (web admin Spin & Win + POS 1.29.0/vc34 More -> Spin & Win codes): staff check code, type the customer's
  WhatsApp number (must match the holder's), "Send code" WhatsApps a 6-digit code (hashed, 10 min, 5 tries; free text so
  only inside 24h window -> else "ask them to send hi"); chat log shows "sent a code", never the code. Phones masked
  (+260 ••• 1234) in spin views. POS also carries 3e5fa75. POS 1.29.0 pushed; APK to build + publish.
- Welcome buttons now Free eye test / Spin & Win / FAQs (`a985b8a`); V72 wheel = 5 prizes; QR poster in Downloads.
- Templates still to create (see chat 10-21): specskart_followup, eye_checkup_reminder, eye_test_recall (+ optional
  spin_and_win broadcast). Existing: order_update, lens_verify, staff_new_order (needs Document header),
  lens_post_purchase (needs Copy-code button). All English (en).

## 2026-10-21 (3): Spin bring-a-friend unlock (V71, 236 tests)
- Any prize whose name contains "friend" (`SpinService.isFriendPrize`) is LOCKED (state WAITING_FRIEND, redeem 409) until a
  customer NEW since the spin messages the bot with the SPIN-code (forwarded wa.me link prefills it; bot catches any
  SPIN-XXXXX in text -> `claimFriend`). Friend gets own code (spins.friend_code, same expiry); winner WhatsApp'd "unlocked";
  staff push. One friend per code, a lead can be friend once, existing customers refused. Both codes redeemed in web admin.
- Client said only #2 (second pair) + #8 (bring a friend) of the 8 extra prize ideas; suggested second pair = 20% off
  cheaper frame on sale price. V72 adds both to the live wheel (35/25/15/15/10); owner edits in web admin -> Spin & Win.
- Client rule then: ONE PRIZE AT A TIME, not one spin ever (V73 drops unique lead_id/friend_lead_id; lead row locked
  while spinning). Can't spin while own prize OR a friend code you joined with is in its week (used or not); after
  expiry both winner and friend can spin again. Someone holding an active code can't take a friend's code.

## 2026-10-21 (2): Spin & Win (`8bae327`, V70) + reactions/system msgs in chat (`99707f8`, V69) + 8:00 push once/day (`d1c1f60`)
- Customer types "spin" / menu row -> signed 14-day link /spin/<leadId>; server picks prize (weights), spins.lead_id UNIQUE,
  repeat = same result. Code SPIN-XXXXX valid 7 days, WhatsApp msg + staff push. Staff: web admin -> Spin & Win (check/use code;
  ADMIN edits wheel). Default prizes = client's 3 (family eye check 50 / K100 off photochromatic+blue 35 / free sunglasses 15).
  Not done: day-5 expiry reminder (needs template), POS-app redeem screen (needs APK). Not live-tested.

## 2026-10-21: website frame orders = collect at the shop (`d95fb5b`, `59fe7bd`, V67)
- Checkout asks only name/phone/email; no address, location, door/bus options. New orders get
  delivery_method STORE, ship_address "Collect at <shop>", shipping 0 (store_config fee ignored).
- Customer WhatsApps: CONFIRMED/PAID say "pay/collect at the shop" + shop address + map; PACKED = "ready to
  collect"; SHIPPED sends nothing for STORE; DELIVERED = "Collected". Staff slip: "Customer collects at".
- Tracking page + web admin order page: Ready to collect -> Collected (PACKED -> DELIVERED now allowed).
- V67: Delivery FAQ, payment_note, guarantee_note reworded (only if unedited). Old DOOR/PICKUP code kept for old orders.
- Product page stock now sets the shop shelf (`5460b54`); POS product screens refresh after Stock edits (POS `3e5fa75`, next APK).

## 2026-10-20 (6): staff reply score, waiting-first leads, sale -> lead (backend only, no APK)
- 8:00 push "📊 Yesterday: X of Y customers got no reply" + average/slowest reply (shop hours only,
  `LeadNudges.shopClock`) + last-7-days funnel chats -> booked -> came -> bought; tap opens Leads.
  Counted from new lead_events STAFF_NEEDED / STAFF_REPLIED / EYE_TEST_BOOKED (no history before this deploy).
- Leads list (POS + web): customers waiting for a person sorted first, stage "⏰ Waiting N min for a reply";
  CONVERTED with no lens stage shows "✅ Bought". Stale waits (>24h, customer quiet) cleared each minute;
  a fresh handoff after a stale unanswered wait now restarts the staff reminders (was stuck forever after 6).
- Till sale (frame, walk-in lenses, combined) with a phone that matches a lead -> `LeadNudges.bought`: status
  CONVERTED, booked visit marked done, pending quote follow-up cancelled, LEAD_CONVERTED event (source SHOP).
  Only works when staff type the customer's number on the sale.

## RESUME HERE — 2026-10-20 (2): lead audit + bot fixes
Audited all 46 real Zambian leads since 10-01 (fresh backup). 0 real orders. Bot was the main leak:
- Map pin sent to customers was the TEST shop "Shop1" at **Mumbai** coords. V62: `stores.map_url` (client link
  https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9) + address on active shops, Shop1 -> "Specskart Opticians", FAQ
  location/delivery answers rewritten, new FAQ "Offers" (50% frames sale -- **delete/edit in POS FAQs when it ends**).
  Shop lat/lng still Mumbai (only used for distance/route maths) -- re-pin from the shop in POS.
- Typed questions now answered from FAQs by keyword (location, promo, eye test, insurance, NHIMA, hours, contact
  lenses, delivery); "where" no longer = track order; lens/Rx price questions get the price + "send a photo of your
  prescription" instead of the configurator link; ad opener "more info" gets a facts intro + menu in one message.
- No more repeats: same answer / menu within 2h -> one "someone from our team will reply" note, then quiet. Photos
  -> that note (staff are already pushed). Menu no longer sent after STOP.
- 5-touch online-frames nurture OFF (`WHATSAPP_FRAMES_NURTURE_ENABLED`, default false). Checkup reminder, recall,
  post-purchase unaffected.
- (3) Typed power ("how much for -2 lenses", "colourmatic 0.5") -> real pair prices from the lens price list
  (single vision; CYL/ADD/bifocal -> photo ask). "Are you open?" / handoff note say open/closed NOW in Lusaka
  time. Opening hour 8:00 (Google Maps listing) -- V63 fixes FAQ text. Public holidays not handled.
- (4) Chat -> shop visit (V65, `LeadNudges`, 5-min job): "Free eye test" button (welcome/menu/eye-test FAQ) ->
  next 3 open days -> booked (`leads.visit_on`), staff pushed, reminder 7-12h that morning; named days understood
  ("visit on jan 1 2027" -> closed + New Year's Day + next open days; "open saturday?"); ONE "would you like to come
  in?" + Book button ~20h after their last message when a price/staff answer went quiet (7-21h, 24h window only,
  max 1/week); staff re-pushed "X waiting N min" every 5 min (max 6 = 30 min, open hours) until a staff reply;
  "see frames"/"pics" -> 5 frame photos + prices from the catalogue (needs WHATSAPP_ASSET_BASE_URL). Holidays
  2026+2027 hardcoded in WhatsAppBotService.HOLIDAYS.
- (5) Bookings page: POS 1.28.0/vc33 (`specskart-pos@1530258`) More -> Eye test bookings (Today / Coming up /
  Didn't come / Done, "Check-up done" + Undo, tap = chat); backend `53631b6` GET /api/admin/visits, POST
  /{lead}/done|undo (V66). 8:00 push "N eye tests booked today" -> /bookings; "Still coming in today?" 3h before
  closing; next-morning "Sorry we missed you" + new days -- both once, skipped when marked done. Photo always
  acknowledged (`dcdec66`). Staff reminders every 5 min max 6 (`483d009`). APK needs publishing (release + row).
- Not fixed by code (people): staff first-reply took 6-22h for a third of leads; 31 of 46 never got a human reply.

## RESUME HERE — 2026-10-20
**DONE + LIVE:** backend `6e1bb87` (206 tests green) deployed on specskart-api-9b08 (user confirmed Live). POS 1.27.0 /
build 32 (`29c8adf`) GitHub release v1.27.0 published + app_releases row added (min 31, not mandatory; verified via
/api/public/app/version). Not device-tested.
- **Frame "Something went wrong"** (client screenshot): reused frame code hit `uq_product_sku` -> bare 500. Now 409
  `SKU_TAKEN` "Code X is already used by "<frame>"". Works now, no config.
- **Eye-checkup reminder** (`EyeCheckupReminderService/Job`, V61): everyone who has ever messaged us, every 2 days, max 4,
  stops on reply / order / STOP, 50 new people per day, skips leads mid-nurture. Template-only. **OFF until env set.**
- **Staff "New chat"** to a number that never messaged: POS Leads -> New chat -> `POST /api/admin/chat/{number}/start`
  (saves lead, sends follow-up template). Refused, nothing saved, until the template is set. Existing "Send follow-up
  template" button in chat (window closed) uses the same template.
- **NEXT (user says client adds Meta payment card 2026-10-21):** submit 2 Marketing templates (text given in session):
  `eye_checkup_reminder` ({{1}} first name, {{2}} shop link, footer "Reply STOP to unsubscribe") and `specskart_followup`
  ({{1}} first name, {{2}} store name). After approval set on Render: `WHATSAPP_CHECKUP_TEMPLATE=eye_checkup_reminder`,
  `WHATSAPP_CHECKUP_LINK=<shop link from client>`, `WHATSAPP_FOLLOW_UP_TEMPLATE=specskart_followup` (+
  `WHATSAPP_FOLLOW_UP_TEMPLATE_LANG=en_US` if Meta shows English (US)). Then test New chat on a staff phone.
- Client was told: no WhatsApp Business app on the Cloud API number; chat stays in the POS.

## RESUME HERE — 2026-10-16
- **WhatsApp "unsupported" messages** (`9e0ca19`): Meta hid a lead's (Bana Mpundu) text "Hello good afternoon, is the 50%
  sale still on?" -- arrived as type=unsupported (likely ad reply or edited msg). Chat now shows a plain-words note
  (+ reason / ad headline) instead of `[unsupported]`, raw payload logged as `wa unsupported message`. Text itself is
  never recoverable. User: leave it, customer continued the chat.
- **Security pass** (`cfcdb52`): JwtAuthFilter now loads the user each request (role + active from DB, not the token)
  -> deactivate/demote takes effect immediately; WhatsApp broadcast admin-only. 202 tests green. Probed live: /api/sim
  404, unsigned WA webhook 401 on both old + new svc. Rated ~7/10.
- **Open (user's call):** make the 3 GitHub repos private; per-person staff logins instead of shared
  staff@specskart.local (+ 2FA later); move Meta webhook to specskart-api-9b08 then retire old svc; restrict DB
  access from 0.0.0.0/0; backup-restore drill + dependency scanning.

## RESUME HERE — 2026-10-14
**NEXT SESSION (2026-10-15): user will ask for the link -> give `https://github.com/vetukurishivavarma-eng/specskart-pos/releases/download/v1.26.0/specskart-pos-1.26.0.apk`**
GitHub release v1.26.0 PUBLISHED (build 31, verified versionCode 31 + same signer; APK also in Downloads). STILL TO DO by
user: More -> App releases: version 1.26.0, build 30->**31**, min 31, mandatory, that URL. Then re-test a customer photo.
(v1.25.0 GitHub release exists too but superseded -- do not add an app_releases row for 30.)
- **Customer photos visible in POS chat** (V60 `media_id`, POS 1.23.0/vc28): inbound WhatsApp images keep Meta's media id;
  `GET /api/admin/chat/{waId}/media/{mediaId}` streams it (only media from that chat); app shows thumbnail, tap = full screen.
  Only photos received AFTER deploy show -- older ones were stored as "[image]" with no id. PDFs/docs still show "(media)".
  **Then 1.24.0 / build 29**: PDFs/videos/voice notes = "tap to open" (downloads to cache, opens in the phone's app via
  VIEW intent; backend sends a filename with a real extension), stickers inline.
  **Then 1.25.0 / build 30**: documents show their real filename (stored as the message body; never fed to the bot).
  **Then 1.26.0 / build 31** (v1 `6e05712`, POS `c740dcb`): device test showed photos as a GREY BOX. Likely cause
  (not confirmed -- the app's API svc specskart-api-9b08 is in the other Render acct, no log access): the media
  download used RestClient.create() = JDK client, no redirects/no User-Agent -> Meta served an HTML page as "the
  photo". Now JDK HttpClient w/ redirects + curl UA + checks status/HTML + logs `chat media ... failed`. App now
  downloads to cache then shows, with visible "Couldn't load, tap to retry". If still failing: read 9b08 logs for that line.
  Not device-tested.
Shipped (all pushed; backend auto-deploys on Render; POS APKs built by GitHub Actions):
- **Staff (AGENT) vs admin split.** Admin-only now: cost price (hidden + can't be set by staff), supplier
  invoices, profit in Top products, shop price overrides, staff list (`/api/admin/users`), deleting a lead.
  Staff CAN work Leads (list/detail/notes/status/WhatsApp) and reply to customers. (`80c3839`, `9f51313`, `2442b89`)
- **Denied requests return 403, not 401** — secured `/error` turned "not allowed" into "not signed in" and the POS
  app logged staff out (`f26452f`, verified live).
- **Generic staff login** `staff@specskart.local` (name "Staff", password given in chat) via V58+V59. The prod admin
  password is not on this machine, so users are added by migration (portable SQL, no `on conflict`).
- **Mandatory update auto-starts** (`38c9516` + POS 1.21.0): a mandatory release pushes `/update` to every phone, the
  app force-checks and auto-downloads even on mobile data. Android still needs the Install tap.
- **Sign out now releases the device** (`7332bbb` + POS 1.22.0/vc27 `f3a1784`): `POST /api/admin/pos/devices/me/sign-out`
  with X-Device-Id. Before, sign-out was local only and the login stayed bound until an admin released it.

Pending:
1. USER: publish **POS 1.22.0 / build 27** (includes 1.20.0 + 1.21.0 changes) — GitHub release + More -> App releases
   (build 27, min 27, mandatory). APK = the GitHub Actions artifact for `f3a1784` (or ask Claude to fetch it to Downloads).
   1.21.0 APK already in `Downloads\specskart-pos-1.21.0.apk` — superseded, skip it.
2. Not device-tested: cost hiding, leads for staff, auto-update, sign-out release.
3. Phones still on <=1.21.0 do NOT release on sign-out — an admin releases them from More -> Devices & logins until updated.
4. User wants "common features with NG POS in sync" — scope not decided yet (rule going forward / audit+port / permissions).
5. Older pending list (2026-10-11) below still applies.

## RESUME HERE — 2026-10-11
Shipped and verified on device today:
- **Staff WhatsApp chat from the business number** (POS 1.15.0 / vc20).
- **Free Firebase push for staff** (POS 1.16.0 / vc21, published): "New lens order" and "Reply from <name>"
  (a customer reply during an active staff chat; tap opens /chat/<waId>). Backend `2eb8190`, `e6ffa73`
  (Google OAuth JWT `aud` must be a string, not an array — that was the `invalid_grant` failure).
- No WABA billing, by choice (shop is in loss) — do not push billing.

Pending (user said "we will do the other later"):
1. Confirm the customer "ready for pickup" WhatsApp arrives (free text, only inside the 24h window).
2. Staff: install POS 1.16.0, sign in once, tap Allow (registers the push token).
3. Delete `WHATSAPP_STAFF_NUMBERS` on BOTH Render accounts' specskart-api (alerts now go by push).
4. On a quiet day move the Meta webhook to https://specskart-api-9b08.onrender.com/api/webhooks/whatsapp,
   then suspend/delete the OLD specskart services (old account).
5. Templates `staff_reconnect` / `staff_message` written but not created or wired (moot without billing).

## 2026-10-07 update
- **POS devices report their app version on every request** (backend `05e2809`, live; pos `e5dd5dd` = **1.13.0 / vc18**,
  CI run 37517521663 green). App sends X-Device-Id + X-App-Version; `DeviceSessionService.heartbeat` writes only when
  the version changed or last-seen is >5 min old. Test `DeviceHeartbeatTest`; 188 backend tests green.
  Phones on <=1.12.0 still only update their version at sign-in.
- **USER: publish POS 1.13.0** -- `Downloads\specskart-pos-1.13.0.apk` (verified 1.13.0/18, same signer), release
  `v1.13.0` + App releases row (build 18, min 18, mandatory). 1.12.0/17 was published 2026-10-06.
- Keep-awake pinger now on https://specskart-api-9b08.onrender.com/actuator/health (user moved it).
- NG POS 1.26.0 published 2026-10-06: 5/7 tills updated by 10-06 evening; Chilyabale + Kampekete still on 1.25.0
  (they update on next open). When all on 1.26.0 -> switch off the OLD ngpos-api.

## RESUME HERE — 2026-10-06 (Render account move + staff alerts that can't get lost)

1. **Moved to a new Render account** (client pays): workspace "My Workspace", narsipalli@gmail.com.
   NEW: specskart-db `dpg-db1vqt3bc2fs73emnm00-a` (PG16), specskart-api `srv-db1vrogm7kps73d096u0`
   https://specskart-api-9b08.onrender.com (FREE plan -> sleeps), specskart-web https://specskart-web-sysx.onrender.com.
   Created via REST API, not a Blueprint. **Bridge:** the OLD account's specskart-api/web
   (specskart-api.onrender.com / specskart-web.onrender.com, in FB ads) still run, pointed at the NEW db,
   with `SPECSKART_SCHEDULING_ENABLED=false` (switch added in `b725d29`) so jobs run only on the new one.
   Old specskart-db DELETED (data verified: 1,982 rows matched). POS app 1.12.0/vc17 (`specskart-pos@5e6c6d3`)
   points at the new API -- its GitHub Actions build failed twice on a GitHub Actions OUTAGE (not code): re-run it.
2. **Staff order alerts retried until delivered** (`1abdd33`, V55 `staff_alerts`): every web/lens order alert is a
   row per staff number; a rejected send or a webhook `failed` status retries with backoff ~16h, alternating
   template <-> plain document; no receipt in 30 min -> one resend; `delivered`/`read` closes it; give-up = ERROR log.
   Sends return Meta's wamid. Test `StaffAlertRetryTest`; 187 tests green.

**Delivery still needs these (config, not code) -- without them retries just keep failing:**
- **Meta Billing hub: set WABA currency + card** (every template send fails 131042 until then). Then templates
  reach staff phones outside the 24h window.
- **WHATSAPP_STAFF_NUMBERS** (both old+new specskart-api env) is still ONLY the Indian test number
  +919390031682 -- add the Zambian shop/lab numbers.
- Meta webhook URL -> `https://specskart-api-9b08.onrender.com/api/webhooks/whatsapp` (same verify token).
  Until then receipts go to the old URL, which shares the DB, so it still works.
- Move the keep-awake pinger to the new specskart-api URL (free plan sleeps ~15 min idle; retry job only runs awake).
- Old account: turn off old services once every POS is on 1.12.0.

_Previous: 2026-10-02._


## RESUME HERE — 2026-10-02 (rebrand + lens discount + frames back on sale)

**User's direction (2026-10-02): frames AND lenses are both on sale from now on** — the "lens-only funnel" era is over; frames are no longer hidden.

Shipped (v1 + pos pushed; Render auto-deploys api + web; GitHub Actions builds the APK):
1. **Discount on lens counter sales.** Root cause of "can't find discount": it was only on the *Frames* sale, and the Sell tab opened on Lens behind a dropdown. Backend: `WalkInSale.discountMinor` (nullable, 400 BAD_DISCOUNT if <0 or > price), net price stored (no separate discount column — `ponytail:` note in `walkInSale`); new `POST /api/admin/lens-sales/walk-in/quote` so the app shows Before discount / Discount / Total. New test `walkInDiscountComesOffThePriceAndCantExceedIt`; lens tests green.
2. **POS 1.11.0 / versionCode 16**: Sell tab = big Lenses | Frames segmented switch (not a dropdown); lens sale shows price + discount.
3. **Rebrand (app + web)**: cobalt `#2342F0` primary, navy ink `#0A0F1F`, coral accent, lime `#C8F04B` "lens glint", cool neutrals; Bricolage Grotesque headings (+ Plus Jakarta body); app bevels/heavy shadows → flat hairline cards. Web keeps token NAMES (`clay` is now cobalt) so every page restyled at once.
4. **New logo**: browline frame (heavy brow bar, tapered lenses, keyhole bridge) + lime glint; white on a cobalt tile for launcher icon/favicon. Paths live in `pos/src/ui/Logo.tsx`, `pos/scripts/mark-svg.js`, `frontend/src/components/Logo.tsx`, `frontend/public/favicon.svg` — keep in step. Icons regenerated (`node scripts/generate-icons.js && node scripts/generate-android-icons.js`, sharp --no-save).
5. **Website frames back**: nav Frames / Lenses / Frame Finder + cart icon; new dark hero with Frames (K300–K3,000) / Lenses / Frame Finder cards; featured frames grid + "picked for your face" strip re-enabled.

6. **One combined bill for frame + lenses** (user asked, same day): `POST /api/admin/pos/combined-sales` (`pos/CombinedSaleService`) records the frame sale and the lens walk-in in ONE transaction — a rejected lens half rolls the frame sale + stock back (`CombinedSaleTest`). Frame first, lens last (lens sends the customer WhatsApp as its final step). Each half dedupes on its own clientReference (`<ref>-f` / `<ref>-l`) so offline replay is safe. Frame sale notes get "With lenses LENS-XXXXXXXX". POS Sell tab is now one `CounterSale` screen (Frames card + "Add lenses" card + one Bill: one discount split by value, one payment, one total, one receipt); FrameSell/LensSell deleted. Reports still list the two halves as separate rows (totals correct). 183 backend tests green.

7. **Counter lens orders go to the lab properly + deposits** (V54: `lens_inquiries.pd`, `paid_minor`). Counter now takes Axis R/L (lab only — never sets specialAxis, so price unchanged) and PD. A counter lens order joins the same ORDERED -> READY -> collected ladder as web orders (POS "Lens orders" screen, was "Web orders"), and if a phone was given the customer gets WhatsApp at order / ready / collected (walk-ins used to be silent). "Customer takes them now" keeps the old instant behaviour (must be paid in full). "Paying now" on the bill = deposit: frames are always paid in full, the rest goes against the lenses; `balanceMinor` shown on the order card + receipt, and pickup refuses to close without a payment method while a balance is due (`PAYMENT_NEEDED`). Note: day sales totals still count the lens at full price on the order day (not cash received) — a cash-vs-sales report is a follow-up. 185 backend tests green.

8. **24h-window fallback** (`whatsapp/ServiceWindow`): customer order updates and staff alerts go as plain messages when the number messaged us in the last 24h (free, can't be blocked by billing), template only when the window is shut.
9. **WhatsApp root cause found (2026-10-02 18:06 UTC):** every template send fails with **131042 "WhatsApp Business account currency is not configured"** — the WABA has no billing currency/payment method. USER must set currency + card in Meta Billing hub. This is why Nachi's staff alert never arrived. Render env now has all 4 approved templates (`order_update`, `staff_new_order`, `lens_verify`, `lens_post_purchase`). `WHATSAPP_STAFF_NUMBERS` = user's Indian test number(s) only — add Zambian lab/shop numbers. Prod also logs `payments.provider=mock` → online payment never confirms (needs Flutterwave env or hide option).
10. **DB backup without pg_dump** (specskart-db allow-list blocked this machine even with its IP added): ADMIN-only `GET /api/admin/backup` streams a zip (one CSV per table via COPY in one REPEATABLE READ snapshot + manifest.csv + restore.sql in FK order), "Download database backup" button on the web admin Dashboard. **Taken 2026-10-02 18:45 UTC: 51 tables, 1,921 rows, verified (every CSV matches manifest) → `Downloads\specskart-backup.zip`.** Restore: point the app at the new DB once (Flyway builds schema), then `psql "<url>" -f restore.sql` from the unzipped folder.

Not done / NEXT:
- **User: publish POS 1.11.0 / build 16** from the GitHub Actions artifact (release `v1.11.0` + App releases row: 1.11.0, build 16, minimum 16, mandatory).
- Not device-tested; web checked with headless Edge screenshots only. Only Home/header/footer got a layout redesign — other pages just inherited the new tokens.
- `ui-ux-pro-max` skill was NOT available (not installed; clone blocked) — redesign done without it.
- **specskart-db EXPIRES 2026-10-03** — still the top user action.

_Previous: 2026-10-01. Session: session_01WTgxFowKm8na5mEv3SaWQB_

## RESUME HERE — 2026-09-30 / 10-01 (laptop shut down after this)

All pushed to `main` (Render auto-deploys api + web). Backend tests green (175+), frontend tsc + vitest green, POS tsc green. Nothing device-tested.

Shipped this session (v1 `a066c42` -> `c2051d7` -> `99123ef`; pos `dafbf6a` -> `17f8d77` -> `52ca857`):
1. **Lens pricing = client's 2025 sheet** (V51): 15 SPH-band rows + CYL_EXTRA K200 + 4 RX rows, `LensPricing` picks one row; off-sheet Rx -> 400 PRICE_ON_REQUEST. Clear multifocal priced as Colormatic; multifocal -> RX when SPH>3 / CYL>2 / Add>3 / special axis (user said keep as per sheet). Price edits ADMIN-only (POS More -> Lens pricing).
2. **2nd admin** V52 `shiva@specskart.local` (password given in chat). V52 uses Postgres-only `on conflict`: FlywayMigrationTest skips executing it on H2 — never edit V52 (checksum).
3. **Lead tracking**: bot sends signed personal `/lens?l=&exp=&sig=` link (SignedLinks `lead-lens/<id>`, 30d) -> skips number verification; LENS_* events on the lead timeline per step; POS `lead/[id]` screen (stage, Rx, steps + WhatsApp chat, notes, WhatsApp/Call/send-link); stage on Leads list; Rx + WhatsApp on Web order cards; failed WhatsApp delivery statuses now logged WARN with Meta's code.
4. **Client's frames flow on WhatsApp**: price question -> "frames from K300 up to K3,000, please visit our store" + shop address/map; frames question -> "want to know what suits you?" -> Yes: face-shape chart (`frontend/public/face-shape-chart.jpg`) / Explore: website `/store`.
5. **Store filters**: shape chips (+Square), "Under K500/1000/2000/3000/4000/5000" (`maxPrice`), sort "Price: Low to High / High to Low"; brand on tiles.
6. **Products from POS**: staff + admin add frame with photo (expo-image-picker, NEW native dep), code, brand (V53), price, shape. DELETE stays ADMIN.
7. **Frame sale discount** (K off whole sale, spread per line; backend validates + records subtotal/discount/total).
8. **Walk-in by typed number** (`POST /api/admin/pos/walk-ins/manual`), WALK_IN lead, no marketing opt-in.

NEXT (user):
- Wait for GitHub Actions to build POS **1.10.0 / versionCode 15** (15, not 14 — 14 was never published), create GitHub release `v1.10.0` with `specskart-pos-1.10.0.apk`, then POS More -> App releases: version 1.10.0, build 15, minimum 15, mandatory.
- Verify on Render: https://specskart-web.onrender.com/face-shape-chart.jpg loads; send "price" and "frames" to the bot.
- **specskart-db (free) EXPIRES 2026-10-03** — upgrade in Render or everything is lost.
- Nachi (Patience Nachilima, LENS-92EE70F0) order has Rx all 0.00 — confirm her power on WhatsApp before the lab makes it.


_Last updated: 2026-09-09. Session: session_01LikGxFAD44keaoNy1Mzp7g_

## Session 2026-09-09 c — product-accurate AR try-on: Phase A (Python service)

Client rejected the 2D photo-on-a-plane try-on and asked for a Lenskart-style
AR showing the *exact* selected product, fully automated from the product photo.

**Decision (see `docs/AR_TRYON.md`):** single-image-to-3D (TripoSR/Hunyuan3D) does
NOT work on eyewear — thin arms melt, lenses go opaque, needs 6–24 GB VRAM this
box/Render don't have. Chose **parametric auto-fit**: measure the frame from the
photo (CV), build a parametric 3D frame in mm, texture the front with the real
photo pixels, procedural arms, auto-fit to the face at render (no sliders).
Exact: silhouette, lens/bridge shape, colour, front finish. Approximate: arm
styling + true side thickness. Rimless / angled / busy-bg photos → rejected with
a reason, never a generic substitute.

### Phase A — DONE (committed): `ar-service/` (Python, FastAPI, CPU-only)
- `segment.py` chroma-key cut-out (+ opt-in `AR_USE_REMBG` u2net, experimental).
- `measure.py` — lens contour, width/height, bridge, rim, colour, dark/light,
  rimless + angled-photo rejection. Size anchored to a per-category lens-width table.
- `geometry.py` — trimesh parametric GLB: rims extruded from the measured lens
  contour + face-curved, transparent lens fill, procedural bridge + temple arms,
  a photo-textured front card. GLB carries frame_width_mm etc. in metadata.
- `validate.py` — geometry / bounds 90–175mm / symmetry / <1.5MB / loads-in-three.
- `pipeline.py` + `main.py` (`POST /generate` multipart, `GET /healthz`).
- `tests/test_pipeline.py` — 5 checks green (synthetic fixtures, no pytest dep):
  ratios within tolerance, dark+light colour, round+cateye contours, valid GLB
  produced, rimless blob rejected.
- Verified GLBs re-load in trimesh with the right parts + dims (129×36mm wayfarer,
  121×53mm round). NOT yet viewed in a browser (Chrome ext not connected here) —
  `ar_preview.html` (self-contained three.js viewer, GLBs embedded) is the check.

### Phase B — NOT STARTED — Spring wiring
Migration V14: `products.ar_status|ar_model_version|ar_generation_error|ar_generated_at`
+ `ar_model_files` blob table. `ArAssetService` (@Async → calls ar-service via
RestClient, stores GLB, flips status NONE→PENDING→PROCESSING→READY|FAILED).
Endpoints: `POST /api/admin/ar-assets/{id}/generate|retry`, `GET /api/public/ar-assets/{id}`,
`GET /api/public/ar-models/{id}.glb?v=N` (immutable cache). Auto-trigger from
`AdminCatalogService.addImage`. `AppProperties.Ar(serviceUrl, autoGenerate)`.
Expose ar_status/ar_model_url on the public `ProductDetail` DTO. `ArAssetServiceTest`.

### Phase C — NOT STARTED — frontend rework (`TryOn.tsx` / `tryOnScene.ts`)
Load ONLY the selected product's GLB via GLTFLoader (three examples/jsm). **Delete
the fit sliders** and `buildGlasses`/`buildTexturedFrame` runtime meshing. Auto-fit:
scale = 1.36×IPD (iris landmarks) / model frame_width_mm; position eye-midpoint +
bridge→nose landmark 168; rotation from the existing head matrix; keep the FACE_OVAL
occluder. States: READY→try-on · PENDING→"preparing" (+flat preview if a cut-out
exists) · FAILED/UNAVAILABLE→"not available for this frame", NEVER a generic model.

### Phase D — test with 5+ real catalogue photos → `ar-service/TEST_RESULTS.md`
Seeded products use `picsum.photos` (not glasses) — the client's real photos are
the eval set. Drop them in `ar-service/test_photos/`.

## Session 2026-09-09 b — try-on: lens-front crop + procedural 3D arms

Frontend: tsc + vite build green, 19 vitest green (was 17).

- **Try-on frame model reworked.** The textured frame mapped the *whole* product
  photo onto one curved plane, so the foreshortened temple stubs in the photo
  came along and it read as a sticker. Now `buildTexturedFrame` maps only the
  central lens-front band (`FRAME_MODEL_TUNING.texInsetX`, default 10% off each
  side, via `texture.repeat.x`/`offset.x` + ClampToEdge) and bolts on real
  procedural 3D temple arms — extracted `buildTempleArm()` shared with
  `buildGlasses` — hinged at the front's outer-top, tinted with
  `colourToHex(product.colour)`. `tryOnScene.setFrame` now threads `spec.colour`
  into `buildTexturedFrame`. New knobs: `texInsetX`, `templeLenCm`,
  `templeHingeYFrac`. Tests: 2 new in `glassesModel.test.ts`.
  **Not device-tested (no camera here).** Next if still not convincing:
  per-frame 3D models (vendor / commission).

## Session 2026-09-09 (HEAD `1e7e6bf`, all pushed to `main`, Render auto-deploys web + api)

Backend 50 tests green · frontend tsc + 17 vitest + vite build green.

- **`4610445` — order-status WhatsApp via approved templates + failures visible in admin.**
  A real paid test order sent NO customer confirmation: `OrderNotificationService.onStatus` used
  free-form `sendText`, which Meta only delivers inside the customer's 24h service window, and the
  failure was a swallowed `log.warn`. Now sends via an approved template when one is configured
  (`WHATSAPP_ORDER_UPDATE_TEMPLATE` — {{1}} first name, {{2}} status line, {{3}} order no,
  {{4}} tracking URL; `WHATSAPP_STAFF_ORDER_TEMPLATE` — {{1}} order no, {{2}} amount, {{3}} customer,
  {{4}} admin link), plain-text fallback otherwise. Every failed send now writes a `⚠ …: <reason>`
  OrderEvent → shown red on the admin order timeline (`OrderDetail.tsx`). `OrderNotificationTest`.
  **TODO (user): create + approve the two templates in Meta Business Manager, set the two env vars
  on `specskart-api`.**
- **`e7f626e` — inbound WhatsApp webhook crash + duplicate bot replies.**
  Meta's near-simultaneous re-deliveries all passed the `existsByDedupeKey` check then collided on
  the `webhook_events_dedupe_key_key` unique constraint at commit — after `bot.handleInbound` had
  already sent — so customers got 3–4 identical replies and every duplicate logged a stack trace.
  New `WebhookDedupe.claim()` (REQUIRES_NEW tx + `saveAndFlush`) makes the losing racer fail on the
  insert before any side effect; `process()` treats a thrown `DataAccessException` like "already
  seen". 6-thread concurrency test in `FunnelIntegrationTest`.
- **`456db84` — storefront cart UX.** ProductDetail "Add to bag" adds in place (no jump to /cart) +
  "Added to bag ✓" pop + header cart-icon bump; new **"Buy now"** → straight to `/checkout`. New
  `components/CartIcon.tsx` = persistent header bag icon + live count badge, visible on mobile too.
- **`a93c839`** — visible "Staff login →" link on the store page.
- **`baafba1` — `BackgroundKeyer`** (zero-dep border flood-fill background remover + auto-crop).
  Admin product editor "✨ Generate from main photo" →
  `POST /api/admin/catalog/products/{id}/try-on-image/from-photo` (optional `?tolerance=8..120`);
  direct try-on upload also keys out flat/opaque backgrounds. `BackgroundKeyerTest`. Plain light
  backgrounds only.
- **`ef758f3` + `5a6de16` + `1e7e6bf` — virtual try-on rebuilt as real-time 3D.**
  The old try-on was a 2D affine `ctx.drawImage` of the front PNG (no perspective / yaw / pitch /
  depth). Deleted `lib/tryOn.ts` + its tests. New pipeline:
  - `useFaceLandmarker` / `lib/tryOnScene.ts` `TryOnScene` — FaceLandmarker in **VIDEO mode with
    `outputFacialTransformationMatrixes`** (per-frame 4×4 head pose).
  - `lib/tryOnMatrix.ts` `solveGlassesMatrix()` + `TRY_ON_3D_TUNING` {fovDeg, scale, offsetForwardCm,
    offsetUpCm, flipZ} — the on-device tuning knobs.
  - `lib/glassesModel.ts` — `buildTexturedFrame()` maps the product's cut-out photo onto a
    face-wrapping curved plane (real design + true 3D pose); `buildGlasses()` parametric generic
    mesh tinted by `colourToHex(product.colour)` is the fallback when there's no cut-out.
  - Depth-only **face occluder** from MediaPipe's FACE_OVAL ring so the temples hide behind the
    cheeks/ears (kills most of the "sticker" look).
  - Transparent WebGL canvas over `<video>`; caller CSS-mirrors both layers. `TryOn` is lazy-loaded
    (Three.js + MediaPipe kept out of the main bundle: 153 kB gz main / ~145 kB gz try-on chunk).
  - **"Adjust fit"** panel in the try-on modal — Size / Up-down / Depth sliders write to the live
    scene and persist per browser (`localStorage` `specskart_tryon_fit`).
  - Works on every frame → the "try-on isn't ready for this frame" dead-end is gone; ProductDetail
    always offers try-on, Frame Finder no longer checks for cut-out images.
  - Added `three@0.186`. `glassesModel.test.ts` + `tryOnMatrix.test.ts`.
  - **Status: tracks + renders on device, but user still finds the photo-on-a-plane approach flat.
    Known ceiling — a front photo's temple arms are foreshortened stubs and can't become true 3D
    arms. Next level = per-frame 3D models (commission or a try-on vendor). Open middle-ground idea:
    crop texture to the lens-front + bolt on procedural 3D arms.**

_Earlier: Last updated 2026-09-08 (Phase 2 complete). Session: session_01Tne6a6brsdoue2fDNsWxqQ_

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

### 2026-09-08 — frontend polish (committed)
- **vite dev optimizer no longer hangs on mediapipe**: `optimizeDeps.include: ['@mediapipe/tasks-vision']`
  in vite.config.ts forces it to pre-bundle at startup instead of discovering it mid-session
  (that discovery caused the "bundling dependencies…" stall + ~14s reload loop).
- **White flash on light↔dark route swap reduced**: Google Fonts moved from a render-blocking
  `@import` in index.css to `<link rel="preconnect"/stylesheet">` in index.html; `html { background:#f6f3ee }`
  inline so a torn-down layout never shows the browser's white default. `color-scheme: light`.
- tsc -b clean, `vite build` clean (520 kB / 154 kB gzip). No backend change.

### 2026-09-09 — order alerts + AR try-on accuracy + upgrade plan (committed)
- **Staff order alert.** `WHATSAPP_STAFF_NUMBERS` (comma-separated env var, blank = off) → every
  listed number gets a plain WhatsApp when an order is paid: order no, items, total, customer,
  `/admin/orders/{id}` link. `AppProperties.WhatsApp.staffNumbers` (List<String>),
  `OrderNotificationService.notifyNewOrder`, called from `CheckoutService.confirmPayment` after the
  customer message. `StaffOrderAlertTest` green.
- **Customer status updates: already complete** — `updateStatus` → `onStatus` sends a personalised
  WhatsApp on every transition. No change.
- **AR try-on accuracy rewrite** (`lib/tryOn.ts`): width from the temple silhouette (234/454) not a
  magic eye-span multiple; centred on the nose bridge (168) which carries yaw/pitch; roll from
  4-point eye centres; new `smoothPlacement` EMA. `useFaceLandmarker().track` = non-throwing
  single detection for a loop. **`TryOn.tsx` is now a live camera overlay** (~8 fps) with
  "Freeze this look" to compare/share; frame strip works live and frozen. `TRY_ON_TUNING` still
  the one knob but defaults are anatomy-derived. Real-device pass = polish, not a blocker.
  Backend 46 green, frontend 17 green, both builds clean.
- **`docs/UPGRADE_PLAN.md`** — full review of chat-interaction upgrades (track-order intent, list
  messages, in-chat cart, human handoff), web UX changes, feature backlog, and the ops/security
  debt, ordered by value. Read that for what's next.
- Still not started: `followUpAt` date reminders (Lead field + V14 + endpoint + real dashboard
  "due" count).

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
