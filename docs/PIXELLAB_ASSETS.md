# THE INTERSTICE — PixelLab asset requests

Every texture currently in the mod is a **temporary development asset**, generated procedurally by
`tools/gen_textures.py` so all systems can be tested. Replace each file by saving the PixelLab output
**with exactly the same filename at exactly the same path**, then rebuild (`gradlew build`).
No code change is needed.

## Art direction (applies to every request)

- Brutalist, industrial, abandoned. Weathered poured concrete, rust, oxidised dark steel, water stains, soot.
- Low saturation: cold greys with a faint blue-green cast in shadows; rust is the only warm material.
- Lighting baked into textures must be **flat and neutral** (no strong directional highlights). The mod lights
  everything dynamically with Veil; baked shadows would fight that.
- No text, logos, symbols, signage or recognisable real-world branding.
- Minecraft pixel-art scale: crisp pixels, no anti-aliased gradients across the whole tile, no dithering noise.

## Material maps (Veil)

The current Veil integration uses Veil's deferred light buffers, which take albedo and normals from the
standard Minecraft rendering. **Only base-colour (albedo) textures are used today.** Do not spend effort on
normal, roughness or metallic maps yet; PixelLab does not generate them automatically and the mod does not
read them. If a PBR path is added later, those maps will be requested separately (`*_n.png`, `*_s.png`).

---

## Batch A — Structural block textures (most important for the look)

All: **16×16 px**, orthographic flat face (texture swatch, not an isometric block), **opaque (no
transparency)**, **seamless/tileable on all four edges**, not animated.
Destination folder: `src/main/resources/assets/interstice/textures/block/`

| # | Output filename | PixelLab prompt | Usage |
|---|---|---|---|
| A1 | `weathered_concrete.png` | "Seamless 16x16 pixel art texture of weathered poured concrete wall, cold mid-grey, subtle pitting and air holes, faint vertical water streaks, flat even lighting, no highlights, brutalist, low saturation" | Main wall surface (most of the walls) |
| A2 | `concrete_panel.png` | "Seamless 16x16 pixel art texture of a precast concrete panel with a thin dark recessed seam along the top and left edges, cold grey, flat lighting, brutalist architecture" | Panel seams on walls, floors, bunkers |
| A3 | `stained_concrete.png` | "Seamless 16x16 pixel art texture of old concrete heavily stained by water and rust runoff, darker grey with brown-green vertical streaks, damp, flat lighting" | Bottom of walls, floor variation |
| A4 | `cracked_concrete.png` | "Seamless 16x16 pixel art texture of grey concrete with a jagged dark crack running diagonally and small chipped fragments, flat lighting, abandoned industrial" | Damaged wall patches |
| A5 | `concrete_floor.png` | "Seamless 16x16 pixel art texture of worn industrial concrete floor slab, darker grey, scuffed and dusty, faint grid joint line through the middle, flat lighting, top-down" | Corridor floor |
| A6 | `concrete_rubble.png` | "Seamless 16x16 pixel art texture of broken concrete rubble and gravel chunks, mixed greys, dusty, flat lighting, top-down" | Floor debris, collapsed passages |
| A7 | `rusted_plate.png` | "Seamless 16x16 pixel art texture of a heavy riveted steel plate covered in orange-brown rust and dark oxidation, four rivets near the corners, flat lighting" | Buttresses, beam undersides, anchor |
| A8 | `dark_metal.png` | "Seamless 16x16 pixel art texture of dark gunmetal industrial steel, very dark blue-grey, faint horizontal brushed bands, slight grime, flat lighting" | Beams, bridges, frames |
| A9 | `metal_grate.png` | "16x16 pixel art texture of a rusty steel floor grate, square grid of thin bars with open holes, seamless, holes fully transparent, flat lighting" — **needs transparency** (holes = alpha 0), seamless | Walkways, drainage, pit covers |
| A10 | `rusted_pipe.png` | "Seamless 16x16 pixel art texture of the side of a large rusted industrial pipe, cylindrical shading from left to right only, orange-brown rust over dark steel, flat overall lighting, tileable top-to-bottom" | Pipe side |
| A11 | `rusted_pipe_top.png` | "16x16 pixel art texture of the open end of a rusted steel pipe seen straight on, dark hollow circular opening in the centre, rusted rim" (not seamless) | Pipe end cap |

## Batch B — Light fixtures

All **16×16 px**, front orthographic view, **opaque**, **not seamless**, not animated. The lamp blocks are
small cubes (8×8 px of the texture is visible per face: pixels 2..13), so keep the subject centred.
Destination: `src/main/resources/assets/interstice/textures/block/`

