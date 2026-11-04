# THE INTERSTICE

*A cinematic psychological horror dimension for Minecraft Java 1.21.1 (Fabric + Veil).*

Two colossal concrete walls run forever along the Z axis. Their tops are lost in fog and darkness. Giant chains
hang between them. Things live on the walls. Most of the time they do not attack.

> Version `0.1.0-alpha` — first playable vertical slice. All textures and sounds are **temporary development
> assets** (see [docs/PIXELLAB_ASSETS.md](docs/PIXELLAB_ASSETS.md)).

---

## 1. Requirements and installation

| Side | Install |
|---|---|
| **Client** | Minecraft Java 1.21.1, Fabric Loader ≥ 0.16 (built against 0.20.1), Fabric API 0.116.x+1.21.1 (built against 0.116.17), **Veil 4.5.x for Fabric 1.21.1** (built against 4.5.2), `the-interstice-0.1.0-alpha.jar` |
| **Dedicated server** | Fabric Loader, Fabric API, `the-interstice-0.1.0-alpha.jar`. **Do not install Veil on a dedicated server**: Veil 4.5.2's Fabric build hard-depends on client-only Fabric modules and refuses to load on servers. The mod's server side does not use Veil. |

Copy the jars into `.minecraft/mods/` (client) or `mods/` (server). Java 21 is required.

Veil download: <https://modrinth.com/mod/veil> (the client refuses to start without it, with an explicit message).

## 2. Building

Requires JDK 21. From the project root:

```bash
./gradlew build
```

Outputs: `build/libs/the-interstice-0.1.0-alpha.jar` (mod) and `-sources.jar`.

Toolchain (verified 2026-11-03): Gradle 9.4.0 wrapper, Fabric Loom 1.16.3 (Veil 4.5.2 is built with Loom 1.16.3
and rejects older Loom), Mojang official mappings, Fabric Loader 0.20.1, Fabric API 0.116.17+1.21.1,
Veil `foundry.veil:veil-fabric-1.21.1:4.5.2` from `maven.blamejared.com`.

Other tasks:

```bash
./gradlew runClient
```

```bash
./gradlew runGametest
```

`runGametest` runs the headless server-side runtime tests (`src/gametest`, never packaged in the mod jar).

## 3. Getting in and development commands

All commands need permission level 2 (operator, or cheats enabled in singleplayer).

| Command | Effect |
|---|---|
| `/interstice enter` | Teleport into THE INTERSTICE (safe arrival point; your previous position is remembered) |
| `/interstice leave` | Return to where you entered from (or world spawn) |
| `/interstice stampede` / `stampede start [intensity]` | Start a Stampede at PREMONITION |
| `/interstice stampede stop` | Abort the Stampede |
| `/interstice stampede phase <phase>` | Jump to a phase (`premonition` … `recovery`, tab-completed) |
| `/interstice quake [intensity]` | Earthquake without a Stampede |
| `/interstice spawn wall` / `spawn ground` / `spawn faller` | Wall creature on the nearest wall / ground searcher in front of you / a creature that falls next to you |
| `/interstice whisper [line]` | Play a voice line to yourself (`hide`, `dont_move`, `not_yet`, `go`, `they_hear`, `look_up`, `closer`, `stay`) |
| `/interstice state` | Print event, director and population state |
| `/interstice debug` | Toggle the diagnostics overlay with live server state for you |
| `/interstice director pause \| resume \| tension <0-1> \| frequency <x> \| trigger <action>` | Control the horror director |

Settings screen: **F7** (rebindable: *Options → Controls → The Interstice*).

## 4. Settings

Client (`config/interstice-client.json`, editable in the F7 screen, applied live):
graphics preset (LOW / MEDIUM / HIGH / ULTRA), fog density, dynamic lighting, volumetric light, post-processing,
bloom, particles, chain detail, camera shake (0 disables), reduce flashing lights, reduce intense effects,
ambience volume, voice volume, environmental audio (EFX), voice subtitles, diagnostics overlay.

