# Performance

Measurements, not projections. All numbers come from the dev autotest benchmark (`dev/AutoTest`, flag file
`run/interstice_autotest.flag` containing `model` for the full capture run or `model bench` for the
benchmarks only), which logs `BENCH` lines with FPS, client creature counts, CPU skinning cost (time spent
skinning meshes per second, vertices skinned per second), horde counts and the integrated server's average
tick time.

**Machine and conditions (one machine, a few runs, not a benchmark suite):** Windows 11, NVIDIA GeForce
RTX 3060, JDK 21, dev client (Fabric Loom `runClient`, not an optimised production launch), integrated
server, preset HIGH, render distance 12, small dev window (854×480), 2026-11-04.

## v5 meshes (detailed anatomy, 5921 vertices instead of 1474)

Same machine and scenes, after the v5 template (2026-11-04):

| Scene | Frame cap | FPS | Creatures on the client | CPU skinning | Server tick (avg) |
|---|---|---|---|---|---|
| A. 40 real creatures + ambient, uncapped | none | 631 (v4: 895) | 45 | 20.8 ms/s, 3.74 M vertices/s | 1.45 ms |
| B. Stampede horde passage, uncapped | none | 507–523 | 16–17 real runners; 1703–1770 simulated, 5–8 mid drawn | 19.5–20.8 ms/s | 1.14–1.27 ms |
| A, in the full capture run | vsync, 120 | 97 (below the cap) | 46 | 43.6 ms/s, 7.05 M vertices/s | 1.28 ms |
| B, in the full capture run | vsync, 120 | 99–107 | 13–15 real runners; 1686–1775 simulated, 53–60 far, 37–39 mid | 41–43 ms/s | 1.0–1.07 ms |

The skinning cost per vertex is unchanged (about 5.6 ns); the meshes are four times heavier, so the
45-creature scene costs about 1.6 ms per frame instead of 1.1. The capped rows were measured at the end of
a run that had just written about 850 screenshots and they no longer hold the 120 FPS cap; the uncapped
rows (a separate, benchmark-only run) are the ones to compare. The v4 tables below are kept for reference.

## v4 scenes and results

| Scene | Frame cap | FPS | Creatures on the client | CPU skinning | Server tick (avg) |
|---|---|---|---|---|---|
| A. 40 real creatures with AI on (5 per species: 3 on the walls, 2 on the ground) + 5 ambient, in the Interstice | vsync, 120 | 120 (at the cap) | 45 | 19.5 ms/s, 2.87 M vertices/s | 1.04 ms |
| A, uncapped | none | 895 | 45 | 8.3 ms/s, 1.31 M vertices/s | 1.29 ms |
| B. Stampede horde passage (composition with a Hollow surge), camera on the wall | vsync, 120 | 119–120 (at the cap) | 12–13 real runners; 1695–1782 visual instances simulated, 46–47 far quads and 39–41 mid meshes drawn | 25.4 ms/s, 3.54 M vertices/s | 0.86–0.90 ms |
| B, uncapped (different camera spot: 0 far / 4–7 mid drawn) | none | 489–499 | 16–17 real runners; 1712–1779 simulated | 27.3 ms/s, 4.0 M vertices/s | 1.16 ms |

Reading the numbers:

- CPU skinning costs about 6–7 ns per vertex once the JIT is warm (8.3 ms for 1.31 M vertices; 27.3 ms
  for 4.0 M). In scene B uncapped that is 0.055 ms per frame; in scene A uncapped 0.009 ms per frame
  (8.3 ms over 895 frames). The creature mesh is not the bottleneck at these counts.
- The capped runs only show that the scenes stay at the cap; they say nothing about headroom. The
  uncapped runs do, but their camera framing differs from the capped ones (in B uncapped almost no horde
  instance was in view), so the two B rows are not comparable draw for draw.
- Server: 45 creatures with AI (wall locomotion, neighbour queries every 20 ticks, ground perception and
  pathfinding) keep the integrated server at about 1.0–1.3 ms per tick (budget 50 ms).
- Not measured: a dedicated server with several players, the LOW/MEDIUM/ULTRA presets, large windows,
  Sodium/Iris, and thousands of *real* entities (by design there are never more than
  `maxRunnersPerPlayer` real runners per player; the rest of the horde is visual).

Screenshots of the benchmark scenes: `docs/performance/bench_*.png` (the horde scene is very dark: it is
lit only by the dimension's sparse lamps).

## What keeps it scalable

- **Hybrid horde** ([STAMPEDE.md](STAMPEDE.md)): thousands of visual instances are closed-form functions of
  (seed, index, time) with no per-tick simulation and no networking; only near creatures are real.
- **LOD**: real entities beyond 26 blocks are skinned on LOD1 (800–1960 vertices depending on the species,
  instead of 5921); mid horde instances always use LOD1; far instances are single quads.
- **Batching**: horde meshes are drawn in one batch per species texture; far quads in one batch.
- **Bounded queries**: neighbour reactions use small box queries (7 blocks) every 20 ticks, staggered by
  entity id; population upkeep runs every 40 ticks; there is no scan of every creature every tick.
- **No per-frame allocation in skinning**: pose and skinning buffers are reused per entity
  (`SkinnedMeshPose`); horde poses are reused per species.
- **Assets**: 8 meshes (about 7 MB of JSON in the jar, parsed once and cached per species, reloaded with
  resource packs) and 8 textures (seven 512×512, the Burden 1024×1024; about 2.8 MB).

## Diagnostics in game

`/interstice debug` (or the diagnostics option) shows the skinning cost (ms/s and vertices/s), horde
counts (simulated, far, mid), real creature count and FPS.
