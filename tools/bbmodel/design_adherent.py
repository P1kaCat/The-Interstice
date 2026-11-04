"""
THE ADHERENT, v3 (rebuilt from scratch): rig, geometry, skin and keyframed animations,
written to models/adherent.bbmodel (Blockbench Modded Entity, box UV, 128x128).

Design (creature-artist / body-horror-style principles): a starved human figure crawling on
all fours. Anatomy almost right, then broken: arms with an extra elbow that fold up above the
back like a mantis; grasshopper legs whose knees rise behind the hips; a human skull face with
no eyes and no lips; a lower jaw split down the middle that opens sideways. It rears to its
full height when it notices something.

Animations (keyframed, editable in Blockbench, played in game by AdherentAnimator):
idle, walk, run, rear (hold), screech, jaws (hunting overlay), listen (search overlay),
fall, recover.

Space below: Minecraft model space (Y down, ground at y=24, front is -Z), radians, pixels.
Usage: python -I tools/bbmodel/design_adherent.py
"""
import base64
import io
import json
import math
import os
import uuid

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "models", "adherent.bbmodel")
TEX_DIR = os.path.join(ROOT, "src", "client", "resources", "assets", "interstice", "textures", "entity")
TW = TH = 128
rng = np.random.default_rng(11)

# ======================================================================= rig + geometry
P = {}  # bone -> {parent, offset, rot, cubes}


def bone(name, parent, offset, rot=(0, 0, 0)):
    P[name] = {"parent": parent, "offset": offset, "rot": rot, "cubes": []}
    return P[name]["cubes"]


def mirror(cubes):
    return [(m, -(x + w), y, z, w, h, d, k) for (m, x, y, z, w, h, d, k) in cubes]


# --- pelvis (root)
c = bone("hips", None, (0, 9, 9))
c += [("skin", -3.2, -2.4, -2.2, 6.4, 4.4, 5.0, None),
      ("bone", -0.6, -3.2, -1.8, 1.2, 1.0, 4.2, None)]
hip = [("bone", 2.6, -2.8, -1.4, 1.1, 1.5, 3.2, "crest")]
c += hip + mirror(hip)

# --- waist: starved, the spine showing
c = bone("spine", "hips", (0, -0.6, -2.2))
c.append(("skin", -2.0, -1.6, -8.0, 4.0, 3.2, 8.0, None))
for k in range(5):
    c.append(("bone", -0.5, -2.5, -7.6 + k * 1.6, 1.0, 1.0, 0.9, "vert"))

# --- ribcage, open over a dark cavity
c = bone("chest", "spine", (0, -0.4, -8.0))
c += [("cavity", -3.1, -2.7, -11.0, 6.2, 5.6, 11.0, None),
      ("skin", -3.4, -3.6, -11.0, 6.8, 1.0, 11.0, None),
      ("bone", -0.6, 2.8, -10.6, 1.2, 0.8, 8.0, None)]
for k in range(6):
    z = -10.4 + k * 1.6
    s = k * 0.07
    rib = [("bone", 2.3 - s, -2.9, z, 1.1, 1.5, 0.7, "rib_a"),
           ("bone", 3.0 - s, -1.5, z, 0.7, 2.8, 0.7, "rib_b"),
           ("bone", 2.2, 1.3, z, 1.0, 1.0, 0.7, "rib_c")]
    c += rib + mirror(rib)
for k in range(6):
    c.append(("bone", -0.45, -4.6 - (0.6 if k < 2 else 0), -10.3 + k * 1.8, 0.9, 1.1 + (0.6 if k < 2 else 0), 0.8, None))
blade = [("bone", 1.0, -4.7, -10.2, 3.0, 1.4, 4.2, "blade")]
c += blade + mirror(blade)

# --- neck: long, corded
c = bone("neck", "chest", (0, -1.0, -11.0), (0.3, 0, 0))
c += [("skin", -1.2, -1.2, -7.0, 2.4, 2.4, 7.0, None)]
for k in range(4):
    c.append(("bone", -0.8, 1.0, -6.5 + k * 1.5, 1.6, 0.5, 0.7, "trachea"))
