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

The creature's geometry is the organic v5 mesh (`models/adherent.bbmodel`, see `docs/CREATURE.md`).
PixelLab supplies **only the skin texture**, laid out on the mesh's UV atlas.

| # | Filename | Size | Details |
|---|---|---|---|
| C1 | `adherent.png` | **512×512** | UV-mapped skin for the v5 organic mesh. **Layout reference: `docs/uv/adherent_uv_template_512.png`** (regenerate with `python -I tools/creature/uv_template.py` after any mesh change). Each rectangle is one unrolled limb or body tube (u around the limb, v along it). Prompt: "Creature skin texture atlas, 512x512, ash-grey skin slightly warm in the folds, stretched over tendons and bones, subtle darker veins and creases, faint bruise-pink in the joints, darker grey on the back ridge, near-black curved nails, flat lighting, no baked highlights, unsettling but not gory". Opaque, not seamless, not animated. Keep the current rectangles: the exporter copies the PNG embedded in `models/adherent.bbmodel`, so paint into that file in Blockbench (or replace the texture there) and re-run `export_adherent.py`. |
| C2 | ~~`adherent_glow.png`~~ | — | **Obsolete**: the organic creature has no emissive layer (the glow texture was removed with the v3 cube model). Do not request. |
| C3 | `horde_sprite.png` | **128×32** (4 frames of 32×32, left to right) | Distant horde silhouettes drawn flat **on the wall plane**, head pointing to the **right (+X)**, seen from straight in front of the wall (the creature is clinging to a vertical wall, belly against it). Prompt: "4-frame pixel art sprite sheet, 128x32, each frame 32x32, a thin long-limbed four-legged creature seen from directly above as it scuttles to the right, limbs spread wide, run cycle, very dark grey near-black silhouette with minimal detail, transparent background". **Transparent background required**; consistent scale and centre across frames. |

Destination for C1–C3: `src/client/resources/assets/interstice/textures/entity/`


## Batch G — The creature family (seven species)

Same workflow and constraints as C1. Each species has its **own UV atlas** (the islands differ in size and order because the meshes differ): always paint on that species' template. The current textures in `textures/entity/<species>.png` are **generated development textures** (palette and procedural patterns from `tools/creature/species_generator.py`), clearly temporary.

Common to every request: **512×512** (Burden: 1024×1024), opaque (alpha 255 on every island; the space between islands is unused), not seamless, not animated, flat lighting with no baked highlights or cast shadows, base colour only (the mod reads no normal/roughness maps today; if they are produced later they must use the same UV atlas and be named `<species>_n.png` / `<species>_s.png`). Each rectangle is one unrolled tube (u around the limb or body part, v along it): paint continuous skin along v and make the left and right edges of each island match (they meet around the limb). Nails, the inside of the mouth, the eye sockets and nostrils, the teeth and the bone (vertebral processes, scapular ridges, elbow points, hock spurs) have their own small islands (near-black, dark red, near-black, yellowed ivory, pale bone). The current generated texture includes baked ambient occlusion: keep the shading of the creases when repainting, or the creature will look flat in game.

