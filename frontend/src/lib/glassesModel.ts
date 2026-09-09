import * as THREE from 'three'

// A parametric 3D eyeglasses mesh, modelled in centimetres with the origin
// between the lenses and +Z pointing toward the viewer (matching the MediaPipe
// canonical face). It is a real object — it foreshortens, the temples recede to
// the ears, the lenses catch light — not a flat image. Colour is swapped per
// product; we do not have a 3D model per frame, so shape is generic and tint
// carries the product identity.

const NAMED_COLOURS: [string, number][] = [
  ['tortoise', 0x5b3a1e], ['havana', 0x5b3a1e],
  ['rose gold', 0xb76e79], ['rosegold', 0xb76e79],
  ['gold', 0xc9a24b], ['silver', 0xb9bcc2], ['gunmetal', 0x53565c],
  ['matte black', 0x161618], ['black', 0x1c1c1e],
  ['brown', 0x4a2f1a], ['tan', 0x8a6a45], ['beige', 0xc8b088],
  ['blue', 0x2f4b7c], ['navy', 0x22314f], ['green', 0x2f4a34], ['olive', 0x4a4a2c],
  ['red', 0x8f2d2d], ['burgundy', 0x5e2230], ['pink', 0xb5657f],
  ['grey', 0x6b6e73], ['gray', 0x6b6e73], ['white', 0xe8e6e0],
  ['clear', 0xd9d6cf], ['transparent', 0xd9d6cf], ['crystal', 0xd9d6cf],
]

/** Best-effort map a product colour name to a hex the frame material can take. */
export function colourToHex(name: string | null | undefined): number {
  const n = (name ?? '').toLowerCase().trim()
  if (!n) return 0x26262a
  for (const [key, hex] of NAMED_COLOURS) if (n.includes(key)) return hex
  return 0x26262a
}

/** Rounded-rectangle outline used for both the rim and the lens fill. */
function roundedRect(w: number, h: number, r: number): THREE.Shape {
  const s = new THREE.Shape()
  const x = -w / 2, y = -h / 2
  const rr = Math.min(r, w / 2, h / 2)
  s.moveTo(x + rr, y)
  s.lineTo(x + w - rr, y)
  s.quadraticCurveTo(x + w, y, x + w, y + rr)
  s.lineTo(x + w, y + h - rr)
  s.quadraticCurveTo(x + w, y + h, x + w - rr, y + h)
  s.lineTo(x + rr, y + h)
  s.quadraticCurveTo(x, y + h, x, y + h - rr)
  s.lineTo(x, y + rr)
  s.quadraticCurveTo(x, y, x + rr, y)
  return s
}

export type Glasses = THREE.Group & { setColour(hex: number): void }

/**
 * Load the product's cut-out photo as a texture. Uses an <img> with CORS so the
 * WebGL texture is not tainted (the API sends Access-Control-Allow-Origin for
 * the storefront origin).
 */
export function loadFrameTexture(url: string): Promise<{ texture: THREE.Texture; aspect: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      if (!img.naturalWidth) { reject(new Error('empty image')); return }
      const texture = new THREE.Texture(img)
      texture.colorSpace = THREE.SRGBColorSpace
      texture.anisotropy = 4
      texture.needsUpdate = true
      resolve({ texture, aspect: img.naturalWidth / img.naturalHeight })
    }
    img.onerror = () => reject(new Error('texture load failed'))
    img.src = url
  })
}

/**
 * The actual product frame: its cut-out photo mapped onto a gently face-wrapping
 * curved surface, so it carries the real design while the MediaPipe head matrix
 * gives it true 3D position, rotation and perspective — not a flat 2D paste.
 */
export function buildTexturedFrame(texture: THREE.Texture, aspect: number): THREE.Group {
  const group = new THREE.Group()
  const W = 13.8                       // typical adult frame-front width in cm
  const H = W / Math.max(0.2, aspect)
  const CURVE_X = 2.0                   // outer edges wrap back toward the ears (cm); keep < offsetForward
  const CURVE_Y = 0.6                   // slight forward bow top & bottom

  const geo = new THREE.PlaneGeometry(W, H, 32, 6)
  const pos = geo.attributes.position
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), y = pos.getY(i)
    const zx = -CURVE_X * (x / (W / 2)) ** 2
    const zy = CURVE_Y * (1 - (y / (H / 2)) ** 2)
    pos.setZ(i, zx + zy)
  }
  geo.computeVertexNormals()

  const mat = new THREE.MeshBasicMaterial({
    map: texture, transparent: true, alphaTest: 0.35, side: THREE.DoubleSide, depthWrite: false, toneMapped: false,
  })
  group.add(new THREE.Mesh(geo, mat))
  return group
}

