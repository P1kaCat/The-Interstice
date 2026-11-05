# THE CORRIDOR — rework (2026-11-05)

What was actually implemented in the Corridor rework, how it works, how it was checked, and what is still
missing. The eight creatures were **not** redesigned: models, rigs, clips, AI and senses are unchanged, except
for the brighter eyes, the species sound sets and the integration points listed below.

Checkpoint before the rework: local git tag `checkpoint-v7-before-corridor` (the uncommitted creature v7 work,
including untracked files).

## 1. Finite world and regions

`world/CorridorPlan.java` (pure function of the layout seed, shared by server and client):

- Length 7,000–10,000 blocks, chosen from the seed (`MIN_LENGTH`/`MAX_LENGTH`), from `startZ = -320` (the
  player arrives near z = 0) to `endZ = startZ + length`.
- Four regions, in a fixed order of decay: **Forgotten District → Ironworks → Contaminated Ward → Dead Expanse**.
  Each has at least 1,000 blocks outside its transition zones (`MIN_REGION`); the rest of the length is shared by
  seeded weights. Between two regions a 160-block transition (`TRANSITION`) mixes their buildings lot by lot
  (`regionForLot`), and the client crossfades ambience over it (`weights`).
- Verified by the GameTest `corridorPlanRespectsItsLimits` over 300 seeds: lengths 7,010–9,993, shortest pure
  region 1,359 blocks, regions contiguous, deterministic. Test world: 9,867 blocks.

**Ends.** Beyond either end every column is a solid bulkhead up to the wall top (`TerrainPass.bulkhead`), its face
panelled. The far bulkhead has the **exit**: a 9-wide, 12-high, 8-deep doorway framed in dark metal, ending in the
`exit_threshold` block (intangible, black): walking into it leaves THE INTERSTICE like `/interstice leave`.
Nothing (chains, lamps, shafts) is placed beyond the ends. GameTest `corridorIsClosedWithAnExit`.

**Width** is unchanged (46 ± 17 ± 4 blocks half-width): wide enough for a street, two rows of buildings and the
service alleys along the walls.

### Existing saves (generation versions)

`world/GenerationVersion.java`. Version 1 = the old endless Corridor, version 2 = the finite one. On server start,
a save whose Interstice dimension already has region files but no marker keeps **version 1** (chunks generated
later continue the old layout seamlessly); a new dimension gets version 2. The version is written to
`dimensions/interstice/the_interstice/interstice_generation.txt` and sent to clients (`LayoutSync`). To move an
old save to the new world, remove that dimension folder (its content is lost) before loading. GameTests always
run on a fresh dimension (`build.gradle` deletes it) at version 2.

## 2. Settlements (ground level)

`world/structures/Settlements.java`. The floor is cut into 24-block lots along Z on both sides of a central
street (kept ≥ 7 blocks wide on each side); buildings back onto a service alley along the wall (10 blocks from
the wall face, clear of the buttresses); lots are separated by alleys. Every lot is a pure function of the seed
and is written through the chunk-clipping `ChunkWriter`, so buildings continue across chunk borders.

| Region | Lots |
|---|---|
| Forgotten District | apartment blocks (2–4 storeys), shops, utility buildings, courtyards; asphalt street with a centre line and pavements, street lights, wrecked cars, benches, barricades |
| Ironworks | steel-framed halls (two lots long, 11 high) with machinery, a catwalk and ladder, lockers, overhead pipes and hanging chains, grated floors; container yards with pipe racks; utility buildings; floors of rusted plate and dark metal; cable reels, drain grates |
| Contaminated Ward | clinics (1–2 storeys: treatment rooms with beds, storage, quarantine rooms behind bars, offices), laboratories with a sealed specimen room, shelters; moss and sculk growth that **increases gradually through the region** (walls, floors, hanging roots, cobwebs, street patches) |
| Dead Expanse | mostly empty: rare shelters (bunker with an iron door and buttons, a bed, a cache), ruins of broken walls, checkpoints (booth, desk, cache, a boom across the street), debris fields; shafts are allowed here and in the Ironworks only |