| # | Filename | Destination | UV template | Prompt and details |
|---|---|---|---|---|
| G1 | `crawler.png` | `src/client/resources/assets/interstice/textures/entity/crawler.png` (then embed it in `models/crawler.bbmodel` and run `python -I tools/creature/export_adherent.py crawler`) | `docs/uv/crawler_uv_template_512.png` (wireframe alone: `docs/uv/crawler_uv_wire_512.png`) | Crawler: small, low, agile wall creature (0.62 scale of the Adherent, long splayed limbs). Prompt: "Creature skin texture atlas, 512x512, dark ash-grey, nearly charcoal, slightly warm in the folds, fine wrinkles at every joint, small dark nails, sparse pale scratches on the back as if from rubbing against concrete, flat lighting, no baked highlights, unsettling but not gory". Palette: #545050 base, #4a3536 folds, #1a1716 nails. |
| G2 | `strider.png` | `src/client/resources/assets/interstice/textures/entity/strider.png` (then embed it in `models/strider.bbmodel` and run `python -I tools/creature/export_adherent.py strider`) | `docs/uv/strider_uv_template_512.png` (wireframe alone: `docs/uv/strider_uv_wire_512.png`) | Strider: very tall, very long thin limbs, narrow torso. Prompt: "Creature skin texture atlas, 512x512, pale grey-beige skin stretched tight over long bones and tendons, visible tendon lines running the whole length of the limbs, bruise-pink at the knees and elbows, darker calloused hands and feet, flat lighting, no baked highlights, unsettling but not gory". Palette: #857d78 base, #705454 folds, #2a2523 hands. |
| G3 | `burden.png` | `src/client/resources/assets/interstice/textures/entity/burden.png` (then embed it in `models/burden.bbmodel` and run `python -I tools/creature/export_adherent.py burden`) | `docs/uv/burden_uv_template_1024.png` (wireframe alone: `docs/uv/burden_uv_wire_1024.png`) | Burden: massive, heavy, thick limbs, huge shoulders (1.3 scale, 1.5x thicker). **1024×1024** (its atlas is twice the others). Prompt: "Creature skin texture atlas, 1024x1024, dark brown-grey thick hide, deep folds and creases around the shoulders and neck, old healed scars and abrasions, callused knuckles, the skin looks heavy and loose, flat lighting, no baked highlights, unsettling but not gory". Palette: #665c59 base, #5c3d3d folds, #241d1c knuckles. |
| G4 | `clinger.png` | `src/client/resources/assets/interstice/textures/entity/clinger.png` (then embed it in `models/clinger.bbmodel` and run `python -I tools/creature/export_adherent.py clinger`) | `docs/uv/clinger_uv_template_512.png` (wireframe alone: `docs/uv/clinger_uv_wire_512.png`) | Clinger: flattened wide body pressed to the wall, very long hooked fingers. Prompt: "Creature skin texture atlas, 512x512, concrete-grey mottled skin that imitates weathered concrete (blotches, stains, faint vertical streaks), slightly lighter belly, near-black hooked nails; it must read as part of the wall at a distance, flat lighting, no baked highlights, unsettling but not gory". Palette: #737370 base, #5c5452 folds, #4d4d4c blotches, #121111 nails. |
| G5 | `hollow.png` | `src/client/resources/assets/interstice/textures/entity/hollow.png` (then embed it in `models/hollow.bbmodel` and run `python -I tools/creature/export_adherent.py hollow`) | `docs/uv/hollow_uv_template_512.png` (wireframe alone: `docs/uv/hollow_uv_wire_512.png`) | Hollow: emaciated, very thin, ribs and spine visible. Prompt: "Creature skin texture atlas, 512x512, pale, almost bloodless grey-white skin, every rib and vertebra showing as darker grooves, thin pink-grey membranes at the joints, sunken areas between the bones, slightly translucent-looking (but opaque), flat lighting, no baked highlights, unsettling but not gory". Palette: #948c87 base, #806060 folds, #6e6560 rib grooves. |
| G6 | `watcher.png` | `src/client/resources/assets/interstice/textures/entity/watcher.png` (then embed it in `models/watcher.bbmodel` and run `python -I tools/creature/export_adherent.py watcher`) | `docs/uv/watcher_uv_template_512.png` (wireframe alone: `docs/uv/watcher_uv_wire_512.png`) | Watcher: very long neck held upright, elongated head. Prompt: "Creature skin texture atlas, 512x512, ash-grey body, noticeably paler head and neck (bone-pale), fine vertical folds along the neck, smooth eyeless head with heavy folds where eyes would be, a thin darker line under the jaw for the hidden mouth, flat lighting, no baked highlights, unsettling but not gory". Palette: #75706f base, #8f8a88 head, #664a4c folds. |
| G7 | `fractured.png` | `src/client/resources/assets/interstice/textures/entity/fractured.png` (then embed it in `models/fractured.bbmodel` and run `python -I tools/creature/export_adherent.py fractured`) | `docs/uv/fractured_uv_template_512.png` (wireframe alone: `docs/uv/fractured_uv_wire_512.png`) | Fractured: asymmetric: long left arm, short thick right arm, raised left shoulder. Prompt: "Creature skin texture atlas, 512x512, grey skin with pinkish healed seams running diagonally across the body as if it had been broken and grown back, mismatched texture between the left (smoother) and right (rougher, darker) sides, flat lighting, no baked highlights, unsettling but not gory". Palette: #736866 base, #6b4545 seams, #5a504e right side. |

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

---

## Batch "Corridor rework" — survival items, devices, threshold (2026-11-05)

The files below are **temporary development textures** drawn by `tools/assets/gen_survival_textures.py` (each PNG
carries a "temporary" text marker). Same rules as above: save the PixelLab output with exactly the same filename
at exactly the same path.

Items: **16×16 px**, transparent background, single object centred, slight 3/4 view like vanilla items, dark
1-px outline, no text. Destination: `src/main/resources/assets/interstice/textures/item/`. Item models already
exist (`item/generated`, or `item/handheld` for the flashlight, injector and spike).

