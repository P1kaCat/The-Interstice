"""The Corridor's own copies of the functional vanilla blocks, the 32x32 lamps, spawn eggs and water (2026-11-05).

Input: the PixelLab generations downloaded by fetch_pixellab.py (tools/assets/pixellab_jobs/corridor_copies.txt,
prompts in docs/PIXELLAB_ASSETS.md, batch "Corridor copies"). This script:
  - grades them towards the Corridor palette (a little desaturated and darker; lamps and eggs untouched),
  - splits the 32x64 doors and beds into their two halves, draws the door item icons from the doors,
  - draws the chain, antenna rod, dark iron and glass-edge textures procedurally from the generated metal,
  - animates the water (built-in pack corridor_water, overriding the vanilla water textures),
  - writes block/item models (vanilla parent templates where the silhouette is vanilla), blockstates (from the
    vanilla ones in the Loom-cached client jar, with our model names), the climbable tag and the names.
No vanilla texture is copied.

    python -I tools/assets/make_corridor_copies.py <raw_dir>
"""
import glob
import json
import os
import shutil
import sys
import zipfile

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
A = os.path.join(ROOT, "src", "main", "resources", "assets", "interstice")
TEX = os.path.join(A, "textures", "block")
ITEM = os.path.join(A, "textures", "item")
PACK = os.path.join(ROOT, "src", "client", "resources", "resourcepacks", "corridor_water")
JAR = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")))[-1]
T = "interstice:block/"
RAW = None


def raw(name):
    return np.asarray(Image.open(os.path.join(RAW, name + ".png")).convert("RGBA")).astype(float)


def grade(a, sat=0.85, val=0.92):
    a = a.copy()
    rgb = a[..., :3]
    m = rgb.mean(-1, keepdims=True)
    a[..., :3] = (m + (rgb - m) * sat) * val
    return a


