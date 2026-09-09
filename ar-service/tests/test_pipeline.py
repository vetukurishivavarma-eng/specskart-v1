"""
Run: ar-service/.venv/Scripts/python -m pytest   (or just: python tests/test_pipeline.py)

No pytest dependency required — plain asserts + a __main__ runner.
"""
from __future__ import annotations

import io
import sys
from pathlib import Path

import trimesh

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.measure import measure  # noqa: E402
from app.pipeline import generate  # noqa: E402
from app.segment import cutout  # noqa: E402
from tests.fixtures import synthetic_frame  # noqa: E402


def _measure(**kw):
    png, gt = synthetic_frame(**kw)
    m = measure(cutout(png), gt_category(kw))
    assert m.ok, m.reason
    return m, gt


def gt_category(kw):
    return {"round": "ROUND_FRAME", "cateye": "CATEYE"}.get(kw.get("shape", "rect"), "RECTANGLE")


def test_measures_ratios_within_tolerance():
    m, gt = _measure(shape="rect", lens_w=210, lens_h=150, bridge=40, rim=14)
    ppmm = m.px_per_mm
    # The measurable lens *opening* is the drawn path minus one rim each side.
    exp_lens_w = gt["lens_w"] - gt["rim"]
    exp_lens_h = gt["lens_h"] - gt["rim"]
    exp_bridge = gt["bridge"] + gt["rim"]              # gap between openings
    exp_front_w = 2 * gt["lens_w"] + gt["bridge"]      # outer lens edge to outer lens edge
    assert abs(m.lens_width_mm * ppmm - exp_lens_w) / exp_lens_w < 0.15
    assert abs(m.lens_height_mm * ppmm - exp_lens_h) / exp_lens_h < 0.18
    assert abs(m.bridge_mm * ppmm - exp_bridge) / exp_bridge < 0.4
    assert abs(m.total_width_mm * ppmm - exp_front_w) / exp_front_w < 0.15
    # the frame-front width must exclude the temple stubs in the photo
    assert m.total_width_mm * ppmm < 2 * gt["lens_w"] + gt["bridge"] + 6 * gt["rim"]
    print(f"  rect: lens {m.lens_width_mm:.0f}mm  bridge {m.bridge_mm:.0f}mm  front {m.total_width_mm:.0f}mm")


def test_reads_dark_and_light_colour():
    dark, _ = _measure(colour=(24, 24, 28))
    assert dark.is_dark
    light, _ = _measure(colour=(196, 170, 120))  # tortoise-ish
    assert not light.is_dark
    assert abs(light.frame_colour[0] - 196) < 45
    print(f"  colour: dark={dark.frame_colour}  light={light.frame_colour}")


def test_round_and_cateye_shapes_measure():
    for shape in ("round", "cateye"):
        m, gt = _measure(shape=shape, lens_w=170, lens_h=170 if shape == "round" else 120)
        assert len(m.left_contour_mm) >= 4
        print(f"  {shape}: {len(m.left_contour_mm)} contour pts, lens {m.lens_width_mm:.0f}mm")


def test_generate_produces_a_valid_glb():
    png, _ = synthetic_frame(shape="rect")
    res = generate([png], "RECTANGLE")
    assert res.ok, res.error
    assert res.glb and len(res.glb) < 1_500_000

    scene = trimesh.load(io.BytesIO(res.glb), file_type="glb")
    names = set(scene.geometry)
    assert {"rim_L", "rim_R", "temple_L", "temple_R", "bridge"} <= names, names
    assert "front" in names  # the textured real-photo card
    w, h, d = scene.bounds[1] - scene.bounds[0]
    assert 90 <= w <= 175 and d >= 8, (w, h, d)
    print(f"  glb: {len(res.glb) // 1024}KB  {len(names)} parts  {w:.0f}×{h:.0f}×{d:.0f}mm")


def test_rejects_a_photo_with_no_lens_openings():
    # a filled blob -> no closed openings -> must fail, not fake a frame
    from PIL import Image, ImageDraw
    img = Image.new("RGB", (600, 300), (245, 245, 245))
    ImageDraw.Draw(img).ellipse((100, 80, 500, 220), fill=(20, 20, 20))
    buf = io.BytesIO(); img.save(buf, "JPEG")
    res = generate([buf.getvalue()], "RECTANGLE")
    assert not res.ok
    print(f"  rejected blob: {res.error!r}")


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for fn in fns:
        print(fn.__name__)
        fn()
    print(f"\n{len(fns)} passed")
