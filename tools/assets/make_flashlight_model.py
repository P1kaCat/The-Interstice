"""The flashlight's 3D item model (2026-11-05): a tube along +Y (tail cap, knurled grip with a rubber switch,
neck, wide head with the lens on top), held pointing forward in first and third person, diagonal in the GUI like
the spyglass. Two textures (lens dark / lens lit) and two models; the client's "interstice:lit" item property picks.

    python -I tools/assets/make_flashlight_model.py
"""
import json
import os

import numpy as np
from PIL import Image, PngImagePlugin

A = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice")
N = 32
rng = np.random.default_rng(31)


def px(u):
    return int(round(u * 2))


def fill(img, u0, v0, u1, v1, f):
    for y in range(px(v0), px(v1)):
        for x in range(px(u0), px(u1)):
            img[y, x] = f(x - px(u0), y - px(v0), px(u1) - px(u0), px(v1) - px(v0))


def shade(c, k):
    return [min(255, max(0, int(ch * k))) for ch in c] + [255]


def texture(lit):
    img = np.zeros((N, N, 4), np.uint8)
    metal = (78, 82, 88)
    # head side (uv 0..5 x 0..3.5): gunmetal, a bright highlight band, darker rim at the lens end
    fill(img, 0, 0, 5, 3.5, lambda x, y, w, h: shade(metal, (1.35 if 3 <= x <= 4 else 1.0) * (0.75 if y < 2 else 1.0) * (0.9 + rng.random() * 0.12)))
    # lens (uv 0..5 x 4..9): reflector rings and the glass
    def lens(x, y, w, h):
        r = np.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / (w / 2)
        if r > 0.92:
            return shade(metal, 0.7)
        if lit:
            c = (255, 246, 214) if r < 0.45 else (250, 226, 170) if r < 0.75 else (214, 190, 140)
            return list(c) + [255]
        c = (54, 62, 70) if r < 0.45 else (96, 104, 112) if r < 0.75 else (70, 76, 84)
        return shade(c, 0.95 + rng.random() * 0.1)
    fill(img, 0, 4, 5, 9, lens)
    # grip (uv 6..8.5 x 0..7.5): dark rubber with knurled rings
    grip = (34, 34, 38)
    fill(img, 6, 0, 8.5, 7.5, lambda x, y, w, h: shade(grip, (1.45 if y % 3 == 0 else 1.0) * (1.25 if x == 1 else 1.0) * (0.9 + rng.random() * 0.15)))
    # tail cap side and end (uv 9..12 x 0..1.5, 9..12 x 2..5)
    fill(img, 9, 0, 12, 1.5, lambda x, y, w, h: shade(grip, 0.8 + 0.3 * (y == 0)))
    fill(img, 9, 2, 12, 5, lambda x, y, w, h: shade(grip, 0.7 + 0.2 * (np.hypot(x - 2.5, y - 2.5) < 2)))
    # neck (uv 13..16 x 0..1.5) and switch (uv 13..14 x 2..3.5)
    fill(img, 13, 0, 16, 1.5, lambda x, y, w, h: shade(metal, 0.85 + 0.25 * (x % 3 == 0)))
    fill(img, 13, 2, 14, 3.5, lambda x, y, w, h: shade((168, 64, 40), 1.0 + 0.2 * (y == 0)))
    # back of the head (uv 6..11 x 8..13): a ring narrowing to the neck
    fill(img, 6, 8, 11, 13, lambda x, y, w, h: shade(metal, 0.65 + 0.25 * (np.hypot(x - 4.5, y - 4.5) > 3)))
    return img


def save(name, img):
    p = os.path.join(A, "textures", "item", name + ".png")
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "procedural flashlight model texture; tools/assets/make_flashlight_model.py")
    Image.fromarray(img, "RGBA").save(p, pnginfo=info)


def box(fr, to, side, up=None, down=None, extra=None):
    faces = {f: {"uv": side, "texture": "#t"} for f in ("north", "south", "east", "west")}
    if up:
        faces["up"] = {"uv": up, "texture": "#t"}
    if down:
        faces["down"] = {"uv": down, "texture": "#t"}
    return {"from": fr, "to": to, "faces": faces}


def model(tex, overrides=None):
    # every part has all six faces (a missing top or bottom showed as a hole where a part is wider than the next)
    els = [
        box([6.5, 0, 6.5], [9.5, 1.5, 9.5], [9, 0, 12, 1.5], up=[9, 2, 12, 5], down=[9, 2, 12, 5]),                   # tail cap
        box([6.75, 1.5, 6.75], [9.25, 9, 9.25], [6, 0, 8.5, 7.5], up=[6, 0, 8.5, 2.5], down=[6, 0, 8.5, 2.5]),        # knurled grip
        box([9.25, 6, 7.5], [9.75, 7.5, 8.5], [13, 2, 14, 3.5], up=[13, 2, 14, 3], down=[13, 2, 14, 3]),             # switch
        box([6.5, 9, 6.5], [9.5, 10.5, 9.5], [13, 0, 16, 1.5], up=[13, 0, 16, 1.5], down=[13, 0, 16, 1.5]),           # neck
        box([5.5, 10.5, 5.5], [10.5, 14, 10.5], [0, 0, 5, 3.5], up=[0, 4, 5, 9], down=[6, 8, 11, 13]),               # head + lens
    ]
    d = {
        "textures": {"t": tex, "particle": tex},
        "elements": els,
        "display": {
            "firstperson_righthand": {"rotation": [-70, 0, 15], "translation": [1, 1.5, -1], "scale": [1, 1, 1]},
            "firstperson_lefthand": {"rotation": [-70, 0, -15], "translation": [1, 1.5, -1], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [-75, 0, 0], "translation": [0, 0.5, -1], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_lefthand": {"rotation": [-75, 0, 0], "translation": [0, 0.5, -1], "scale": [0.9, 0.9, 0.9]},
            "gui": {"rotation": [-67.5, 0, 45], "translation": [0, 0, 0], "scale": [1.3, 1.3, 1.3]},
            "ground": {"rotation": [90, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
            "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1.3, 1.3, 1.3]},
            "head": {"rotation": [90, 0, 0], "translation": [0, 0, -14], "scale": [1, 1, 1]},
        },
    }
    if overrides:
        d["overrides"] = overrides
    return d


def write(rel, obj):
    p = os.path.join(A, "models", "item", rel + ".json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    save("flashlight_model", texture(False))
    save("flashlight_model_lit", texture(True))
    write("flashlight", model("interstice:item/flashlight_model",
                              [{"predicate": {"interstice:lit": 1}, "model": "interstice:item/flashlight_lit"}]))
    write("flashlight_lit", model("interstice:item/flashlight_model_lit"))
    print("flashlight model ok")