**Interiors.** Apartments and clinics: on every storey a two-wide back hallway, a partition, rooms cut along the
building each opening onto the hallway (about half through a door that closes), stairs at the far end of the
hallway between storeys, windows on the street and side walls (intact, broken, boarded or blacked out). Two
entrances: the front door (sometimes barricaded with planks) and an alley door that is always open. Rooms are
furnished by kind (bedroom, kitchen, living room, storage; treatment, storage, quarantine, office).

**Hiding places.** Closets (a one-block cell behind a door in a room corner), lockers (halls, utility buildings,
labs), rooms with closable doors, a crawlspace under a grate in utility buildings, wardrobes and machinery to hide
behind, shipping containers with doors. Line of sight is blocked by walls, closed doors and furniture but not by
glass, panes, bars, chains, fences or cobwebs (`Perception.clear`), so a window does not hide you and a closet
does. Creatures do not open doors; a nose or a hearing creature still comes to the door, and a Crawler can come in
through a broken window (one-block gap).

**Loot.** Chests and barrels are created with the chunk as pending block entities carrying a loot table
(`ChunkWriter.lootChest`): `interstice:chests/residential`, `industrial`, `medical`, `shelter`, `expanse`
(`data/interstice/loot_table/chests/`). Shelters always hold a cache. GameTest `buildingsHaveRealInteriors` loads
two buildings per region (all 8 crossing chunk borders in the run): 70 doors, 17 loot containers, corners present.

## 3. Survival

- **Food never heals** in THE INTERSTICE: `FoodDataMixin` (hunger-based natural regeneration) and `PlayerMixin`
  (peaceful regeneration) read `naturalRegeneration` as false there; `LivingEntityMixin` refuses the Regeneration
  effect on players there (golden apples, suspicious stew, potions, beacons). Hunger itself still works. Other
  dimensions are untouched. GameTest `foodNeverHealsInTheInterstice` (15 s with a full stomach and a golden apple:
  8.0 → 8.0 health; a bandage then heals 4).
- **Medical items** (`item/MedicalItem`, values in `config/interstice-server.json`): bandage (heals 4, 2.5 s,
  interrupted when hit), medical kit (14, 5.5 s, interrupted when hit), emergency injector (8, 0.6 s). Applying
  makes a little noise.
