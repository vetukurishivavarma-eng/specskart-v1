"""
Measure a frame from its RGBA cut-out.

Everything the parametric model needs comes from the alpha mask and the opaque
pixels: the frame's outer silhouette, the two lens openings (their contours,
widths, heights), the bridge, rim thickness, and the dominant colour. Ratios
are exact; absolute size is anchored to an assumed lens width (the frontend
re-scales to the actual face at try-on, so only proportions matter).

Anything that can't be measured cleanly -> ok=False with a reason. The caller
must NOT fall back to a generic frame.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np
from PIL import Image

# Assumed lens width (mm) per frame-category code, to turn pixels into mm.
# Only sets the model's nominal size; proportions come from the photo.
_LENS_WIDTH_MM = {
    "OVERSIZED": 57.0, "AVIATOR": 58.0, "ROUND_FRAME": 47.0, "OVAL_FRAME": 52.0,
    "CATEYE": 53.0, "RECTANGLE": 52.0, "WAYFARER": 52.0, "GEOMETRIC": 51.0,
    "BROWLINE": 50.0, "THIN_RIM": 49.0,
}
_DEFAULT_LENS_WIDTH_MM = 52.0


@dataclass
class Measurements:
    ok: bool
    reason: str | None = None
    px_per_mm: float = 1.0
    total_width_mm: float = 0.0
    total_height_mm: float = 0.0
    lens_width_mm: float = 0.0
    lens_height_mm: float = 0.0
    bridge_mm: float = 0.0
    rim_thickness_mm: float = 0.0
    frame_colour: tuple[int, int, int] = (40, 40, 44)
    is_dark: bool = True
    rimless: bool = False
    # lens-opening outlines, in mm, each centred on its own lens centre (+x right, +y up)
    left_contour_mm: list[tuple[float, float]] = field(default_factory=list)
    right_contour_mm: list[tuple[float, float]] = field(default_factory=list)
    # pixel-space, for texturing the front card
    bbox_px: tuple[int, int, int, int] = (0, 0, 0, 0)  # x, y, w, h
    lens_centres_px: tuple[tuple[float, float], tuple[float, float]] = ((0, 0), (0, 0))


def _fail(reason: str) -> Measurements:
    return Measurements(ok=False, reason=reason)


def measure(cut: Image.Image, frame_category: str | None = None) -> Measurements:
    rgba = np.asarray(cut.convert("RGBA"))
    solid = rgba[:, :, 3] > 60
    H, W = solid.shape
    if solid.sum() < 0.004 * H * W:
        return _fail("cut-out is almost empty — segmentation found no frame")

    # Largest opaque blob = the frame.
    n, lbl, stats, _ = cv2.connectedComponentsWithStats(solid.astype(np.uint8), 8)
    if n < 2:
        return _fail("no connected frame region")
    fi = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    frame = lbl == fi
    fx, fy, fw, fh, farea = stats[fi]
    if fw < 0.2 * W or fw > 0.99 * W or fh < 0.08 * H:
        return _fail(f"frame region looks wrong ({fw}x{fh}px in {W}x{H})")

    # Lens openings = holes fully enclosed by the frame (transparent, not touching
    # the frame's bounding box border).
    inner = np.zeros((H, W), np.uint8)
    inner[fy:fy + fh, fx:fx + fw] = (~solid[fy:fy + fh, fx:fx + fw]).astype(np.uint8)
    ln, llbl, lstats, lcent = cv2.connectedComponentsWithStats(inner, 8)
    holes = []
    for i in range(1, ln):
        x, y, w, h, area = lstats[i]
        touches = x <= fx + 1 or y <= fy + 1 or x + w >= fx + fw - 1 or y + h >= fy + fh - 1
        if not touches and area > 0.02 * farea:
            holes.append((area, i, (x, y, w, h), tuple(lcent[i])))
    holes.sort(reverse=True)

    dark_px = _dominant_colour(rgba, frame)
    colour = dark_px
    is_dark = (0.299 * colour[0] + 0.587 * colour[1] + 0.114 * colour[2]) < 90

    if len(holes) < 2:
        # One opening spanning most of the width can still be a real pair with a
        # very thin/!metal bridge; otherwise it's rimless / not a usable front shot.
        if len(holes) == 1 and holes[0][2][2] > 0.55 * fw:
            return _split_single_opening(frame, llbl, holes[0], (fx, fy, fw, fh),
                                         farea, colour, is_dark, frame_category)
        return Measurements(ok=False, rimless=True,
                            reason="no closed lens openings — rimless frame or not a front-on photo")

    # Two biggest holes, ordered left→right by centroid x.
    (a1, i1, b1, c1), (a2, i2, b2, c2) = holes[0], holes[1]
    if c1[0] > c2[0]:
        i1, b1, c1, i2, b2, c2 = i2, b2, c2, i1, b1, c1

    lw = np.mean([b1[2], b2[2]])
    lh = np.mean([b1[3], b2[3]])
    if not 0.7 <= b1[2] / b2[2] <= 1.43:
        return _fail("left and right lenses differ too much — photo may be angled")
    bridge = max(0.0, b2[0] - (b1[0] + b1[2]))

    rim_px = _rim_thickness(frame, (fx, fy, fw, fh))

    # Frame-front width = across the outer lens edges + a rim each side. The raw
    # blob bbox would also include the foreshortened temple stubs in the photo.
    front_w = (b2[0] + b2[2]) - b1[0] + 2 * rim_px
    front_h = max(b1[3], b2[3]) + 2 * rim_px

    lens_width_mm = _LENS_WIDTH_MM.get((frame_category or "").upper(), _DEFAULT_LENS_WIDTH_MM)
    px_per_mm = lw / lens_width_mm

    m = Measurements(
        ok=True,
        px_per_mm=px_per_mm,
        total_width_mm=front_w / px_per_mm,
        total_height_mm=front_h / px_per_mm,
        lens_width_mm=lw / px_per_mm,
        lens_height_mm=lh / px_per_mm,
        bridge_mm=bridge / px_per_mm,
        rim_thickness_mm=float(np.clip(rim_px / px_per_mm, 1.0, 12.0)),
        frame_colour=colour,
        is_dark=is_dark,
        bbox_px=(int(b1[0] - rim_px), int(min(b1[1], b2[1]) - rim_px),
                 int(round(front_w)), int(round(front_h))),
        lens_centres_px=(tuple(map(float, c1)), tuple(map(float, c2))),
    )
    m.left_contour_mm = _contour_mm(llbl == i1, c1, px_per_mm)
    m.right_contour_mm = _contour_mm(llbl == i2, c2, px_per_mm)

    if not (95 <= m.total_width_mm <= 170 and 18 <= m.lens_height_mm <= 60):
        return _fail(f"measured size out of range (w={m.total_width_mm:.0f}mm h={m.lens_height_mm:.0f}mm)")
    return m


def _split_single_opening(frame, llbl, hole, fbbox, farea, colour, is_dark, cat):
    _, i, (x, y, w, h), _ = hole
    m = (llbl == i).astype(np.uint8)
    col_density = m.sum(axis=0)
    mid = x + int(np.argmin(col_density[x:x + w])) if w else x
    left = m.copy(); left[:, mid:] = 0
    right = m.copy(); right[:, :mid] = 0
    if left.sum() < 0.15 * m.sum() or right.sum() < 0.15 * m.sum():
        return Measurements(ok=False, rimless=True, reason="lens openings not separable")
    lb = cv2.boundingRect(left); rb = cv2.boundingRect(right)
    lens_width_mm = _LENS_WIDTH_MM.get((cat or "").upper(), _DEFAULT_LENS_WIDTH_MM)
    lw = np.mean([lb[2], rb[2]]); px_per_mm = lw / lens_width_mm
    fx, fy, fw, fh = fbbox
    lc = (lb[0] + lb[2] / 2, lb[1] + lb[3] / 2)
    rc = (rb[0] + rb[2] / 2, rb[1] + rb[3] / 2)
    out = Measurements(
        ok=True, px_per_mm=px_per_mm,
        total_width_mm=fw / px_per_mm, total_height_mm=fh / px_per_mm,
        lens_width_mm=lw / px_per_mm, lens_height_mm=np.mean([lb[3], rb[3]]) / px_per_mm,
        bridge_mm=max(0.0, rb[0] - (lb[0] + lb[2])) / px_per_mm,
        rim_thickness_mm=float(np.clip(_rim_thickness(frame, fbbox) / px_per_mm, 1.0, 12.0)),
        frame_colour=colour, is_dark=is_dark, bbox_px=(fx, fy, fw, fh),
        lens_centres_px=(lc, rc),
    )
    out.left_contour_mm = _contour_mm(left.astype(bool), lc, px_per_mm)
    out.right_contour_mm = _contour_mm(right.astype(bool), rc, px_per_mm)
    return out


def _contour_mm(mask: np.ndarray, centre_px, px_per_mm: float) -> list[tuple[float, float]]:
    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not cnts:
        return []
    c = max(cnts, key=cv2.contourArea)
    eps = 0.012 * cv2.arcLength(c, True)
    c = cv2.approxPolyDP(c, eps, True).reshape(-1, 2).astype(np.float32)
    cx, cy = centre_px
    # image y is down; model y is up
    return [((px - cx) / px_per_mm, (cy - py) / px_per_mm) for px, py in c]


def _rim_thickness(frame: np.ndarray, bbox) -> float:
    dt = cv2.distanceTransform(frame.astype(np.uint8), cv2.DIST_L2, 3)
    ridge = dt[dt > 0.5]
    return float(np.percentile(ridge, 70)) * 2 if ridge.size else 3.0


def _dominant_colour(rgba: np.ndarray, frame: np.ndarray) -> tuple[int, int, int]:
    px = rgba[frame & (rgba[:, :, 3] > 200)][:, :3]
    if px.size == 0:
        return (40, 40, 44)
    # Drop the brightest 20% (specular highlights on acetate / metal) then take the median.
    lum = px @ np.array([0.299, 0.587, 0.114])
    keep = px[lum <= np.percentile(lum, 80)]
    med = np.median(keep if len(keep) else px, axis=0)
    return tuple(int(v) for v in med)
