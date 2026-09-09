import * as THREE from 'three'

// A parametric 3D eyeglasses mesh, modelled in centimetres with the origin
// between the eyes and +Z pointing toward the viewer (matching the MediaPipe
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
  s.moveTo(x + r, y)
  s.lineTo(x + w - r, y)
  s.quadraticCurveTo(x + w, y, x + w, y + r)
  s.lineTo(x + w, y + h - r)
  s.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
  s.lineTo(x + r, y + h)
  s.quadraticCurveTo(x, y + h, x, y + h - r)
  s.lineTo(x, y + r)
  s.quadraticCurveTo(x, y, x + r, y)
  return s
}

export type Glasses = THREE.Group & { setColour(hex: number): void }

export function buildGlasses(colour = 0x26262a): Glasses {
  const group = new THREE.Group() as Glasses

  const frameMat = new THREE.MeshStandardMaterial({ color: colour, roughness: 0.35, metalness: 0.15 })
  const lensMat = new THREE.MeshPhysicalMaterial({
    color: 0x223344, transparent: true, opacity: 0.22, roughness: 0.06,
    metalness: 0, transmission: 0.6, ior: 1.5, side: THREE.DoubleSide,
  })

  const LENS_W = 4.9, LENS_H = 3.6, LENS_R = 1.2
  const CENTRE_X = 3.35 // half the pupillary distance (~67 mm)

  const rimShape = roundedRect(LENS_W, LENS_H, LENS_R)
  const rimHole = roundedRect(LENS_W - 0.7, LENS_H - 0.7, LENS_R - 0.3)
  rimShape.holes.push(rimHole)
  const rimGeo = new THREE.ExtrudeGeometry(rimShape, { depth: 0.5, bevelEnabled: true, bevelThickness: 0.12, bevelSize: 0.1, bevelSegments: 2, curveSegments: 16 })
  rimGeo.center()

  const lensGeo = new THREE.ShapeGeometry(roundedRect(LENS_W - 0.6, LENS_H - 0.6, LENS_R - 0.3), 16)

  for (const sign of [-1, 1]) {
    const rim = new THREE.Mesh(rimGeo, frameMat)
    rim.position.set(sign * CENTRE_X, 0, 0)
    rim.userData.part = 'frame'
    group.add(rim)

    const lens = new THREE.Mesh(lensGeo, lensMat)
    lens.position.set(sign * CENTRE_X, 0, -0.05)
    group.add(lens)
  }

  // Bridge over the nose.
  const bridge = new THREE.Mesh(new THREE.BoxGeometry(CENTRE_X * 2 - LENS_W + 0.3, 0.5, 0.5), frameMat)
  bridge.position.set(0, LENS_H / 2 - 0.9, 0.15)
  bridge.userData.part = 'frame'
  group.add(bridge)

  // Hinges + temples (arms) sweeping back to the ears and bending down at the end.
  const TEMPLE_LEN = 11.5
  for (const sign of [-1, 1]) {
    const hinge = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.9, 0.9), frameMat)
    hinge.position.set(sign * (CENTRE_X + LENS_W / 2 - 0.2), LENS_H / 2 - 1.1, 0)
    hinge.userData.part = 'frame'
    group.add(hinge)

    const arm = new THREE.Mesh(new THREE.BoxGeometry(0.35, 0.5, TEMPLE_LEN), frameMat)
    arm.position.set(sign * (CENTRE_X + LENS_W / 2 + 0.1), LENS_H / 2 - 1.1, -TEMPLE_LEN / 2 + 0.2)
    arm.rotation.y = sign * -0.12 // toe in toward the head
    arm.userData.part = 'frame'
    group.add(arm)

    const earBend = new THREE.Mesh(new THREE.BoxGeometry(0.35, 1.6, 0.5), frameMat)
    earBend.position.set(sign * (CENTRE_X + LENS_W / 2 - 0.15), LENS_H / 2 - 1.8, -TEMPLE_LEN + 0.3)
    earBend.userData.part = 'frame'
    group.add(earBend)
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
