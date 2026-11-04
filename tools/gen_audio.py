"""
TEMPORARY DEVELOPMENT AUDIO for THE INTERSTICE (original, synthesized locally).

All sounds are generated from scratch with numpy/scipy DSP (no samples, no
online services). Voice lines are produced from local Windows SAPI renders
(wav files passed with --voices DIR) re-synthesised into whispers with an LPC
noise vocoder. Replace any file with a final recording of the same name.

Usage:  python -I tools/gen_audio.py --voices <dir-with-<line>.wav>
"""
import argparse
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 32000
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "interstice", "sounds")
rng = np.random.default_rng(424242)


def t(sec):
    return np.arange(int(sec * SR)) / SR


def lp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "low")
    return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def bp(x, f0, f1, order=2):
    b, a = signal.butter(order, [f0 / (SR / 2), f1 / (SR / 2)], "band")
    return signal.lfilter(b, a, x)


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def brown(sec):
    x = np.cumsum(noise(sec))
    return hp(x, 15) / 400.0


def env_ad(n, attack, decay_tau):
    a = int(attack * SR)
    e = np.exp(-np.arange(n) / (decay_tau * SR))
    if a > 0:
        e[:a] *= np.linspace(0, 1, a)
    return e


def reverb(x, seconds=3.5, wet=0.45, damp=2500):
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (seconds / 6.0 * SR))
    ir = lp(ir, damp)
    ir[:int(0.03 * SR)] = 0
    y = signal.fftconvolve(x, ir)[: len(x) + n]
    y = y / (np.max(np.abs(y)) + 1e-9)
    dry = np.concatenate([x, np.zeros(n)])[: len(y)]
    dry = dry / (np.max(np.abs(dry)) + 1e-9)
    return dry * (1 - wet) + y * wet


def norm(x, peak=0.9):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, fin=0.01, fout=0.05):
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def loopable(x, xfade=2.0):
    n = int(xfade * SR)
    head, tail = x[:n], x[-n:]
    w = np.linspace(0, 1, n)
    body = x[n:-n] if len(x) > 2 * n else x
    blend = tail * (1 - w) + head * w
    return np.concatenate([body, blend])


def save(rel, x, peak=0.9):
    p = os.path.join(OUT, rel + ".ogg")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    data = norm(np.asarray(x, dtype=np.float64), peak).astype(np.float32)
    # libsndfile's Vorbis encoder overflows the stack on large single writes (Windows): write in blocks
    with sf.SoundFile(p, "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])


def partials(sec, freqs, taus, amps):
    tt = t(sec)
    out = np.zeros_like(tt)
    for f, tau, a in zip(freqs, taus, amps):
        out += a * np.sin(2 * np.pi * f * tt + rng.random() * 6.28) * np.exp(-tt / tau)
    return out


# ------------------------------------------------------------------ beds
def drone():
    sec = 44
    tt = t(sec)
    x = np.zeros_like(tt)
    for f, a in [(36.7, 1.0), (41.2, 0.7), (55.0, 0.5), (73.4, 0.25), (110.3, 0.08)]:
        lfo = 0.6 + 0.4 * np.sin(2 * np.pi * (0.013 + rng.random() * 0.02) * tt + rng.random() * 6)
        x += a * lfo * np.sin(2 * np.pi * f * tt + 0.3 * np.sin(2 * np.pi * 0.05 * tt))
    x += lp(brown(sec), 120) * 2.5
    x += bp(noise(sec), 300, 900) * 0.02 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.021 * tt))
    save("ambient/drone", loopable(x, 3.0), 0.8)


def air():
    sec = 34
    tt = t(sec)
    gust = 0.35 + 0.65 * np.clip(lp(noise(sec), 0.25) * 30 + 0.5, 0, 1)
    x = bp(noise(sec), 180, 1400) * gust + lp(noise(sec), 300) * 0.5 * gust
    save("ambient/air", loopable(x, 3.0), 0.6)


