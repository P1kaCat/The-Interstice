# THE INTERSTICE — project context (handoff notes)

Working notes from the Claude Code development sessions (2026-11-03/04): what was decided, why, what
was verified, and what is left. Read this before continuing the work.

## Goal

Original brief: a cinematic psychological horror dimension for Minecraft Java **1.21.1 (Fabric + Veil)**.
Two colossal concrete walls along Z, giant animated chains, creatures living on the walls, a signature
**Stampede** event (premonition → earthquake → horde on the walls → creatures fall → ground search →
the whispered "Hide."), an invisible horror director, minimalist UI, multiplayer-safe, performance-budgeted.

## Toolchain (verified 2026-11-03)

| Item | Version | Note |
|---|---|---|
| Minecraft | 1.21.1 | Mojang official mappings (`loom.officialMojangMappings()`) |
| Fabric Loader | 0.20.1 | |
| Fabric API | 0.116.17+1.21.1 | |
| Fabric Loom | **1.16.3** | Veil 4.5.2 was built with Loom 1.16.3 and rejects older Loom |
| Gradle wrapper | 9.4.0 | |
| Veil | `foundry.veil:veil-fabric-1.21.1:4.5.2` | maven.blamejared.com |
| JDK | 21 | `JAVA_HOME=C:\Program Files\Java\jdk-21` |

**Veil is client-only** (`modClientImplementation`, `recommends` in fabric.mod.json): its Fabric build
hard-depends on client-environment Fabric modules (rendering, key-binding) and cannot load on a dedicated
server. Server = Fabric API + this mod only. The client entrypoint throws a clear error if Veil is missing.

## Commands

```bash
./gradlew build          # jar in build/libs
./gradlew runClient
./gradlew runGametest    # headless server tests (src/gametest, not packaged)
```

In game (op): `/interstice enter|leave|stampede [start|stop|phase <p>]|quake|spawn wall|ground|faller|whisper [line]|state|debug|director pause|resume|tension|frequency|trigger <action>`. Settings screen: **F7**.

### Visual smoke test (dev only)

`run/interstice_autotest.flag` enables `client/dev/AutoTest`; it enters the dimension, stages events,
saves screenshots to `run/screenshots/` and quits. File content `model` = creature close-up turntable
(idle, walk frames, hunt, rear/stare, on wall). Launch with an existing save named `autotest`:

```bash
./gradlew runClient --args="--username Explorer --quickPlaySingleplayer autotest"
```

The `autotest` save was copied from `build/gametest/world` (a GameTest world). The datapack dimension
loads into it, which also proved the dimension appears in pre-existing worlds.

## Architecture (key decisions)

- **Determinism**: `IntersticeLayout` = macro architecture as pure functions of a layout seed
  (derived from the world seed via `RandomState.getOrCreateRandomFactory(id("layout"))`). The server sends
  the seed (`LayoutSync`); the client rebuilds chain placement, the wall continuation above the block world,
  horde lanes and audio origins locally. Uses only `util/Hash` + `util/Noise` (never `Random`/MC noise).
- **Chunk-safe worldgen**: every structure is described in world space and written through `ChunkWriter`,
  which clips to the current chunk. Anomalies always fit inside one chunk. Passes: Terrain → Structures
  (bridges, beams, platforms, bunkers, pipes, lamps, lamp posts, landmarks) → Anomalies.
  World: floor y=64, block walls up to y=300, `VIRTUAL_TOP` 1400 for rendered continuation, pits to y=-50
  with water at the bottom.
- **Sky continuation**: `IntersticeSkyRenderer` draws the walls above y=300 in the sky pass *with depth
  writes*, so terrain occludes it and the Veil fog shader fogs it by true distance.
- **Veil**: post pipeline `interstice:atmosphere` (fog → threshold → Veil dual blur ×3 → grade) and a no-bloom
  variant; `renderStage: after_level` (needed so the depth buffer is intact). Uniforms pushed in
  `FabricVeilPostProcessingEvent.PRE`. Chains + horde drawn in `FabricVeilRenderLevelStageEvent`
  AFTER_ENTITIES. Lights: `LampLightManager` (Veil point/spot lights, in-scattering, budgets per preset).
  Camera shake: subclass of Veil `ScreenShakeType`.
- **Hybrid horde**: far/mid = closed-form client instances (no networking per creature); near = real
  server `AdherentEntity` runners, some of which really fall, survive and search. One packet per phase change.
- **Creature AI**: wall mode = constrained 2D plane locomotion (`WallMovement`, re-solves X against real
  blocks); ground mode = `AdherentGroundGoal` brain (RECOVER/SEARCH/INVESTIGATE/HUNT/RETREAT) using
  `Perception` (FOV, two LOS rays, light, crouch, motion, distance, enclosure) + `NoiseMap` (hearing with
  occlusion). Creatures are transient (`shouldBeSaved() = false`).
- **Audio**: `AudioDirector` (beds, positional one-shots at believable origins, silence windows, event
  sync), `EfxProcessor` (OpenAL EFX reverb + occlusion low-pass, via mixin on `Channel.play`; disables
  itself if unsupported), `VoicePlayer` (listener-relative whisper + optional subtitle).

## The creature (v4, organic skinned mesh)

Full description, files and regeneration commands: `docs/CREATURE.md`. Brief: `docs/MONSTER_REDESIGN_PROMPT_FR.md`,
references `docs/monster-references/`. The cube-built v3 is archived (git tag `adherent-v3-cubes`,
`models/archive/adherent_v3_cubes.bbmodel`, `tools/archive/bbmodel_cubes/`, `docs/archive_BLOCKBENCH_cubes.md`).

