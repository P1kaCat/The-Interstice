"""TEMPORARY development textures for the Corridor survival items and the ward emitter (2026-11-05).

Every texture written here is a placeholder drawn procedurally so the items are recognisable in game. The final
art is to be made in PixelLab: see docs/PIXELLAB_ASSETS.md, section "Corridor rework: survival items". Each file
also carries a 'temporary' marker in its PNG text chunk.

    python -I tools/assets/gen_survival_textures.py
"""
import os

import numpy as np
from PIL import Image, PngImagePlugin

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures")
rng = np.random.default_rng(7)


def canvas():
    return np.zeros((16, 16, 4), np.uint8)


def px(a, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        a[y, x] = (*c, 255)


def rect(a, x0, y0, x1, y1, c, jitter=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            j = int(rng.integers(-jitter, jitter + 1)) if jitter else 0
            px(a, x, y, tuple(max(0, min(255, v + j)) for v in c))


def outline(a, c=(20, 18, 18)):
    m = a[..., 3] > 0
    o = a.copy()
    for y in range(16):
        for x in range(16):
            if m[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < 16 and 0 <= yy < 16 and m[yy, xx]:
                    o[y, x] = (*c, 255)
                    break
    return o


def save(a, rel):
    _p = os.path.join(ROOT, rel)
    if os.path.exists(_p) and "PixelLab" in str(Image.open(_p).info.get("interstice", "")):
        print("kept PixelLab texture", rel)
        return
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "temporary development texture - replace with PixelLab art (docs/PIXELLAB_ASSETS.md)")
    Image.fromarray(a, "RGBA").save(p, pnginfo=info)
    print("wrote", rel)


def canned_ration():
    a = canvas()
    rect(a, 4, 3, 11, 13, (120, 124, 128), 6)
    rect(a, 4, 6, 11, 10, (150, 70, 40), 5)       # label
    rect(a, 5, 7, 10, 7, (205, 190, 150))
    rect(a, 4, 3, 11, 3, (175, 178, 182))
    rect(a, 4, 13, 11, 13, (90, 92, 95))
    return outline(a)


def dry_biscuits():
    a = canvas()
    for i, (x, y) in enumerate(((3, 5), (7, 7), (5, 10))):
        rect(a, x, y, x + 5, y + 3, (190, 160, 105), 8)
        px(a, x + 1, y + 1, (150, 120, 75)); px(a, x + 4, y + 2, (150, 120, 75))
    return outline(a)


def nutrient_paste():
    a = canvas()
    rect(a, 6, 2, 9, 13, (170, 175, 160), 6)
    rect(a, 7, 1, 8, 1, (60, 60, 60))
    rect(a, 6, 5, 9, 8, (95, 140, 90), 4)
    return outline(a)


def bandage():
    a = canvas()
    rect(a, 3, 4, 12, 11, (225, 222, 210), 7)
    for x in range(3, 13, 2):
        rect(a, x, 4, x, 11, (200, 196, 182))
    rect(a, 12, 6, 14, 9, (230, 228, 218), 5)      # loose end
    px(a, 6, 7, (150, 40, 35)); px(a, 7, 8, (150, 40, 35))
    return outline(a)


def medical_kit():
    a = canvas()
    rect(a, 2, 4, 13, 13, (200, 200, 196), 6)
    rect(a, 6, 2, 9, 3, (80, 80, 80))
    rect(a, 7, 6, 8, 11, (190, 35, 30))
    rect(a, 5, 8, 10, 9, (190, 35, 30))
    rect(a, 2, 13, 13, 13, (140, 140, 136))
    return outline(a)


def emergency_injector():
    a = canvas()
    for i in range(9):
        rect(a, 3 + i, 12 - i, 4 + i, 12 - i, (210, 210, 200), 4)
    for i in range(3, 7):
        px(a, 4 + i, 12 - i, (220, 120, 30))       # amber band
    px(a, 13, 2, (180, 180, 190)); px(a, 14, 1, (180, 180, 190))   # needle
    rect(a, 2, 12, 3, 13, (60, 60, 60))
    return outline(a)


def flashlight():
    a = canvas()
    for i in range(9):
        rect(a, 3 + i, 11 - i, 5 + i, 12 - i, (55, 58, 62), 6)
    rect(a, 11, 2, 13, 4, (95, 98, 100))
    px(a, 13, 2, (255, 240, 190)); px(a, 12, 2, (240, 225, 170))
    px(a, 6, 9, (170, 60, 40))                      # switch
    return outline(a)


def battery():
    a = canvas()
    rect(a, 5, 3, 10, 13, (40, 42, 46), 4)
    rect(a, 5, 3, 10, 5, (200, 160, 40), 6)
    rect(a, 7, 2, 8, 2, (170, 170, 175))
    px(a, 7, 8, (220, 220, 220)); px(a, 8, 8, (220, 220, 220)); px(a, 8, 7, (220, 220, 220)); px(a, 8, 9, (220, 220, 220))
    return outline(a)


def flash_flare():
    a = canvas()
    rect(a, 6, 4, 9, 13, (165, 40, 35), 6)
    rect(a, 6, 2, 9, 3, (230, 230, 225))
    rect(a, 6, 10, 9, 10, (240, 220, 80))
    rect(a, 7, 1, 8, 1, (90, 90, 90))
    return outline(a)


def rattle_lure():
    a = canvas()
    rect(a, 4, 5, 11, 12, (130, 110, 70), 10)
    rect(a, 4, 5, 11, 5, (160, 140, 95))
    for (x, y) in ((6, 8), (9, 9), (7, 10)):
        px(a, x, y, (70, 70, 72))
    rect(a, 7, 2, 8, 4, (90, 90, 95))               # winding key
    rect(a, 6, 2, 9, 2, (110, 110, 115))
    return outline(a)


def resonance_spike():
    a = canvas()
    for i in range(10):
        rect(a, 3 + i, 12 - i, 3 + i, 12 - i, (150, 160, 175), 8)
        px(a, 4 + i, 12 - i, (95, 100, 115))
    for i in range(2, 9, 3):
        px(a, 3 + i, 12 - i, (120, 200, 230))       # tuning grooves
    rect(a, 1, 13, 3, 14, (60, 45, 35))
    return outline(a)


def torn_note():
    a = canvas()
    rect(a, 3, 2, 12, 13, (205, 196, 170), 6)
    for y in (4, 6, 8, 10):
        rect(a, 4, y, 10 - (y % 3), y, (90, 85, 80))
    px(a, 12, 13, (0, 0, 0)); px(a, 11, 13, (0, 0, 0))
    a[13, 11:13] = 0
    return outline(a)


def ward_side():
    a = np.zeros((16, 16, 4), np.uint8)
    rect(a, 0, 0, 15, 15, (70, 74, 80), 8)
    rect(a, 2, 3, 13, 5, (40, 42, 46))
    rect(a, 3, 4, 12, 4, (110, 190, 230))           # indicator strip
    for y in range(8, 15, 2):
        rect(a, 2, y, 13, y, (52, 55, 60))          # speaker grille
    return a


def ward_top():
    a = np.zeros((16, 16, 4), np.uint8)
    rect(a, 0, 0, 15, 15, (78, 82, 88), 8)
    rect(a, 5, 5, 10, 10, (35, 36, 40))
    rect(a, 7, 7, 8, 8, (120, 200, 235))
    return a


if __name__ == "__main__":
    for name, fn in [("canned_ration", canned_ration), ("dry_biscuits", dry_biscuits), ("nutrient_paste", nutrient_paste),
                     ("bandage", bandage), ("medical_kit", medical_kit), ("emergency_injector", emergency_injector),
                     ("flashlight", flashlight), ("battery", battery), ("flash_flare", flash_flare),
                     ("rattle_lure", rattle_lure), ("resonance_spike", resonance_spike), ("torn_note", torn_note)]:
        save(fn(), f"item/{name}.png")
    save(ward_side(), "block/ward_emitter_side.png")
    save(ward_top(), "block/ward_emitter_top.png")