def hum():
    sec = 9
    tt = t(sec)
    x = np.zeros_like(tt)
    for k, a in [(1, 1.0), (2, 0.5), (3, 0.35), (4, 0.15), (6, 0.08)]:
        x += a * np.sin(2 * np.pi * 50 * k * tt)
    x *= 1 + 0.15 * lp(noise(sec), 8) * 20
    x += bp(noise(sec), 2000, 6000) * 0.03
    save("ambient/hum", loopable(x, 1.0), 0.5)


# ------------------------------------------------------------------ environment
def impact_far(i):
    sec = 3.5
    tt = t(sec)
    f = 62 * np.exp(-tt * 1.6) + 34
    thud = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(len(tt), 0.002, 0.6)
    ring = partials(sec, [113 + i * 7, 287, 431 - i * 11, 659, 1013], [1.8, 1.2, 0.9, 0.6, 0.35], [0.5, 0.35, 0.25, 0.18, 0.08])
    burst = lp(noise(sec), 900) * env_ad(len(tt), 0.001, 0.05)
    x = thud * 1.2 + ring + burst * 0.7
    x = lp(x, 1600 - i * 200)  # distance
    save(f"env/impact_far_{i}", fade(reverb(x, 5.5, 0.65, 1800), 0.0, 0.8))


def chain_groan(i):
    sec = 4.5 + i * 0.6
    tt = t(sec)
    f = 78 + 18 * np.sin(2 * np.pi * (0.25 + i * 0.07) * tt) + 6 * lp(noise(sec), 3) * 40
    phase = np.cumsum(f) / SR
    saw = signal.sawtooth(2 * np.pi * phase) * 0.6
    stick = (lp(noise(sec), 30) * 60 > 0.4).astype(float)
    stick = lp(stick, 40)
    creak = bp(noise(sec), 400, 1300) * stick * 0.8
    body = lp(saw, 900) * (0.5 + 0.5 * stick)
    envl = np.sin(np.pi * tt / sec) ** 0.7
    save(f"env/chain_groan_{i}", fade(reverb((body + creak) * envl, 4.0, 0.5, 2200), 0.05, 0.5))


def chain_impact(i):
    sec = 3.0
    x = np.zeros(int(sec * SR))
    for k in range(3 + i):
        at = int((0.05 + k * (0.11 + rng.random() * 0.08)) * SR)
        hit = partials(sec - at / SR, [190 + rng.random() * 40, 523, 811 + rng.random() * 50, 1377, 2210],
                       [0.9, 0.6, 0.4, 0.25, 0.12], [0.6, 0.4, 0.3, 0.2, 0.1]) * (0.9 ** k)
        x[at:] += hit
    save(f"env/chain_impact_{i}", fade(reverb(lp(x, 3500), 4.5, 0.55, 2500), 0.0, 0.6))


def structure_groan(i):
    sec = 6.0
    tt = t(sec)
    f = 34 + 14 * np.sin(np.pi * tt / sec + i) + 4 * lp(noise(sec), 2) * 50
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    grind = bp(noise(sec), 90, 400) * (0.4 + 0.6 * np.abs(lp(noise(sec), 4) * 40))
    cracks = np.zeros_like(tt)
    for _ in range(4 + i):
        at = int(rng.random() * (sec - 0.3) * SR)
        cracks[at:at + 400] += rng.standard_normal(400) * np.exp(-np.arange(400) / 60)
    x = tone * 0.8 + grind * 0.7 + bp(cracks, 800, 4000) * 0.6
    x *= np.sin(np.pi * tt / sec) ** 0.5
    save(f"env/structure_groan_{i}", fade(reverb(x, 4.0, 0.5, 1500), 0.1, 0.8))


