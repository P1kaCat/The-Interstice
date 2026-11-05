# THE CORRIDOR — creature and environment audio (specification)

The files in `src/main/resources/assets/interstice/sounds/` listed here are **procedural development sounds**
(`tools/audio/gen_corridor_audio.py`). They are layered and varied and work in game, but they are not final sound
design. To replace one, record it to the same path and name (mono Ogg Vorbis, 44.1 or 48 kHz, peak about -1 dBFS,
no long silence at the start) and rebuild; no code change is needed. More variations can be added in
`sounds.json` under the same event.

Integration points: `step`/`scrape` are played by the client when the animation puts a limb on the floor/wall
(`CreatureFootsteps`); `breath` and `click` are the creature's ambient sounds (server); `vocal` is its alert
(server, `AdherentGroundGoal`); `click` is also the Burden's body creak while it moves on a wall and the Strider's
joint creak every third step.

## Per species (`creature/<species>/<kind>_<n>.ogg`, event `creature.<species>.<kind>`)

| Species | step (×4, 0.05–1.6 s) | scrape (×3) | breath (×2–3, loop-free) | vocal (×2–3) | click (×3–4) |
|---|---|---|---|---|---|
| Adherent | padded thud + claw on concrete | claws dragging on a wall, organic friction | uneven breathing with low throat movement | throat growl rising | knuckle/joint click |
| Crawler | 2–3 rapid claw taps | short burst of friction | fast shallow panting | chittering | tiny ticks |
| Strider | long-spaced deep impact with body resonance | slow drag | deep slow breath, weight shifting | low moan | articular creaking |
| Burden | massive low impact, sub rumble, crumbling tail | heavy drag | heavy wet breathing | deep organic resonance | body creaking under its weight |
| Clinger | near-silent finger tap | brief nail scrape | **no breathing**: a few finger clicks | sudden hiss and snap | small joint clicks |
| Hollow | hollow knock (resonant, like an empty body) | hollow scrape | breathing through a tube, irregular | irregular dry vocalisation | dry clicking (listening) |
| Watcher | soft heavy step | quiet scrape | extremely slow breathing (5 s) | subtle deep resonance | slow creak of the head turning |
| Fractured | 0–1 heavy fall, 2–3 dragging scuff (asymmetric) | irregular scrape | stuttering breath | two voices out of step | misaligned double click |

Recording notes for the final set: layered (contact + body + breath where relevant), no generic monster screams,
no vanilla sounds. Silence is part of the Clinger and the Watcher: keep their files quiet and sparse.

## Region beds (`ambient/<region>.ogg`, looped, streamed, ~26 s, seamless)

| Event | Character |
|---|---|
| `ambient.forgotten_district` | wind through empty streets, distant building creaks, a rare interior knock |
| `ambient.ironworks` | inharmonic metallic resonance slowly beating, machinery hum, chain tension |
| `ambient.contaminated_ward` | ventilation, low organic murmur, slow wet movement |
| `ambient.dead_expanse` | sparse low wind, long silences, one far chain |

## Environment (`env/<name>_<n>.ogg`)

| Event | Character | Used by |
|---|---|---|
| `env.building_rattle` (×3) | loose glass and metal rattling in a frame | Burden steps near an indoor listener; quakes and the Stampede indoors |
| `env.dust_fall` (×2) | grit trickling from a ceiling | same |
| `env.metal_contact` (×3) | a claw or a heavy foot on metal | creature steps on metal floors |
| `env.chain_tension` (×2) | a slow stretched groan | chains near the listener during quakes and the horde |