tendon = [("bone", 0.7, -1.4, -6.8, 0.5, 0.5, 6.6, "tendon")]
c += tendon + mirror(tendon)

# --- skull: narrow, elongated, eyeless, lipless; the face is mostly sockets and teeth
c = bone("head", "neck", (0, 0, -7.0), (-0.3, 0, 0))
c += [("skin", -2.3, -4.6, -6.0, 4.6, 4.6, 6.6, None),       # long narrow cranium
      ("skin", -1.9, -5.2, -4.4, 3.8, 0.8, 4.2, None),       # high dome
      ("skin", -2.5, -2.7, -6.45, 5.0, 0.8, 0.9, None),      # brow, skin over bone
      ("cavity", -0.5, -1.0, -6.12, 1.0, 1.8, 0.2, None),    # nasal hole
      ("gum", -1.9, 0.0, -6.1, 3.8, 0.45, 1.4, None),        # receded gum, no lip
      ("cavity", -1.7, 0.3, -5.6, 3.4, 2.8, 4.4, None)]      # throat behind the jaws
hollow = [("dark", 0.25, -2.0, -6.2, 1.85, 1.75, 0.35, "eye_hollow"), ("glow", 1.0, -1.35, -6.24, 0.25, 0.25, 0.05, "glint")]
c += hollow + mirror(hollow)
cheek = [("skin", 1.9, -0.4, -5.6, 0.6, 1.0, 3.0, "cheek")]
c += cheek + mirror(cheek)
for i in range(7):
    x = -1.75 + i * 0.55
    c.append(("tooth", x, 0.35, -6.0, 0.34, 1.7 - 0.25 * (i in (0, 6)), 0.34, "tooth_up"))

# --- split jaw: two halves hinged at the corners of the face, opening sideways
for side, sx in (("l", 1), ("r", -1)):
    half = [("skin", -1.9, 0.0, -5.0, 1.9, 1.8, 4.8, "jaw_half"),
            ("skin", -1.4, 1.7, -5.3, 1.4, 0.7, 1.4, "chin")]
    for i in range(3):
        half.append(("tooth", -1.75 + i * 0.55, -1.3, -4.9, 0.32, 1.35, 0.32, "tooth_low"))
    for i in range(5):  # teeth lining the split edge
        half.append(("tooth", -2.05, -0.3 + (i % 2) * 0.25, -4.7 + i * 0.9, 0.28, 1.0, 0.28, "tooth_split"))
    bone(f"jaw_{side}", "head", (sx * 1.9, 1.0, -1.0)).extend(half if sx > 0 else mirror(half))

# --- arms: shoulder, an extra elbow, forearm, a long human hand
for side, sx in (("l", 1), ("r", -1)):
    fix = (lambda cs: cs) if sx > 0 else mirror
    bone(f"arm_{side}", "chest", (sx * 3.7, -2.3, -9.6), (-2.25, 0, sx * 0.85)).extend(fix([
        ("bone", -1.4, -1.4, -1.4, 2.8, 2.8, 2.8, "shoulder"),
        ("skin", -0.8, 0.5, -0.8, 1.6, 9.5, 1.6, "upper"),
        ("bone", 0.35, 1.2, -0.95, 0.4, 8.0, 0.4, "arm_tendon")]))
    bone(f"elbow_{side}", f"arm_{side}", (0, 10.0, 0), (1.95, 0, 0)).extend(fix([
        ("bone", -1.0, -1.0, -1.0, 2.0, 2.0, 2.0, "joint"),
        ("skin", -0.65, 0.4, -0.65, 1.3, 9.0, 1.3, "mid")]))
    bone(f"fore_{side}", f"elbow_{side}", (0, 9.5, 0), (1.1, 0, 0)).extend(fix([
        ("bone", -0.85, -0.85, -0.85, 1.7, 1.7, 1.7, "joint2"),
        ("skin", -0.55, 0.3, -0.55, 1.1, 8.5, 1.1, "fore")]))
    hand = [("skin", -1.6, 0.0, -0.6, 3.2, 2.2, 1.2, "palm")]
    for j, (x, ln) in enumerate([(-1.55, 3.8), (-0.85, 5.4), (-0.15, 6.0), (0.55, 5.4), (1.2, 4.2)]):
        hand += [("skin", x, 2.1, -0.2, 0.32, ln, 0.32, f"finger{j}"),
                 ("claw", x - 0.04, 2.1 + ln, -0.24, 0.4, 1.6, 0.4, "nail")]
    bone(f"hand_{side}", f"fore_{side}", (0, 8.8, 0), (-1.25, 0, 0)).extend(fix(hand))

