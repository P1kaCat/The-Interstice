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

## The creature (current state — in progress)

Pipeline: **`models/adherent.bbmodel` is the source of truth**.

```
tools/bbmodel/design_adherent.py  -> models/adherent.bbmodel (+ textures/entity/adherent*.png)
tools/bbmodel/bbmodel_to_java.py  -> AdherentGeometry.java + AdherentAnimations.java (generated)
```

- Blockbench's own Java export targets Yarn names; the converter emits Mojang-mapped code. Coordinate
  mapping Blockbench → MC model space: x mirrored, y = 24 − y, rotations (−x, −y, +z), animation
  positions (−x, −y, z).
- v3 design (from scratch): starved human figure on all fours, arms with an extra elbow splayed like a
  spider/mantis, grasshopper legs, narrow eyeless lipless skull, jaw split down the middle opening
  sideways. 21 bones, ~155 cubes, 128×128 skin. Render scale 1.6 (`AdherentAnimator.SCALE`), hitbox 1.3×2.4.
- 9 keyframed animations (idle, walk, run, rear, screech, jaws, listen, fall, recover), played with the
  vanilla `KeyframeAnimations` system; `AdherentAnimator` crossfades base layers by behaviour and adds
  overlays; walk/run time is driven by distance travelled.
- The generator plants the rest pose automatically (lowers the body until claws touch y=24, bisects shin
  angles for the soles).

**Open problems (next steps, in order):**
1. **Bake IK into the keyframes**: in `design_adherent.py`, after sampling each animation, solve limb
   angles (arm: elbow+fore; leg: leg+shin) with the `world()` FK so stance-phase hands/feet sit on y=24.
   Needed for: walk/run (feet don't fully touch), rear (the body floats; feet must stay planted), idle.
2. Re-run the model autotest and judge: the last screenshots predate the v3 skull/splayed arms (the earlier
   v3 head looked boxy/cartoonish, the arms stood up like rabbit ears from the front).
3. Re-run `./gradlew runGametest` (last full pass was before the v3 creature; hitbox changed since).
4. Jaw opening direction (yRot sign) not yet visually confirmed.

## Verification status

- `./gradlew runGametest`: 10/10 passing (world gen at several distances, bridge continuity across chunk
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
  code from GitHub. Commands to run manually are in `docs/BLOCKBENCH.md`.
- 379 skills from `P1kaCat/SkillsMachine` were installed into `~/.claude/skills` (Codex-oriented; ~26k
  tokens of descriptions per session). None covers Blockbench; the Blender skills (`creature-artist`,
  `body-horror-style`, `animation`, `rigging`) guided the creature design.
- User communicates in French.
