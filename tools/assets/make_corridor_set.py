"""The Corridor's own versions of the vanilla decorative blocks (2026-11-05, option 3 of the 32x32 plan).

Textures (32x32, procedural, in the family of the concrete and the PixelLab materials) for the bars, the blackout
pane, the rotten log and the grime soil, and the block/item models and blockstates of all eleven blocks. The
blockstates take the structure of the vanilla ones (stairs, slab, wall, fence, pane, log: read from the Minecraft
client jar Loom caches) with our model names; the models use vanilla parent templates with our textures. No
vanilla texture is copied.

    python -I tools/assets/make_corridor_set.py
"""
import glob
import json
import os
import zipfile

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

A = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice")
TEX = os.path.join(A, "textures", "block")
N = 32
JAR = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")))[-1]


def w(rel, obj):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(name, rgb, alpha=None):
    a = np.full((N, N), 255.0) if alpha is None else alpha
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "32x32 procedural (tools/assets/make_corridor_set.py)")
    Image.fromarray(np.dstack([np.clip(rgb, 0, 255), a]).astype(np.uint8), "RGBA").save(os.path.join(TEX, name + ".png"), pnginfo=info)


def noise(sigma, seed):
    n = gaussian_filter(np.random.default_rng(seed).standard_normal((N, N)), sigma, mode="wrap")
    return (n - n.mean()) / (n.std() + 1e-9)


def textures():
    r = np.random.default_rng(55)
    # rusted bars: four vertical bars and two cross straps, rust, transparent between
    rgb = np.zeros((N, N, 3))
    alpha = np.zeros((N, N))
    base = np.array([88.0, 62, 48])
    for x0 in (2, 10, 18, 26):
        for x in range(x0, x0 + 3):
            alpha[:, x] = 255
            rgb[:, x] = base * (0.8 + 0.25 * (x - x0))
    for y0 in (3, 27):
        alpha[y0:y0 + 2, :] = 255
        rgb[y0:y0 + 2, :] = base * 0.85
    rust = np.clip(noise(2, 56) * 0.6 + 0.2, 0, 1)
    rgb = rgb * (1 - 0.35 * rust[..., None]) + np.array([112, 56, 30]) * 0.35 * rust[..., None]
    rgb += r.standard_normal((N, N, 1)) * 5
    save("rusted_bars", rgb, alpha)
    save("rusted_bars_edge", np.full((N, N, 3), 70.0) + noise(2, 57)[..., None] * 6)
    # blackout pane: nearly black painted glass in a dark frame, slightly see-through at scratches
    rgb = np.full((N, N, 3), 14.0) + noise(3, 58)[..., None] * 3
    alpha = np.full((N, N), 235.0)
    scratch = (np.abs(noise(1.0, 59)) > 2.2)
    alpha[scratch] = 150
    rgb[scratch] = 40
    for e in (0, 1, 30, 31):
        rgb[e, :] = rgb[:, e] = 38
        alpha[e, :] = alpha[:, e] = 255
    save("blackout_pane", rgb, alpha)
    save("blackout_pane_edge", np.full((N, N, 3), 38.0))
    # rotten log: grey-brown wood, deep vertical grain, dark rot patches; top: rings and a soft core
    x = np.arange(N)
    grain = np.sin(x * 0.9 + np.cumsum(r.normal(0, 0.4, N)))[None, :] * 8 + noise(1.0, 60) * 6
    rgb = np.array([74.0, 62, 52])[None, None, :] + grain[..., None]
    rot = np.clip(noise(4, 61) - 0.6, 0, 1)
    rgb *= (1 - 0.45 * rot)[..., None]
    save("rotten_log", rgb)
    yy, xx = np.mgrid[0:N, 0:N]
    rr = np.hypot(yy - 15.5, xx - 15.5)
    top = np.array([96.0, 80, 64])[None, None, :] * (0.85 + 0.12 * np.sin(rr * 1.4))[..., None] + noise(1, 62)[..., None] * 4
    top[rr > 13.5] = np.array([60, 50, 42])
    top[rr < 3] *= 0.55
    save("rotten_log_top", top)
    # grime soil: dark packed soil with grit and a little rubble
    rgb = np.array([58.0, 50, 42])[None, None, :] + (noise(2.5, 63) * 7 + r.standard_normal((N, N)) * 6)[..., None]
    grit = r.random((N, N)) > 0.9
    rgb[grit] += 18
    save("grime_soil", rgb)
    print("textures ok")


def vanilla_blockstate(name):
    with zipfile.ZipFile(JAR) as z:
        return json.loads(z.read(f"assets/minecraft/blockstates/{name}.json"))


def remap(obj, mapping):
    s = json.dumps(obj)
    for a, b in mapping.items():
        s = s.replace(f'"minecraft:block/{a}"', f'"interstice:block/{b}"')
    return json.loads(s)


