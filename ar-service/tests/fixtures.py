"""Synthetic front-on frame photos with known geometry, for the measurement tests."""
from __future__ import annotations

import io

from PIL import Image, ImageDraw


def synthetic_frame(
    shape: str = "rect",
    lens_w: int = 210,
    lens_h: int = 150,
    bridge: int = 40,
    rim: int = 14,
    colour: tuple[int, int, int] = (30, 30, 34),
    bg: tuple[int, int, int] = (244, 244, 246),
) -> tuple[bytes, dict]:
    """Return (jpeg_bytes, ground_truth). Ground truth is in pixels."""
    total_w = lens_w * 2 + bridge
    pad = 120
    W, H = total_w + pad * 2, lens_h + pad * 2
    img = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(img)

    cy = H // 2
    left_cx = pad + lens_w // 2
    right_cx = pad + lens_w + bridge + lens_w // 2

    def lens(cx: int) -> tuple[int, int, int, int]:
        return (cx - lens_w // 2, cy - lens_h // 2, cx + lens_w // 2, cy + lens_h // 2)

    for cx in (left_cx, right_cx):
        box = lens(cx)
        if shape == "round":
            d.ellipse(box, outline=colour, width=rim)
        elif shape == "cateye":
            d.rounded_rectangle(box, radius=lens_h // 2, outline=colour, width=rim)
            d.polygon([(box[0], box[1]), (box[0] - 24, box[1] - 18), (box[0] + 30, box[1] + 6)], fill=colour)
        else:
            d.rounded_rectangle(box, radius=18, outline=colour, width=rim)

    # bridge
    d.line([(left_cx + lens_w // 2 - rim, cy - lens_h // 4),
            (right_cx - lens_w // 2 + rim, cy - lens_h // 4)], fill=colour, width=rim)
    # temple stubs (foreshortened, like a real front photo)
    d.line([(pad, cy - lens_h // 4), (pad - 26, cy - lens_h // 4)], fill=colour, width=rim)
    d.line([(W - pad, cy - lens_h // 4), (W - pad + 26, cy - lens_h // 4)], fill=colour, width=rim)

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=92)
    return buf.getvalue(), {
        "lens_w": lens_w, "lens_h": lens_h, "bridge": bridge, "rim": rim,
        "total_w": total_w, "colour": colour,
    }
