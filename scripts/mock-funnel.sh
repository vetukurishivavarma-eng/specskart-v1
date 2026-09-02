#!/usr/bin/env bash
# End-to-end Phase 1 funnel against a locally running backend (dev/mock profile).
# Requires: backend on :8080, python3 on PATH. No Meta credentials needed.
set -e
B="${1:-http://localhost:8080}"
j() { python -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

echo "== admin login =="
TOKEN=$(curl -s $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@specskart.local","password":"admin12345"}' | j "['token']")
AUTH="Authorization: Bearer $TOKEN"

echo "== simulate Facebook click-to-WhatsApp inbound =="
curl -s $B/api/sim/whatsapp/inbound -H 'Content-Type: application/json' -d '{
  "waId":"260977123456","name":"Chanda Mwansa","text":"Hi",
  "referral":{"source_id":"fb-sep-frames","source_url":"https://fb.com/ad/123",
              "ctwa_clid":"abc123","utm_source":"facebook","utm_campaign":"sep_frames_fb"}
}' >/dev/null

echo "== press FIND_FRAMES, capture the Frame Finder link =="
OUT=$(curl -s $B/api/sim/whatsapp/inbound -H 'Content-Type: application/json' \
  -d '{"waId":"260977123456","buttonId":"FIND_FRAMES"}')
TOK=$(echo "$OUT" | python -c "import sys,json,re;d=json.load(sys.stdin);\
print(re.search(r's=([A-Za-z0-9_-]+)',[m['text'] for m in d['outbox'] if m['text'] and 'frame-finder' in m['text']][-1]).group(1))")
echo "   session token: ${TOK:0:16}..."

echo "== consent + analysis =="
curl -s "$B/api/frame-finder/session/$TOK/consent" -H 'Content-Type: application/json' \
  -d '{"cameraConsent":true,"photoProcessingConsent":true}' >/dev/null
curl -s "$B/api/frame-finder/session/$TOK/analysis" -H 'Content-Type: application/json' -d '{
  "geometry":{"faceWidthRatio":0.82,"foreheadWidthRatio":0.70,"cheekboneWidthRatio":0.82,
              "jawWidthRatio":0.72,"jawAngleDeg":150,"chinRatio":0.20}}' | j "['faceShapeDisplay']"

echo "== CRM: the lead and its journey =="
LID=$(curl -s "$B/api/admin/leads?q=Chanda" -H "$AUTH" | j "['content'][0]['id']")
curl -s "$B/api/admin/leads/$LID" -H "$AUTH" | python -c "
import sys,json;d=json.load(sys.stdin)
print('  name       :', d['lead']['name'])
print('  source     :', d['lead']['source'], '/', d['lead']['campaignName'])
print('  face shape :', d['lead']['faceShape'], d['lead']['recommendedFrames'])
print('  status     :', d['lead']['status'])
print('  journey    :', ' -> '.join(t['type'] for t in d['timeline']))
"
echo "== dashboard =="
curl -s "$B/api/admin/analytics/dashboard" -H "$AUTH" | j ""