def models():
    T = "interstice:block/"
    # concrete stairs and slab (the panel texture), lab slab (clinic tile), concrete low wall (weathered concrete)
    for name, tex in (("concrete_stairs", "concrete_panel"), ("worn_bench", "floorboards")):
        for kind in ("stairs", "inner_stairs", "outer_stairs"):
            suffix = "" if kind == "stairs" else "_" + kind.split("_")[0]
            w(f"models/block/{name}{suffix}.json", {"parent": f"minecraft:block/{kind}", "textures": {"bottom": T + tex, "top": T + tex, "side": T + tex}})
        w(f"blockstates/{name}.json", remap(vanilla_blockstate("stone_brick_stairs"), {
            "stone_brick_stairs": name, "stone_brick_stairs_inner": name + "_inner", "stone_brick_stairs_outer": name + "_outer"}))
        w(f"models/item/{name}.json", {"parent": T + name})
    for name, tex in (("concrete_slab", "concrete_panel"), ("lab_slab", "clinic_tile")):
        w(f"models/block/{name}.json", {"parent": "minecraft:block/slab", "textures": {"bottom": T + tex, "top": T + tex, "side": T + tex}})
        w(f"models/block/{name}_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": T + tex, "top": T + tex, "side": T + tex}})
        w(f"models/block/{name}_double.json", {"parent": "minecraft:block/cube_all", "textures": {"all": T + tex}})
        w(f"blockstates/{name}.json", remap(vanilla_blockstate("smooth_stone_slab"), {
            "smooth_stone_slab": name, "smooth_stone_slab_top": name + "_top", "smooth_stone_slab_double": name + "_double"}))
        w(f"models/item/{name}.json", {"parent": T + name})
    name = "concrete_low_wall"
    for kind in ("post", "side", "side_tall"):
        w(f"models/block/{name}_{kind}.json", {"parent": f"minecraft:block/template_wall_{kind}", "textures": {"wall": T + "weathered_concrete"}})
    w(f"models/block/{name}_inventory.json", {"parent": "minecraft:block/wall_inventory", "textures": {"wall": T + "weathered_concrete"}})
    w(f"blockstates/{name}.json", remap(vanilla_blockstate("stone_brick_wall"), {
        "stone_brick_wall_post": name + "_post", "stone_brick_wall_side": name + "_side", "stone_brick_wall_side_tall": name + "_side_tall"}))
    w(f"models/item/{name}.json", {"parent": T + name + "_inventory"})
    name = "worn_fence"
    for kind in ("post", "side"):
        w(f"models/block/{name}_{kind}.json", {"parent": f"minecraft:block/fence_{kind}", "textures": {"texture": T + "floorboards"}})
    w(f"models/block/{name}_inventory.json", {"parent": "minecraft:block/fence_inventory", "textures": {"texture": T + "floorboards"}})
    w(f"blockstates/{name}.json", remap(vanilla_blockstate("oak_fence"), {"oak_fence_post": name + "_post", "oak_fence_side": name + "_side"}))
    w(f"models/item/{name}.json", {"parent": T + name + "_inventory"})
    # bars and blackout pane: glass-pane templates (the bars texture has transparency)
    for name in ("rusted_bars", "blackout_pane"):
        tex = {"pane": T + name, "edge": T + name + "_edge", "particle": T + name}
        for part in ("post", "side", "side_alt", "noside", "noside_alt"):
            w(f"models/block/{name}_{part}.json", {"parent": f"minecraft:block/template_glass_pane_{part}", "textures": tex})
        w(f"blockstates/{name}.json", remap(vanilla_blockstate("black_stained_glass_pane"), {
            "black_stained_glass_pane_post": name + "_post", "black_stained_glass_pane_side": name + "_side",
            "black_stained_glass_pane_side_alt": name + "_side_alt", "black_stained_glass_pane_noside": name + "_noside",
            "black_stained_glass_pane_noside_alt": name + "_noside_alt"}))
        w(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": T + name}})
    # grimy glass block, rotten log, grime soil
    w("models/block/grimy_glass.json", {"parent": "minecraft:block/cube_all", "textures": {"all": T + "grimy_glass_pane"}})
    w("blockstates/grimy_glass.json", {"variants": {"": {"model": T + "grimy_glass"}}})
    w("models/item/grimy_glass.json", {"parent": T + "grimy_glass"})
    w("models/block/rotten_log.json", {"parent": "minecraft:block/cube_column", "textures": {"side": T + "rotten_log", "end": T + "rotten_log_top"}})
    w("models/block/rotten_log_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": T + "rotten_log", "end": T + "rotten_log_top"}})
    w("blockstates/rotten_log.json", remap(vanilla_blockstate("stripped_spruce_log"), {
        "stripped_spruce_log": "rotten_log", "stripped_spruce_log_horizontal": "rotten_log_horizontal"}))
    w("models/item/rotten_log.json", {"parent": T + "rotten_log"})
    w("models/block/grime_soil.json", {"parent": "minecraft:block/cube_all", "textures": {"all": T + "grime_soil"}})
    w("blockstates/grime_soil.json", {"variants": {"": [{"model": T + "grime_soil"}, {"model": T + "grime_soil", "y": 90},
                                                       {"model": T + "grime_soil", "y": 180}, {"model": T + "grime_soil", "y": 270}]}})
    w("models/item/grime_soil.json", {"parent": T + "grime_soil"})
    # names
    p = os.path.join(A, "lang", "en_us.json")
    lang = json.load(open(p, encoding="utf-8"))
    for n in ("concrete_stairs", "concrete_slab", "lab_slab", "concrete_low_wall", "worn_bench", "worn_fence", "rusted_bars",
              "blackout_pane", "grimy_glass", "rotten_log", "grime_soil"):
        lang[f"block.interstice.{n}"] = n.replace("_", " ").title()
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(lang, f, indent=2)
        f.write("\n")
    print("models ok")


if __name__ == "__main__":
    textures()
    models()