| # | Output filename | PixelLab prompt | Usage |
|---|---|---|---|
| R1 | `canned_ration.png` | "16x16 pixel art item icon, dented steel food can with a faded brown paper label, no text, grimy, transparent background" | Food (hunger only) |
| R2 | `dry_biscuits.png` | "16x16 pixel art item icon, three dry square hardtack biscuits stacked, pale tan, transparent background" | Food |
| R3 | `nutrient_paste.png` | "16x16 pixel art item icon, squeezable grey-green paste tube with a black cap, clinical, transparent background" | Food |
| R4 | `bandage.png` | "16x16 pixel art item icon, rolled off-white gauze bandage with a loose end and a small dried blood spot, transparent background" | Healing (small) |
| R5 | `medical_kit.png` | "16x16 pixel art item icon, battered pale grey medical case with a dark red cross and a black handle, transparent background" | Healing (large) |
| R6 | `emergency_injector.png` | "16x16 pixel art item icon, diagonal auto-injector pen, white body with an amber band and a short needle, transparent background" | Healing (fast) |
| R7 | `flashlight.png` | "16x16 pixel art item icon, diagonal black metal flashlight with a pale warm lens at the top and a small red switch, transparent background" | Light |
| R8 | `battery.png` | "16x16 pixel art item icon, single upright dark battery cell with a yellow top band and a white plus sign, transparent background" | Recharges the flashlight |
| R9 | `flash_flare.png` | "16x16 pixel art item icon, red cardboard magnesium flare stick with a white cap and a yellow band, transparent background" | Protective device |
| R10 | `rattle_lure.png` | "16x16 pixel art item icon, small rusty tin box with a clockwork winding key on top, bolts visible inside, transparent background" | Protective device (thrown) |
| R11 | `resonance_spike.png` | "16x16 pixel art item icon, diagonal steel spike with three thin glowing cyan tuning grooves and a dark leather grip, transparent background" | Rare lethal device |
| R12 | `torn_note.png` | "16x16 pixel art item icon, torn yellowed paper scrap with a few dark handwritten lines (illegible), transparent background" | Lore note |

Blocks: **16×16 px**, opaque, flat face. Destination: `src/main/resources/assets/interstice/textures/block/`.

| # | Output filename | PixelLab prompt | Usage |
|---|---|---|---|
| R13 | `ward_emitter_side.png` | "16x16 pixel art texture, side of a small grey metal device with a horizontal speaker grille and a thin glowing pale-blue indicator strip, flat lighting" | Ward emitter (sides; the model shows the lower 10 px) |
| R14 | `ward_emitter_top.png` | "16x16 pixel art texture, top of a grey metal device with a dark square port and a small pale-blue light in the centre, flat lighting" | Ward emitter (top) |
| R15 | `exit_threshold.png` | "16x16 pixel art texture, almost pure black surface with very faint dark-grey grain, like looking into deep darkness, seamless" | The exit at the far end of the Corridor |

Procedural on purpose (no PixelLab needed): `textures/entity/eye_halo.png` (a radial falloff), the graded
`<species>_eyes.png` atlases (generated from the models).

---

## Batch "Corridor materials" — building blocks of the regions (2026-11-05)

Temporary textures by `tools/assets/gen_corridor_materials.py`. All **16×16 px**, opaque (except where noted),
**seamless on all edges**, flat lighting, same grime as the concrete family. Destination: `textures/block/`.

