"""
Build a parametric 3D frame (GLB) from the measurements.

Coordinates: millimetres, origin midway between the lens centres at their
vertical centre, +X to the wearer's left as the camera sees them, +Y up,
+Z toward the camera.

What is exact (straight from the photo): the lens-opening outline, lens
width/height, bridge width, rim thickness, colour, and — on the textured front
card — the actual pattern/finish pixels. What is procedural: the side profile
of the rims and the temple arms (a front photo does not contain them).
"""
from __future__ import annotations

import io

import numpy as np
import trimesh
from PIL import Image
from shapely.geometry import Polygon
from shapely.validation import make_valid

from .measure import Measurements

_RIM_DEPTH = 4.0        # mm, front-to-back thickness of a rim
_FACE_CURVE = 3.0       # mm the outer edges wrap back toward the ears
_TEMPLE_LEN = 128.0     # mm hinge-to-bend
_LENS_ALPHA = 0.16


def build_glb(m: Measurements, front_png: Image.Image | None) -> bytes:
    scene = trimesh.Scene()
    colour = np.array(m.frame_colour) / 255.0

    half = (m.lens_width_mm + m.bridge_mm) / 2.0
    for sign, contour in ((-1.0, m.left_contour_mm), (1.0, m.right_contour_mm)):
        tag = "L" if sign < 0 else "R"
        cx = sign * half
        rim = _rim_mesh(contour, m.rim_thickness_mm)
        _bend(rim, _FACE_CURVE, m.total_width_mm / 2)
        rim.apply_translation([cx, 0, 0])
        rim.visual = _solid(colour, metallic=0.0 if m.is_dark else 0.25, rough=0.45)
        _add(scene, rim, f"rim_{tag}")

        lens = _lens_mesh(contour)
        lens.apply_translation([cx, 0, _RIM_DEPTH * 0.35])
        lens.visual = _glass()
        _add(scene, lens, f"lens_{tag}")

    bridge = _bridge(m)
    bridge.visual = _solid(colour, 0.0 if m.is_dark else 0.25, 0.45)
    _add(scene, bridge, "bridge")

    for sign in (-1.0, 1.0):
        _add(scene, _temple(sign, m, colour), f"temple_{'L' if sign < 0 else 'R'}")

    if front_png is not None:
        card = _front_card(m, front_png)
        if card is not None:
            _add(scene, card, "front")

    scene.metadata["frame_width_mm"] = float(m.total_width_mm)
    scene.metadata["lens_width_mm"] = float(m.lens_width_mm)
    scene.metadata["bridge_mm"] = float(m.bridge_mm)
    return scene.export(file_type="glb")


# --- pieces ---------------------------------------------------------------

def _add(scene: trimesh.Scene, mesh: trimesh.Trimesh, name: str) -> None:
    scene.add_geometry(mesh, node_name=name, geom_name=name)

def _poly(contour) -> Polygon:
    p = Polygon(contour)
    if not p.is_valid:
        p = make_valid(p).buffer(0)
    if p.geom_type == "MultiPolygon":
        p = max(p.geoms, key=lambda g: g.area)
    return p


def _rim_mesh(contour, thickness) -> trimesh.Trimesh:
    inner = _poly(contour)
    outer = inner.buffer(thickness, join_style=1)
    ring = outer.difference(inner)
    if ring.geom_type == "MultiPolygon":
        ring = max(ring.geoms, key=lambda g: g.area)
    mesh = trimesh.creation.extrude_polygon(ring, _RIM_DEPTH)
    mesh.apply_translation([0, 0, -_RIM_DEPTH / 2])
    return mesh


def _lens_mesh(contour) -> trimesh.Trimesh:
    inner = _poly(contour).buffer(0.4)  # tuck slightly under the rim
    mesh = trimesh.creation.extrude_polygon(inner, 0.8)
    mesh.apply_translation([0, 0, -0.4])
    return mesh


def _bridge(m: Measurements) -> trimesh.Trimesh:
    span = m.bridge_mm + 2 * m.rim_thickness_mm
    box = trimesh.creation.box([max(span, 4.0), m.rim_thickness_mm * 1.3, _RIM_DEPTH * 0.9])
    box.apply_translation([0, m.lens_height_mm * 0.30, 0])
    return box


def _temple(sign: float, m: Measurements, colour) -> trimesh.Trimesh:
    hinge_x = sign * (m.total_width_mm / 2 - m.rim_thickness_mm * 0.5)
    hinge_y = m.lens_height_mm * 0.28
    t = float(np.clip(m.rim_thickness_mm, 1.5, 5.0))

    hinge = trimesh.creation.box([t * 1.4, t * 1.6, t * 1.6])
    shaft = trimesh.creation.box([t, t * 1.2, _TEMPLE_LEN])
    shaft.apply_translation([0, -t * 0.2, -_TEMPLE_LEN / 2])
    bend = trimesh.creation.box([t, 11.0, t * 1.1])
    bend.apply_translation([0, -6.0, -_TEMPLE_LEN + 1.5])
    arm = trimesh.util.concatenate([hinge, shaft, bend])

    arm.apply_transform(trimesh.transformations.rotation_matrix(np.radians(sign * 6), [0, 1, 0]))
    arm.apply_translation([hinge_x, hinge_y, 0])
    arm.visual = _solid(np.array(colour), 0.0 if m.is_dark else 0.3, 0.4)
    return arm


def _front_card(m: Measurements, png: Image.Image):
    """A thin quad the shape of the frame silhouette, textured with the real
    photo — so the exact colour, pattern and finish read head-on."""
    x, y, w, h = m.bbox_px
    if w < 8 or h < 8:
        return None
    crop = png.convert("RGBA").crop((x, y, x + w, y + h))

    fw = m.total_width_mm
    fh = m.total_height_mm
    plane = trimesh.creation.box([fw, fh, 0.6])
    plane.apply_translation([0, 0, _RIM_DEPTH / 2 + 0.3])
    _bend(plane, _FACE_CURVE, fw / 2)

    v = plane.vertices
    u = (v[:, 0] / fw) + 0.5
    vv = 0.5 - (v[:, 1] / fh)
    plane.visual = trimesh.visual.TextureVisuals(
        uv=np.column_stack([u, np.clip(vv, 0, 1)]),
        image=crop,
        material=trimesh.visual.material.PBRMaterial(
            baseColorTexture=crop, alphaMode="MASK", alphaCutoff=0.5,
            metallicFactor=0.0, roughnessFactor=0.6, doubleSided=True,
        ),
    )
    return plane


def _bend(mesh: trimesh.Trimesh, curve: float, half: float) -> None:
    v = mesh.vertices
    v[:, 2] -= curve * np.square(np.clip(v[:, 0] / max(half, 1e-3), -1, 1))


def _solid(colour, metallic, rough):
    c = np.clip(np.append(np.asarray(colour, float), 1.0), 0, 1)
    return trimesh.visual.TextureVisuals(material=trimesh.visual.material.PBRMaterial(
        baseColorFactor=c, metallicFactor=float(metallic), roughnessFactor=float(rough),
        doubleSided=True,
    ))


def _glass():
    return trimesh.visual.TextureVisuals(material=trimesh.visual.material.PBRMaterial(
        baseColorFactor=[0.62, 0.68, 0.74, _LENS_ALPHA], alphaMode="BLEND",
        metallicFactor=0.0, roughnessFactor=0.05, doubleSided=True,
    ))
