# THE INTERSTICE — the five levels

Status as of 2026-11-06. Checkpoint before this work: git tag `checkpoint-before-five-levels`.

| Level | Dimension | State |
|---|---|---|
| 00 The Threshold | `interstice:threshold` | **implemented** (passage, twist, white rift, transition) |
| 01 The Corridor | `interstice:the_interstice` | existing; reached through the white rift; its far exit now leads to 02 |
| 02 The Wheat Farm | `interstice:wheat_farm` | **first pass** (track, fields, animated wheat, turbines, rift) |
| 03 The Compression | `interstice:compression` | not yet built |
| 04 The Impossible | `interstice:impossible` | not yet built |

## Shared foundation

- `world/levels/IntersticeLevel`: the five levels in order (number, dimension key, title, `next()`).
- `ModWorld.isAnyLevel(level)`: any of the five; `ModWorld.isInterstice(level)` stays "Level 01" (its creatures,
  Stampede, lights and layout are Corridor systems).
- Survival rules (`SurvivalRules.isInterstice`) apply in **all** levels: food never heals, no natural regeneration.
- No vanilla music in any level (`MusicManagerMixin` checks `ClientState.inAnyLevel`).
- `block/RiftBlock` (`interstice:rift`): intangible, invisible as a block (each level draws its own rift), sends a
  player to the next level (`IntersticeTeleport.throughRift`) with a portal cooldown (no bounce-back loops). A
  rift towards a level whose dimension is not loaded leads out of THE INTERSTICE.
- `IntersticeTeleport.enter` (`/interstice enter`) now starts at Level 00; `enterCorridor` is the Corridor's own
  arrival (used by the white rift, the dev auto-tests). The return point is only remembered when coming from
  outside the levels; leaving from any level returns there.

## Level 00 — The Threshold

- Generation (`world/threshold/ThresholdChunkGenerator`, `ThresholdLayout`): fixed coordinates, identical in every
  world. A straight corridor, square section 5 x 5, from z -4 to z 66, whose real blocks are all invisible
  (barriers: floor, walls, ceiling, back wall), a few invisible light blocks, and the end wall at z 67 with the rift
  in its centre column (x 0, 4 high). No creatures.
- The twist (`client/render/threshold/ThresholdRenderer`): the whole corridor (floor included) is drawn turned about
  its axis **relative to the viewer**: 32 blocks of plain straight corridor, then a smooth 180-degree twist over 30
  blocks, then 8 straight blocks to the end wall. The slice under the player's feet is always upright (they walk
  on a flat floor); ahead the corridor turns over; looking back, the entrance hangs upside down; walking on, the
  floor straightens under the feet. Half-block slices; light baked per vertex in the corridor's own frame (one
  continuous light slot down the ceiling, which coils into a spiral through the twist, and the tear's glow).
- Smooth materials (`tools/assets/make_threshold_textures.py`): 256 px procedural concrete (walls/ceiling, floor)
  and light slot, wrapped noise (no seam), linear filtering (`"blur": true`), one repeat per 4 blocks, continuous
  across slices and faces.
- The rift: a white tear down the end wall: a lens-shaped slit with jagged, faintly shimmering lips, three soft
  halos and a blinding core, additive; its light baked onto the last metres of the corridor.
- The transition (`client/effects/RiftTransition`, `ReceivingLevelScreenMixin`): the screen whitens over the last
  2.5 blocks, is all white in the tear, the loading screen is painted white, and the Corridor fades in from white.
- Lighting: the Corridor's darkened lightmap applies here too (`LightTextureMixin`). The drawn lights really light
  (`client/render/veil/ThresholdLights`): Veil point lights every 2.5 blocks under the ceiling slot (cold white,
  radius 6.5) and a strong white light before the tear (radius 14, a little in-scattering), placed through the same
  viewer-relative twist every frame so they turn with the corridor.
- GameTest `thresholdLeadsIntoTheCorridor`: shell, end wall and tear in place, >= 30 flat blocks before the twist;
  entering lands in the corridor; survival rules apply; the tear leads into the Corridor with a cooldown.
  (GameTest servers do not create datapack dimensions: `GameTestServerLevelsMixin` adds the Threshold.)

### Manual checks (visual)

`/interstice enter`, walk 32 blocks of flat corridor, then through the twist: smooth walls (no steps, no seams),
the light slot spiralling, the floor always flat underfoot, the entrance upside down behind; the tear must read as
a blinding split in the wall; crossing goes white -> Corridor fading in, no loading screen.

## Level 02 — The Wheat Farm (first pass)

- Generation (`world/wheat/WheatChunkGenerator`, `WheatLayout`): a straight farm track (x -1..1) from z 0 to 1600,
  fenced at x +-3 (worn fence, a few gaps), perfectly flat; gently rolling fields everywhere else whose surface block
  is a golden field (`wheat_field`, PixelLab) so the landscape reads as wheat to the horizon. Pure functions of
  position (no seams). Fixed golden evening (`fixed_time` 12350, overworld sky), warm biome fog. No creatures.
- The wheat (`client/render/wheat/WheatRenderer` + Veil program `level/wheat`): per-chunk static vertex buffers
  within 3 chunks (3 clumps per block, two crossed quads, two PixelLab clump sprites, hashed size/angle/tint), built
  two per frame and freed far away. The wind is in the vertex shader in world coordinates: long waves travelling
  across the field, broad gusts, trembling ears, a sheen where the gusts bend the tops; near the edge of the drawn
  radius the stalks sink into the field surface (no border).