| # | Filename | PixelLab prompt | Usage |
|---|---|---|---|
| B1 | `industrial_lamp.png` | "16x16 pixel art of a caged industrial wall lamp seen from the front, cold white-blue glowing bulb behind a dark steel wire cage, dark metal housing, centred" | Common corridor lights |
| B2 | `sodium_lamp.png` | "16x16 pixel art of a caged industrial lamp with a warm orange sodium-vapour glow behind a dark wire cage, dark metal housing, centred" | Warm variation |
| B3 | `emergency_lamp.png` | "16x16 pixel art of a small red emergency warning lamp behind a protective steel cage, deep red glass, dark housing, centred" | Doorways, shelters, Stampede emergency lighting |
| B4 | `flood_lamp_front.png` | "16x16 pixel art of the front face of a large industrial floodlight, round bright white lens with a thick dark metal bezel, centred, flat lighting" | Flood light emitting face |
| B5 | `flood_lamp_side.png` | "Seamless 16x16 pixel art of dark ribbed metal housing of an industrial floodlight, cooling fins as horizontal ridges, gunmetal grey" | Flood light sides |

## Batch C — The Adherent (creature)

The creature's **3D geometry is defined in code** (`AdherentModel.java`: boxes for torso, spine ridge,
neck, head, jaw plate, long two-segment arms with three claws, short digitigrade legs, folded vestigial chest
arms). PixelLab supplies **only the skin texture**, laid out on a fixed UV map.

| # | Filename | Size | Details |
|---|---|---|---|
| C1 | `adherent.png` | **512×512** | UV-mapped skin for the v4 organic mesh. **Layout reference: `docs/uv/adherent_uv_template_512.png`** (regenerate with `python -I tools/creature/uv_template.py` after any mesh change). Each rectangle is one unrolled limb or body tube (u around the limb, v along it). Prompt: "Creature skin texture atlas, 512x512, ash-grey skin slightly warm in the folds, stretched over tendons and bones, subtle darker veins and creases, faint bruise-pink in the joints, darker grey on the back ridge, near-black curved nails, flat lighting, no baked highlights, unsettling but not gory". Opaque, not seamless, not animated. Keep the current rectangles: the exporter copies the PNG embedded in `models/adherent.bbmodel`, so paint into that file in Blockbench (or replace the texture there) and re-run `export_adherent.py`. |
| C2 | `adherent_glow.png` | **64×64** | Emissive mask rendered fullbright: **transparent everywhere except the face slit region** (u 20–22, v 40–44 on the template: the white "slit" box). Prompt: "64x64 transparent image, only a thin vertical pale bone-white glowing slit, everything else fully transparent". |
| C3 | `horde_sprite.png` | **128×32** (4 frames of 32×32, left to right) | Distant horde silhouettes drawn flat **on the wall plane**, head pointing to the **right (+X)**, seen from straight in front of the wall (the creature is clinging to a vertical wall, belly against it). Prompt: "4-frame pixel art sprite sheet, 128x32, each frame 32x32, a thin long-limbed four-legged creature seen from directly above as it scuttles to the right, limbs spread wide, run cycle, very dark grey near-black silhouette with minimal detail, transparent background". **Transparent background required**; consistent scale and centre across frames. |

Destination for C1–C3: `src/client/resources/assets/interstice/textures/entity/`

## Batch D — Giant chain

| # | Filename | Size | Details |
|---|---|---|---|
| D1 | `chain_link.png` | **32×32** | Wraps around the procedural chain-link tube: U runs **along the link's length (repeats 4× around one link)**, V runs **around the tube cross-section**. Prompt: "Seamless 32x32 pixel art texture of massively heavy old forged iron, dark rust-brown with lighter worn edges and pitting, flat lighting, tileable in both directions". Opaque, **seamless**, not animated. |

Destination: `src/client/resources/assets/interstice/textures/entity/`

## Batch E — Particles

Destination: `src/client/resources/assets/interstice/textures/particle/`. The game tints them, so draw them
**white/light grey**.

| # | Filename | Size | Prompt |
|---|---|---|---|
| E1–E4 | `dust_0.png` … `dust_3.png` | 8×8 | "8x8 pixel art of a tiny irregular clump of falling concrete dust, white, soft edges, transparent background" (4 variations, increasing size) |
| E5 | `mote_0.png` | 8×8 | "8x8 pixel art of a single tiny soft dust mote, 2x2 bright core with faint halo, white, transparent background" |

## Batch F — Mod icon

| # | Filename | Size | Prompt | Destination |
|---|---|---|---|---|
| F1 | `icon.png` | 128×128 | "Pixel art icon, two colossal dark concrete walls on the left and right vanishing upward into black fog, a single enormous rusted chain hanging in the middle, a tiny red emergency light at the bottom, oppressive, minimal, no text" (opaque) | `src/main/resources/assets/interstice/` |

---

## Audio (not PixelLab — for reference)

All sounds are original placeholders synthesised by `tools/gen_audio.py` (mono 32 kHz Ogg Vorbis). The voice
lines in `assets/interstice/sounds/voice/` are whisper-vocoded from a local Windows text-to-speech render and
must be replaced by a real whispered recording (same filenames, **mono**). See `README.md` → *Audio assets*.