- **Food**: canned ration, dry biscuits, nutrient paste (vanilla food also works, for hunger only).
- **Light**: flashlight (right-click toggles; drains a charge point a second while held lit, 20 minutes; a Veil
  spot light on every client; but a lit player counts as a light source to every creature's eyes), battery
  (recharges the most drained flashlight by 10 minutes).

## 4. Creatures cannot be killed by ordinary combat

`SurvivalRules.canHarmCreature`, applied in `AdherentEntity.hurt`: only the severance damage type
(`interstice:severance`), `/kill` and the void, and the environment the creatures already live with (their own
falls, suffocation, drowning, cramming) can hurt them. Swords, axes, arrows, tridents, explosions, fire, lava,
potions and other mobs do nothing: the creature flinches, makes a sound, the noise tells it where you are, and a
creature on a wall may scurry off. (No inflated health pools.) GameTest `ordinaryWeaponsCannotKillCreatures`.

### Protective devices (escape, not combat)

| Device | Effect | Species differences |
|---|---|---|
| Magnesium flash (`flash_flare`) | Dazzles ground creatures within 12 blocks that have the user in sight and roughly in front of them (`deviceStun`, they resume afterwards); wall creatures flinch and may let go. The bang is loud (noise radius 26). | Watcher ×1.6, Clinger ×1.2, Adherent/Strider/Fractured ×1, Burden ×0.4, Crawler ×0.35 (recovers fast), Hollow immune (near blind). GameTest `flashDazzlesWhatLooksAtIt`. |
| Rattle lure (`rattle_lure`) | Thrown; rattles where it lands for 10 s, a loud noise every 0.75 s: creatures go to look there. | Strongest on keen ears (Hollow, Burden). Does not hide the thrower from sight or smell. |
| Ward emitter (block) | Runs about 3 minutes; ground creatures keep out of 7 blocks (they back off, a hunter drops to investigating). | Hollow keeps 11 blocks away; Burden and Clinger ignore it. GameTest `wardKeepsAHunterAway` (closest approach 5.6–5.8 blocks). |
| Resonance spike (`resonance_spike`) | Single use, rare. Driven into a creature lying stunned, it kills it with severance damage. | Only the Crawler, the Hollow and the Fractured; against the others (and anything not stunned) it only rings and wakes it. |

There is no universal weapon. Loot distributions: industrial for devices and batteries, medical for healing,
residential for food, bandages and batteries, shelter caches for a little of everything.

## 5. Eyes (the only visual change to the creatures)

Positions, shapes and proportions unchanged. The emissive atlases are graded to a saturated crimson
(`tools/creature/eye_glow.py`, also applied by `export_adherent.py`; median red 83–221 → 198–239), the emissive pass
is drawn twice (additive), and a small soft halo (0.2 block) is drawn around the eyes with the same additive,
depth-tested, fogged render type: visible without bloom, never through walls. The red Veil eye light is slightly
stronger (radius 3, brightness 0.42), still too small to light a room. In the black fog the bright eyes outlast
the body a little and then fade with distance.

## 6. Black fog

`fog.fsh` (Veil post pipeline) and `AtmosphereController`:
- Fog colour is near black (a breath of blue-grey near the floor; red in an emergency, dust in a quake).
- **Horizontal**: the fog closes completely between 58 % and 88 % of the current render distance, whatever it is,
  so no chunk edge is visible; the vanilla fog (fallback without post-processing) also ends at 90 % of it.
- **Vertical**: what is 45–150 blocks above the viewer sinks to black (relative to the camera, so a player high on
  a bridge still sees around them); the sky is black.
- The fake wall continuation above y = 300 (`IntersticeSkyRenderer`) is **disabled**; it can be turned back on with
  `legacyWallContinuation` in the client config for comparison. Other LOD (chains, horde) is unchanged.
- Settings (F7): fog density, fog distance, darkness above; quality presets keep their ray-march step counts.

Not yet verified in game at several render distances (to do with the next play session).

## 7. Chains

`ChainField` / `ChainRenderer`: chain density follows the region (Ironworks ×2.4, District ×0.9, Ward ×1,
Expanse ×0.55), none beyond the ends; new **torn chains** hanging from one wall anchor and swinging heavily; the
Stampede shakes them (tremor during the premonition, full shudder while the horde passes, hanging chains swing up
to 3.2 times wider). Chain tension groans near the nearest chain during quakes and the horde.
Not done: a modelled broken end link, normal maps.

## 8. Sound

- **Vanilla music** never plays in THE INTERSTICE (`MusicManagerMixin`); other dimensions unchanged.
- **Creature sound identities** (`registry/SpeciesSounds`): five event families per species (step, scrape, breath,
  vocal, click), 134 files in all; see `docs/CORRIDOR_AUDIO.md` for the character of each and the final-recording
  specification. The server uses breath/click as ambient sounds and the vocal for alerts.
- **Animation-synchronised footsteps** (`client/audio/CreatureFootsteps`): each frame the height of every limb tip
  above its support surface is read from the skinned pose; a limb that lifts and comes back down plays its
  species' step (ground) or scrape (wall) at that moment, plus a metal layer on metal floors. Voice limit: 14
  contacts a second, 70 ms per creature, 26 blocks (Burden 44). The Fractured's right side lands heavier.
- **The Burden** is heard from indoors: its steps rattle glass and metal around the listener, shake dust from the
  ceiling and add a small camera tremor when close.
- **Region beds** crossfaded by region weight at the camera, muffled indoors: District wind and creaks, Ironworks
  metallic resonance and hum, Ward ventilation and organic murmur, Expanse sparse wind and silence.
- **Stampede/quake**: indoors, rattles and dust; outdoors, chain tension.
- **Player noise** for creature hearing now depends on the surface: metal ×1.6 (even sneaking on metal is heard),
  rubble ×1.3, planks ×1.15, carpet/moss ×0.65.

All of it is **procedural development audio** (`tools/audio/gen_corridor_audio.py`), not final sound design.

## 9. Tests

`./gradlew runGametest`: 39 tests (11 new for the rework). Last full run: 38/39, the failing one being
`clingersHoldOnWhenTheWallsFlee`, known to be timing-sensitive on this PC (18/24 creatures visible to the reaction
query; it passed on earlier runs). Survival, devices, plan, buildings and ends all pass.

## 11. After the first play session (2026-11-05, afternoon)

Feedback: fixtures floating, regions looking like vanilla blocks mixed with modded ones, buildings lined up too
perfectly along the sides, region borders too sharp, walls too dark to feel their height, giant chains without a
hitbox and looking odd. Changes:
- **Mounted fixtures.** `LampBlock` has a `facing` (UP standing, DOWN hanging, horizontal = wall sconce) with real
  fixture models (mount plate, arm or rod, cage); the generator sets it, and `LampMounts` (last generation pass)
  turns any fixture without a support towards one, or hangs it on a chain, or stands it on a post.
- **Materials.** 20 new blocks in one family with the concrete and the rust (`gen_corridor_materials.py`, temporary
  textures, PixelLab batch "Corridor materials"): grimy brick, plaster (and cracked), asphalt (and its centre line),
  sidewalk, floorboards, tar roof, grimy glass pane; corrugated metal, steel beam, oxidised plate, hazard stripe,
  container panels; clinic tile, stained tile, clinic floor; flesh growth and growth veins (the Ward's infestation,
  replacing moss and sculk). Region palettes and floors use them instead of bricks, terracotta, copper, quartz,
  coloured concrete and polished andesite (furniture, doors and containers stay vanilla).
- **Less regular streets.** Setbacks from the wall vary from 10 to 25 blocks per lot; small "islands" (kiosks,
  container stacks, ruins, debris) stand in wide streets, only where at least 6 blocks of street stay open on each
  side of them.
- **Softer borders.** Transition zones are 400 blocks; each floor column picks its region with a blobby noise and a
  per-column fray across the zone (`CorridorPlan.regionForColumn`), buildings with a per-lot jitter.
- **Height.** Strings of wall lights climb higher and denser (up to ~200 blocks, fading into the black fog), and
  flood lamps at the foot of the walls shine straight up, grazing them.
- **Chains.** Dark forged iron with rust instead of the pinkish texture, smoother links (up to 34 x 12 segments),
  real light (they no longer glow at light level 7 in the dark), and they are solid: the local player is pushed out
  of any link it walks into (with a metallic knock).
- Generation fix: a container overwritten by a later block no longer leaves a pending block entity behind.

## 12. Second play session (2026-11-05, late afternoon)

Feedback: no horizontal chains in sight, the town was one straight road, far horde creatures crossed the buttresses,
random lamp patches and floating or repeated structures, weak fall/hit sounds, no quake while the horde passes and
no warning cry before the great quake, not enough buildings to hide in places, wall lights switching off with
distance. Changes:
- **Anomalies are off in version-2 worlds** (`AnomalyPass`): no hanging rooms, duplicated bunkers, lamp gardens,
  stairs or debris in the air among the streets. Legacy worlds keep them.
- **The town**: the main street wanders where the Corridor is wide (`Settlements.streetCenter`); cross streets cut
  the District and the Ward every 50-110 blocks (half of them in the Ironworks), some widened into plazas with a
  monument, benches and street lights; zebra crossings where they meet the main road; a second row of smaller
  buildings (shops, kiosks, sheds, labs) stands in front of deep lots across a 3-block alley; street lights stand
  on the pavement edges. More buildings everywhere (District and Ward lots almost always built; the Expanse has
  shelters, ruins, checkpoints or debris on about half its lots).
- **Horde LOD** follows the wall relief (out over buttresses and ledges, into recessed panels).
- **Wall lights** keep their Veil light much further (preset ranges 80/110/140/190 blocks, budgets 10/20/40/64)
  and fade out smoothly instead of switching off.
- **Horizontal chains**: two thirds of the chains are spans, most of them between 30 and ~150 blocks above the
  floor (clear of the rooftops), so they cross the view from the street.
- **Stampede**: a few seconds before the great quake, the horde's cry comes from where it is coming
  (`event.horde_scream`, 36 voices, far and reverberant); while it passes, a lesser quake shakes the walls.
- **Falls and pain**: each species has its own landing (`creature.<sp>.land`, body, cracking, grit; scaled by size)
  and pain cry (`creature.<sp>.hurt`), used for falls, harmless hits and deaths.

## 13. Postures and better falls/hits (2026-11-05, evening)

- **Four postures** (`AdherentEntity.Posture`, synced): STAND; STOOP under a tight ceiling (less than ~0.4 block
  above the model's head: the Strider and the Burden under 3 blocks, e.g. inside buildings); CROUCH under 2 blocks
  (about 1.85 blocks, and 0.9 wide for the squeezing species so a door is enough); CRAWL through a one-block gap
  (unchanged; not the Burden). The tallest posture that fits here and on the next path nodes is taken at once,
  a taller one only after a second of room. Each lowers the speed a little more. Paths are planned for a 1-block
  body (2 blocks for the Burden). GameTest `striderStoopsAndCrouchesUnderLowCeilings`.
- **Clips** `stoop` and `crouch` for every species (`species_generator.py`, v8): the walk with the neck bowed and the
  face kept forward, the body dropped just enough to clear the ceiling with a margin (crouch: Strider 0.71, Burden
  0.65, Watcher 0.18 blocks; the others already fit), shorter strides and lower lifts; contacts IK-solved and
  validated like the other gaits (0.15-1.3 px). Generated in **merge mode** (`INTERSTICE_ONLY=stoop,crouch`): only
  these clips are computed and merged into the existing .bbmodel, about 6 minutes per species instead of an hour.
- **Falls and hits** re-synthesised with a formant voice model (jittered glottal pulses through moving formants,
  rasp, breath, growl): a landing is a noise-based body thud, a flesh slap, bones and claws clattering, grit, and
  the wind knocked out of it; a hit is a flesh impact followed by a short strangled cry in the species' register
  (hollow resonance for the Hollow, a broken rhythm for the Fractured, thinner for the Clinger).

## 14. Gait, hitboxes, walls and the hunt (2026-11-05, evening)

- **No more hopping**: the run only enters once the speed allows it at least 1.35 of its (long) cycles a second;
  the walk holds up to 2.4 cycles a second (`AdherentAnimator.runMix`); the AI's walking paces are capped per
  species under that (`AdherentEntity.walkMaxSpeed`, `AdherentGroundGoal.walkPace`) and the pursuit is a real run.
- **Footsteps by gait** (`CreatureFootsteps`): walking, the feet carry a soft, spaced rhythm and the hands barely
  brush the floor (lower pitch, at least 150 ms between contacts, each limb once per step); running, every limb.
- **Hitboxes follow the body**: on a wall a thin box (1 block thick) as tall as ~72 % of the body's length; on the
  ground standing, widened to cover more of the length (head and haunches); postures as before.
- **Ground <-> wall**: at the foot of a wall a creature stops, turns to it, rears against it, then goes up while
  the client eases its body from the ground orientation to the wall one; ambient wall creatures low on a wall
  sometimes climb down when a player walks the floor nearby (not the Clinger or the Watcher, at most 3 on the
  ground around a player), and turn off the wall onto the floor.
- **Holes in a wall** (one missing block, a vent, a doorway's edge) no longer swallow or stop a climbing creature:
  it rests on the most protruding surface of the 3x3 patch under its body (`WallMovement.surfaceX`).
- **Facades**: a hunter whose prey stands on a roof or a ledge close by goes straight at the building and climbs
  it like a spider (vanilla climbing while it collides), its body turned upright with the climbing gait, instead of
  going round to the nearest point inside, through a window. Not the Burden. GameTest `hunterClimbsAFacadeToTheRoof`.
- **Entering the hunt**: the creature stops, turns to its prey and rears for 1.5 s while it cries
  (`creature.<sp>.alert`: a rasping shriek, a roar for the Burden, a hiss and snap for the Clinger, a hollow warble
  for the Hollow, two voices for the Fractured), then launches itself; it no longer slides while reared.

## 15. 32x32 everywhere: the Corridor set and its copies of the vanilla blocks (2026-11-05, night)

- **Decorative vanilla blocks replaced by the mod's own** (`tools/assets/make_corridor_set.py`, procedural 32x32):
  concrete stairs/slab, lab slab, concrete low wall, worn bench, worn fence, rusted bars, blackout pane, grimy glass,
  rotten log, grime soil. The concrete/metal family was redrawn at 32x32 with 4 random variants
  (`make_concrete_32.py`).
- **Functional vanilla blocks** (2026-11-05, night, PixelLab Tier 1): the generator still names the vanilla blocks,
  and `ChunkWriter.set` writes the Corridor's own copies instead (`world/generation/VanillaSubstitutes`), properties
  copied by name. Doors (worn, rotten, pale, steel), rusted ladder, concrete button, worn pressure plate, supply
  locker (chest) and supply drum (barrel) -- both barrels underneath, same loot tables (`BlockEntityTypeMixin`
  lets the barrel block entity accept them) --, hospital and worn beds (scenery, `CotBlock`), rotten bookshelf,
  rusted basin (cauldron), rusted stove (smoker), industrial furnace (blast furnace), lab stand (brewing stand),
  scrap anvil, hydraulic press (piston), rusted funnel (hopper), antenna rod (lightning rod), rusted chain, dust
  web, dead roots, flesh node (sculk catalyst), abyssal rock (bedrock). Workstations are scenery (no menus).
  Textures: 32x32 PixelLab generations kept in `tools/assets/pixellab_raw/corridor_copies/`, built by
  `tools/assets/make_corridor_copies.py` (also the 32x32 lamps, the drawn spawn eggs and the water).
- **Water** stays the vanilla fluid; its textures come from the built-in pack "Interstice: Corridor water (32x)"
  (`src/client/resources/resourcepacks/corridor_water/`, on by default). The temporary Faithful 32x pack is gone:
  no third-party texture remains in the mod.

## 10. Known limitations (honest list)

- Regions are not Minecraft biomes (F3 still shows the dimension's single biome); everything keys off `CorridorPlan`.
- Buildings use vanilla blocks; the non-voxel architecture of the brief (smooth pipes, curved supports, organic
  meshes) is not implemented. The Ward's growth is vanilla moss, sculk and roots.
- Machinery does not make noise; only footsteps on metal are louder.
- All new item, block and sound assets are temporary (procedural); PixelLab requests in `docs/PIXELLAB_ASSETS.md`
  (batch "Corridor rework"), audio specs in `docs/CORRIDOR_AUDIO.md`.
- Chunk generation with buildings measured at ~100 ms/chunk in the GameTest (was ~38 ms before, same PC, fewer
  structures); not profiled further yet.
- Not verified: dedicated server with real clients, multiplayer audio, the fog at several render distances, the
  settings screen, how the new sounds actually sound in game.

## 16. Connected textures and material transitions (2026-11-05, night)

Our own, no Continuity needed (`client/render/blocks/ConnectedTextures`, `CorridorBlockModel`: Fabric Renderer API,
baked models wrapped after baking; items untouched).
- **Connected**: grimy glass, rusted plate (its 4 variants), oxidized plate, red and teal containers, corrugated
  sheet, hazard plating. Same blocks side by side form one larger panel framed only on its outer edge. Each face
  is four quadrants, each picking one of five tiles (isolated, centre, joined left-right, joined up-down, inner
  corner) built from two textures: framed and frameless (`tools/assets/make_connected.py`; the frameless plates
  and the container/sheet/hazard frames are PixelLab inpaintings, the glass and oxidized plate procedural).
- **Transitions**: flesh growth (with veins), grime soil and rubble spill a ragged edge onto the neighbouring
  concrete, tile, plaster, brick, asphalt, sidewalk and floorboard faces in the same plane (cutout overlay, two
  variants per side, `textures/block/overlay/`).
- Rendering cost: a few neighbour lookups per face of those blocks at chunk rebuild; nothing per frame.

## 17. Light and dust (2026-11-05, late night)

- **Spot light orientation fixed**: Veil takes a point to a spot light's space as R (p - pos) and the beam runs
  along +Z there; we built R the other way (rotationTo(-Z -> dir)), so the flashlight beam went sideways or
  behind the player, the floor uplights lit the floor and the wall floods lit their own wall. Now
  rotationTo(dir -> +Z) in `DeviceLights` and `LampLightManager`.
- **High flood lamps** (`StructurePass.highFloods`, version 2): on both walls 30 blocks above the floor, every 40
  blocks (sides staggered), aimed straight across. Long narrow beams (15 degrees, 110 blocks), at 55% brightness:
  they pick out the far wall and the chains in between without lighting the corridor. They rank as 2.5x closer
  for the light budget so they are kept when far away.
- **Dust in the beams** (`client/effects/BeamDust`, particle `interstice:beam_mote`): motes spawned inside the
  flashlight cones and the flood lamp beams near the camera, fullbright, tinted by the beam, visible only where a
  beam lights them (they fade in and out as they drift across its edge or the flashlight sweeps past). At most
  420 at once.
- **3D flashlight** (`tools/assets/make_flashlight_model.py`): tail cap, knurled grip with a switch, neck and a
  wide head; the lens is dark or lit (item property `interstice:lit`). Held pointing forward.
- **More connected textures**: bookshelves join side by side into long shelves (frameless version by PixelLab
  inpainting; tops and bottoms keep the planks); sound plaster creeps over the edges of cracked plaster patches
  so they read as ragged holes. **No grid on large walls**: the 4 random variants of each concrete/metal material
  now share their edges and brightness, and the three wall concretes share one edge between them
  (`tools/assets/unify_variants.py`, run once after `make_concrete_32.py`); the dark metal's lines are every 8 px
  so they run on across blocks; the steel beam's side wraps along its length.

## 18. Fourth pass on light (2026-11-05, late night)

- **Darker ground**: in THE INTERSTICE the lightmap is darkened as it is written (`LightTextureMixin`,
  `CorridorLightmap`): the brightness (gamma) setting is undone and block light keeps 10% (dark) to 90% (full) of
  its vanilla value along a steep curve, so the floors are dark and the Veil lights do the lighting. Rendering
  only: light levels (perception, spawning) are unchanged.
- **Wall searchlights** (`wall_spotlight`, LampKind.SPOTLIGHT) replace the high flood lamps: their own model (wall
  plate, yoke, finned drum, big gridded lens; PixelLab lens), a 140-block, 18-degree beam at 4.2 brightness.
- **Flashlight**: the beam starts at the lamp in the hand (from the camera in first person, from the body
  otherwise) and aims at the crosshair 24 blocks ahead; half-angle 34 degrees, 30 blocks, brighter. The 3D
  model's parts all have six faces (the holes were missing tops and bottoms).
- **Beam dust**: denser (14 motes per tick in a flashlight cone, 6 in a spot, up to 900), bigger and brighter,
  and visible across the whole cone (softer fall-off).
- **No vanilla particles**: the biome's ambient white ash is removed.

## 19. Flicker fix and Veil dust (2026-11-05, night)

- **Flicker**: the first darker lightmap recovered the pre-gamma value from vanilla's rounded output; near full
  light one step of rounding meant a quarter of the brightness, so vanilla's tiny per-frame torch flicker became a
  violent one. Now the gamma read inside `LightTexture.updateLightTexture` returns 0 in THE INTERSTICE
  (`LightTextureMixin`, second option read) and `CorridorLightmap` is a plain scale per block light level.
- **Light selection**: lamps already lit rank 30% closer (no two lamps trading places at the edge of the budget
  each frame) and a light fades in over ~16 frames when it gets a Veil handle.
- **Dust in the beams, in the Veil fog shader** (replaces the particles): `fog.fsh` takes up to 6 cones
  (`BeamDust` picks the flashlights, then the nearest flood lamps and searchlights). Per pixel it solves where the
  view ray is inside each cone, walks that part in a 3D grid of 0.32-block cells and draws the speck of each cell
  (42% of cells): millimetre specks, at least a pixel wide, wobbling and twinkling, lit by the cone's fall-off and
  hidden behind the scene. Within 22 blocks of the eye.
- **The real flicker**: `LampLightManager.flicker` took game ticks as seconds (11.3 rad per tick: a 36 Hz strobe with
  cuts to 5%) on 15% of the lamps. Hidden while the floods pointed into the walls; with the beams fixed and the
  darker lightmap the whole corridor strobed. Now in seconds, a soft glow with short smooth dips to a third, and
  flood lamps and searchlights never flicker (they only dim during light-failure events).
- **Less light on the floors**: the lightmap keeps 5% (dark) to 60% (full light) of vanilla block light (was 10-90%),
  the point lamps are a quarter dimmer (industrial 1.2, sodium 1.05), flood lamps 2.0, the flashlight 1.7.
- **Projectors toned down** (they were the excess light): flood lamps 1.2 (was 2.6 at first), searchlights 1.5
  (was 4.2), their in-scattering 0.28 / 0.22 (was 0.9 / 0.7: it filled the air with light and brightened the whole
  view and the chains). **Chains**: swing, impulse ringing and vibrations at half speed (vibrations about 2.5x
  slower), links drawn at 62% brightness. **Flashlight dust**: 40% of the spot lights' speck density.
- **Beacons removed**: `LampLightManager` kept 3 invisible point lights (85-block radius, in-scattering 1.6) high on
  the walls around the camera, meant to show silhouettes through the fog. With the darker lightmap they were the
  main light: they lit each chain evenly from end to end (a screenshot showed a span grey along its whole length
  with no beam near it) and greyed the air. The wall searchlights light the heights now, from a visible fixture.
- **Darker again, stronger flashlight**: the lightmap keeps 3% to 30% of vanilla block light, the post exposure is
  1.05 (was 1.3); the flashlight is 3.6 bright over 40 blocks. Beams carry a `handheld` flag (the flashlight's
  thinner dust no longer depends on the beam length).
- **Veil spot cones, read right**: in Veil's spot shader the cone's edge is `setSize` (radians) and `setAngle` only
  the soft edge inside it (falloff = smoothstep(size, size - angle, angle to the axis)). Our values were swapped:
  the flashlight lit an 11-degree spot (size 0.2) at a quarter strength while its dust filled 34 degrees, and the
  searchlights had a 57-degree cone (size 1.0) that flooded whole plazas evenly. Now flashlight size 34, soft 16
  (2.4 bright); floods 32 / 14; searchlights 12 / 5, tilted 14 degrees up so their lower edge never reaches the
  floor. (The lightmap was checked at runtime: block light 0 comes out black.)
- **A trace of light everywhere**: the lightmap keeps 18% of vanilla's darkest value (about 7/255 on screen), up to
  30% at full light: building shapes can just be made out when one squints. **The flashlight does not light
  itself**: the cone's apex sits 0.6 block ahead of the lamp, so the 3D model and the hand are behind it.
- **Set into the walls and floors** (generation): floor uplights every ~12 blocks (85% present) at the very foot of
  the walls, replacing the floor block (lens flush, dark metal footing): their beams graze the walls so they read as
  immensely tall. Wall searchlights and the wall flood lamps of the lamp strings replace a wall block (lens flush
  with the face); the searchlight is now a full block (gridded lens, finned sides). Searchlight beam: 10-degree
  cone, 6 degrees up, 150 blocks, 0.9 bright, in-scattering 0.4: a faint shaft that just reaches the far wall.
- **GUI brightness kept**: the darkened lightmap leaves the full-sky row alone (the Corridor has no sky light, so the
  world never uses it); inventory items, text and other full-bright drawing (block 15 / sky 15) look normal again.