# --- legs: knees rising behind the hips like a grasshopper's
for side, sx in (("l", 1), ("r", -1)):
    fix = (lambda cs: cs) if sx > 0 else mirror
    bone(f"leg_{side}", "hips", (sx * 2.7, 1.2, 1.6), (2.0, 0, sx * 0.22)).extend(fix([
        ("bone", -1.2, -1.2, -1.2, 2.4, 2.4, 2.4, "hipjoint"),
        ("skin", -1.0, 0.4, -1.0, 2.0, 9.0, 2.0, "thigh")]))
    bone(f"shin_{side}", f"leg_{side}", (0, 9.6, 0), (-2.65, 0, 0)).extend(fix([
        ("bone", -1.0, -1.0, -1.0, 2.0, 2.0, 2.0, "knee"),
        ("skin", -0.7, 0.4, -0.7, 1.4, 15.5, 1.4, "shin")]))
    bone(f"foot_{side}", f"shin_{side}", (0, 15.8, 0), (0.42, 0, 0)).extend(fix([
        ("skin", -1.1, -0.4, -4.4, 2.2, 1.0, 5.0, "foot"),
        ("claw", -1.05, -0.1, -5.6, 0.35, 0.6, 1.4, "toe"), ("claw", -0.2, -0.1, -5.9, 0.35, 0.6, 1.7, "toe2"),
        ("claw", 0.65, -0.1, -5.6, 0.35, 0.6, 1.4, "toe")]))


# ======================================================================= forward kinematics (sanity)
def rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def world(name, local, pose=None):
    """Model-space position of a point given in a bone's local frame (rest pose + optional offsets)."""
    chain = []
    n = name
    while n:
        chain.append(n)
        n = P[n]["parent"]
    M, t = np.eye(3), np.zeros(3)
    for n in reversed(chain):
        off = np.array(P[n]["offset"], float)
        r = np.array(P[n]["rot"], float)
        if pose and n in pose:
            r = r + np.array(pose[n].get("rot", (0, 0, 0)))
            off = off + np.array(pose[n].get("pos", (0, 0, 0)))
        t = t + M @ off
        M = M @ rot_zyx(*r)
    return t + M @ np.array(local, float)


def contacts(pose=None):
    return {
        "claw_l": world("hand_l", (0, 2.1 + 6.0 + 1.6, 0), pose)[1],
        "foot_l": world("foot_l", (0, 0.6, -2), pose)[1],
        "head_top": world("head", (0, -4.6, -3), pose)[1],
        "elbow_l": world("elbow_l", (0, 0, 0), pose)[1],
        "knee_l": world("shin_l", (0, 0, 0), pose)[1],
    }


def _set_rot_x(bones, v):
    for b in bones:
        rr = list(P[b]["rot"])
        rr[0] = v
        P[b]["rot"] = tuple(rr)


def plant(bones, lo, hi, measure, target=24.0):
    """Bisects one rest rotation (same on both sides) so measure() lands exactly on the ground."""
    _set_rot_x(bones, lo)
    y_lo = measure()
    _set_rot_x(bones, hi)
    y_hi = measure()
    if (y_lo - target) * (y_hi - target) > 0:
        raise SystemExit(f"cannot plant {bones}: range gives y {y_lo:.1f}..{y_hi:.1f}")
    for _ in range(60):
        mid = (lo + hi) / 2
        _set_rot_x(bones, mid)
        if (measure() - target) * (y_hi - y_lo) > 0:
            hi = mid
        else:
            lo = mid


