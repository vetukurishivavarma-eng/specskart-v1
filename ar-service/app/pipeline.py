"""photo bytes -> (glb bytes | error), with a measurements report."""
from __future__ import annotations

from dataclasses import asdict, dataclass

from PIL import Image

from .geometry import build_glb
from .measure import measure
from .segment import cutout
from .validate import validate


@dataclass
class Result:
    ok: bool
    glb: bytes | None = None
    error: str | None = None
    measurements: dict | None = None


def generate(images: list[bytes], frame_category: str | None = None) -> Result:
    if not images:
        return Result(ok=False, error="no product images supplied")

    # The front-on shot is the one whose cut-out is widest and least tall-skewed;
    # for a single image this is a no-op.
    best_cut: Image.Image | None = None
    best_score = -1.0
    for data in images:
        try:
            cut = cutout(data)
        except Exception as e:  # noqa: BLE001
            continue
        a = _alpha_bbox(cut)
        if a is None:
            continue
        w, h = a
        score = w * (1.0 if 1.6 <= w / max(h, 1) <= 4.5 else 0.5)
        if score > best_score:
            best_score, best_cut = score, cut

    if best_cut is None:
        return Result(ok=False, error="could not segment a frame from any supplied photo")

    m = measure(best_cut, frame_category)
    report = {k: v for k, v in asdict(m).items()
              if k not in ("left_contour_mm", "right_contour_mm")}
    if not m.ok:
        return Result(ok=False, error=m.reason, measurements=report)

    try:
        glb = build_glb(m, best_cut)
    except Exception as e:  # noqa: BLE001
        return Result(ok=False, error=f"model build failed: {e}", measurements=report)

    good, why = validate(glb)
    if not good:
        return Result(ok=False, error=why, measurements=report)

    return Result(ok=True, glb=glb, measurements=report)


def _alpha_bbox(img: Image.Image):
    bbox = img.split()[-1].getbbox()
    if bbox is None:
        return None
    return bbox[2] - bbox[0], bbox[3] - bbox[1]
