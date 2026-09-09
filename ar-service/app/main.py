"""
Specskart AR generation service.

POST /generate  (multipart)
    product_id      : str   (echoed back, for the caller's logs)
    frame_category  : str   (optional; sets nominal lens width)
    images          : file[] (1+ product photos; front-on shot preferred)
  -> 200 { ok, product_id, glb_base64, measurements }
  -> 422 { ok: false, product_id, error, measurements }   (photo unusable)

GET /healthz -> { status: "ok" }

Stateless. The Spring backend owns product<->model mapping, storage and status.
"""
from __future__ import annotations

import base64

from fastapi import FastAPI, Form, UploadFile
from fastapi.responses import JSONResponse

from .pipeline import generate

app = FastAPI(title="Specskart AR service", version="1")

_MAX_IMAGE_BYTES = 12 * 1024 * 1024


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


@app.post("/generate")
async def generate_endpoint(
    product_id: str = Form(...),
    frame_category: str | None = Form(None),
    images: list[UploadFile] = [],  # noqa: B006  (FastAPI needs the mutable default)
):
    payloads: list[bytes] = []
    for up in images:
        data = await up.read()
        if len(data) > _MAX_IMAGE_BYTES:
            return JSONResponse(status_code=413, content={
                "ok": False, "product_id": product_id, "error": f"{up.filename} exceeds 12 MB"})
        payloads.append(data)

    res = generate(payloads, frame_category)
    body = {"ok": res.ok, "product_id": product_id, "measurements": res.measurements}
    if not res.ok:
        body["error"] = res.error
        return JSONResponse(status_code=422, content=body)
    body["glb_base64"] = base64.b64encode(res.glb).decode()
    return body
