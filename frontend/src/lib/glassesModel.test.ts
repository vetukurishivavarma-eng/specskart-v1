import { describe, it, expect } from 'vitest'
import * as THREE from 'three'
import { buildGlasses, buildTexturedFrame, colourToHex } from './glassesModel'

describe('colourToHex', () => {
  it('maps common product colour names, case/substring insensitive', () => {
    expect(colourToHex('Matte Black')).toBe(0x161618)
    expect(colourToHex('Tortoise Shell')).toBe(0x5b3a1e)
    expect(colourToHex('Gold')).toBe(0xc9a24b)
  })
  it('falls back to a neutral dark for unknown / empty', () => {
    expect(colourToHex('chartreuse-ish')).toBe(0x26262a)
    expect(colourToHex(null)).toBe(0x26262a)
  })
})

describe('buildGlasses', () => {
  it('is a real 3D group with lens rims, a bridge and two temples', () => {
    const g = buildGlasses()
    const frameParts = [] as THREE.Mesh[]
    g.traverse((o) => { if (o instanceof THREE.Mesh && o.userData.part === 'frame') frameParts.push(o) })
    // 2 rims + bridge + 2 hinges + 2 arms + 2 ear bends
    expect(frameParts.length).toBe(9)
    const box = new THREE.Box3().setFromObject(g)
    expect(box.max.x - box.min.x).toBeGreaterThan(10) // ~14 cm wide
    expect(box.min.z).toBeLessThan(-8) // temples reach back toward the ears
  })

  it('setColour tints every frame part but leaves the lenses alone', () => {
    const g = buildGlasses()
    g.setColour(0xff0000)
    g.traverse((o) => {
      if (o instanceof THREE.Mesh && o.userData.part === 'frame') {
        expect((o.material as THREE.MeshStandardMaterial).color.getHex()).toBe(0xff0000)
      }
    })
  })
})

describe('buildTexturedFrame', () => {
  it('crops the texture to the central band and keeps the real photo up front', () => {
    const tex = new THREE.Texture()
    const g = buildTexturedFrame(tex, 2.2, 'gold')
    // texture shows a central slice, not the splayed arm stubs at the edges
    expect(tex.repeat.x).toBeLessThan(1)
    expect(tex.offset.x).toBeGreaterThan(0)
    expect(tex.repeat.x + tex.offset.x * 2).toBeCloseTo(1, 5)

    const front: THREE.Mesh[] = []
    g.traverse((o) => { if (o instanceof THREE.Mesh && o.userData.part === 'texture') front.push(o) })
    expect(front).toHaveLength(1)
    expect((front[0].material as THREE.MeshBasicMaterial).map).toBe(tex)
  })

  it('bolts on real 3D temple arms, tinted to the product, that reach back to the ears', () => {
    const g = buildTexturedFrame(new THREE.Texture(), 2.2, 'gold')
    const arms: THREE.Mesh[] = []
    g.traverse((o) => { if (o instanceof THREE.Mesh && o.userData.part === 'frame') arms.push(o) })
    expect(arms.length).toBe(6) // 2 × (hinge + shaft + ear bend)
    for (const m of arms) expect((m.material as THREE.MeshStandardMaterial).color.getHex()).toBe(0xc9a24b)

    const box = new THREE.Box3().setFromObject(g)
    expect(box.min.z).toBeLessThan(-8)
    expect(box.max.x - box.min.x).toBeGreaterThan(10)
  })
})