def save(path, a, note="PixelLab generation (pixflux 32x32), graded; tools/assets/make_corridor_copies.py"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", note)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(path, pnginfo=info)


def block(name, a, **kw):
    save(os.path.join(TEX, name + ".png"), a, **kw)


def opaque(a):
    a = a.copy()
    a[..., 3] = 255
    return a


def seamless(a, width=6):
    """Blend with a half-shifted copy near the borders so the tile wraps without a seam."""
    rgb = a[..., :3]
    sh = np.roll(np.roll(rgb, 16, 0), 16, 1)
    d = np.minimum(np.minimum(np.arange(32), 31 - np.arange(32)), width) / width
    w = np.minimum(d[:, None], d[None, :])[..., None]
    out = a.copy()
    out[..., :3] = rgb * w + sh * (1 - w)
    return out


def w(rel, obj):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def vanilla_blockstate(name):
    with zipfile.ZipFile(JAR) as z:
        return json.loads(z.read(f"assets/minecraft/blockstates/{name}.json"))


def remap(obj, mapping):
    s = json.dumps(obj)
    for a, b in sorted(mapping.items(), key=lambda kv: -len(kv[0])):
        s = s.replace(f'"minecraft:block/{a}"', f'"interstice:block/{b}"')
    return json.loads(s)


def model(name, parent, textures, **extra):
    d = {"parent": parent, "textures": textures}
    d.update(extra)
    w(f"models/block/{name}.json", d)


def item_block(name, model_name=None):
    w(f"models/item/{name}.json", {"parent": T + (model_name or name)})


def item_flat(name, texture):
    w(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def single(name, model_name=None):
    w(f"blockstates/{name}.json", {"variants": {"": {"model": T + (model_name or name)}}})


HORIZONTAL_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


# ---------------------------------------------------------------- textures
def textures():
    # doors: one 32x64 picture each, split; item icon = the door at half size
    for d in ("worn_door", "rotten_door", "pale_door", "steel_door"):
        a = opaque(grade(raw(d)))
        block(d + "_top", a[:32])
        block(d + "_bottom", a[32:])
        icon = np.zeros((32, 32, 4))
        icon[:, 8:24] = a[::2, ::2]
        save(os.path.join(ITEM, d + ".png"), icon)
    # ladder: stretch the drawn ladder to the full width so its rails sit on the block edges
    a = grade(raw("rusted_ladder"))
    cols = np.where(a[..., 3].max(0) > 40)[0]
    x0, x1 = cols.min(), cols.max() + 1
    lad = np.asarray(Image.fromarray(a[:, x0:x1].astype(np.uint8), "RGBA").resize((32, 32), Image.NEAREST)).astype(float)
    lad[..., 3] = np.where(lad[..., 3] > 100, 255, 0)
    block("rusted_ladder", lad)
    for n in ("supply_locker_front", "supply_locker_front_open", "supply_locker_side", "supply_locker_top",
              "supply_drum_side", "supply_drum_top", "supply_drum_top_open", "hospital_bed_side", "worn_bed_side",
              "rotten_bookshelf", "rusted_basin_side", "rusted_stove_front", "rusted_stove_side", "rusted_stove_top",
              "industrial_furnace_front", "industrial_furnace_side", "industrial_furnace_top", "scrap_anvil_top",
              "hydraulic_press_front", "hydraulic_press_side", "rusted_funnel_top", "flesh_node_side", "flesh_node_top"):
        block(n, opaque(grade(raw(n))))
    drum_bottom = opaque(grade(raw("supply_drum_top")))
    drum_bottom[..., :3] *= 0.75
    block("supply_drum_bottom", drum_bottom)
    for b in ("hospital_bed", "worn_bed"):
        a = opaque(grade(raw(b)))
        block(b + "_head", a[:32])
        block(b + "_foot", a[32:])
    iron = opaque(seamless(grade(raw("rusted_iron"))))
    block("rusted_iron", iron)
    dark = iron.copy()
    dark[..., :3] *= 0.55
    block("rusted_iron_dark", dark)
    block("abyssal_rock", opaque(seamless(grade(raw("abyssal_rock"), 0.7, 0.85))))
    for n in ("chem_stand", "dust_web", "dead_roots"):
        a = grade(raw(n), 0.9, 0.95)
        a[..., 3] = np.where(a[..., 3] > 90, 255, 0)
        block(n, a)
    # antenna rod (lightning rod model: knob UV 0..4 x 0..4, rod UV 0..2 x 4..16, in 16ths): brighter rusted iron
    rod = iron.copy()
    rod[..., :3] = np.clip(rod[..., :3] * 1.25 + np.array([10, 4, 0]), 0, 255)
    block("antenna_rod", rod, note="procedural from the PixelLab rusted iron; tools/assets/make_corridor_copies.py")
    # chain (vanilla chain model: plane A uses UV x 0..3, plane B x 3..6, in 16ths -> 0..6 and 6..12 here)
    chain = np.zeros((32, 32, 4))
    dark, body, high = np.array([38.0, 28, 24]), np.array([104.0, 64, 42]), np.array([150.0, 98, 62])
    grain = gaussian_filter(np.random.default_rng(11).standard_normal((32, 32)), 0.8, mode="wrap") * 10

    def ring(x0, cy):
        # an oval link 6 px wide and 12 px tall: dark rim, rust body, a highlight on the left, hollow middle
        for y in range(cy - 6, cy + 6):
            for x in range(x0, x0 + 6):
                if cy - 4 <= y < cy + 4 and x0 + 2 <= x < x0 + 4:
                    continue
                if (y in (cy - 6, cy + 5)) and x in (x0, x0 + 5):
                    continue   # round the corners
                yy = y % 32
                rim = x in (x0, x0 + 5) or y in (cy - 6, cy + 5) or (cy - 4 <= y < cy + 4 and x in (x0 + 1, x0 + 4) and False)
                c = dark if rim else (high if x == x0 + 1 else body)
                chain[yy, x, :3] = c + grain[yy, x]
                chain[yy, x, 3] = 255
    for cy in (8, 24):
        ring(0, cy)
    for cy in (0, 16):
        ring(6, cy)
    block("rusted_chain", chain, note="procedural, colours from the PixelLab rusted iron; tools/assets/make_corridor_copies.py")
    a = grade(raw("rusted_chain_item"), 0.9, 0.95)
    save(os.path.join(ITEM, "rusted_chain.png"), a)
    # lamps (replace the old 16x16 placeholders); flood lamp housing graded like the metal
    for n in ("industrial_lamp", "sodium_lamp", "emergency_lamp", "flood_lamp_front"):
        block(n, opaque(raw(n)), note="PixelLab generation (pixflux 32x32); tools/assets/make_corridor_copies.py")
    block("flood_lamp_side", opaque(grade(raw("flood_lamp_side"))))
    # glass pane edge at 32x32, from the pane's own frame colour
    pane = np.asarray(Image.open(os.path.join(TEX, "grimy_glass_pane.png")).convert("RGBA")).astype(float)
    frame = pane[:, :2, :3].reshape(-1, 3).mean(0)
    edge = np.zeros((32, 32, 4))
    edge[..., :3] = frame + gaussian_filter(np.random.default_rng(7).standard_normal((32, 32)), 1, mode="wrap")[..., None] * 6
    edge[..., 3] = 255
    block("grimy_glass_pane_top", edge, note="procedural 32x32 (frame colour of grimy_glass_pane); tools/assets/make_corridor_copies.py")
    # spawn eggs (drawn, untinted: IntersticeClient registers a white colour provider)
    for sp in ("adherent", "crawler", "burden", "clinger", "hollow", "watcher", "fractured"):
        save(os.path.join(ITEM, f"{sp}_spawn_egg.png"), raw(f"{sp}_spawn_egg"), note="PixelLab generation (pixflux 32x32)")
    print("textures ok")


def water():
    base = raw("water_base")[..., :3].mean(-1)
    base = (base - base.mean()) / (base.std() + 1e-6)
    base = gaussian_filter(base, 0.6, mode="wrap")
    base = seamless(np.dstack([base, base, base, np.ones_like(base)]))[..., 0]

    def frame(img):
        g = np.clip(178 + img * 22, 0, 255)
        return np.dstack([g, g, g, np.full_like(g, 190)])
    frames = []
    yy, xx = np.mgrid[0:32, 0:32]
    for i in range(32):
        t = i / 32 * 2 * np.pi
        a = np.roll(np.roll(base, i, 1), i // 2, 0)
        b = np.roll(np.roll(base.T, -i, 0), 16, 1)
        mix = 0.5 + 0.5 * np.sin(t)
        warp = np.sin((xx + yy) / 32 * 2 * np.pi + t) * 0.25
        frames.append(frame(a * mix + b * (1 - mix) + warp))
    still = np.concatenate(frames, 0)
    big = np.kron(base, np.ones((2, 2)))
    flow = np.concatenate([frame(np.roll(big, 2 * i, 0)) for i in range(32)], 0)
    tex = os.path.join(PACK, "assets", "minecraft", "textures", "block")
    note = "procedural animation of a PixelLab water swatch; tools/assets/make_corridor_copies.py"
    save(os.path.join(tex, "water_still.png"), still, note)
    save(os.path.join(tex, "water_flow.png"), flow, note)
    for n in ("water_still", "water_flow"):
        with open(os.path.join(tex, n + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"animation": {"frametime": 2}}, f)
    with open(os.path.join(PACK, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"pack": {"pack_format": 34, "description": "THE INTERSTICE: the Corridor's 32x32 water"}}, f, indent=2)
    icon = Image.fromarray(np.clip(frames[0][..., :3] * np.array([0.45, 0.6, 0.62]), 0, 255).astype(np.uint8), "RGB").resize((64, 64), Image.NEAREST)
    icon.save(os.path.join(PACK, "pack.png"))
    print("water ok")


# ---------------------------------------------------------------- models and blockstates
def models():
    names = {}
    # doors
    for d, v in (("worn_door", "spruce_door"), ("rotten_door", "dark_oak_door"), ("pale_door", "birch_door"), ("steel_door", "iron_door")):
        mapping = {}
        for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                     "top_left", "top_left_open", "top_right", "top_right_open"):
            model(f"{d}_{part}", f"minecraft:block/door_{part}", {"bottom": T + d + "_bottom", "top": T + d + "_top"})
            mapping[f"{v}_{part}"] = f"{d}_{part}"
        w(f"blockstates/{d}.json", remap(vanilla_blockstate(v), mapping))
        item_flat(d, f"interstice:item/{d}")
    # ladder, button, pressure plate
    model("rusted_ladder", "minecraft:block/ladder", {"texture": T + "rusted_ladder", "particle": T + "rusted_ladder"})
    w("blockstates/rusted_ladder.json", remap(vanilla_blockstate("ladder"), {"ladder": "rusted_ladder"}))
    item_flat("rusted_ladder", T + "rusted_ladder")
    for kind in ("button", "button_pressed", "button_inventory"):
        model("concrete_" + kind, "minecraft:block/" + kind, {"texture": T + "concrete_panel"})
    w("blockstates/concrete_button.json", remap(vanilla_blockstate("stone_button"),
                                                 {"stone_button": "concrete_button", "stone_button_pressed": "concrete_button_pressed"}))
    item_block("concrete_button", "concrete_button_inventory")
    model("worn_pressure_plate", "minecraft:block/pressure_plate_up", {"texture": T + "floorboards"})
    model("worn_pressure_plate_down", "minecraft:block/pressure_plate_down", {"texture": T + "floorboards"})
    w("blockstates/worn_pressure_plate.json", remap(vanilla_blockstate("spruce_pressure_plate"), {
        "spruce_pressure_plate": "worn_pressure_plate", "spruce_pressure_plate_down": "worn_pressure_plate_down"}))
    item_block("worn_pressure_plate")
    # supply locker: front faces the facing direction
    for suffix, front in (("", "supply_locker_front"), ("_open", "supply_locker_front_open")):
        model("supply_locker" + suffix, "minecraft:block/orientable_with_bottom",
              {"front": T + front, "side": T + "supply_locker_side", "top": T + "supply_locker_top", "bottom": T + "supply_locker_top"})
        model("supply_locker_vertical" + suffix, "minecraft:block/cube_bottom_top",
              {"top": T + front, "side": T + "supply_locker_side", "bottom": T + "supply_locker_top"})
    variants = {}
    for open_ in ("false", "true"):
        sfx = "_open" if open_ == "true" else ""
        for f, y in HORIZONTAL_Y.items():
            variants[f"facing={f},open={open_}"] = {"model": T + "supply_locker" + sfx, "y": y}
        variants[f"facing=up,open={open_}"] = {"model": T + "supply_locker_vertical" + sfx}
        variants[f"facing=down,open={open_}"] = {"model": T + "supply_locker_vertical" + sfx, "x": 180}
    w("blockstates/supply_locker.json", {"variants": variants})
    item_block("supply_locker")
    # supply drum: the barrel's structure
    model("supply_drum", "minecraft:block/cube_bottom_top", {"top": T + "supply_drum_top", "side": T + "supply_drum_side", "bottom": T + "supply_drum_bottom"})
    model("supply_drum_open", "minecraft:block/cube_bottom_top", {"top": T + "supply_drum_top_open", "side": T + "supply_drum_side", "bottom": T + "supply_drum_bottom"})
    w("blockstates/supply_drum.json", remap(vanilla_blockstate("barrel"), {"barrel": "supply_drum", "barrel_open": "supply_drum_open"}))
    item_block("supply_drum")
    # beds: mattress on a dark metal frame, headboard on the head half
    for b in ("hospital_bed", "worn_bed"):
        for part in ("head", "foot"):
            side = {"texture": "#side", "uv": [0, 4, 16, 10]}
            els = [{"from": [0, 3, 0], "to": [16, 9, 16], "faces": {
                "up": {"texture": "#top"}, "down": {"texture": "#frame"},
                "north": dict(side), "south": dict(side), "east": dict(side), "west": dict(side)}}]
            legs = [(0, 0), (14, 0)] if part == "head" else [(0, 14), (14, 14)]
            for x, z in legs:
                els.append({"from": [x, 0, z], "to": [x + 2, 3, z + 2], "faces": {f: {"texture": "#frame"} for f in ("north", "south", "east", "west", "down")}})
            els.append({"from": [0, 1, 0], "to": [16, 3, 16], "faces": {f: {"texture": "#frame"} for f in ("north", "south", "east", "west", "down")}})
            if part == "head":
                els.append({"from": [0, 3, 0], "to": [16, 14, 1], "faces": {f: {"texture": "#frame"} for f in ("north", "south", "east", "west", "up")}})
            for e in els:
                for face in e["faces"].values():
                    face.setdefault("cullface", None)
                    face.pop("cullface")
            w(f"models/block/{b}_{part}.json", {"parent": "minecraft:block/block",
                                                 "textures": {"top": T + f"{b}_{part}", "side": T + f"{b}_side", "frame": T + "dark_metal",
                                                              "particle": T + f"{b}_side"}, "elements": els})
        variants = {}
        for f, y in HORIZONTAL_Y.items():
            for part in ("head", "foot"):
                variants[f"facing={f},part={part}"] = {"model": T + f"{b}_{part}", "y": y}
        w(f"blockstates/{b}.json", {"variants": variants})
        item_block(b, b + "_foot")
    # bookshelf, basin, stove, furnace
    model("rotten_bookshelf", "minecraft:block/cube_column", {"side": T + "rotten_bookshelf", "end": T + "floorboards"})
    single("rotten_bookshelf"); item_block("rotten_bookshelf")
    model("rusted_basin", "minecraft:block/cauldron", {"particle": T + "rusted_basin_side", "top": T + "rusted_iron", "bottom": T + "rusted_iron_dark",
                                                       "side": T + "rusted_basin_side", "inside": T + "rusted_iron_dark"})
    single("rusted_basin"); item_block("rusted_basin")
    for n in ("rusted_stove", "industrial_furnace"):
        model(n, "minecraft:block/orientable", {"front": T + n + "_front", "side": T + n + "_side", "top": T + n + "_top"})
        w(f"blockstates/{n}.json", {"variants": {f"facing={f}": {"model": T + n, "y": y} for f, y in HORIZONTAL_Y.items()}})
        item_block(n)
    # lab stand, roots, web: crossed planes
    for n in ("chem_stand", "dead_roots", "dust_web"):
        model(n, "minecraft:block/cross", {"cross": T + n})
        single(n)
        item_flat(n, T + n)
    # anvil
    model("scrap_anvil", "minecraft:block/template_anvil", {"top": T + "scrap_anvil_top", "body": T + "rusted_iron", "particle": T + "rusted_iron"})
    w("blockstates/scrap_anvil.json", remap(vanilla_blockstate("anvil"), {"anvil": "scrap_anvil"}))
    item_block("scrap_anvil")
    # hydraulic press: ram face towards the facing direction (rotations of the barrel)
    model("hydraulic_press", "minecraft:block/cube_bottom_top", {"top": T + "hydraulic_press_front", "side": T + "hydraulic_press_side", "bottom": T + "rusted_iron"})
    barrel = vanilla_blockstate("barrel")["variants"]
    w("blockstates/hydraulic_press.json", {"variants": {k.split(",")[0]: dict(v, model=T + "hydraulic_press")
                                                        for k, v in barrel.items() if k.endswith("open=false")}})
    item_block("hydraulic_press")
    # funnel (hopper silhouette), antenna (lightning rod silhouette), chain
    model("rusted_funnel", "minecraft:block/hopper", {"particle": T + "rusted_iron", "top": T + "rusted_funnel_top", "side": T + "rusted_iron",
                                                      "inside": T + "rusted_iron_dark"})
    single("rusted_funnel"); item_block("rusted_funnel")
    model("antenna_rod", "minecraft:block/lightning_rod", {"texture": T + "antenna_rod", "particle": T + "antenna_rod"})
    single("antenna_rod"); item_block("antenna_rod")
    model("rusted_chain", "minecraft:block/chain", {"all": T + "rusted_chain", "particle": T + "rusted_chain"})
    w("blockstates/rusted_chain.json", remap(vanilla_blockstate("chain"), {"chain": "rusted_chain"}))
    item_flat("rusted_chain", "interstice:item/rusted_chain")
    # flesh node, abyssal rock
    model("flesh_node", "minecraft:block/cube_bottom_top", {"top": T + "flesh_node_top", "side": T + "flesh_node_side", "bottom": T + "flesh_growth"})
    single("flesh_node"); item_block("flesh_node")
    model("abyssal_rock", "minecraft:block/cube_all", {"all": T + "abyssal_rock"})
    single("abyssal_rock"); item_block("abyssal_rock")
    # spawn eggs: drawn textures
    for sp in ("adherent", "crawler", "burden", "clinger", "hollow", "watcher", "fractured"):
        item_flat(f"{sp}_spawn_egg", f"interstice:item/{sp}_spawn_egg")
    # ladder is climbable
    tag = os.path.join(ROOT, "src", "main", "resources", "data", "minecraft", "tags", "block", "climbable.json")
    os.makedirs(os.path.dirname(tag), exist_ok=True)
    with open(tag, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"replace": False, "values": ["interstice:rusted_ladder"]}, f, indent=2)
    # names
    p = os.path.join(A, "lang", "en_us.json")
    lang = json.load(open(p, encoding="utf-8"))
    for n in ("worn_door", "rotten_door", "pale_door", "steel_door", "rusted_ladder", "concrete_button", "worn_pressure_plate",
              "supply_locker", "supply_drum", "hospital_bed", "worn_bed", "rotten_bookshelf", "rusted_basin", "rusted_stove",
              "industrial_furnace", "chem_stand", "scrap_anvil", "hydraulic_press", "rusted_funnel", "antenna_rod", "rusted_chain",
              "dust_web", "dead_roots", "flesh_node", "abyssal_rock"):
        lang[f"block.interstice.{n}"] = n.replace("_", " ").title()
    lang["block.interstice.chem_stand"] = "Lab Stand"
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(lang, f, indent=2)
        f.write("\n")
    print("models ok")


if __name__ == "__main__":
    RAW = sys.argv[1]
    textures()
    water()
    models()