# the rest pose stands on the ground: claw tips and soles at y = 24
# lower the body until the claws touch, then solve the shins for the soles
_lo, _hi = 6.0, 18.0
for _ in range(60):
    _mid = (_lo + _hi) / 2
    P["hips"]["offset"] = (0, _mid, 9)
    if world("hand_l", (0, 2.1 + 6.0 + 1.6, 0))[1] > 24.0:
        _hi = _mid
    else:
        _lo = _mid
plant(["shin_l", "shin_r"], -2.8, -1.95, lambda: world("foot_l", (0, 0.6, -2))[1])

# ======================================================================= animations
# Each animation: (name, length s, loop mode, sampler(t01) -> {bone: {"rot": (x,y,z), "pos": (x,y,z)}}, keys)
TAU = 2 * math.pi
ANIMS = []


def anim(name, length, loop, keys=17, interp="catmullrom"):
    def deco(fn):
        ANIMS.append((name, length, loop, fn, keys, interp))
        return fn
    return deco


def S(x):
    return math.sin(x)


def C(x):
    return math.cos(x)


@anim("idle", 4.0, "loop", keys=41)
def idle(t):
    p = TAU * t
    snap = 1.0 if 0.66 < t < 0.73 else 0.0                       # a sudden head snap, then stillness
    return {
        "chest": {"rot": (0.03 * S(p * 2), 0, 0.02 * S(p)), "pos": (0, 0.35 * S(p * 2), 0)},
        "spine": {"rot": (0.02 * S(p * 2 + 0.6), 0, 0)},
        "neck": {"rot": (0.06 * S(p + 1.2), 0.08 * S(p), 0)},
        "head": {"rot": (-0.25 * snap, 0.12 * S(p) + 0.95 * snap, 0.22 * S(p * 0.5 + 0.4) + 0.5 * snap)},
        "jaw_l": {"rot": (0, 0.06 * max(0, S(p * 3)), 0)},
        "jaw_r": {"rot": (0, -0.06 * max(0, S(p * 3)), 0)},
        "hand_l": {"rot": (0.12 * max(0, S(p * 2 + 1)), 0, 0)},
        "hand_r": {"rot": (0.12 * max(0, S(p * 2 + 2.5)), 0, 0)},
        "arm_l": {"rot": (0.03 * S(p * 2), 0, 0)},
        "arm_r": {"rot": (0.03 * S(p * 2 + 1), 0, 0)},
    }


def crawl(t, amp=1.0, run=False):
    """Diagonal crawl / bounding gallop. Swing lifts the elbows higher (mantis reach)."""
    p = TAU * t
    out = {}
    pairs = {"l": 0.0, "r": math.pi} if not run else {"l": 0.0, "r": 0.35}
    leg_ph = {"l": math.pi, "r": 0.0} if not run else {"l": math.pi, "r": math.pi + 0.35}
    for side, o in pairs.items():
        sw = S(p + o)                       # + : reaching forward
        lift = max(0.0, C(p + o))           # swing phase
        out[f"arm_{side}"] = {"rot": (-0.32 * sw * amp - 0.22 * lift * amp, 0, 0)}
        out[f"elbow_{side}"] = {"rot": (0.35 * lift * amp, 0, 0)}
        out[f"fore_{side}"] = {"rot": (-0.28 * lift * amp + 0.15 * sw * amp, 0, 0)}
        out[f"hand_{side}"] = {"rot": (0.25 * sw * amp, 0, 0)}
    for side, o in leg_ph.items():
        sw = S(p + o)
        lift = max(0.0, C(p + o))
        out[f"leg_{side}"] = {"rot": (0.28 * sw * amp + 0.25 * lift * amp, 0, 0)}
        out[f"shin_{side}"] = {"rot": (-0.3 * lift * amp + 0.12 * sw * amp, 0, 0)}
        out[f"foot_{side}"] = {"rot": (-0.2 * sw * amp, 0, 0)}
    bob = (abs(S(p)) if not run else abs(S(p))) * (1.2 if run else 0.7) * amp
    out["hips"] = {"rot": ((0.1 * S(p) if run else 0.0), 0.06 * S(p) * (0 if run else 1), 0.05 * S(p)), "pos": (0, -bob, 0)}
    out["chest"] = {"rot": ((-0.12 * S(p) if run else 0.0), -0.08 * S(p) * (0 if run else 1), -0.07 * S(p))}
    out["neck"] = {"rot": ((-0.1 if run else 0.0) + 0.04 * S(2 * p), 0.05 * S(p), 0)}
    out["head"] = {"rot": (0.04 * S(2 * p + 1), -0.05 * S(p), 0.05 * S(p))}
    return out


