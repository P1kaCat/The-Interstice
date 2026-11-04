# The Stampede (multispecies)

Server-authoritative state machine in
[`StampedeController`](../src/main/java/com/interstice/event/stampede/StampedeController.java):
IDLE → PREMONITION → STRUCTURAL_DISTURBANCE → EARTHQUAKE ("Hide.") → HORDE_APPROACH → HORDE_PASSAGE →
CREATURE_FALLS → GROUND_SEARCH → AFTERMATH → RECOVERY → IDLE. One packet per phase change.

## Composition

Each event draws a species composition ([`SpeciesMix`](../src/main/java/com/interstice/entity/species/SpeciesMix.java)):

1. Base weights from `config/interstice-server.json` → `stampedeSpeciesWeights`
   (default: adherent 65, crawler 12, strider 8, burden 5, clinger 3, hollow 3, watcher 2, fractured 2).
2. Per-event variation: every weight × exp(N(0, `stampedeCompositionVariation` = 0.35)).
3. With probability `stampedeSurgeChance` (0.12), one species other than the Adherent is multiplied by
   `stampedeSurgeFactor` (5): a rare Stampede dominated by Striders, Hollows, ...
4. Normalised and logged (`Stampede composition: ...`); `/interstice species` prints the current one.

The weights are sent to clients in the Stampede sync packet. The server picks real runners from them;
clients assign species to their visual instances by hashing the instance index into the same
distribution (`SpeciesMix.pick(u)`), so both sides agree without per-creature networking (tested: the
codec round trip preserves the weights and client/server picks match).

## Species roles during the event

| | Run speed × | Fall chance × | Death on impact × | Other |
|---|---|---|---|---|
| Adherent | 1.0 | 1.0 | 1.0 | the main flow |
| Crawler | 1.15 | 0.3 | 0.6 | rarely falls |
| Strider | 1.25 | 1.0 | 1.3 | long strides (fewer, longer cycles) |
| Burden | 0.8 | 0.5 | 0.7 | vibration while running; heavy landing (40-block noise, dust, loud low impact) |
| Clinger | 0.9 | 0.15 | 1.0 | in the visual horde most of them hold on (barely move) while the rest passes; ambient Clingers hold on during the quake (85 %) and react only in AFTERMATH |
| Hollow | 0.95 | 2.5 | 1.4 | loses its grip most often |
| Watcher | 0.9 | 0.8 | 1.0 | ambient Watchers react at the start of PREMONITION, the others 4 s later |
| Fractured | 1.0 | 1.3 | 1.0 | |

Fall multipliers are also configurable per species (`speciesFallChanceMultiplier`). The forced falls at
the end of CREATURE_FALLS prefer the most fall-prone of a few random runners.

## Ground behaviour after a fall

Each survivor rolls a disposition from its species weights (HUNTER, INVESTIGATOR, FLEER, HIDER,
STUNNED; see [CREATURE_ECOSYSTEM.md](CREATURE_ECOSYSTEM.md)). The guaranteed searcher spawned at the
start of GROUND_SEARCH (so that hiding matters) is an Adherent with the full brain.

## Horde level of detail

| Tier | Distance | What | Per-instance cost |
|---|---|---|---|
| Near | < 16 blocks of the camera, capped per player (`maxRunnersPerPlayer`, default 8) | real server entities (collision, perception, falls, damage) with full skinned mesh (LOD0 within 26 blocks, LOD1 beyond) | server tick + network + CPU skinning |
| Mid | 16–64 blocks, up to the preset's mid budget | the species' LOD1 mesh posed with its climb clip, batched per species texture | CPU skinning of 440–780 vertices |
| Far | up to 430 blocks | one camera-aligned quad per instance, size/aspect/shade from the species | arithmetic + 4 vertices |

Visual instances are closed-form functions of (seed, index, time): nothing is simulated per tick or
networked. They are not promoted into real entities; the near tier spawns its own runners at the edge
of the visual pack, and visual instances inside 16 blocks are skipped, so the two tiers do not overlap
in space. A visual instance and a real runner are not the same individual: there is no identity handover
(a creature can be seen to "change" between tiers if one watches the 16-block boundary closely).

## Tests

GameTests: phase transition; real runners and real falls during HORDE_PASSAGE; runners of at least 4
species when the composition is even; species distribution within 1.2 % of the configured weights,
normalised variation and surge rate; sync codec and client/server species agreement.
Not automated: the visual horde tiers (client rendering) — see the benchmark in
[PERFORMANCE.md](PERFORMANCE.md) and `docs/performance/bench_horde*.png`.
