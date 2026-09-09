"""
Gate: a generated GLB is only READY if it passes every check here. A failure
returns a reason the admin sees; the customer never gets the model.
"""
from __future__ import annotations

import io

import numpy as np
import trimesh

MAX_BYTES = 1_500_000


def validate(glb: bytes) -> tuple[bool, str | None]:
    if not glb or len(glb) < 800:
        return False, "GLB is empty or truncated"
    if len(glb) > MAX_BYTES:
        return False, f"GLB too large for the web ({len(glb) // 1024} KB > {MAX_BYTES // 1024} KB)"

    try:
        scene = trimesh.load(io.BytesIO(glb), file_type="glb")
    except Exception as e:  # noqa: BLE001
        return False, f"GLB will not load: {e}"

    geoms = list(scene.geometry.values()) if hasattr(scene, "geometry") else [scene]
    tris = sum(len(g.faces) for g in geoms if hasattr(g, "faces"))
    if tris < 200:
        return False, f"model has almost no geometry ({tris} triangles)"

    ext = scene.bounds if hasattr(scene, "bounds") else geoms[0].bounds
    if ext is None:
        return False, "model has no bounding box"
    w, h, d = (ext[1] - ext[0])
    if not (90 <= w <= 175):
        return False, f"frame width {w:.0f}mm is outside the plausible 90–175mm"
    if not (18 <= h <= 75):
        return False, f"frame height {h:.0f}mm is outside the plausible 18–75mm"
    if d < 8:
        return False, f"model is nearly flat (depth {d:.0f}mm) — temples missing"

    # Roughly left/right symmetric about x=0.
    cx = (ext[0][0] + ext[1][0]) / 2
    if abs(cx) > 0.18 * w:
        return False, "model is not centred between the lenses"

    names = set(scene.geometry) if hasattr(scene, "geometry") else set()
    if not any(n.startswith("rim") for n in names):
        return False, "no rim geometry"
    if not any(n.startswith("temple") for n in names):
        return False, "no temple arms"

    return True, None