@anim("walk", 1.6, "loop", keys=17)
def walk(t):
    return crawl(t)


@anim("run", 0.7, "loop", keys=13)
def run(t):
    return crawl(t, 1.6, True)


@anim("rear", 1.4, "hold", keys=15)
def rear(t):
    k = min(1.0, t / 0.8)
    k = k * k * (3 - 2 * k)                                    # ease in-out, then hold
    shake = 0.03 * S(t * 40) * (1 - k)
    return {
        "hips": {"rot": (-1.15 * k, 0, 0), "pos": (0, -5.5 * k, 2.5 * k)},
        "spine": {"rot": (-0.15 * k, 0, 0)},
        "chest": {"rot": (-0.12 * k + shake, 0, 0)},
        "leg_l": {"rot": (1.0 * k, 0, 0)}, "leg_r": {"rot": (1.0 * k, 0, 0)},
        "shin_l": {"rot": (0.55 * k, 0, 0)}, "shin_r": {"rot": (0.55 * k, 0, 0)},
        "foot_l": {"rot": (-0.4 * k, 0, 0)}, "foot_r": {"rot": (-0.4 * k, 0, 0)},
        "arm_l": {"rot": (3.3 * k, 0, -0.12 * k)}, "arm_r": {"rot": (3.25 * k, 0, 0.12 * k)},
        "elbow_l": {"rot": (-1.75 * k, 0, 0)}, "elbow_r": {"rot": (-1.7 * k, 0, 0)},
        "fore_l": {"rot": (-0.85 * k, 0, 0)}, "fore_r": {"rot": (-0.8 * k, 0, 0)},
        "hand_l": {"rot": (1.2 * k, 0, 0)}, "hand_r": {"rot": (1.25 * k, 0, 0)},
        "neck": {"rot": (1.1 * k, 0, 0)},
        "head": {"rot": (0.15 * k, 0, 0.45 * k)},
        "jaw_l": {"rot": (0, 0.12 * k, 0)}, "jaw_r": {"rot": (0, -0.12 * k, 0)},
    }


@anim("screech", 1.1, "once", keys=23, interp="linear")
def screech(t):
    open_ = min(1.0, t / 0.18) * (1.0 if t < 0.8 else max(0.0, 1 - (t - 0.8) / 0.3))
    shake = S(t * 70) * open_
    return {
        "neck": {"rot": (-0.35 * open_, 0.05 * shake, 0)},
        "head": {"rot": (-0.3 * open_, 0.12 * shake, 0.1 * shake)},
        "jaw_l": {"rot": (0.25 * open_, 0.95 * open_, -0.35 * open_)},
        "jaw_r": {"rot": (0.25 * open_, -0.95 * open_, 0.35 * open_)},
        "chest": {"rot": (-0.08 * open_, 0, 0)},
    }


@anim("jaws", 0.5, "loop", keys=9)
def jaws(t):
    o = 0.65 + 0.12 * S(TAU * t * 2)
    return {"jaw_l": {"rot": (0.2 * o, 0.75 * o, -0.25 * o)}, "jaw_r": {"rot": (0.2 * o, -0.75 * o, 0.25 * o)}}


@anim("listen", 3.0, "loop", keys=25)
def listen(t):
    p = TAU * t
    return {"neck": {"rot": (0.45, 0.35 * S(p), 0)},
            "head": {"rot": (-0.35, 0.55 * S(p + 0.7), 0.3 * S(p * 0.5))},
            "jaw_l": {"rot": (0, 0.1 * max(0, S(p * 2)), 0)}, "jaw_r": {"rot": (0, -0.1 * max(0, S(p * 2)), 0)}}


