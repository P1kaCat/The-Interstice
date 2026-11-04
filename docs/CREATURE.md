# The Adherent: model, animation and rendering pipeline (v4)

The Adherent is now the dominant species of a family of eight; the other seven are derived from this model
(see [CREATURE_ECOSYSTEM.md](CREATURE_ECOSYSTEM.md)). This document covers the Adherent and the shared
pipeline (exporter, runtime skinning, animator), which every species uses.

The v4 creature replaces the cube-built v3 (archived as git tag `adherent-v3-cubes` and
`models/archive/adherent_v3_cubes.bbmodel`). Design brief: `docs/MONSTER_REDESIGN_PROMPT_FR.md`;
references: `docs/monster-references/`.

## Files

| File | Role |
|---|---|
| `models/adherent.bbmodel` | **Editable source.** Blockbench *Generic* (`free`) format: 24 bones, organic meshes rigidly parented to bones in the editor, 512×512 skin embedded, 10 keyframed animations. |
| `tools/creature/design_adherent.py` | Generator that produced the source: lofted tapered meshes, UV atlas and painted skin, animation authoring, IK solve of contacts and validation report. Re-running it **overwrites** the `.bbmodel`. |
| `tools/creature/export_adherent.py` | Exporter: `.bbmodel` → game asset. Works on any edited version of the file. |
| `src/client/resources/assets/interstice/models/entity/adherent.json` | Game asset: bones, LOD0 + LOD1 meshes with skin weights, animation channels. |
| `src/client/resources/assets/interstice/textures/entity/adherent.png` | Skin, extracted from the `.bbmodel`. |
| `models/adherent_validation.txt` | Generator report: sizes and contact errors **between keyframes**. |
| `tools/creature/make_gifs.py` | Builds the GIFs/stills in `docs/creature/` from the dev autotest captures. |
| `tools/creature/uv_template.py` | Draws the UV layout over the skin (`docs/uv/`) for repainting. |

## Regenerating

After editing the `.bbmodel` in Blockbench (keep bone names; keep bone rest rotations at 0; don't rotate
mesh elements, move their vertices instead):

```bash
python -I tools/creature/export_adherent.py
```

```bash
./gradlew build
```

To rebuild everything from the generator (this discards manual Blockbench edits):

```bash
python -I tools/creature/design_adherent.py
```

```bash
python -I tools/creature/export_adherent.py
```

## How it renders in game

- `SkinnedMeshAsset` loads the JSON (reloaded with resource packs).
- `SkinnedMeshPose` adds the weighted clips (additive offsets from the rest pose, Catmull-Rom sampling
  identical to the generator's validation), computes bone matrices (`G = G_parent · T(pivot − parentPivot + pos) · Rz · Ry · Rx`),
  skins every vertex on the CPU (linear blend skinning, up to 3 bones), converts Blockbench space to
  Minecraft model space and emits triangles through the vanilla vertex consumer. Vanilla lighting, the
  red hurt overlay and Veil's deferred lights apply as for any entity.
- `AdherentMeshModel` plugs this into the vanilla `MobRenderer`; `AdherentRenderer` orients the body on
  walls (belly to the surface, heading from movement) and tumbles it while falling.
- `AdherentAnimator` crossfades base clips (idle, turn, walk, run, cling, climb, rear, fall, recover) and adds
  overlays (observe, threat). Walk/run/climb are driven by distance travelled: one cycle per
  `cycleBlocks` (stride ÷ stance fraction), so planted contacts move at ground speed. A hunt begins with
  a 1.5 s rear-and-threat beat. The head turns late towards what the creature is looking at. Standing
  while the body yaw changes plays the turn clip, driven by the yaw turned (`cycleDegrees`), see
  [CREATURE_ECOSYSTEM.md](CREATURE_ECOSYSTEM.md).
- Horde mid-distance instances use LOD1 (vertex-clustered, about half the triangles) with the climb
  clip; far instances stay sprites.

## Skinning at joints

In Blockbench each mesh follows one bone. The exporter blends vertices near a joint 50/50 between the
two bones at the pivot, fading out over 3 px, so overlapping segments move together at the joint
instead of separating. There are no painted weights: this is automatic, reproducible, and coarse
compared with hand-painted skinning.

## Visual proofs (`docs/creature/`)

Captured in game by the dev autotest (`echo model > run/interstice_autotest.flag`, then
`./gradlew runClient --args="--username Explorer --quickPlaySingleplayer autotest"`), assembled with
`python -I tools/creature/make_gifs.py`. Overworld views use a superflat daylit world with an armor stand
(player size, about 2 blocks) as the scale reference.

| File | What it shows |
|---|---|
| `v1_front.png`, `v2_profile.png`, `v3_three_quarter.png` | Idle, neutral background, next to the armor stand |
| `walk.gif` (real speed), `run.gif` | Locomotion driven by real travel; the camera follows, the armor stand stays behind |
| `rear.gif` | Hunt start: rearing to about 3.3 blocks with the threat overlay (mouth opening) |
| `fall_recover.gif` | Fall clip, impact, getting back up |
| `v4_wall_cling.png`, `climb.gif` | On the real wall in the Interstice (night vision on for readability) |
| `v5_in_dimension.png` | In the dimension's own light and fog, no night vision |

Each GIF frame is one captured game frame (every 1 or 2 ticks); step through them in any GIF viewer to
check contacts.

## Status (2026-11-04)

| | |
|---|---|
| Compiled | yes (`./gradlew build`) |
| Automated tests | `./gradlew runGametest` 10/10 (server behaviour, wall clinging, fall/recover, hunting, horde runners); the generator's contact validation (`models/adherent_validation.txt`) |
| Observed in game | the five stills above |
| Observed in motion | the five GIFs above (walk, run, rear+threat, fall+recover, climb) |
| Measured | one creature on screen: 1.4 to 2.0 ms of CPU skinning per second at 120 FPS (1474 vertices per frame), dev machine, one observation |
| Not observed | observe overlay alone, cling idle over time, crossfades during real AI behaviour, horde LOD1 up close, multiplayer |

## Known limitations

- Generator convention: cycles (walk, run, climb, cling, idle, fall) are written over a 0..1 phase;
  one-shot clips (observe, rear, threat, recover) in seconds.
- Blockbench preview of the animations is **not verified**: Blockbench was not driven from this
  session (the Blockbench MCP server is not registered). The file follows the Generic format, but the
  rotation sign convention Blockbench uses when previewing animations in this format has not been
  checked against the in-game result.
- Contact validation (`models/adherent_validation.txt`) covers the authored clips, not crossfades
  between clips or slopes/steps (turning in place: `models/adherent_turn_validation.txt`).
- No normal/roughness maps; the skin is a single base-colour texture.
- Compared with the reference images the limbs are leaner and the musculature less defined; the head
  projects forward more than it sinks between the shoulders.
- The fall clip is authored for mid-air; on flat ground (as in `fall_recover.gif`) the tumbling limbs are
  shown on the spot before the impact.
- Worlds that contain the dimension show vanilla's "experimental settings" warning when loaded (datapack
  dimension); the dev autotest dismisses it, players have to click through it.