def rumble(i):
    sec = 8.0
    tt = t(sec)
    x = lp(brown(sec), 70 + i * 15) * 3 + lp(noise(sec), 140) * 0.3
    x *= np.sin(np.pi * tt / sec) ** 1.3
    save(f"env/rumble_{i}", fade(x, 0.5, 1.0))


def debris(i):
    sec = 3.0
    x = np.zeros(int(sec * SR))
    n = 40 + i * 20
    for k in range(n):
        at = int((np.abs(rng.normal(0.35, 0.4))) * SR) % (len(x) - 600)
        g = rng.standard_normal(500) * np.exp(-np.arange(500) / (20 + rng.random() * 80))
        x[at:at + 500] += bp(g, 600 + rng.random() * 2000, 5000) * (0.3 + rng.random())
    hiss = bp(noise(sec), 1500, 7000) * env_ad(len(x), 0.05, 0.8) * 0.15
    save(f"env/debris_{i}", fade(reverb(x + hiss, 2.5, 0.4, 3000), 0.0, 0.5))


def clicks(sec, rate, f0=1800, f1=5000, decay=25):
    x = np.zeros(int(sec * SR))
    tcur = 0.0
    while tcur < sec - 0.01:
        tcur += rng.exponential(1.0 / rate)
        at = int(tcur * SR)
        if at + 300 >= len(x):
            break
        c = rng.standard_normal(300) * np.exp(-np.arange(300) / decay)
        x[at:at + 300] += c * (0.4 + rng.random() * 0.6)
    return bp(x, f0, f1)


def scuttle_far(i):
    sec = 2.6
    tt = t(sec)
    x = clicks(sec, 22 + i * 6) * (np.sin(np.pi * tt / sec) ** 0.8)
    save(f"env/scuttle_far_{i}", fade(reverb(lp(x, 2800), 3.0, 0.55, 2000), 0.05, 0.4))


def unexplained(i):
    sec = 5.0
    tt = t(sec)
    if i == 1:  # reversed swell
        tone = np.sin(2 * np.pi * 220 * tt) * 0.4 + np.sin(2 * np.pi * 233 * tt) * 0.4
        x = (tone * env_ad(len(tt), 0.0, 1.2))[::-1]
    elif i == 2:  # three knocks, far
        x = np.zeros_like(tt)
        for k, at in enumerate([0.8, 1.35, 1.9]):
            n0 = int(at * SR)
            x[n0:n0 + 3000] += lp(rng.standard_normal(3000), 400) * np.exp(-np.arange(3000) / 500) * 3
    else:  # bent, dissonant whistle
        f = 900 + 140 * np.sin(2 * np.pi * 0.3 * tt)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.3 * np.sin(np.pi * tt / sec) + np.sin(2 * np.pi * 947 * tt) * 0.12 * np.sin(np.pi * tt / sec)
    save(f"env/unexplained_{i}", fade(reverb(x, 5.0, 0.6, 2000), 0.05, 0.8))


def lamp_fail():
    sec = 1.4
    tt = t(sec)
    buzz = signal.square(2 * np.pi * 100 * tt) * 0.3 * (lp(noise(sec), 30) * 30 > 0.2)
    crackle = bp(noise(sec), 2000, 8000) * (rng.random(len(tt)) > 0.995) * 3
    click = np.zeros_like(tt)
    click[int(1.1 * SR):int(1.1 * SR) + 200] = rng.standard_normal(200) * np.exp(-np.arange(200) / 30)
    save("env/lamp_fail", fade(buzz + crackle + click * 2, 0.0, 0.1))


def quake():
    sec = 14.0
    tt = t(sec)
    swell = np.clip(tt / 3.0, 0, 1) * np.clip((sec - tt) / 5.0, 0, 1)
    x = lp(brown(sec), 60) * 5 * swell + lp(noise(sec), 180) * 0.6 * swell
    for _ in range(14):
        at = int(rng.random() * (sec - 1) * SR)
        cr = rng.standard_normal(2000) * np.exp(-np.arange(2000) / 300)
        x[at:at + 2000] += bp(cr, 300, 2500) * 1.2 * swell[at]
    save("event/quake", fade(reverb(x, 4.0, 0.4, 900), 0.2, 1.5))