@anim("fall", 0.8, "loop", keys=17)
def fall(t):
    p = TAU * t
    out = {"hips": {"rot": (0.3 * S(p), 0, 0.35 * S(p + 1))}, "head": {"rot": (0.4 * S(p * 2), 0.6 * S(p * 2 + 1), 0.3 * S(p))},
           "jaw_l": {"rot": (0.2, 0.7, -0.2)}, "jaw_r": {"rot": (0.2, -0.7, 0.2)}}
    for i, side in enumerate("lr"):
        sg = 1 if side == "l" else -1
        out[f"arm_{side}"] = {"rot": (0.9 * S(p * 2 + i * 2), 0, sg * (0.6 + 0.4 * S(p * 2 + i)))}
        out[f"elbow_{side}"] = {"rot": (-0.8 * max(0, S(p * 2 + i * 3)), 0, 0)}
        out[f"leg_{side}"] = {"rot": (-0.8 * S(p * 2 + i * 2 + 1), 0, sg * 0.5)}
        out[f"shin_{side}"] = {"rot": (0.9 * max(0, S(p * 2 + i)), 0, 0)}
    return out


@anim("recover", 2.4, "once", keys=25)
def recover(t):
    k = max(0.0, min(1.0, (t - 0.2) / 1.9))
    k = k * k * (3 - 2 * k)
    down = 1 - k
    twitch = (S(t * 55) * 0.25) if 0.35 < t < 0.5 or 1.2 < t < 1.3 else 0
    out = {"hips": {"rot": (0.15 * down, 0, 0.55 * down), "pos": (0, 7.5 * down, 0)},
           "chest": {"rot": (0.2 * down, 0, -0.25 * down)},
           "neck": {"rot": (0.6 * down, 0, 0)}, "head": {"rot": (0.3 * down, 0.4 * down + twitch, 0.7 * down)},
           "jaw_l": {"rot": (0.15 * down, 0.4 * down, 0)}, "jaw_r": {"rot": (0.15 * down, -0.4 * down, 0)}}
    for side in "lr":
        sg = 1 if side == "l" else -1
        out[f"arm_{side}"] = {"rot": (1.2 * down, 0, sg * 0.9 * down)}
        out[f"elbow_{side}"] = {"rot": (-1.3 * down + (twitch if side == "r" else 0), 0, 0)}
        out[f"leg_{side}"] = {"rot": (-0.9 * down, 0, sg * 0.6 * down)}
        out[f"shin_{side}"] = {"rot": (1.2 * down, 0, 0)}
    return out


# ======================================================================= UV packing + skin
def footprint(w, h, d):
    return 2 * (math.ceil(d) + math.ceil(w)), math.ceil(d) + math.ceil(h)


uv_of, alloc, items = {}, [], []
for name, p in P.items():
    for i, cb in enumerate(p["cubes"]):
        key = cb[7] or f"{name}#{i}"
        if key not in uv_of:
            uv_of[key] = None
            items.append((key, cb))
items.sort(key=lambda kc: -footprint(kc[1][4], kc[1][5], kc[1][6])[1])
x0 = y0 = row_h = 0
for key, cb in items:
    fw, fh = footprint(cb[4], cb[5], cb[6])
    if x0 + fw > TW:
        x0, y0, row_h = 0, y0 + row_h, 0
    if y0 + fh > TH:
        raise SystemExit("texture atlas overflow")
    uv_of[key] = (x0, y0)
    alloc.append((x0, y0, cb[4], cb[5], cb[6], cb[0]))
    x0 += fw
    row_h = max(row_h, fh)

MAT = {"skin": (0.47, 0.44, 0.42), "bone": (0.62, 0.57, 0.46), "cavity": (0.16, 0.05, 0.05), "gum": (0.16, 0.03, 0.035),
       "tooth": (0.74, 0.69, 0.55), "claw": (0.06, 0.055, 0.05), "dark": (0.035, 0.03, 0.03), "glow": (0.03, 0.03, 0.03)}
