#!/usr/bin/env python3
"""
Generates the built-in tool skins: 16x16 pixel-art item textures for swords, pickaxes, axes, shovels and hoes
in several colour themes, plus the item models and the item definitions that switch to them.

How the switch works (all vanilla item rendering, no custom drawing code):
  the game adds a custom_model_data string (the skin id) to the tool you are holding, and the item definition
  of every vanilla tool (assets/minecraft/items/<material>_<tool>.json) picks the skin's model for that string,
  falling back to the normal model for everything else.

Run from the project root:   python3 tools/generate_tool_skins.py
"""
import json, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets")
MOD = os.path.join(ROOT, "modulesmod")
MC = os.path.join(ROOT, "minecraft")

TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
MATERIALS = ["wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# name, accent for the menu, blade (hi, main, lo), trim (hi, main, lo), handle (hi, main, lo), outline
SKINS = [
    ("flame", "Flame", "#ff7a2f", ("#ffd27a", "#ff7a2f", "#c2361c"), ("#ffe9a8", "#ffb43a", "#c2761a"), ("#9a5a3a", "#6f3d22", "#472511"), "#2b0f0a"),
    ("frost", "Frost", "#7fe3ff", ("#e8fbff", "#8fe6ff", "#3a9ccf"), ("#ffffff", "#c6f3ff", "#7ac0e0"), ("#8aa4b8", "#5f7a90", "#3c5266"), "#0d2233"),
    ("emerald", "Emerald", "#37d67a", ("#9dffc4", "#37d67a", "#14804a"), ("#d8ffe8", "#7be8a8", "#3aa86e"), ("#a07a4a", "#755331", "#4a3320"), "#0a2a18"),
    ("amethyst", "Amethyst", "#b06cff", ("#e6c6ff", "#b06cff", "#6a2db0"), ("#f3e4ff", "#d3a8ff", "#9a64d6"), ("#8f6aa8", "#684a80", "#42285a"), "#1c0b33"),
    ("shadow", "Shadow", "#7b5cff", ("#6b6f88", "#34374b", "#16182a"), ("#9d7bff", "#6a4cd6", "#3a2690"), ("#4a4660", "#322f45", "#1e1c2c"), "#07070f"),
    ("candy", "Candy", "#ff8fc7", ("#ffe0f0", "#ff8fc7", "#d9488f"), ("#ffffff", "#ffd0e6", "#f09ac0"), ("#e8c9a0", "#c9a070", "#a07a4a"), "#4a1030"),
    ("sunstone", "Sunstone", "#ffd24a", ("#fff2a8", "#ffd24a", "#d9951a"), ("#ffffff", "#ffe98a", "#e0b030"), ("#b88a5a", "#8a5f35", "#5a3a1f"), "#3a2406"),
    ("ocean", "Ocean", "#3ec6c1", ("#a8fff4", "#3ec6c1", "#1d7f9c"), ("#e0fffb", "#7be0d8", "#3a9fa8"), ("#7a9ab0", "#58768d", "#38566b"), "#082a33"),
]


def shape(tool):
    """pixel sets for head / trim / handle, in the usual tool diagonal (handle bottom-left, head top-right)."""
    head, trim, handle = set(), set(), set()
    for y in range(16):
        for x in range(16):
            t, d = x - y, (x + y) - 15
            if tool == "sword":
                if abs(d) <= max(0, min(1, 14 - t)) and -2 <= t <= 15:
                    head.add((x, y))
                if -5 <= t <= -3 and abs(d) <= 4:
                    trim.add((x, y))
                if -12 <= t <= -6 and d == 0:
                    handle.add((x, y))
                if -14 <= t <= -13 and abs(d) <= 1:
                    trim.add((x, y))
            else:
                if d == 0 and -14 <= t <= (3 if tool == "shovel" else 9):
                    handle.add((x, y))
                if tool == "pickaxe" and abs(d) <= 8:
                    tc = 9 - d * d / 9.0
                    if tc - 1.3 <= t <= tc + 1.3:
                        head.add((x, y))
                if tool == "axe" and 3 <= t <= 12:
                    left = min(7, t - 2, 14 - t)
                    if -left <= d <= 1:
                        head.add((x, y))
                if tool == "shovel" and 4 <= t <= 13:
                    if abs(d) <= (2 if t in (4, 13) else 3):
                        head.add((x, y))
                if tool == "hoe":
                    if 8 <= t <= 10 and -7 <= d <= 1:
                        head.add((x, y))
                    if 5 <= t <= 7 and -7 <= d <= -5:
                        head.add((x, y))
    # the trim colour also marks a thin edge on tool heads
    if tool != "sword":
        for (x, y) in list(head):
            if (x - 1, y - 1) not in head:
                trim.add((x, y))
        trim -= handle
    head -= trim
    return head, trim, handle


def render(tool, skin):
    _, _, _, blade, tr, hd, outline = skin[0], skin[1], skin[2], skin[3], skin[4], skin[5], skin[6]
    head, trim, handle = shape(tool)
    union = head | trim | handle
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def tone(x, y):
        if (x - 1, y) not in union or (x, y - 1) not in union:
            return 0   # top-left edge: light
        if (x + 1, y) not in union or (x, y + 1) not in union:
            return 2   # bottom-right edge: dark
        return 1

    for layer, pal in ((head, blade), (trim, tr), (handle, hd)):
        for (x, y) in layer:
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = rgb(pal[tone(x, y)]) + (255,)
    for y in range(16):
        for x in range(16):
            if (x, y) in union:
                continue
            if any((x + dx, y + dy) in union for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = rgb(outline) + (255,)
    return img


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    cases_by_tool = {t: [] for t in TOOLS}
    catalogue = []
    for skin in SKINS:
        sid, name, accent = skin[0], skin[1], skin[2]
        catalogue.append({"id": sid, "name": name, "accent": accent})
        for tool in TOOLS:
            tex = f"skin_{sid}_{tool}"
            path = os.path.join(MOD, "textures", "item", tex + ".png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            render(tool, skin).save(path)
            write(os.path.join(MOD, "models", "item", tex + ".json"), json.dumps(
                {"parent": "minecraft:item/handheld", "textures": {"layer0": "modulesmod:item/" + tex}}, indent=1))
            cases_by_tool[tool].append({"when": sid, "model": {"type": "minecraft:model", "model": "modulesmod:item/" + tex}})

    for mat in MATERIALS:
        for tool in TOOLS:
            name = f"{mat}_{tool}"
            write(os.path.join(MC, "items", name + ".json"), json.dumps({"model": {
                "type": "minecraft:select", "property": "minecraft:custom_model_data",
                "fallback": {"type": "minecraft:model", "model": "minecraft:item/" + name},
                "cases": cases_by_tool[tool]}}, indent=1))

    write(os.path.join(MOD, "cosmetics", "tool_skins.json"), json.dumps({"skins": catalogue}, indent=1))

    # contact sheet for a quick look (not shipped in the jar)
    sheet = Image.new("RGBA", (16 * 5 * 6, 16 * len(SKINS) * 6), (60, 60, 70, 255))
    for r, skin in enumerate(SKINS):
        for c, tool in enumerate(TOOLS):
            im = render(tool, skin).resize((96, 96), Image.NEAREST)
            sheet.paste(im, (c * 96, r * 96), im)
    sheet.save("/tmp/tool_skins_sheet.png")
    print("wrote", len(SKINS), "skins")


if __name__ == "__main__":
    main()