def horde():
    sec = 22.0
    tt = t(sec)
    dense = clicks(sec, 900, 1200, 6000, 18) * 0.8
    dense2 = clicks(sec, 400, 500, 2500, 40) * 0.6
    roar = lp(brown(sec), 120) * 3 + bp(noise(sec), 150, 600) * 0.4
    mod = 0.8 + 0.2 * np.sin(2 * np.pi * 0.17 * tt)
    x = (dense + dense2 + roar) * mod
    save("event/horde", loopable(reverb(x, 2.5, 0.35, 3000)[: len(x)], 2.0), 0.85)


def premonition():
    sec = 18.0
    tt = t(sec)
    sub = np.sin(2 * np.pi * 28 * tt) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * tt))
    whine = (np.sin(2 * np.pi * 1800 * tt) + np.sin(2 * np.pi * 1807 * tt)) * 0.015
    x = sub + whine + lp(brown(sec), 50)
    save("event/premonition", loopable(x, 2.0), 0.7)


# ------------------------------------------------------------------ creature
def rattle(sec, rate, formant):
    tt = t(sec)
    pulses = (np.sin(2 * np.pi * rate * tt) > 0.85).astype(float)
    src = pulses * rng.standard_normal(len(tt)) * 0.5 + rng.standard_normal(len(tt)) * 0.05
    return bp(src, formant * 0.7, formant * 1.4)


def creature_idle(i):
    sec = 1.6
    tt = t(sec)
    x = rattle(sec, 26 + i * 5, 700 + i * 150) * np.sin(np.pi * tt / sec) + clicks(sec, 8, 2500, 6000) * 0.5
    save(f"creature/idle_{i}", fade(x, 0.02, 0.2))


def fm(sec, f0, f1, ratio, index, decay):
    tt = t(sec)
    f = f0 * (f1 / f0) ** (tt / sec)
    ph = np.cumsum(f) / SR
    mod = np.sin(2 * np.pi * ph * ratio) * index
    return np.sin(2 * np.pi * ph + mod) * env_ad(len(tt), 0.01, decay)


def creature_hurt(i):
    x = fm(0.8, 1400 + i * 200, 700, 1.41, 4, 0.25) + bp(noise(0.8), 1500, 6000) * env_ad(int(0.8 * SR), 0.0, 0.1) * 0.6
    save(f"creature/hurt_{i}", fade(x, 0.0, 0.1))


def creature_death():
    sec = 2.2
    x = fm(sec, 900, 120, 0.5, 6, 0.9) * 0.8 + rattle(sec, 18, 500) * np.linspace(1, 0.2, int(sec * SR))
    save("creature/death", fade(reverb(x, 2.0, 0.3), 0.0, 0.5))


def creature_scream(i):
    sec = 2.3
    tt = t(sec)
    x = fm(sec, 2200 - i * 300, 500, 1.5, 3.5, 2.0) * 0.8
    x += bp(noise(sec), 1000, 5000) * 0.25 * np.exp(-tt / 1.5)
    x *= np.clip(tt / 0.05, 0, 1)
    save(f"creature/scream_{i}", fade(reverb(x, 2.5, 0.35), 0.0, 0.4))


def creature_step(i):
    sec = 0.22
    c = rng.standard_normal(int(sec * SR)) * np.exp(-np.arange(int(sec * SR)) / (60 + i * 25))
    save(f"creature/step_{i}", fade(bp(c, 900 + i * 300, 5000), 0.0, 0.02))


