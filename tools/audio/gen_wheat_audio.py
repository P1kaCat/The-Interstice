"""Level 02, The Wheat Farm: procedural sounds (2026-11-06).

  - ambient.wheat_wind   (30 s loop): open-field wind, slow swells, a faint whistle now and then;
  - ambient.wheat_rustle (20 s loop): dry stalks rubbing, granular crackle in waves (its volume follows the gusts
    the wheat shader shows, WheatAudio);
  - ambient.wheat_gust   (4 one-shots): single gusts passing, played now and then by WheatAudio;
  - turbine.whoosh       (one rotor turn, 2 pi / 0.85 s, looped): three blade passes (the swish rising and falling
    as each blade sweeps down past the tower), the generator's low hum and a faint gearbox whine. Positional.

Recorded material (all CC0, public domain; kept out of git in tools/audio/downloads/, see docs/AUDIO_CREDITS.md):
  - bsb_wind_s0595/wind_s0595.wav             BigSoundBank #0595 "Wind", Joseph Sardin
  - bsb_countryside_s0097/countryside_s0097.wav BigSoundBank #0097 "Countryside", Joseph Sardin
  - oga_wind_ignasd/Wind*.ogg                 OpenGameArt "Wind", IgnasD
  - oga_wind_whoosh_loop/wind_woosh_loop.ogg  OpenGameArt "wind whoosh loop", SketchMan3
Without them the script falls back to the procedural layers alone.

    python -I tools/audio/gen_wheat_audio.py
"""
import json
import os
import sys

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import gen_audio as G  # noqa: E402

SR = G.SR
G.rng = np.random.default_rng(20261106)
EVENTS = {}


DL = os.path.join(os.path.dirname(__file__), "downloads")


def rec(rel):
    """A downloaded recording, mono, at SR, normalised to unit RMS (None if it is not there)."""
    p = os.path.join(DL, rel)
    if not os.path.exists(p):
        print("missing", rel, "(procedural only)")
        return None
    x, sr = sf.read(p, always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(sr, SR)
        x = resample_poly(x, SR // g, sr // g)
    x = x - x.mean()
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def fit(x, n):
    """x cut (or tiled) to n samples."""
    if len(x) >= n:
        return x[:n]
    return np.tile(x, n // len(x) + 1)[:n]


def t(sec):
    return np.arange(int(sec * SR)) / SR


def reg(event, name, x, peak=0.85, stream=True):
    G.save(name, x, peak)
    EVENTS.setdefault(event, []).append(("interstice:" + name, stream))


def wind():
    sec = 60
    tt = t(sec)
    swell = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.045 * tt + 1.1)) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.13 * tt) ** 2)
    body = G.bp(G.brown(sec), 70, 650) * swell
    air = G.bp(G.noise(sec), 900, 2600) * 0.05 * swell ** 2
    whistle = np.sin(2 * np.pi * (520 + 40 * np.sin(2 * np.pi * 0.2 * tt)) * tt) * 0.012 * np.clip(np.sin(2 * np.pi * 0.03 * tt) - 0.6, 0, None) * 2.5
    x = body + air + whistle
    real = rec("bsb_wind_s0595/wind_s0595.wav")
    if real is not None:
        n = len(tt)
        r = G.hp(fit(real[int(8 * SR):], n), 45)
        x = r * 0.75 * (0.55 + 0.45 * swell) + x / (np.sqrt(np.mean(x ** 2)) + 1e-12) * 0.35
    far = rec("bsb_countryside_s0097/countryside_s0097.wav")
    if far is not None:
        # only the wind in it: 120-1300 Hz keeps the moving air, the birds and insects sit above
        f = G.bp(fit(far[int(20 * SR):], len(tt)), 120, 1300, order=4)
        x = x + f / (np.sqrt(np.mean(f ** 2)) + 1e-12) * 0.18
    reg("ambient.wheat_wind", "ambient/wheat_wind", G.loopable(G.fade(x, 1.0, 1.0), 2.5), peak=0.75)


def rustle():
    sec = 20
    tt = t(sec)
    waves = np.clip(0.5 + 0.5 * np.sin(2 * np.pi * 0.17 * tt) + 0.25 * np.sin(2 * np.pi * 0.41 * tt + 2), 0, None)
    hiss = G.bp(G.noise(sec), 2200, 7500) * 0.35
    # crackle: sparse tiny clicks of dry straw, denser on the waves
    clicks = np.zeros(len(tt))
    r = G.rng
    for i in range(int(sec * 900)):
        k = r.integers(0, len(tt) - 60)
        if r.random() < 0.25 + 0.75 * waves[k] / (waves.max() + 1e-9):
            clicks[k:k + 40] += r.standard_normal(40) * np.exp(-np.arange(40) / 8.0) * r.uniform(0.2, 1.0)
    clicks = G.bp(clicks, 1500, 9000) * 0.6
    x = (hiss + clicks) * (0.25 + 0.75 * waves)
    reg("ambient.wheat_rustle", "ambient/wheat_rustle", G.loopable(G.fade(x, 1.0, 1.0), 2.0), peak=0.7)


def gusts():
    """Single gusts: IgnasD's three winds and the whoosh loop as a swell, each with a soft start and end."""
    for rel, start, sec in (("oga_wind_ignasd/Wind.ogg", 0, None), ("oga_wind_ignasd/Wind2.ogg", 0, None),
                            ("oga_wind_ignasd/Wind3.ogg", 0, None), ("oga_wind_whoosh_loop/wind_woosh_loop.ogg", 0, None)):
        x = rec(rel)
        if x is None:
            continue
        if "woosh" in rel:
            # a 6 s loop: twice through under one long swell, so it reads as one gust going by
            x = np.tile(x, 2)
            x = x * np.sin(np.linspace(0, np.pi, len(x))) ** 1.5
        x = G.hp(x, 50)
        reg("ambient.wheat_gust", "ambient/wheat_gust_" + str(len(EVENTS.get("ambient.wheat_gust", [])) + 1),
            G.fade(x, 0.3, 0.8), peak=0.8, stream=False)


def turbine():
    omega = 0.85
    sec = 2 * np.pi / omega   # one turn of the rotor: the loop repeats seamlessly
    tt = t(sec)
    n = len(tt)
    swish = np.zeros(n)
    base = G.bp(G.noise(sec), 180, 1400)
    for k in range(3):
        c = sec * (k + 0.5) / 3
        d = np.minimum(np.abs(tt - c), sec - np.abs(tt - c))
        env = np.exp(-(d / 0.42) ** 2)
        swish += base * env
    hum = np.sin(2 * np.pi * 43 * tt) * 0.10 + np.sin(2 * np.pi * 86 * tt) * 0.05 + np.sin(2 * np.pi * 129 * tt + 0.4) * 0.02
    whine = np.sin(2 * np.pi * 342 * tt) * 0.006
    x = swish * 0.9 + hum + whine
    reg("turbine.whoosh", "ambient/turbine_whoosh", x, peak=0.8)


def write_sounds_json():
    p = os.path.join(G.OUT, "..", "sounds.json")
    with open(p, encoding="utf-8") as f:
        data = json.load(f)
    for ev, names in EVENTS.items():
        data[ev] = {"sounds": [({"name": n, "stream": True} if s else n) for n, s in names]}
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"{len(EVENTS)} events written")


if __name__ == "__main__":
    wind()
    rustle()
    gusts()
    turbine()
    write_sounds_json()
