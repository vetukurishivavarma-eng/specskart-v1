# Specskart AR generation service

Turns a product photo into a **product-accurate parametric 3D frame (GLB)** for
the virtual try-on. Separate from the Spring backend on purpose — it does image
work, not business logic. CPU-only, no GPU, no cloud AI.

See [`../docs/AR_TRYON.md`](../docs/AR_TRYON.md) for why this approach (parametric,
not single-image-to-3D) and the accuracy ceiling.

## What it does

```
photo bytes ─▶ segment (chroma key / optional u2net)
            ─▶ measure  (lens outline · width · height · bridge · rim · colour)
            ─▶ build    (trimesh parametric frame in mm, front card textured
                         with the real photo, procedural bridge + temple arms)
            ─▶ validate (geometry, bounds 90–175 mm, symmetry, < 1.5 MB, loads)
            ─▶ GLB bytes  |  or a reason it was rejected
```

Exact from the photo: silhouette, lens/bridge shape and ratios, colour, and the
front finish (real pixels on the front card). Procedural: temple-arm styling and
true side thickness — a front photo doesn't contain them. Rimless frames, angled
shots and busy backgrounds are **rejected with a reason**, never faked.

## Run

```bash
cd ar-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt          # Linux/Mac: .venv/bin/pip
.venv/Scripts/python -m uvicorn app.main:app --port 8000
```

Test:
```bash
.venv/Scripts/python tests/test_pipeline.py             # 5 checks, no pytest needed
curl -F product_id=demo -F frame_category=WAYFARER \
     -F images=@test_photos/frame.jpg http://localhost:8000/generate
```

## API

| | |
|---|---|
| `POST /generate` | multipart: `product_id`, `frame_category` (optional), `images[]` (1+). → `200 {ok, glb_base64, measurements}` or `422 {ok:false, error, measurements}` |
| `GET /healthz` | `{status:"ok"}` |

Stateless. The Spring backend owns product↔model mapping, storage and status.

## Config

| env | default | |
|---|---|---|
| `AR_USE_REMBG` | off | `1` = use the u2net matte instead of the chroma key, for photos on busy / uneven backgrounds. Downloads `u2net.onnx` (~176 MB) on first use. Experimental — its lens-opening recovery is still rough on thin rims; the chroma key is the tested default and is right for plain catalogue backdrops. |

## Hardware

CPU only. Peak RAM < 1 GB (chroma-key path) / < 1.5 GB (rembg). Parametric
meshing is milliseconds; segmentation is the cost (~0.3 s chroma, ~2 s rembg).
One generation per SKU, re-run only on photo change — throughput is not a concern.
Runs fine on an 8 GB laptop and on a Render 1 GB instance. **No GPU anywhere.**

## Deploy (Render, new web service)

- Root: `ar-service/`
- Build: `pip install -r requirements.txt`
- Start: `python -m uvicorn app.main:app --host 0.0.0.0 --port $PORT`
- Plan: 1 GB (512 MB works for the chroma-key path only)
- Set `AR_SERVICE_URL=https://<this-service>.onrender.com` on `specskart-api`.

## Test photos

`test_photos/` is git-ignored. Drop the client's **real catalogue photos** there
for the formal eval (see `docs/AR_TRYON.md` §7 / `TEST_RESULTS.md`). Synthetic
fixtures in `tests/fixtures.py` cover the measurement maths deterministically.