| # | Output filename | PixelLab prompt |
|---|---|---|
| M1 | `grimy_brick.png` | "Seamless 16x16 pixel art texture of old soot-blackened red-brown brick wall, dark mortar, water runs, desaturated, flat lighting" |
| M2 | `plaster_wall.png` | "Seamless 16x16 pixel art texture of stained grey-beige plaster wall, water marks, grime towards the bottom, flat lighting" |
| M3 | `plaster_cracked.png` | "Seamless 16x16 pixel art texture of cracked grey plaster with a fallen patch showing dark brick, flat lighting" |
| M4 | `asphalt.png` | "Seamless 16x16 pixel art texture of old dark asphalt road with fine grey aggregate specks, top-down, flat lighting" |
| M5 | `asphalt_line.png` | "Seamless 16x16 pixel art texture of dark asphalt with a worn, chipped yellow painted line running vertically through the middle, top-down" |
| M6 | `sidewalk.png` | "Seamless 16x16 pixel art texture of dark grey concrete sidewalk pavers, 2x2 slabs with dark joints, dusty, top-down" |
| M7 | `floorboards.png` | "Seamless 16x16 pixel art texture of dark rotten wooden floorboards, four horizontal boards with gaps, grimy" |
| M8 | `tar_roof.png` | "Seamless 16x16 pixel art texture of a black tar roof with scattered grey gravel, top-down" |
| M9 | `grimy_glass_pane.png` (+ `grimy_glass_pane_top.png`, 16x16 dark edge) | "16x16 pixel art window glass, translucent dirty grey-green glass with a dark frame and dirt along the bottom, semi-transparent" (keep alpha) |
| M10 | `corrugated_metal.png` | "Seamless 16x16 pixel art texture of grey corrugated steel sheet with vertical ridges and rust streaks running down" |
| M11 | `steel_beam.png` / `steel_beam_top.png` | "16x16 pixel art texture of a dark steel I-beam side with rivets" / "top view of a dark steel I-beam cross-section" |
| M12 | `oxidized_plate.png` | "Seamless 16x16 pixel art texture of weathered dark grey-green steel plate with oxidation blotches and four corner rivets" |
| M13 | `hazard_stripe.png` | "Seamless 16x16 pixel art texture of worn yellow and black diagonal hazard stripes, paint chipped to grey metal" |
| M14 | `container_red.png` / `container_teal.png` | "Seamless 16x16 pixel art texture of a dull red (resp. faded teal) corrugated shipping container panel with rust runs" |
| M15 | `clinic_tile.png` | "Seamless 16x16 pixel art texture of small off-white ceramic wall tiles with dark grout, grimy, flat lighting" |
| M16 | `stained_tile.png` | "Same off-white tiles heavily stained with yellow-brown seepage" |
| M17 | `clinic_floor.png` | "Seamless 16x16 pixel art texture of worn grey-green hospital linoleum floor with scuffs, top-down" |
| M18 | `flesh_growth.png` | "Seamless 16x16 pixel art texture of a dark red-brown organic growth mass with darker veins and a few wet highlights, unsettling, flat lighting" |
| M19 | `growth_veins.png` | "16x16 pixel art, thin dark red organic veins spreading on a transparent background (carpet overlay)" (keep alpha) |

---

## Status (2026-11-05): batches "Corridor rework" and "Corridor materials" generated

All 36 textures of the two batches above (items, ward emitter, threshold, region materials) were generated with
PixelLab (pixflux, 32x32, through the project's MCP server, `.mcp.json`) and graded towards the Corridor palette
(slightly desaturated and darkened; the glass pane made translucent with an opaque frame). They are kept at 32x32
(Minecraft scales them per block; at 16x16 the brick mortar and tile grout turned to mush). Each PNG carries an
"interstice: PixelLab generation" text chunk; the procedural generators skip any file that has it.
Still procedural: `grimy_glass_pane_top.png` (edge strip), the eye halo, the chain link texture.

## Batch "Corridor copies" (2026-11-05, night, Tier 1) — generated

48 pixflux generations (32x32, doors and beds 32x64 then split), job list in
`tools/assets/pixellab_jobs/corridor_copies.txt`, raw images in `tools/assets/pixellab_raw/corridor_copies/`,
built by `tools/assets/make_corridor_copies.py` (grading, splitting, door icons, procedural chain/antenna/glass
edge, animated water). Prompt pattern: "Minecraft block texture filling the entire canvas, <subject>, rust, grime,
desaturated, flat lighting" (`view` side or high top-down, `outline` lineless, `shading` basic); sprites (ladder,
lab stand, web, roots, chain icon, spawn eggs) with `no_background`. Regenerated once: locker front and open
front (white margins), basin side and iron (wrong subject), deep rock (came out as bricks). Covers: the 4 doors,
ladder, locker, drum, 2 beds, bookshelf, basin, stove, furnace, lab stand, anvil top, press, funnel, web, roots,
flesh node, abyssal rock, water swatch, chain icon, the 5 lamp textures (were 16x16) and 7 spawn eggs.

## Batch "Connected textures" (2026-11-05, night) — generated

9 inpaintings (10 generations each): the 4 rusted plate variants as 2x2 mosaics with the seams and rivets
masked ("plain continuous rusty orange-brown steel plate surface... no seams, no border lines, no rivets"; the
centre 32x32 crop wraps exactly), and a 3-4 px frame ring on the red and teal containers, corrugated sheet,
oxidized plate (unused: came out white, replaced by a procedural frame) and hazard plating. Jobs in
`tools/assets/pixellab_jobs/connected.txt`, raws in `tools/assets/pixellab_raw/connected/`.