| Preset | Veil lights | Light range | In-scattering | Horde instances (mid-LOD) | Particles/s | Chain LOD / range | Fog steps | Bloom |
|---|---|---|---|---|---|---|---|---|
| LOW | 6 | 48 | off | 350 (40) | 40 | 0 / 220 | 4 | off |
| MEDIUM | 12 | 64 | off | 900 (90) | 90 | 1 / 320 | 8 | on |
| HIGH | 24 | 80 | on | 1800 (160) | 150 | 2 / 460 | 14 | on |
| ULTRA | 40 | 112 | on | 3200 (260) | 240 | 3 / 640 | 22 | on |

Server (`config/interstice-server.json`): event frequency multiplier, director on/off, natural Stampedes on/off,
minimum minutes between Stampedes, ambient creatures per player, runners per player, falls per player
(min/max), searchers per player, creature damage, voice on/off. In singleplayer the F7 screen also edits the
event frequency / director / Stampede toggles.

## 5. Architecture

```
com.interstice
├─ Interstice                 common entrypoint: registries, lifecycle, noise hooks
├─ core/IntersticeServer      per-server authoritative state (tick hub)
├─ registry/                  blocks, sounds, entities + particles, dimension keys + generator codec
├─ block/                     lamp fixtures (+ LampHooks: client discovery without client imports)
├─ util/Hash, Noise           deterministic hashing/value noise shared by server and client
├─ world/IntersticeLayout     macro architecture as pure functions of the layout seed
├─ world/generation/          ChunkGenerator, chunk-clipping writer, terrain pass
├─ world/structures/          walkways, beams, platforms, shelters, pipes, lights, landmarks
├─ world/anomalies/           chunk-contained spatial anomaly framework
├─ world/IntersticeTeleport   enter/leave, safe spawn, persistent return point (Fabric attachment)
├─ entity/                    AdherentEntity, WallPopulation
├─ entity/ai/                 Perception (sight), NoiseMap (hearing), AdherentGroundGoal (brain)
├─ entity/navigation/         WallMovement (constrained wall locomotion)
├─ event/                     EnvironmentEvents, VoiceEvents
├─ event/stampede/            StampedePhase, StampedeController (state machine, hybrid horde)
├─ event/director/            HorrorDirector (tension, pacing)
├─ network/ModNetwork         S2C payloads
├─ config/ServerConfig
└─ command/IntersticeCommands

com.interstice.client         (separate Loom source set: never on the dedicated-server classpath)
├─ IntersticeClient, ClientState (event mirror + derived presentation signals)
├─ render/veil/               VeilIntegration (post pipelines, uniforms, stage hooks), LampLightManager
├─ render/atmosphere/         AtmosphereController, sky renderer (wall continuation), dimension effects
├─ render/chains/             ChainField (placement), ChainMesh (procedural LOD meshes), ChainRenderer
├─ render/creatures/          AdherentMeshModel, AdherentAnimator, AdherentRenderer, HordeRenderer,
│                             mesh/ (SkinnedMeshAsset, SkinnedMeshPose: CPU-skinned organic mesh)
├─ audio/                     AudioDirector, LoopSound, VoicePlayer, EfxProcessor (OpenAL EFX)
├─ effects/                   dust particles, budgeted dust spawner, Veil quake camera shake
├─ ui/                        settings screen, HUD (whisper subtitle, diagnostics)
├─ mixin/                     FogRenderer (fog colour/distance), audio Channel (EFX hook)
└─ dev/AutoTest               scripted screenshot smoke test (only if run/interstice_autotest.flag exists)
```

Key design points:

- **Determinism.** The server derives a layout seed from the world seed via the dimension's `RandomState`
  and sends it to clients. World generation, chain placement, the wall continuation above the block world, the
  horde, distant ambient crawlers and audio origins are all pure functions of that seed and coordinates.