def creature_alert():
    sec = 1.6
    tt = t(sec)
    x = rattle(sec, 40, 1200) * np.clip(tt / 1.0, 0, 1) + bp(noise(sec), 3000, 8000) * 0.3 * np.clip(tt / 1.2, 0, 1)
    x += fm(sec, 400, 1600, 2.0, 2.0, 3.0) * 0.3 * np.clip((tt - 0.6) / 0.6, 0, 1)
    save("creature/alert", fade(x, 0.0, 0.15))


def creature_land():
    sec = 1.5
    tt = t(sec)
    thud = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-tt * 4) + 35) / SR) * env_ad(len(tt), 0.001, 0.25)
    crunch = bp(noise(sec), 500, 4000) * env_ad(len(tt), 0.0, 0.12)
    save("creature/land", fade(reverb(thud * 1.4 + crunch, 2.0, 0.3), 0.0, 0.3))


def creature_sniff(i):
    sec = 1.3
    tt = t(sec)
    e = np.zeros_like(tt)
    for k in range(3 + i):
        c = 0.15 + k * 0.22
        e += np.exp(-((tt - c) / 0.05) ** 2)
    x = bp(noise(sec), 1200, 5000) * e
    save(f"creature/sniff_{i}", fade(x, 0.0, 0.1))


# ------------------------------------------------------------------ voice: LPC whisper vocoder
def lpc(frame, order):
    r = np.correlate(frame, frame, "full")[len(frame) - 1: len(frame) + order]
    if r[0] <= 1e-9:
        silent = np.zeros(order + 1)
        silent[0] = 1.0
        return silent, 0.0
    a = np.zeros(order + 1)
    a[0] = 1.0
    err = r[0]
    for i in range(1, order + 1):
        acc = r[i] + np.dot(a[1:i], r[i - 1:0:-1])
        k = -acc / err
        a_new = a.copy()
        a_new[1:i] += k * a[i - 1:0:-1]
        a_new[i] = k
        a = a_new
        err *= (1 - k * k)
        if err <= 0:
            break
    return a, max(err, 0.0)


def whisper(path_in, rel):
    x, sr = sf.read(path_in)
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = signal.resample_poly(x, SR, sr)
    x = np.concatenate([np.zeros(int(0.08 * SR)), x / (np.max(np.abs(x)) + 1e-9), np.zeros(int(0.2 * SR))])
    pre = signal.lfilter([1, -0.95], [1], x)
    frame, hop, order = int(0.025 * SR), int(0.0125 * SR), 18
    win = np.hanning(frame)
    out = np.zeros(len(x) + frame)
    for start in range(0, len(x) - frame, hop):
        seg = pre[start:start + frame] * win
        a, err = lpc(seg, order)
        gain = np.sqrt(err / frame + 1e-12)
        exc = rng.standard_normal(frame) * gain
        y = signal.lfilter([1], a, exc)
        out[start:start + frame] += y * win
    out = signal.lfilter([1], [1, -0.95], out)
    out = hp(out, 300)
    out = lp(out, 9000)
    # breathy closeness: a little low-mid body, gentle compression
    out = np.tanh(norm(out, 1.0) * 1.6)
    save(rel, fade(out, 0.01, 0.12), 0.85)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voices", default=None)
    args = ap.parse_args()
    drone(); air(); hum()
    for i in (1, 2, 3):
        impact_far(i); chain_groan(i); structure_groan(i); debris(i); scuttle_far(i); unexplained(i); creature_idle(i)
    for i in (1, 2):
        chain_impact(i); rumble(i); creature_hurt(i); creature_scream(i); creature_sniff(i)
    for i in (1, 2, 3, 4):
        creature_step(i)
    lamp_fail(); quake(); horde(); premonition(); creature_death(); creature_alert(); creature_land()
    if args.voices:
        for name in ["hide", "dont_move", "not_yet", "go", "they_hear", "look_up", "closer", "stay"]:
            p = os.path.join(args.voices, name + ".wav")
            if os.path.exists(p):
                whisper(p, f"voice/{name}")
    print("audio written to", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