- Wind turbines (`TurbineRenderer`): smooth meshes, no blocks: tapered round mast, capsule nacelle, nose cone,
  three twisted aerofoil blades narrowing to the tip; facing the wind; rotor from game time + per-turbine phase
  (deterministic, frame-rate independent); fewer segments far away; an invisible footing so the tower is solid.
- The rift: a free-standing white tear at the end of the track (`client/render/RiftTearRenderer`); Level 03 not
  being built, it leads out of THE INTERSTICE for now.
- `/interstice level <0-4>` goes straight to the start of a built level (testing).
- GameTest `corridorExitLeadsToTheWheatFarm`: track, fences and field in place, deterministic ground, survival rules,
  the Corridor's exit arrives on the track, the farm's rift leads out.

Second pass (2026-11-06): perfectly flat ground (rolls showed as block steps); stalks gradient-mapped onto one
golden ramp, 5 clumps per block, drawn to 62 blocks (3 per block on the outer ring); a smooth procedural golden
canopy for the field surface and packed earth for the track; a custom evening sky (`WheatSky`: gradient dome, no
clouds of blocks, a small white-hot sun low ahead along the track with tight glare and wide glow).

Third pass: **the whole farm is visible** — the vanilla fog is a light haze to 2,100 blocks there
(`FogRendererMixin`), and beyond the loaded chunks `WheatFarRenderer` draws the field plane (16-block tiles near,
64 far), the track strip, the fence lines and the farmhouse's massing; turbines to 2,200 blocks. **The farmhouse**
(`FarmHouse`): two storeys of plaster on a concrete footing, log corners, a wooden band, dark windows, a wooden
gable roof, a brick chimney, a porch; its front door is a `fake_door` (iron-type, cannot be opened by hand) and the
rift (a tear of light) stands over it. **Clouds and god rays** (Veil post pipeline `wheat_atmosphere`): a cloud
slab at 230-330 ray-marched through drifting fbm noise, lit towards the sun, fading into the haze far off; god rays
by radial blur of the bright sky towards the sun. **Sounds** (`tools/audio/gen_wheat_audio.py`, `WheatAudio`):
open-field wind (since the third pass a CC0 recording mixed over the synthesis, see `docs/AUDIO_CREDITS.md`, plus single recorded gusts now and then), the wheat's dry rustle (its volume follows the shader's gusts at the listener), and the turbines'
blade swishes, hum and gearbox whine on the two nearest hubs, one rotor turn per loop.

Third pass (2026-11-06): **the house from afar**. Vanilla's far clip plane (render distance x 4) cut everything past
~770 blocks; `GameRendererFarMixin` pushes it to 5,000 blocks in Level 02. The haze is lighter (fog 250-4,200), the
farmhouse's windows are lit (warm, full bright, `textures/level/window_lit.png`), and from afar a soft warm halo marks
it. **The wind farm**: `TURBINE_ROWS` (7) rows on each side, ~70 to ~1,000 blocks out, every 140 blocks along the
track from z -500 to z 2,300 (staggered rows, 15% gaps); past 750 blocks each is drawn as a low-poly silhouette. Sounds
use rows 0-2. **The path keeper** (`world/wheat/PathKeeper`): a survival or adventure player past the fence line (or
behind the start, or beyond the house) hears a whisper ("Do not leave... this early.", voice line `do_not_leave`); the screen
whites out (`EnvEvent.WHITEOUT`) and, at full white, they are set back in the middle of the track at the same distance,
facing the house. Creative and spectator players are left alone.

32x32 pass (2026-11-06, `tools/assets/make_wheat_32.py`, PixelLab jobs `wheat_farm_32.txt`): the field blocks are
**tilled farmland** (four furrows per block along the track, two variants that differ only by clods and stubble, never
rotated, so the rows run on unbroken from block to block); the track is packed earth (two variants, any rotation) and
spills a ragged edge onto the farmland beside it (ConnectedTextures overlay set `path`). The stalks are two ripe
PixelLab clumps on one straw ramp. Past the stalks' radius, inside the loaded chunks, `WheatFarRenderer` lays the wheat
canopy (`textures/level/wheat_canopy.png`) just over the soil. Fixed: the stalk shader sampled the block atlas (noise
and green moss) because its texture was not bound; `WheatRenderer` now binds it on unit 0 itself. The teal stalks that remained came from the
vertex colour arriving with its bytes in another order (alpha, the height up the stalk, read as red): `wheat.vsh` now
takes the height from the texture row and no longer reads the colour. The field closes over behind the start of the
track (`WheatLayout.inTrackStrip`: no wheat only between the fences, from the start to the house). Client check:
flag file `interstice_autotest.flag` containing `wheat` (`AutoTest` wheat mode: close view, back, ahead, and a dump of
the stalk texture as the GPU has it).

Not yet: the distant Level 04 silhouettes, a visible rift and the white transition at the Corridor's exit.

## Still to do (in order)

1. Level 02 remainder: distant Level 04 silhouettes, audio, the Corridor exit's visible rift and transition.
3. Level 03 The Compression: connected maze generation with traversability validation, crawl traversal, the Folded,
   the Listener (hearing-driven), the Seam; enclosed acoustics; rift to 04.
4. Level 04 The Impossible: monumental custom geometry with simplified collision, void rendering, the Dormant, the
   Pilgrim, the Suspended (spatial trigger).
5. Rifts showing the destination (offscreen render target / portal camera) for 01->02, 02->03, 03->04.
6. Audio for levels 00, 02-04 (procedural synthesis like `tools/audio/gen_corridor_audio.py`); per-level Veil post.
