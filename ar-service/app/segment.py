"""
Cut the frame out of a product photo.

Eyewear catalogue shots are almost always on a plain, light, near-uniform
backdrop. A border-seeded flood fill in Lab colour keys that out reliably and
with zero model weights. If the photo already has a real alpha channel (an
admin-supplied PNG cut-out) we trust that instead.

ponytail: flood-fill chroma key. Swap in an ONNX matting model (u2net / rmbg)
only if the client stops shooting on plain backdrops — the interface here
(bytes -> RGBA) does not change.
"""
from __future__ import annotations

import os
import cv2
import numpy as np
from PIL import Image

# A pixel this far (Lab ΔE, roughly) from the sampled backdrop AND connected to
# the border is background.
_TOLERANCE = 18.0


def _has_real_alpha(img: Image.Image) -> bool:
    if img.mode != "RGBA":
        return False
    a = np.asarray(img)[:, :, 3]
    # A genuine cut-out has a real spread of alpha; a JPEG-turned-RGBA is all 255.
    return a.min() < 250 and (a < 16).mean() > 0.02


def cutout(data: bytes) -> Image.Image:
    """Photo bytes -> RGBA image with the backdrop made transparent."""
    img = Image.open(_bytesio(data))
    img = _downscale(img.convert("RGBA"), 1400)

    if _has_real_alpha(img):
        return img

    # Opt-in u2net matte for photos shot on busy / uneven backgrounds. Default is
    # the chroma key, which is what the tests cover and what a plain catalogue
    # backdrop needs. (rembg's lens-opening recovery is still rough — see README.)
    if os.getenv("AR_USE_REMBG") == "1":
        cut = _rembg_cutout(img)
        if cut is not None:
            return cut

    rgb = np.asarray(img)[:, :, :3]
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB).astype(np.float32)
    h, w = lab.shape[:2]

    # Backdrop reference = median of a thin border frame.
    border = np.concatenate([
        lab[:4].reshape(-1, 3), lab[-4:].reshape(-1, 3),
        lab[:, :4].reshape(-1, 3), lab[:, -4:].reshape(-1, 3),
    ])
    ref = np.median(border, axis=0)

    dist = np.linalg.norm(lab - ref, axis=2)
    near_bg = (dist < _TOLERANCE).astype(np.uint8)

    # Keep only background that is *connected to the border* — a white lens glare
    # in the middle of the frame is near the backdrop colour but not connected.
    ff = near_bg.copy()
    mask = np.zeros((h + 2, w + 2), np.uint8)
    for x in range(0, w, 8):
        for y in (0, h - 1):
            if ff[y, x]:
                cv2.floodFill(ff, mask, (x, y), 2)
    for y in range(0, h, 8):
        for x in (0, w - 1):
            if ff[y, x]:
                cv2.floodFill(ff, mask, (x, y), 2)
    background = ff == 2

    # Lens openings: regions the backdrop colour but walled off from the border by
    # the rim. Keep only opening-sized, low-variance blobs so a light frame detail
    # or a logo isn't punched out.
    enclosed = (near_bg & ~background).astype(np.uint8)
    en, elbl, estats, _ = cv2.connectedComponentsWithStats(enclosed, 8)
    for i in range(1, en):
        area = estats[i, cv2.CC_STAT_AREA]
        if area < 0.004 * h * w:
            continue
        region = rgb[elbl == i]
        if region.std() < 16:
            background[elbl == i] = True

    # Tidy speckle without eroding thin rims or shrinking the lens openings.
    fg = (~background).astype(np.uint8) * 255
    fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))

    out = np.dstack([rgb, fg])
    return Image.fromarray(out, "RGBA")


_REMBG_SESSION = None
_REMBG_TRIED = False


def _rembg_cutout(img: Image.Image) -> Image.Image | None:
    """u2net matting — handles shadows, gradients and busy backdrops the
    flood-fill can't. Optional: if rembg isn't installed we return None and the
    caller uses the chroma key."""
    global _REMBG_SESSION, _REMBG_TRIED
    if _REMBG_TRIED and _REMBG_SESSION is None:
        return None
    try:
        if _REMBG_SESSION is None:
            from rembg import new_session, remove  # noqa: F401
            _REMBG_SESSION = new_session("u2net")
        from rembg import remove
        out = remove(img, session=_REMBG_SESSION,
                     post_process_mask=True,
                     alpha_matting=False).convert("RGBA")
        _REMBG_TRIED = True
        # Punch out the lens openings the matte leaves opaque.
        return _open_lenses(out)
    except Exception:  # noqa: BLE001
        _REMBG_TRIED = True
        _REMBG_SESSION = None
        return None


def _open_lenses(img: Image.Image) -> Image.Image:
    """After matting, the frame is a solid blob. Recover the lens openings:
    holes fully enclosed by the frame."""
    a = (np.asarray(img)[:, :, 3] > 40).astype(np.uint8)
    if a.sum() == 0:
        return img
    filled = a.copy()
    ff_mask = np.zeros((a.shape[0] + 2, a.shape[1] + 2), np.uint8)
    cv2.floodFill(filled, ff_mask, (0, 0), 1)          # 1 everywhere outside the frame
    holes = (filled == 0) & (a == 0)                    # inside the silhouette, not frame
    if holes.sum() < 0.002 * a.size:
        return img
    out = np.asarray(img).copy()
    out[holes, 3] = 0
    return Image.fromarray(out, "RGBA")


def _downscale(img: Image.Image, longest: int) -> Image.Image:
    if max(img.size) <= longest:
        return img
    s = longest / max(img.size)
    return img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)


def _bytesio(data: bytes):
    import io
    return io.BytesIO(data)
