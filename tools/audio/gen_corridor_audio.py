"""
Corridor rework audio (2026-11-05): per-species creature sound sets, region ambience beds and environmental
reactions, synthesised locally with numpy/scipy (no samples, no online service), on top of the DSP helpers of
tools/gen_audio.py.

These are DEVELOPMENT ASSETS: layered, varied and usable in game, but procedural. Final recordings are specified
in docs/CORRIDOR_AUDIO.md (same file names, same events): replacing a file there replaces it in game.

Writes src/main/resources/assets/interstice/sounds/{creature/<species>,ambient,env}/... and the matching
entries of assets/interstice/sounds.json.

    python -I tools/audio/gen_corridor_audio.py
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import gen_audio as G  # noqa: E402

SR = G.SR
rng = np.random.default_rng(20261105)
G.rng = rng
EVENTS = {}   # event id -> list of sound names


def t(sec):
    return np.arange(int(sec * SR)) / SR


def reg(event, name, x, peak=0.9, stream=False):
    G.save(name, x, peak)
    EVENTS.setdefault(event, []).append(("interstice:" + name, stream))


def mix(*xs):
    """Sum of signals of different lengths (zero-padded to the longest)."""
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs:
        out[:len(x)] += x
    return out


def burst(sec, f0, f1, tau, order=2):
    n = int(sec * SR)
    return G.bp(rng.standard_normal(n), f0, f1, order) * np.exp(-np.arange(n) / (tau * SR))


def thump(sec, f, tau, click=0.3):
    tt = t(sec)
    body = np.sin(2 * np.pi * f * tt * (1 - 0.25 * tt / sec)) * np.exp(-tt / tau)
    tr = G.lp(rng.standard_normal(len(tt)), 900) * np.exp(-tt / 0.012) * click
    return body + tr


def creak(sec, f0, f1, rough=40):
    """Stick-slip friction: a pulse train whose rate glides, through a resonant band (joint, wood, metal)."""
    tt = t(sec)
    rate = rough * (1 + 0.5 * np.sin(2 * np.pi * 0.7 * tt + rng.random() * 6))
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float) * (0.5 + rng.random(len(tt)))
    res = G.bp(pulses, f0, f1, 2)
    return res * np.sin(np.pi * np.clip(tt / sec, 0, 1)) ** 0.7


def breath(sec, f_lo, f_hi, cycles, rough=0.0, wet=0.0, throat=0.0, irregular=0.0):
    tt = t(sec)
    phase = np.cumsum(cycles / sec * (1 + irregular * np.sin(2 * np.pi * 0.37 * tt + rng.random() * 6))) / SR
    env = np.clip(np.sin(np.pi * (phase % 1.0)), 0, 1) ** 1.5
    x = G.bp(rng.standard_normal(len(tt)), f_lo, f_hi) * env
    if rough:
        x += G.rattle(sec, 22, (f_lo + f_hi) / 2) * env * rough
    if throat:
        x += np.sin(2 * np.pi * throat * tt + 0.3 * np.sin(2 * np.pi * 3 * tt)) * env * 0.35
    if wet:
        x += G.clicks(sec, 6, 300, 1200, 60) * env * wet
    return G.fade(x, 0.05, 0.25)


def comb(x, delay_ms, fb=0.6):
    d = int(delay_ms * SR / 1000)
    y = np.copy(x)
    for i in range(d, len(y)):
        y[i] += fb * y[i - d]
    return y


# ---------------------------------------------------------------- species

def adherent():
    sp = "adherent"
    for i in range(4):   # padded thud + claw scrape on concrete
        x = mix(thump(0.3, 70 + i * 6, 0.05, 0.4) * 0.8, burst(0.12, 1800, 5200, 0.03) * 0.5)
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(x, 0, 0.03))
    for i in range(3):   # wall: longer organic friction, claws dragging
        x = burst(0.45, 700, 3200, 0.2) * (0.6 + 0.4 * np.sin(np.pi * t(0.45) / 0.45)) + creak(0.45, 1500, 4000, 60) * 0.3
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(x, 0.01, 0.08))
    for i in range(3):   # uneven breathing, low throat movement
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(2.2 + i * 0.3, 250, 1400, 2.0, rough=0.3, throat=85 + i * 7, irregular=0.35))
    for i in range(2):   # alert: throat growl rising
        x = G.fm(1.4, 130 + i * 20, 95, 0.5, 5, 0.8) * 0.7 + G.rattle(1.4, 28, 600) * 0.5
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(G.reverb(x, 1.2, 0.2), 0.02, 0.3))
    for i in range(3):
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(burst(0.06, 2500, 7000, 0.006) + burst(0.06, 600, 1500, 0.01) * 0.5, 0, 0.01))


def crawler():
    sp = "crawler"
    for i in range(4):   # rapid claw taps, two or three per contact
        n = int(0.16 * SR)
        x = np.zeros(n)
        for k in range(2 + i % 2):
            o = int((k * 0.035 + rng.random() * 0.01) * SR)
            b = burst(0.03, 3000, 8000, 0.004)
            x[o:o + len(b)] += b[: n - o] * (1 - k * 0.2)
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(x, 0, 0.01))
    for i in range(3):   # short bursts of friction
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(burst(0.18, 1500, 6000, 0.06), 0.005, 0.03))
    for i in range(3):   # fast shallow breathing
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(1.3, 900, 4000, 5.0 + i, rough=0.1, irregular=0.2))
    for i in range(2):   # chittering
        x = G.clicks(1.0, 30 + i * 8, 2500, 7000, 40) + G.fm(1.0, 900, 700, 2.0, 3, 0.4) * 0.3
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(x, 0.0, 0.2))
    for i in range(3):
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(burst(0.03, 4000, 9000, 0.003), 0, 0.005))


def strider():
    sp = "strider"
    for i in range(4):   # long-spaced deep impacts with body resonance
        x = thump(0.9, 48 + i * 4, 0.22, 0.25) + G.partials(0.9, [96, 151, 233], [0.3, 0.2, 0.12], [0.25, 0.15, 0.08])
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(G.reverb(x, 1.5, 0.25), 0, 0.15))
    for i in range(3):
        x = burst(0.7, 300, 1600, 0.35) * 0.6 + creak(0.7, 200, 700, 25) * 0.6
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(x, 0.02, 0.1))
    for i in range(2):   # deep, slow weight-shifting breath
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(3.4, 120, 700, 1.4, rough=0.15, throat=55, irregular=0.1))
    for i in range(2):   # low moan
        x = G.fm(2.4, 85 + i * 10, 70, 0.5, 2.5, 1.8) + G.lp(G.brown(2.4), 300) * 0.3
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(G.reverb(x, 2.5, 0.35), 0.1, 0.6))
    for i in range(3):   # articular creaking
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(creak(0.9, 300, 1100, 18 + i * 6), 0.02, 0.15))


def burden():
    sp = "burden"
    for i in range(4):   # massive low impact, sub rumble, crumbling tail
        x = thump(1.6, 34 + i * 3, 0.45, 0.5) * 1.2 + G.lp(G.brown(1.6), 120) * np.exp(-t(1.6) / 0.6) * 0.8
        x += G.bp(rng.standard_normal(len(x)), 1500, 5000) * np.exp(-t(1.6) / 0.25) * G.clicks(1.6, 30, 1500, 5000, 30)[:len(x)] * 0.6
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(G.reverb(x, 2.2, 0.3), 0, 0.3), peak=0.95)
    for i in range(3):   # heavy drag
        x = G.lp(burst(1.1, 80, 900, 0.6), 900) * 1.2 + creak(1.1, 120, 400, 14) * 0.4
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(x, 0.05, 0.2))
    for i in range(3):   # heavy wet breathing
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(3.2, 90, 600, 1.3, rough=0.5, wet=0.4, throat=48 + i * 4, irregular=0.15))
    for i in range(2):   # deep organic resonance
        x = G.partials(3.0, [41, 62, 83, 125], [1.4, 1.0, 0.8, 0.5], [0.6, 0.4, 0.3, 0.15]) + G.fm(3.0, 70, 50, 0.5, 3, 2.0) * 0.4
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(G.reverb(x, 3.0, 0.35), 0.2, 0.8))
    for i in range(3):   # body creak under the weight
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(creak(1.2, 90, 400, 10 + i * 3), 0.05, 0.2))


def clinger():
    sp = "clinger"
    for i in range(4):   # near silence: a soft finger tap
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(burst(0.05, 1200, 3500, 0.006), 0, 0.01), peak=0.4)
    for i in range(3):   # brief scrape
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(burst(0.12, 2500, 7000, 0.035), 0.003, 0.02), peak=0.6)
    for i in range(3):   # no breathing loop: finger movement, small joint clicks
        sec = 1.4
        x = np.zeros(int(sec * SR))
        for k in range(2 + i):
            o = int(rng.random() * (sec - 0.1) * SR)
            b = burst(0.04, 2000, 6000, 0.004)
            x[o:o + len(b)] += b
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", x, peak=0.45)
    for i in range(2):   # sudden contact: a sharp hiss and snap
        x = mix(G.hp(burst(0.35, 3000, 9000, 0.1), 2500), burst(0.04, 800, 3000, 0.005) * 2)
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(x, 0, 0.05))
    for i in range(4):
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(burst(0.03, 3500, 9000, 0.002), 0, 0.005), peak=0.5)


def hollow():
    sp = "hollow"
    for i in range(4):   # a hollow knock: resonant, like an empty body
        x = comb(burst(0.25, 200, 1500, 0.02), 2.4 + i * 0.3, 0.75)
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(x, 0, 0.05))
    for i in range(3):
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(comb(burst(0.35, 600, 3000, 0.12), 3.1, 0.5), 0.01, 0.05))
    for i in range(3):   # breathing through a tube: comb-filtered, irregular
        x = comb(breath(2.6, 300, 2500, 1.7, irregular=0.5), 4.5 + i, 0.7)
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", G.fade(x, 0.05, 0.2))
    for i in range(3):   # irregular dry vocalisation
        sec = 1.3
        f = 300 + 250 * np.abs(np.sin(2 * np.pi * (1.3 + i * 0.4) * t(sec))) * (1 + 0.3 * rng.standard_normal(len(t(sec))).cumsum() / 400)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * G.env_ad(int(sec * SR), 0.05, 0.6) * 0.5 + G.clicks(sec, 14, 1500, 5000, 30)
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(comb(x, 3.0, 0.5), 0.02, 0.2))
    for i in range(3):   # dry clicking (listening)
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(G.clicks(0.6, 9 + i * 3, 1800, 6000, 80), 0, 0.05))


def watcher():
    sp = "watcher"
    for i in range(4):
        x = thump(0.4, 62 + i * 5, 0.08, 0.15) * 0.7
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(x, 0, 0.05), peak=0.6)
    for i in range(3):
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(burst(0.5, 400, 2000, 0.25) * 0.6, 0.02, 0.1), peak=0.6)
    for i in range(2):   # extremely slow breathing
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(5.0, 150, 900, 1.0, throat=60, irregular=0.05), peak=0.55)
    for i in range(2):   # subtle deep resonance
        x = G.partials(4.0, [55, 82.5, 110], [2.5, 2.0, 1.5], [0.5, 0.3, 0.2]) * np.sin(np.pi * t(4.0) / 4.0)
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(G.reverb(x, 3.0, 0.4), 0.3, 0.8), peak=0.7)
    for i in range(3):   # the head turning: a slow quiet creak
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(creak(1.6, 250, 800, 9 + i * 2), 0.1, 0.3), peak=0.5)


def fractured():
    sp = "fractured"
    for i in range(4):   # asymmetric: 0-1 heavy fall on the good side, 2-3 a dragging scuff on the other
        if i < 2:
            x = mix(thump(0.35, 75 + i * 8, 0.06, 0.5), burst(0.08, 1500, 4500, 0.02) * 0.4)
        else:
            x = burst(0.42, 500, 2500, 0.18) * np.linspace(0.4, 1, int(0.42 * SR)) + thump(0.42, 90, 0.03, 0.1) * 0.3
        reg(f"creature.{sp}.step", f"creature/{sp}/step_{i}", G.fade(x, 0, 0.05))
    for i in range(3):
        x = burst(0.5, 600, 3500, 0.15) * (0.5 + 0.5 * (np.sin(2 * np.pi * 7.3 * t(0.5)) > 0))
        reg(f"creature.{sp}.scrape", f"creature/{sp}/scrape_{i}", G.fade(x, 0.01, 0.06))
    for i in range(3):   # uneven, stuttering breath
        reg(f"creature.{sp}.breath", f"creature/{sp}/breath_{i}", breath(2.4, 200, 1600, 2.6, rough=0.4, throat=95, irregular=0.8))
    for i in range(2):   # uneven vocalisation: two voices out of step
        x = G.fm(1.5, 180, 120, 0.5, 4, 0.9) * 0.5 + G.fm(1.5, 247, 160, 0.7, 3, 0.7) * 0.4 * (np.sin(2 * np.pi * 5.1 * t(1.5)) > -0.3)
        reg(f"creature.{sp}.vocal", f"creature/{sp}/vocal_{i}", G.fade(G.reverb(x, 1.2, 0.2), 0.02, 0.3))
    for i in range(3):   # misaligned double clicks
        n = int(0.2 * SR)
        x = np.zeros(n)
        b = burst(0.04, 2200, 7000, 0.004)
        x[:len(b)] += b
        o = int((0.06 + 0.05 * rng.random()) * SR)
        x[o:o + len(b)] += b * 0.6
        reg(f"creature.{sp}.click", f"creature/{sp}/click_{i}", G.fade(x, 0, 0.01))


# --------------------------------------------------------------- regions

def bed(name, x):
    reg(f"ambient.{name}", f"ambient/{name}", G.loopable(G.fade(x, 1.0, 1.0), 2.0), peak=0.8, stream=True)


def ambience():
    sec = 26
    tt = t(sec)
    # District: wind through empty streets, building creaks, a far interior knock now and then
    wind = G.bp(G.brown(sec), 150, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.07 * tt) ** 2) + G.bp(G.noise(sec), 1500, 4000) * 0.04 * (np.sin(2 * np.pi * 0.11 * tt) > 0.6)
    x = wind
    for k in range(4):
        o = int(rng.random() * (sec - 2) * SR)
        c = creak(1.5, 300, 900, 12) * 0.25
        x[o:o + len(c)] += c
    o = int(sec * 0.6 * SR)
    k = thump(0.6, 90, 0.1) * 0.15
    x[o:o + len(k)] += k
    bed("forgotten_district", x)
    # Ironworks: metallic resonance (inharmonic, slowly beating), machinery hum, chain tension
    x = G.partials(sec, [55.0, 117.3, 176.9, 231.4, 389.0], [60] * 5, [0.35, 0.22, 0.12, 0.08, 0.03])
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.05 * tt)
    x += np.sin(2 * np.pi * 50 * tt) * 0.1 + np.sin(2 * np.pi * 100 * tt) * 0.05
    for k in range(3):
        o = int(rng.random() * (sec - 3) * SR)
        c = creak(2.5, 180, 600, 7) * 0.3
        x[o:o + len(c)] += c
    bed("ironworks", x + G.lp(G.brown(sec), 200) * 0.2)
    # Contaminated Ward: low organic ambience, ventilation, slow wet movement
    vent = G.bp(G.noise(sec), 300, 1400) * 0.12 * (0.8 + 0.2 * np.sin(2 * np.pi * 0.3 * tt))
    organ = G.lp(G.brown(sec), 140) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.09 * tt + 1))
    wet = G.clicks(sec, 0.6, 250, 900, 15) * 0.4
    bed("contaminated_ward", vent + organ + wet)
    # Dead Expanse: sparse low wind, long silences, one distant chain
    gust = (np.sin(2 * np.pi * 0.035 * tt) ** 8)
    x = G.bp(G.brown(sec), 90, 500) * gust * 0.8
    o = int(sec * 0.35 * SR)
    c = creak(3.0, 150, 450, 5) * 0.12
    x[o:o + len(c)] += c
    bed("dead_expanse", x)


def environment():
    for i in range(3):   # loose glass and metal rattling in a frame
        x = G.rattle(1.1, 18 + i * 6, 2200 + i * 500) * G.env_ad(int(1.1 * SR), 0.02, 0.5) + G.clicks(1.1, 25, 3000, 8000, 50) * 0.3
        reg("env.building_rattle", f"env/building_rattle_{i}", G.fade(x, 0.01, 0.2))
    for i in range(2):   # dust and grit trickling from a ceiling
        sec = 1.8
        x = G.hp(G.clicks(sec, 120, 3000, 9000, 120), 2000) * np.exp(-t(sec) / 0.8) + G.bp(G.noise(sec), 2000, 6000) * 0.1 * np.exp(-t(sec) / 0.5)
        reg("env.dust_fall", f"env/dust_fall_{i}", G.fade(x, 0.02, 0.3), peak=0.6)
    for i in range(3):   # a claw or a boot on metal
        x = mix(G.partials(0.5, [640 + i * 90, 1530 + i * 130, 2870], [0.12, 0.08, 0.05], [0.5, 0.3, 0.15]), burst(0.02, 2000, 8000, 0.003))
        reg("env.metal_contact", f"env/metal_contact_{i}", G.fade(x, 0, 0.05), peak=0.7)
    for i in range(2):   # chain under tension: a slow stretched groan
        x = creak(2.8, 120, 500, 6 + i * 2) * 0.8 + G.partials(2.8, [70, 141], [1.5, 1.0], [0.2, 0.1])
        reg("env.chain_tension", f"env/chain_tension_{i}", G.fade(G.reverb(x, 2.5, 0.4), 0.2, 0.6))


def write_sounds_json():
    p = os.path.join(G.OUT, "..", "sounds.json")
    with open(p, encoding="utf-8") as f:
        data = json.load(f)
    for ev, names in EVENTS.items():
        data[ev] = {"sounds": [({"name": n, "stream": True} if s else n) for n, s in names]}
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"{len(EVENTS)} events, {sum(len(v) for v in EVENTS.values())} files")


if __name__ == "__main__":
    adherent(); crawler(); strider(); burden(); clinger(); hollow(); watcher(); fractured()
    ambience()
    environment()
    write_sounds_json()


# ------------------------------------------------- falls, pain, the horde's cry (after play feedback)

SIZE = {"adherent": 1.0, "crawler": 0.55, "strider": 1.3, "burden": 2.0, "clinger": 0.8, "hollow": 0.85, "watcher": 1.15, "fractured": 1.0}
VOICE = {"adherent": 1.0, "crawler": 1.7, "strider": 0.7, "burden": 0.45, "clinger": 1.3, "hollow": 1.2, "watcher": 0.8, "fractured": 1.05}


def voice(sec, f0s, formants, rasp=0.4, breathy=0.2, growl=0.0, jitter=0.03):
    """A creature voice: a jittered glottal pulse train (f0 contour from f0s, interpolated) through formant resonances
    (F1..F3 contours: list of (f_start, f_end, bandwidth)), with rasp (noise gated by the pulses), breath and an
    optional sub-harmonic growl, soft-clipped."""
    n = int(sec * SR)
    tt = np.arange(n) / SR
    f0 = np.interp(tt, np.linspace(0, sec, len(f0s)), f0s) * (1 + jitter * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1)) * 0.3)
    ph = np.cumsum(f0) / SR
    saw = 2 * (ph % 1.0) - 1
    pulses = np.maximum(0, np.sin(2 * np.pi * ph)) ** 6
    src = saw * 0.6 + pulses * 1.2
    if growl:
        src *= 1 + growl * np.sin(np.pi * ph)   # period doubling: a growl
    src += rasp * rng.standard_normal(n) * (0.3 + pulses)
    out = np.zeros(n)
    seg = 256
    for (fa, fb, bw) in formants:
        y = np.zeros(n)
        for i in range(0, n, seg):
            fc = fa + (fb - fa) * min(1, i / max(1, n - 1))
            lo, hi = max(40, fc - bw / 2), min(SR / 2 - 100, fc + bw / 2)
            y[i:i + seg] = G.bp(src[max(0, i - 512):i + seg], lo, hi)[-len(src[i:i + seg]):]
        out += y
    out += breathy * G.bp(rng.standard_normal(n), 900, 5000)
    return np.tanh(out * 1.6) / 1.2


def land_and_hurt():
    for sp, k in SIZE.items():
        v = VOICE[sp]
        for i in range(3):   # a body hitting the floor
            sec = 0.55 + 0.45 * k
            n = int(sec * SR)
            tt = np.arange(n) / SR
            body = G.lp(rng.standard_normal(n), 140 / k ** 0.5) * np.exp(-tt / (0.09 * k + 0.05)) * 3.0
            sub = np.sin(2 * np.pi * (48 / k ** 0.4) * tt) * np.exp(-tt / (0.12 * k)) * 0.8
            slap = G.bp(rng.standard_normal(n), 300, 2600) * np.exp(-tt / 0.025) * 1.2
            clatter = np.zeros(n)
            for c in range(int(5 + 6 / k)):
                o = int((0.01 + rng.random() * 0.28) * SR)
                b = burst(0.03, 1200, 5500, 0.004) * (0.3 + rng.random() * 0.7)
                clatter[o:o + len(b)] += b[: n - o]
            grit = G.hp(G.clicks(sec, 70, 2500, 8000, 90), 1800) * np.exp(-tt / (0.25 + 0.1 * k)) * 0.35
            x = body + sub + slap + clatter * 0.7 + grit
            if i != 1:   # the wind knocked out of it
                g = voice(0.28, [150 * v, 95 * v], [(500 * v / k ** 0.3, 420 * v / k ** 0.3, 220), (1100 * v / k ** 0.3, 900 * v / k ** 0.3, 300)], rasp=0.8, breathy=0.5, growl=0.5)
                o = int(0.06 * SR)
                x[o:o + len(g)] += g * 0.45 * np.exp(-np.arange(len(g)) / (0.12 * SR))
            reg(f"creature.{sp}.land", f"creature/{sp}/land_{i}", G.fade(G.reverb(x, 1.2, 0.22), 0, 0.12), peak=0.95)
        for i in range(3):   # struck: a flesh impact, then a short strangled cry in the species' register
            sec = 0.5 + 0.12 * i
            f0 = 230 * v * (1 + 0.08 * i)
            fs = [f0 * 1.15, f0 * 1.55, f0 * 1.2, f0 * 0.75]
            fk = v ** 0.5 / k ** 0.25
            formants = [(780 * fk, 560 * fk, 260), (1250 * fk, 950 * fk, 340), (2600 * fk, 2300 * fk, 500)]
            cry = voice(sec, fs, formants, rasp=0.5 + 0.2 * i, breathy=0.25, growl=0.35 if k >= 1 else 0.0)
            env = np.clip(np.arange(len(cry)) / (0.015 * SR), 0, 1) * np.exp(-np.arange(len(cry)) / ((0.18 + 0.05 * i) * SR))
            cry *= env
            if sp == "hollow":
                cry = comb(cry, 3.2, 0.55)
            if sp == "fractured":
                cry *= 0.55 + 0.45 * (np.sin(2 * np.pi * 11 * np.arange(len(cry)) / SR) > -0.2)
            if sp == "clinger":
                cry = G.hp(cry, 900)
            hit_n = int(0.12 * SR)
            hit = G.lp(rng.standard_normal(hit_n), 900) * np.exp(-np.arange(hit_n) / (0.018 * SR)) * 1.4 + burst(0.12, 1500, 6000, 0.004) * 0.5
            x = mix(hit, np.concatenate([np.zeros(int(0.03 * SR)), cry]))
            reg(f"creature.{sp}.hurt", f"creature/{sp}/hurt_{i}", G.fade(x, 0, 0.06), peak=0.92)


def alert_cries():
    """The cry a creature gives as it starts the hunt: short, sharp, in its own register (rasping shriek for most,
    a roar for the Burden, a hiss-snap for the Clinger, a hollow warble for the Hollow, two voices for the Fractured)."""
    for sp, k in SIZE.items():
        v = VOICE[sp]
        fk = v ** 0.5 / k ** 0.25
        for i in range(2):
            sec = 0.7 + 0.15 * i + 0.2 * (k > 1.5)
            f0 = 300 * v
            contour = [f0 * 0.8, f0 * 1.6, f0 * 1.45, f0 * 0.9] if k < 1.5 else [f0 * 0.9, f0 * 1.3, f0 * 1.1, f0 * 0.7]
            formants = [(850 * fk, 700 * fk, 300), (1450 * fk, 1150 * fk, 380), (2900 * fk, 2500 * fk, 600)]
            x = voice(sec, contour, formants, rasp=0.9, breathy=0.3, growl=0.6 if k >= 1.3 else 0.15, jitter=0.05)
            n = len(x)
            env = np.clip(np.arange(n) / (0.02 * SR), 0, 1) * np.exp(-np.arange(n) / (0.35 * SR))
            x *= env
            if sp == "clinger":
                x = mix(G.hp(burst(0.3, 3000, 9000, 0.08), 2500) * 1.2, x * 0.35, burst(0.04, 800, 3000, 0.005) * 2)
            if sp == "hollow":
                x = comb(x, 3.0, 0.6)
            if sp == "fractured":
                y = voice(sec, [c * 1.33 for c in contour], formants, rasp=0.8, breathy=0.2, jitter=0.07) * env * 0.6
                x = mix(x, np.concatenate([np.zeros(int(0.05 * SR)), y]))
            reg(f"creature.{sp}.alert", f"creature/{sp}/alert_{i}", G.fade(G.reverb(x, 1.6, 0.25), 0, 0.15), peak=0.95)


def horde_scream():
    """The Stampede announcing itself: hundreds of throats far down the Corridor, rising, then cut by the quake."""
    sec = 7.0
    tt = t(sec)
    x = np.zeros(len(tt))
    for k in range(36):
        f0 = 250 + rng.random() * 900
        start = rng.random() * 1.5
        dur = sec - start - rng.random() * 1.0
        s = G.fm(dur, f0 * (1.4 + rng.random() * 0.4), f0, 0.5 + rng.random(), 2 + rng.random() * 3, dur * 0.9)
        s *= np.clip(np.arange(len(s)) / (0.6 * SR), 0, 1)
        o = int(start * SR)
        x[o:o + len(s)] += s[: len(x) - o] * (0.5 + rng.random() * 0.5)
    x += G.lp(G.brown(sec), 160) * 2.0 * np.clip(tt / 3, 0, 1)
    x = G.lp(x, 2600)   # far away: the highs are gone
    x = G.reverb(x, 4.5, 0.6)
    reg("event.horde_scream", "event/horde_scream", G.fade(x, 0.8, 1.2), peak=0.95, stream=True)


if __name__ == "__main__" and os.environ.get("INTERSTICE_EXTRA", "1") == "1":
    land_and_hurt()
    alert_cries()
    horde_scream()
    write_sounds_json()