noise = rng.random((TH, TW))
blur = np.array(Image.fromarray((noise * 255).astype(np.uint8)).resize((TW // 8, TH // 8), Image.BILINEAR).resize((TW, TH), Image.BICUBIC)) / 255.0
img = np.zeros((TH, TW, 4))
glow = np.zeros((TH, TW, 4))


def rects(u, v, w, h, d):
    return {"north": (u + d, v + d, w, h), "east": (u, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "up": (u + d, v, w, d), "down": (u + d + w, v, w, d)}


SHADE = {"up": 1.12, "north": 1.0, "south": 0.9, "east": 0.85, "west": 0.85, "down": 0.6}
skin_mask = np.zeros((TH, TW), bool)
for (u, v, w, h, d, mat) in alloc:
    base = np.array(MAT[mat])
    for face, (fx, fy, fw_, fh_) in rects(u, v, w, h, d).items():
        x1, y1 = int(math.floor(fx)), int(math.floor(fy))
        x2, y2 = max(x1 + 1, int(math.ceil(fx + fw_))), max(y1 + 1, int(math.ceil(fy + fh_)))
        reg = (slice(y1, y2), slice(x1, x2))
        n = noise[reg][..., None]
        b = blur[reg][..., None]
        col = base * SHADE[face] * (0.8 + 0.32 * b) * (0.93 + 0.14 * n)
        if mat == "skin":
            skin_mask[reg] = True
            bruise = np.clip((b - 0.6) * 3, 0, 1)
            col = col * (1 - bruise) + bruise * np.array((0.36, 0.27, 0.33)) * SHADE[face]
            if face in ("east", "west", "north", "south") and y2 - y1 > 2:
                col = col * np.linspace(1.0, 0.68, y2 - y1)[:, None, None]      # extremities darken
        if mat == "bone":
            col = col * (0.88 + 0.22 * (n > 0.85))
        if mat == "claw":
            col = col + (n > 0.93) * 0.1
        if mat == "tooth":
            col = col * np.linspace(1.0, 0.75, y2 - y1)[:, None, None] if y2 - y1 > 1 else col
        img[reg][..., :3] = np.clip(col, 0, 1)
        img[reg][..., 3] = 1
        if mat == "glow":
            glow[reg] = (0.9, 0.9, 0.95, 1.0)
for _ in range(110):                                            # veins
    y, x = rng.integers(0, TH), rng.integers(0, TW)
    for _ in range(rng.integers(4, 13)):
        if 0 <= y < TH and 0 <= x < TW and skin_mask[y, x]:
            img[y, x, :3] *= 0.68
            img[y, x, 2] *= 1.06
        y += rng.integers(0, 2)
        x += rng.integers(-1, 2)
os.makedirs(TEX_DIR, exist_ok=True)
Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(os.path.join(TEX_DIR, "adherent.png"))
Image.fromarray((glow * 255).astype(np.uint8), "RGBA").save(os.path.join(TEX_DIR, "adherent_glow.png"))


# ======================================================================= .bbmodel
def r(x):
    return round(x, 4) + 0.0


def box_faces(u, v, w, h, d):
    return {k: {"uv": ([fx, fy, fx + fw, fy + fh] if k != "up" else [fx + fw, fy + fh, fx, fy]), "texture": 0}
            for k, (fx, fy, fw, fh) in rects(u, v, w, h, d).items()}


abs_p, elements, groups, guid = {}, [], {}, {}
for name, p in P.items():
    par = p["parent"]
    base = abs_p[par] if par else (0.0, 0.0, 0.0)
    pv = tuple(base[i] + p["offset"][i] for i in range(3))
    abs_p[name] = pv
    origin = [r(-pv[0]), r(24 - pv[1]), r(pv[2])]
    rot = p["rot"]
    guid[name] = str(uuid.uuid4())
    g = {"name": name, "origin": origin, "rotation": [r(-math.degrees(rot[0])), r(-math.degrees(rot[1])), r(math.degrees(rot[2]))],
         "color": 0, "uuid": guid[name], "export": True, "mirror_uv": False, "isOpen": False, "locked": False,
         "visibility": True, "autouv": 0, "children": []}
    for i, (mat, x, y, z, w, h, d, key) in enumerate(p["cubes"]):
        u, v = uv_of[key or f"{name}#{i}"]
        cid = str(uuid.uuid4())
        elements.append({"name": f"{name}_{mat}_{i}", "box_uv": True, "rescale": False, "locked": False,
                         "render_order": "default", "allow_mirror_modeling": True,
                         "from": [r(-(pv[0] + x + w)), r(24 - (pv[1] + y + h)), r(pv[2] + z)],
                         "to": [r(-(pv[0] + x)), r(24 - (pv[1] + y)), r(pv[2] + z + d)],
                         "autouv": 0, "color": 0, "inflate": 0, "origin": origin, "rotation": [0, 0, 0],
                         "uv_offset": [u, v], "mirror_uv": False, "faces": box_faces(u, v, w, h, d), "type": "cube", "uuid": cid})
        g["children"].append(cid)
    groups[name] = (g, par)
outliner = []
for name, (g, par) in groups.items():
    (groups[par][0]["children"] if par else outliner).append(g)


def keyframes_for(fn, length, keys, loop, interp):
    """Samples an authoring function into Blockbench keyframes (Blockbench axes/units)."""
    times = [length * i / (keys - 1) for i in range(keys)]
    samples = [fn(min(t / length, 1.0 - 1e-9) if loop == "loop" and i < keys - 1 else min(t / length, 1.0))
               for i, t in enumerate(times)]
    if loop == "loop":
        samples[-1] = fn(0.0)                                  # seamless cycle
    animators = {}
    bones = sorted({b for s in samples for b in s})
    for b in bones:
        kfs = []
        for ch in ("rot", "pos"):
            if not any(ch in s.get(b, {}) for s in samples):
                continue
            for t, s in zip(times, samples):
                v = s.get(b, {}).get(ch, (0, 0, 0))
                if ch == "rot":
                    bb = (-math.degrees(v[0]), -math.degrees(v[1]), math.degrees(v[2]))
                else:
                    bb = (-v[0], -v[1], v[2])
                kfs.append({"channel": "rotation" if ch == "rot" else "position",
                            "data_points": [{"x": str(round(bb[0], 3)), "y": str(round(bb[1], 3)), "z": str(round(bb[2], 3))}],
                            "uuid": str(uuid.uuid4()), "time": round(t, 4), "color": -1, "interpolation": interp})
        animators[guid[b]] = {"name": b, "type": "bone", "keyframes": kfs}
    return animators


animations = []
for (name, length, loop, fn, keys, interp) in ANIMS:
    animations.append({"uuid": str(uuid.uuid4()), "name": name, "loop": loop, "override": False, "length": length,
                       "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                       "loop_delay": "", "animators": keyframes_for(fn, length, keys, loop, interp)})

buf = io.BytesIO()
Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(buf, "PNG")
model = {
    "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
    "name": "adherent", "model_identifier": "adherent", "modded_entity_version": "fabric", "modded_entity_flip_y": True,
    "visible_box": [1, 2, 0], "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
    "unhandled_root_fields": {}, "resolution": {"width": TW, "height": TH},
    "elements": elements, "outliner": outliner,
    "textures": [{"path": "", "name": "adherent.png", "folder": "", "namespace": "", "id": "0", "width": TW, "height": TH,
                  "uv_width": TW, "uv_height": TH, "particle": False, "render_mode": "default", "visible": True,
                  "mode": "bitmap", "saved": True, "uuid": str(uuid.uuid4()),
                  "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
    "animations": animations,
}
with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(model, fh, indent=1)

rest = contacts()
print(f"wrote {OUT}: {len(P)} bones, {len(elements)} cubes, {len(alloc)} UV islands, {len(animations)} animations")
print("rest pose (model px, ground y=24): " + ", ".join(f"{k} y={v:.1f}" for k, v in rest.items()))
