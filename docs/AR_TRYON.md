# Product-accurate AR glasses try-on

Status: **in build** (2026-09-09). Approach **C** — parametric 3D auto-fit from the
product photo, chosen after ruling out single-image-to-3D (see "Why not photo→GLB").

---

## 1. The problem with what was here before

The old try-on had two paths, both wrong:

| Product state | Old behaviour | Why it's wrong |
|---|---|---|
| No cut-out image | Generic parametric mesh (`buildGlasses`) tinted by colour name | **Silent substitution.** Not the product. Unknown colour → dark grey rectangle ("the purple/black frame"). |
| Has cut-out PNG | Whole photo pasted on a curved `PlaneGeometry` (`buildTexturedFrame`) | 2D image sheared by any head turn; temple arms are foreshortened stubs from the front photo with no real geometry; scale/depth are hardcoded constants the user nudged with sliders. |

Root cause: **3D AR driven by 2D data.** A single front photo carries no frame
thickness, bridge depth, lens curvature, or temple geometry. MediaPipe tracks the
face; it does not build a model of the product.

## 2. Why not photo → GLB (single-image-to-3D)

TripoSR / Hunyuan3D / Stable-Fast-3D / single-image Meshy are trained on solid,
opaque, blobby objects. On eyewear they reliably produce: temple arms fused to
the head or missing (they're ~2 mm wire, below the model's resolution), opaque
lens lumps, wrong thickness. Every model would fail the "matches the photo"
validation gate. Also: they need 6–24 GB VRAM; this box has 8 GB RAM and no GPU,
and Render has no GPU tier. The "one photo → accurate GLB → no GPU" combination
is not hard, it's impossible. Lenskart et al. use manufacturer CAD or
photogrammetry rigs via a paid SaaS (Fittingbox/Ditto), never a marketing photo.

## 3. Approach C — what we actually build

```
Admin uploads product photo(s)           [exists: product_images / product_image_files]
        │
        ▼
Python AR service  (ar-service/, FastAPI, CPU-only)
   1. segment the frame from the backdrop            (rembg u2net, CPU)
   2. measure it:  total width · lens width/height · bridge · rim thickness ·
      lens-opening contour · dominant colour · rimless? · symmetry check
   3. build a parametric 3D frame in millimetres:
        - rims = the measured lens-opening contour, extruded to the measured
          rim thickness, curved on the face (base curve ~6)
        - bridge + hinges + temple arms (140 mm, splay by frame category)
        - front rim faces UV-mapped to the cropped photo  →  the real colour,
          pattern and finish are the product's own pixels
        - arm + side material = sampled solid colour
   4. validate the GLB (geometry non-empty, bounds 100–160 mm wide, loads in
      three, < 1.5 MB, materials present, left/right symmetric)
        │
        ▼
Spring backend
   - products.ar_status / ar_model_url / ar_model_version / ar_generated_at /
     ar_generation_error   (migration V14)
   - ar_model_files  (GLB bytes, isolated like product_image_files)
   - async orchestration: on photo upload / admin "Generate", POST the image to
     ar-service, store the GLB, flip status.  Never blocks the product API.
        │
        ▼
Frontend  (TryOn.tsx)
   - GLTFLoader loads ONLY the selected product's GLB
   - auto-fit, no sliders:
        scale    = desiredFrameWidth / modelFrameWidth,
                   desiredFrameWidth ≈ 1.36 × measured IPD (from iris landmarks)
        position = eye-midpoint, model bridge → nose-bridge landmark 168,
                   +Z a few mm off the face-mesh depth
        rotation = MediaPipe facial-transformation matrix
   - occlusion: existing FACE_OVAL depth patch
   - states: READY → try-on · PENDING → "preparing, check back" (+ flat preview
     if a cut-out exists) · FAILED / UNAVAILABLE → "not available for this frame",
     NEVER a generic model
```

### Accuracy ceiling (stated honestly)

- **Exact:** front silhouette, lens shape, bridge shape/width, colour, pattern,
  finish — these come straight from the product's own photo.
- **Approximate:** temple-arm styling and true side-profile thickness — a front
  photo doesn't contain them; arms are procedural, category-appropriate, colour-
  matched. Good enough near-frontal (the try-on use case); a purist side view
  will show a generic arm.
- **Rejected, not faked:** rimless frames, busy-background photos, multi-frame
  photos, and anything that fails validation → `FAILED` with a reason, shown to
  the admin, `UNAVAILABLE` to the customer. No substitute.
- **Upgrade path:** when the client can supply a side + angled photo per SKU,
  the same service does multi-view photogrammetry for real arm geometry; when
  there's budget, swap the generator for Fittingbox. DB + API + frontend don't
  change.

## 4. Services / ports

| Service | Port | Runs |
|---|---|---|
| Spring backend | 8080 | Render web service (existing) |
| React frontend | 5173 dev / static | Render static (existing) |
| **ar-service** | 8000 | Render web service (new, CPU) or local; `AR_SERVICE_URL` on the backend |

## 5. Hardware / hosting

- ar-service is **CPU-only**: rembg u2net ≈ 180 MB model, ~1–2 s/image; parametric
  meshing is milliseconds. Peak RAM < 1.5 GB. Fine on an 8 GB laptop and on
  Render's 512 MB–2 GB instances (u2net fits in 512 MB; use the 1 GB plan for headroom).
- No GPU anywhere. No large model downloads beyond u2net.
- Generation is one-shot per SKU (re-run only on photo change), so throughput is
  a non-issue.

## 6. API surface

```
POST /api/admin/ar-assets/{productId}/generate     → 202, status PENDING
GET  /api/public/ar-assets/{productId}             → { status, modelUrl, version, error }
POST /api/admin/ar-assets/{productId}/retry        → 202
GET  /api/public/ar-models/{productId}.glb?v=N     → the GLB bytes (cache-forever on v)

ar-service:
POST /generate   (multipart: productId, frameCategory, colour, images[])
                 → { glb (base64) | error, measurements }
GET  /healthz
```

## 7. Test plan (deliverable)

5+ real frame photos across types (rectangular acetate, round metal, thin-rim
metal, cat-eye, aviator). For each: correct product loaded · silhouette matches
the photo · tracks head movement · < 1 s model load · no jitter/float · never
swaps frame · works on mobile Chrome/Safari. Results logged in
`ar-service/TEST_RESULTS.md`. A photo whose generated model doesn't match is
`FAILED`, not `READY`.