export function buildGlasses(colour = 0x26262a): Glasses {
  const group = new THREE.Group() as Glasses

  const frameMat = new THREE.MeshStandardMaterial({ color: colour, roughness: 0.4, metalness: 0.15 })
  const lensMat = new THREE.MeshPhysicalMaterial({
    color: 0x2b3540, transparent: true, opacity: 0.16, roughness: 0.08,
    metalness: 0, transmission: 0.7, ior: 1.5, side: THREE.FrontSide,
  })

  const LENS_W = 5.9, LENS_H = 4.0, LENS_R = 1.1
  const CENTRE_X = 3.45 // half the pupillary distance (~69 mm)
  const RIM = 0.45      // rim thickness
  const DEPTH = 0.35    // rim extrusion along z

  const rimShape = roundedRect(LENS_W, LENS_H, LENS_R)
  rimShape.holes.push(roundedRect(LENS_W - RIM * 2, LENS_H - RIM * 2, Math.max(0.2, LENS_R - RIM)))
  const rimGeo = new THREE.ExtrudeGeometry(rimShape, {
    depth: DEPTH, bevelEnabled: true, bevelThickness: 0.08, bevelSize: 0.06, bevelSegments: 1, curveSegments: 14,
  })
  rimGeo.center()

  // Lens sits just in front of the rim so it never z-fights or shows the rim's inner wall.
  const lensGeo = new THREE.ShapeGeometry(roundedRect(LENS_W - RIM * 1.4, LENS_H - RIM * 1.4, LENS_R), 14)

  for (const sign of [-1, 1]) {
    const rim = new THREE.Mesh(rimGeo, frameMat)
    rim.position.set(sign * CENTRE_X, 0, 0)
    rim.userData.part = 'frame'
    group.add(rim)

    const lens = new THREE.Mesh(lensGeo, lensMat)
    lens.position.set(sign * CENTRE_X, 0, DEPTH / 2 + 0.03)
    group.add(lens)
  }

  // Bridge: a short bar joining the two inner-top corners over the nose.
  const gap = CENTRE_X * 2 - LENS_W
  const bridge = new THREE.Mesh(new THREE.BoxGeometry(gap + RIM * 2, 0.5, DEPTH), frameMat)
  bridge.position.set(0, LENS_H / 2 - RIM - 0.25, 0)
  bridge.userData.part = 'frame'
  group.add(bridge)

  // Temples: from the hinge at the outer-top of each rim, straight back to the ears,
  // then a short down-bend. Modelled as a child group so a small toe-in rotates about
  // the hinge, not the arm's middle.
  const TEMPLE_LEN = 10.5
  const hingeY = LENS_H / 2 - RIM - 0.2
  for (const sign of [-1, 1]) {
    const arm = new THREE.Group()
    arm.position.set(sign * (CENTRE_X + LENS_W / 2 - RIM / 2), hingeY, 0)
    arm.rotation.y = sign * 0.09 // toe in toward the head

    const hinge = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.7, 0.7), frameMat)
    hinge.userData.part = 'frame'
    arm.add(hinge)

    const shaft = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.45, TEMPLE_LEN), frameMat)
    shaft.position.set(sign * 0.05, -0.1, -TEMPLE_LEN / 2)
    shaft.userData.part = 'frame'
    arm.add(shaft)

    const earBend = new THREE.Mesh(new THREE.BoxGeometry(0.3, 1.4, 0.4), frameMat)
    earBend.position.set(sign * 0.05, -0.75, -TEMPLE_LEN + 0.2)
    earBend.userData.part = 'frame'
    arm.add(earBend)

    group.add(arm)
  }

  group.setColour = (hex: number) => {
    group.traverse((o) => {
      if (o instanceof THREE.Mesh && o.userData.part === 'frame') {
        (o.material as THREE.MeshStandardMaterial).color.setHex(hex)
      }
    })
  }

  return group
}