```
tools/creature/design_adherent.py  -> models/adherent.bbmodel (editable source, Blockbench Generic format)
                                      + models/adherent_validation.txt
tools/creature/export_adherent.py  -> assets/interstice/models/entity/adherent.json + textures/entity/adherent.png
tools/creature/make_gifs.py        -> docs/creature/*.gif, v*.png (from the dev autotest captures)
```

- 24 bones (hips, spine, chest, neck, head, jaw, shoulder/arm/fore/hand/fingers x2, thigh/shin/foot/toes x2),
  49 lofted meshes, 1474 vertices; LOD0 2296 triangles, LOD1 (vertex clustering, horde mid-distance) 1216.
  512x512 painted skin. Sizes (blocks): length 3.27, shoulder 1.73, width 1.47, reared 3.27. Hitbox 1.4x1.8.
- Rendering: CPU linear blend skinning (`SkinnedMeshPose`, up to 3 bones per vertex, joint weights computed
  by the exporter) into the vanilla entity vertex consumer (`AdherentMeshModel` inside the vanilla
  `MobRenderer`). No GeckoLib, no glTF loader; Veil is not involved in the creature mesh.
- 10 clips: idle, observe, walk, run, climb, cling, rear, threat, fall, recover. Contacts are IK-solved in
  the generator and validated between keys at 240 Hz with the runtime interpolation (walk 0.24 px, run
  0.63 px, climb 0.19 px worst height error). Walk/run/climb play by distance travelled (`cycleBlocks`).
- Generator convention: looping clips are written over a 0..1 phase, one-shot clips (observe, rear,
  threat, recover) in seconds. (Fixed 2026-11-04: one-shot clips used to receive the 0..1 phase, so
  recover never stood back up and threat stopped at 83 % of the jaw opening.)
- Validation captures: `echo model > run/interstice_autotest.flag`, then
  `./gradlew runClient --args="--username Explorer --quickPlaySingleplayer autotest"` (world `autotest`,
  superflat), then `python -I tools/creature/make_gifs.py`. The autotest logs subject state, FPS and
  skinning cost at each capture.

**Known gaps:** Blockbench's own animation preview of the file is unverified (Blockbench is not driven from
this environment); contacts are not validated across crossfades, turns or slopes; the texture is a single
base colour map (no normal/roughness); limbs are leaner and less muscled than the reference images; worlds
that contain the dimension trigger vanilla's "experimental settings" warning on load.

## The creature family (2026-11-04)

Eight species share the Adherent's skeleton: Adherent, Crawler, Strider, Burden, Clinger, Hollow, Watcher,
Fractured. `docs/CREATURE_ECOSYSTEM.md` (species, models, behaviour), `docs/STAMPEDE.md` (composition,
LOD), `docs/PERFORMANCE.md` (measurements). Key points for future work:
- `entity/species/Species.java` holds every species parameter; `SpeciesMix` the weighted distributions;
  one entity type per species, all `AdherentEntity`.
- Models: `tools/creature/species_generator.py` (morph of the Adherent template; edit the `SPECIES` table),
  then `python -I tools/creature/export_adherent.py <species>`; `preview_lineup.py` renders a quick offline
  line-up. The Adherent itself still comes from the frozen `design_adherent.py`.
- Captures: `echo model > run/interstice_autotest.flag` (full: ~850 screenshots, ~4 min), or `model bench`
  (benchmarks only, uncapped FPS); then `python -I tools/creature/make_gifs.py`.
- Watch out: a bash heredoc containing apostrophes breaks in this environment; write scripts to files.

## Verification status

- `./gradlew runGametest`: 20/20 passing on 4 consecutive runs (2026-11-04, after the creature family:
  10 original tests + 10 family tests; the three perception tests now allow 1400 ticks because detection by a
  wandering searcher was observed to take up to ~550 ticks) (world gen at several distances, bridge continuity across chunk
  borders, safe spawn, wall clinging, fall → survive → recover, searcher hunts a lit visible player, hidden
  crouched player behind cover stays undetected, Stampede phase transition, real runners + falls, all
  director actions). Test-only mixin creates the dimension in the GameTest server (it ignores datapack dims).
- Client autotest: no errors; all Veil shaders compile; diagnostics showed ~26 Veil lights / 253 fixtures,
  chains, sky quads, horde instances, EFX active. ~119 FPS on HIGH in a small dev window (one observation).
- Not verified: settings-screen interaction, audio as heard, multiplayer with real clients, dedicated
  server with real clients, Sodium/Iris.

## Assets

All textures/sounds are **temporary**. `docs/PIXELLAB_ASSETS.md` lists PixelLab requests (prompt, size,
tiling, transparency, filename, destination). Sounds: 53 original placeholders from `tools/gen_audio.py`
(mono 32 kHz Vorbis; libsndfile needs chunked writes on Windows). Voice lines = local Windows TTS (Zira)
re-synthesised as whispers with an LPC noise vocoder; replace with a real recording.

## Environment notes / gotchas

- Windows + Git Bash. The Bash tool's heredocs with `'EOF'` broke on some Java content: prefer writing
  files directly.
- Minecraft EULA was **not** accepted (`build/gametest/eula.txt` stays `false`); headless testing uses
  Fabric's GameTest path.
- Blockbench MCP (`jasonjgardner/blockbench-mcp-plugin`) is not registered: auto mode blocks installing
  code from GitHub. Old manual commands: `docs/archive_BLOCKBENCH_cubes.md`.
- The dev autotest `next()` sets `stepStart = ticks + 1`, so `t == 0` really runs on a step's first tick.
- 379 skills from `P1kaCat/SkillsMachine` were installed into `~/.claude/skills` (Codex-oriented; ~26k
  tokens of descriptions per session). None covers Blockbench; the Blender skills (`creature-artist`,
  `body-horror-style`, `animation`, `rigging`) guided the creature design.
- User communicates in French.