- **Chunk safety.** Every structure is described in world space and written through a writer that clips to
  the current chunk, so multi-chunk features (bridges spanning ~100 blocks, beams, landmarks) are seamless
  with no cross-chunk state. Anomalies are always contained inside one chunk.
- **Hybrid horde.** Far/mid creatures are closed-form client visuals (no per-creature networking); near each
  player a capped number of real server entities run, fall, survive and search.
- **Event sync.** A phase change costs one small packet; late joiners and dimension-changers receive the full
  state; clients derive all presentation from (phase, start tick, seed).

## 6. Feature status

See the delivery report in the session for the full matrix. Summary:

**Implemented and runtime-tested (headless GameTests, `./gradlew runGametest`, 10/10 passing):**
custom dimension loading in a server, wall mass / void beyond / floor / bedrock generation at several distances,
safe arrival spot, bridge continuity across 5 chunk borders, wall creature clinging to the real wall surface,
fall → survive → RECOVER, ground searcher detecting a lit visible player (→ HUNT), hidden crouched player behind
cover in the dark staying undetected, Stampede PREMONITION → STRUCTURAL_DISTURBANCE transition, real runners
spawning during HORDE_PASSAGE with at least one real fall, every horror-director action executing.

**Client, verified in a scripted dev-client run (`dev/AutoTest`, screenshots, logs: no errors):** the dimension
loads into an existing world; all Veil shaders compile; the `interstice:atmosphere` post pipeline runs (fog,
bloom, grading); Veil dynamic lights attach to fixtures (26 active / 253 known on HIGH); chains render
(spanning and hanging); the sky continuation renders; quake dust/aberration/shake path executes; emergency red
grading during the Stampede; horde instances are simulated and drawn; real creatures render and animate; the
whisper subtitle appears; EFX processing is active; diagnostics overlay works. About 119 FPS was observed
on HIGH in a small dev window on the development machine. That is one observation, not a benchmark.

**Creature (v4 organic mesh):** compiled, server behaviour covered by the GameTests above, observed in game in
still views and in motion (walk, run, rear, fall/recover, wall climb). Proofs, measurements and remaining
defects: [docs/CREATURE.md](docs/CREATURE.md), captures in `docs/creature/`.

**Not verified:** the settings screen interaction (it is code-complete), audio as actually heard, multiplayer
with more than one real client, dedicated server with real clients, Sodium/Iris compatibility (untested;
Veil logs that Iris is absent).

## 7. Assets

- Textures: [docs/PIXELLAB_ASSETS.md](docs/PIXELLAB_ASSETS.md) lists every required PixelLab image with
  prompt, size, projection, transparency, tiling, filename and destination. Creature UV layout reference:
  `docs/uv/adherent_uv_template_512.png`.
- Audio: 53 original placeholder sounds synthesised by `tools/gen_audio.py`. Voice lines are LPC-whispered
  renders of local Windows TTS and should be replaced with a real whispered recording (mono Ogg Vorbis,
  same filenames under `assets/interstice/sounds/voice/`).

## 8. Performance notes

- Rendering budgets per preset (table above) bound Veil lights, horde instances, mid-LOD models,
  particles, chain detail and draw distance, and fog ray-march steps.
- Chains: one procedural mesh per LOD reused for every link, per-link Veil sphere frustum culling, distance
  LOD, preset draw distance. Vertices are transformed on the CPU (no instancing yet).
- Horde: closed-form positions, early rejection by Z distance, frustum culling; no per-tick simulation.
- Lights: chunk palette pre-check (`maybeHas`) before scanning sections; nearest-N selection per frame.
- Server: population uses bounded per-player box queries every 40 ticks; perception is staggered every 4
  ticks per creature; the director evaluates once per second.
- World generation measured in the headless test: ~40 ms per chunk through the *full* vanilla pipeline
  (including lighting) when forced synchronously on the server thread. Normal generation runs on worker threads.
- No FPS figures are claimed: they have not been measured.
