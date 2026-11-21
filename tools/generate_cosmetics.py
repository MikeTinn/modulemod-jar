#!/usr/bin/env python3
"""
Generates the built-in cosmetics: one shared model file + one texture per cosmetic.
All models and textures are made from scratch here (blocky, vanilla-style) - nothing is copied from anywhere.

Run from the project root:   python3 tools/generate_cosmetics.py
Writes:  src/main/resources/assets/modulesmod/cosmetics/cosmetics.json
         src/main/resources/assets/modulesmod/textures/cosmetics/<id>.png

Model space is the player's: 1 unit = 1/16 block, y points DOWN, the player's front is -z.
  HEAD attach: origin = centre of the head's bottom (head box is x -4..4, y -8..0, z -4..4)
  BODY attach: origin = top centre of the torso (torso box is x -4..4, y 0..12, z -2..2, back is +z)

Colour choices: a cosmetic with a tint colour has "tinted" cubes (painted white-ish with shading, the game
multiplies them with the colour you pick) and "fixed" cubes (keep their own colours: gems, trim, bones...).
Parts named right_* / left_* flap (wings), parts named cape* sway, a part that is rotated is its own part.
"""
import json, math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "modulesmod")
D = math.pi / 180.0


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


class Cube:
    def __init__(self, pos, size, color, inflate=0.0, details=None, fixed=False):
        self.pos, self.size, self.color = tuple(pos), tuple(size), rgb(color)
        self.inflate = inflate
        self.details = details or []   # (face, x, y, w, h, "#color") in face pixels
        self.fixed = fixed
        self.tint = False
        self.uv = (0, 0)

    def dims(self):
        w, h, d = (max(1, math.ceil(v)) for v in self.size)
        return w, h, d

    def rect_size(self):
        w, h, d = self.dims()
        return 2 * d + 2 * w, d + h


class Part:
    def __init__(self, name, pivot, rot, cubes):
        self.name, self.pivot, self.rot, self.cubes = name, tuple(pivot), tuple(rot), cubes


class Cos:
    def __init__(self, id, name, category, attach, anim, accent, parts, amp=1.0, tint=None):
        self.id, self.name, self.category, self.attach = id, name, category, attach
        self.anim, self.accent, self.parts, self.amp = anim, accent, parts, amp
        self.tint = tint                      # hex of the default colour, or None = not recolourable
        for p in parts:
            for c in p.cubes:
                c.tint = bool(tint) and not c.fixed


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def pack(cubes):
    """shelf-pack every cube's UV box; returns (texW, texH)."""
    for tw, th in [(64, 64), (128, 64), (128, 128), (256, 128), (256, 256), (512, 256)]:
        x = y = shelf = 0
        ok = True
        for c in sorted(cubes, key=lambda c: -c.rect_size()[1]):
            rw, rh = c.rect_size()
            if x + rw > tw:
                x, y, shelf = 0, y + shelf, 0
            if y + rh > th or rw > tw:
                ok = False
                break
            c.uv = (x, y)
            x += rw
            shelf = max(shelf, rh)
        if ok:
            return tw, th
    raise SystemExit("cosmetic too big for a texture")


def paint(cos, tw, th):
    img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(cos.id)
    tone = {"top": 1.12, "bottom": 0.78, "right": 0.9, "left": 0.9, "front": 1.0, "back": 0.96}
    ref = rgb(cos.tint) if cos.tint else None
    # tinted cubes are painted in light grey: the colour you pick multiplies it
    base_grey = 245

    def pixel(c, color, f):
        if c.tint:
            g = min(1.0, lum(color) / max(1.0, lum(ref)))
            return shade((base_grey, base_grey, base_grey), g * f) + (255,)
        return shade(color, f) + (255,)

    for part in cos.parts:
        for c in part.cubes:
            w, h, d = c.dims()
            rects = face_rects(c.uv[0], c.uv[1], w, h, d)
            for face, (fx, fy, fw, fh) in rects.items():
                for yy in range(fh):
                    for xx in range(fw):
                        f = tone[face] * (1 + rnd.uniform(-0.05, 0.05))
                        if fw > 2 and fh > 2 and (xx in (0, fw - 1) or yy in (0, fh - 1)):
                            f *= 0.86
                        px[fx + xx, fy + yy] = pixel(c, c.color, f)
            for face, dx, dy, dw, dh, col in c.details:
                fx, fy, fw, fh = rects[face]
                for yy in range(dy, min(dy + dh, fh)):
                    for xx in range(dx, min(dx + dw, fw)):
                        px[fx + xx, fy + yy] = pixel(c, rgb(col), 1.0)
    img.save(os.path.join(ROOT, "textures", "cosmetics", cos.id + ".png"))


# ------------------------------------------------------------------ geometry helpers

class Chain(list):
    """a list of Parts that also knows where it ends and where each segment starts"""
    end = (0.0, 0.0, 0.0)
    joints = ()


def chain(prefix, start, segments, color, side=1, fixed=False):
    """
    A bendy thing made of straight segments (horns, antlers, hat tips...). Every segment is its own part
    because only parts can be rotated. segments = (length, thickness, roll_deg, pitch_deg); the direction
    accumulates, so a horn curves. side=-1 mirrors it. Segments point 'up' (-y) at roll 0.
    Returns a list of Parts.
    """
    parts = []
    x, y, z = start
    roll_total, pitch_total = 0.0, 0.0
    for i, (ln, th, roll, pitch) in enumerate(segments):
        roll_total += roll * D * side
        pitch_total += pitch * D
        parts.append(Part(f"{prefix}{i}", (x, y, z), (pitch_total, 0, roll_total),
                          [Cube((-th / 2, -ln, -th / 2), (th, ln, th), color, fixed=fixed)]))
        # the end of this segment is where the next one starts (Rz then Rx, Z applied last)
        vx, vy, vz = 0.0, -ln, 0.0
        vy, vz = vy * math.cos(pitch_total) - vz * math.sin(pitch_total), vy * math.sin(pitch_total) + vz * math.cos(pitch_total)
        vx, vy = vx * math.cos(roll_total) - vy * math.sin(roll_total), vx * math.sin(roll_total) + vy * math.cos(roll_total)
        x, y, z = x + vx, y + vy, z + vz
    out = Chain(parts)
    out.end = (x, y, z)
    out.joints = [p.pivot for p in parts]
    return out


def ring(prefix, y, radius, height, thick, color, n=8, fixed=False):
    """A flat ring seen from above, made of n straight pieces (an octagon for n=8)."""
    parts = []
    seg = 2 * radius * math.tan(math.pi / n) + 0.4
    for i in range(n):
        phi = 2 * math.pi * i / n
        px, pz = radius * math.cos(phi), radius * math.sin(phi)
        parts.append(Part(f"{prefix}{i}", (px, y, pz), (0, -(phi + math.pi / 2), 0),
                          [Cube((-seg / 2, -height / 2, -thick / 2), (seg, height, thick), color, fixed=fixed)]))
    return parts


def fan_wing(prefix, side, feathers, shoulder=(2.0, 2.0, 2.5), fold=0.5):
    """
    feathers = (length, width, angle_deg, colour, depth_offset, fixed). They all start at the shoulder and
    fan out like fingers: angle 0 = straight out, positive = up. side -1 = the player's right wing.
    """
    parts = []
    for i, (ln, wd, ang, color, zoff, fixed) in enumerate(feathers):
        if side < 0:
            pos, roll, yaw = (-ln, -wd / 2, 0), ang * D, fold
        else:
            pos, roll, yaw = (0, -wd / 2, 0), -ang * D, -fold
        parts.append(Part(f"{prefix}_{i}", (side * shoulder[0], shoulder[1], shoulder[2] + zoff), (0, yaw, roll),
                          [Cube(pos, (ln, wd, 1.0), color, fixed=fixed)]))
    return parts


def membrane_wing(prefix, side, fingers, membrane_color, bone_color, arm=True, thick=0.6, fold=0.5):
    """Bat / dragon wing: bone fingers with skin panels in between. fingers = (length, angle_deg)."""
    feathers = []
    for ln, ang in fingers:
        feathers.append((ln, 1.1, ang, bone_color, 0.0, True))
    parts = fan_wing(prefix, side, feathers, fold=fold)
    sh = (2.0, 2.0, 2.5)
    for i in range(len(fingers) - 1):
        (l1, a1), (l2, a2) = fingers[i], fingers[i + 1]
        mid = (a1 + a2) / 2
        ln = 0.82 * min(l1, l2)
        gap = abs(a1 - a2) * D * ln * 0.95
        wd = max(2.2, gap)
        if side < 0:
            pos, roll, yaw = (-ln, -wd / 2, 0), mid * D, fold
        else:
            pos, roll, yaw = (0, -wd / 2, 0), -mid * D, -fold
        parts.append(Part(f"{prefix}_m{i}", (side * sh[0], sh[1], sh[2] + 0.25), (0, yaw, roll),
                          [Cube(pos, (ln, wd, thick), membrane_color)]))
    if arm:
        a = fingers[0][1] * 0.7
        ln = 6
        if side < 0:
            pos, roll, yaw = (-ln, -0.9, 0), a * D, fold
        else:
            pos, roll, yaw = (0, -0.9, 0), -a * D, -fold
        parts.append(Part(f"{prefix}_arm", (side * sh[0], sh[1], sh[2] - 0.2), (0, yaw, roll),
                          [Cube(pos, (ln, 1.8, 1.6), bone_color, fixed=True)]))
    return parts


def both(builder):
    return builder(-1) + builder(1)


def lerp_color(a, b, t):
    a, b = rgb(a), rgb(b)
    return "#%02x%02x%02x" % tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def pet(id, name, accent, cubes, pivot=(-6.5, 0, 0), amp=1.0):
    return Cos(id, name, "PET", "BODY", "bob", accent, [Part("pet", pivot, (0, 0, 0), cubes)], amp)




def P1(name, cubes):
    return Part(name, (0, 0, 0), (0, 0, 0), cubes)


def hat(id, name, accent, parts, tint=None, amp=1.0, anim="none"):
    return Cos(id, name, "HAT", "HEAD", anim, accent, parts, amp, tint)


def mirror_x(cubes):
    """the same cubes on the other side of the head/body (x flipped)."""
    out = []
    for c in cubes:
        x, y, z = c.pos
        w, h, d = c.size
        out.append(Cube((-x - w, y, z), (w, h, d), "#%02x%02x%02x" % c.color, c.inflate, c.details, c.fixed))
    return out


# ------------------------------------------------------------------ hats
HATS = []

HATS.append(hat("top_hat", "Top Hat", "#2b2b33", [P1("hat", [
    Cube((-6, -9, -6), (12, 1, 12), "#25252d"),
    Cube((-4, -16, -4), (8, 7, 8), "#25252d"),
    Cube((-4.4, -16.7, -4.4), (8.8, 0.8, 8.8), "#2e2e38"),
    Cube((-4.2, -12, -4.2), (8.4, 2, 8.4), "#b3202a", fixed=True),
    Cube((-1.2, -11.8, -4.5), (2.4, 1.6, 0.5), "#e8c040", fixed=True),
    Cube((-0.6, -11.4, -4.6), (1.2, 0.8, 0.3), "#2a2a2a", fixed=True),
])], tint="#25252d"))

GOLD = "#ffcc33"
crown = [Cube((-4.5, -11, -4.5), (9, 3, 1.2), GOLD), Cube((-4.5, -11, 3.3), (9, 3, 1.2), GOLD),
         Cube((-4.5, -11, -3.3), (1.2, 3, 6.6), GOLD), Cube((3.3, -11, -3.3), (1.2, 3, 6.6), GOLD)]
for x in (-3.3, 0, 3.3):
    for z in (-4.5, 3.3):
        crown += [Cube((x - 1.2, -13, z), (2.4, 2, 1.2), GOLD), Cube((x - 0.6, -15, z), (1.2, 2, 1.2), GOLD)]
for zc in (-1.8, 1.8):
    for x in (-4.5, 3.3):
        crown += [Cube((x, -13, zc - 1.2), (1.2, 2, 2.4), GOLD), Cube((x, -15, zc - 0.6), (1.2, 2, 1.2), GOLD)]
crown += [Cube((-0.6, -14.6, -4.6), (1.2, 1.2, 0.4), "#e03a3a", fixed=True),
          Cube((-0.6, -14.6, 4.0), (1.2, 1.2, 0.4), "#3a8bff", fixed=True),
          Cube((-4.6, -14.4, -0.6), (0.4, 1.2, 1.2), "#2fbf5a", fixed=True),
          Cube((4.2, -14.4, -0.6), (0.4, 1.2, 1.2), "#2fbf5a", fixed=True),
          Cube((-3.9, -10.1, -4.7), (1, 1, 0.3), "#4a9aff", fixed=True),
          Cube((2.9, -10.1, -4.7), (1, 1, 0.3), "#e03a3a", fixed=True),
          Cube((-0.5, -10.1, -4.7), (1, 1, 0.3), "#ffffff", fixed=True)]
HATS.append(hat("crown", "Gold Crown", "#ffd24a", [P1("hat", crown)], tint=GOLD))

wiz_tip = chain("tip", (0, -18, 0), [(3.2, 4.8, 0, -12), (3.0, 3.4, 0, -22), (2.6, 2.0, 0, -28)], "#6a4de0")
wiz = Part("hat", (0, 0, 0), (0, 0, 0), [
    Cube((-7, -8.6, -7), (14, 0.9, 14), "#3d2a7a"),
    Cube((-5.5, -9.4, -5.5), (11, 0.8, 11), "#3d2a7a"),
    Cube((-4.5, -12, -4.5), (9, 3, 9), "#4b33a0"),
    Cube((-3.8, -15, -3.8), (7.6, 3, 7.6), "#5a3fbd"),
    Cube((-3.2, -18, -3.2), (6.4, 3, 6.4), "#6a4de0"),
    Cube((-4.8, -10.8, -4.8), (9.6, 1.3, 9.6), "#ffd24a", fixed=True),
    Cube((-1.1, -10.9, -5.0), (2.2, 1.5, 0.4), "#fff2a8", fixed=True),
    Cube((-0.5, -10.5, -5.1), (1, 0.7, 0.3), "#6a4a10", fixed=True),
    Cube((-2.4, -14, -3.95), (1, 1, 0.3), "#ffe566", fixed=True),
    Cube((1.6, -16.4, -3.4), (1, 1, 0.3), "#ffe566", fixed=True),
    Cube((-0.4, -12.8, -4.7), (0.8, 0.8, 0.3), "#ffe566", fixed=True),
])
HATS.append(hat("wizard_hat", "Wizard Hat", "#8a5cff", [wiz] + list(wiz_tip), tint="#4b33a0"))

pirate = [
    P1("hat", [
        Cube((-4.4, -11.5, -4.4), (8.8, 3.5, 8.8), "#25252d"),
        Cube((-4.6, -9.2, -4.6), (9.2, 0.9, 9.2), "#e8c040", fixed=True),
        Cube((-1.5, -11.2, -4.6), (3, 1.8, 0.3), "#f4f4f4", fixed=True),
        Cube((-1.1, -10.6, -4.7), (0.8, 0.7, 0.2), "#15151a", fixed=True),
        Cube((0.3, -10.6, -4.7), (0.8, 0.7, 0.2), "#15151a", fixed=True),
        Cube((-2.6, -9.0, -4.7), (5.2, 0.5, 0.2), "#f4f4f4", fixed=True),
    ]),
    Part("front", (0, -8.5, -4.4), (-0.6, 0, 0), [Cube((-4.8, -0.8, -5), (9.6, 0.8, 5), "#25252d"),
                                                   Cube((-4.9, -0.9, -5.1), (9.8, 0.4, 0.5), "#e8c040", fixed=True)]),
    Part("back", (0, -8.5, 4.4), (0.35, 0, 0), [Cube((-4.8, -0.8, 0), (9.6, 0.8, 4.5), "#25252d")]),
    Part("left", (4.4, -8.5, 0), (0, 0, -0.6), [Cube((0, -0.8, -4.6), (4.8, 0.8, 9.2), "#25252d")]),
    Part("right", (-4.4, -8.5, 0), (0, 0, 0.6), [Cube((-4.8, -0.8, -4.6), (4.8, 0.8, 9.2), "#25252d")]),
]
HATS.append(hat("pirate_hat", "Pirate Hat", "#3a3a46", pirate, tint="#25252d"))

santa_tip = chain("tip", (0, -9.5, 0.3), [(3.6, 8.0, 0, -10), (3.4, 6.4, 0, -18), (3.0, 4.6, 0, -30), (2.6, 3.2, 0, -38)], "#d6303b")
ex, ey, ez = santa_tip.end
santa = [Part("trim", (0, 0, 0), (0, 0, 0), [Cube((-4.8, -9.6, -4.8), (9.6, 2, 9.6), "#ffffff", fixed=True)]),
         Part("pom", (ex, ey - 0.6, ez + 0.2), (0, 0, 0), [Cube((-1.7, -1.7, -1.7), (3.4, 3.4, 3.4), "#ffffff", fixed=True)])]
HATS.append(hat("santa_hat", "Santa Hat", "#d6303b", list(santa_tip) + santa, tint="#d6303b"))

hr = chain("hornr", (-4.6, -10, 0), [(2.6, 2.2, 70, 0), (2.6, 1.9, -40, 0), (2.4, 1.5, -35, 0), (2.0, 1.0, -30, 0)], "#f1e9d2", side=-1, fixed=True)
hl = chain("hornl", (4.6, -10, 0), [(2.6, 2.2, 70, 0), (2.6, 1.9, -40, 0), (2.4, 1.5, -35, 0), (2.0, 1.0, -30, 0)], "#f1e9d2", side=1, fixed=True)
viking = P1("helm", [
    Cube((-4.5, -12, -4.5), (9, 4.5, 9), "#8a8d96"),
    Cube((-4.7, -9.2, -4.7), (9.4, 1.4, 9.4), "#5a5d66", fixed=True),
    Cube((-0.7, -9, -4.9), (1.4, 4.2, 0.9), "#8a8d96"),
    Cube((-4.8, -9.0, -2.2), (0.5, 0.7, 0.7), "#d9d9df", fixed=True), Cube((4.3, -9.0, -2.2), (0.5, 0.7, 0.7), "#d9d9df", fixed=True),
    Cube((-4.8, -9.0, 1.5), (0.5, 0.7, 0.7), "#d9d9df", fixed=True), Cube((4.3, -9.0, 1.5), (0.5, 0.7, 0.7), "#d9d9df", fixed=True),
    Cube((-0.5, -13, -4.5), (1, 1.2, 9), "#6a6d76"),
])
HATS.append(hat("viking_helmet", "Viking Helmet", "#b9bcc6", [viking] + list(hr) + list(hl), tint="#8a8d96"))

HATS.append(hat("chef_hat", "Chef Hat", "#f4f4f4", [P1("hat", [
    Cube((-4.3, -11, -4.3), (8.6, 3, 8.6), "#f4f4f4"),
    Cube((-5, -15, -5), (10, 4, 10), "#f4f4f4"),
    Cube((-4.2, -18, -4.2), (8.4, 3, 8.4), "#f4f4f4"),
    Cube((-5.6, -14.4, -1.2), (1.2, 3, 2.4), "#f4f4f4"), Cube((4.4, -14.4, -1.2), (1.2, 3, 2.4), "#f4f4f4"),
    Cube((-1.2, -14.4, -5.6), (2.4, 3, 1.2), "#f4f4f4"), Cube((-1.2, -14.4, 4.4), (2.4, 3, 1.2), "#f4f4f4"),
    Cube((-4.5, -9.4, -4.5), (9, 0.8, 9), "#d8d8d8", fixed=True),
])], tint="#f4f4f4"))

HATS.append(hat("cowboy_hat", "Cowboy Hat", "#b0834f", [
    P1("hat", [Cube((-4, -8.8, -6), (8, 0.8, 12), "#b0834f"),
               Cube((-4, -13.2, -4), (8, 4.4, 8), "#b0834f"),
               Cube((-3.2, -13.9, -3.2), (6.4, 0.8, 6.4), "#a47848"),
               Cube((-4.2, -10.8, -4.2), (8.4, 1.4, 8.4), "#4a2f1a", fixed=True),
               Cube((-1, -11, -4.5), (2, 1.8, 0.4), "#e8c040", fixed=True)]),
    Part("rbrim", (-4, -8.8, 0), (0, 0, 0.5), [Cube((-3.6, -0.8, -6), (3.6, 0.8, 12), "#b0834f")]),
    Part("lbrim", (4, -8.8, 0), (0, 0, -0.5), [Cube((0, -0.8, -6), (3.6, 0.8, 12), "#b0834f")]),
], tint="#b0834f"))

party = []
sizes = [8.6, 7.2, 5.8, 4.4, 3.0]
for i, s in enumerate(sizes):
    party.append(Cube((-s / 2, -8 - 2.4 * (i + 1), -s / 2), (s, 2.4, s), "#f4f4f4" if i % 2 else "#ff5fa8", fixed=bool(i % 2)))
party += [Cube((-1.4, -21.4, -1.4), (2.8, 2.8, 2.8), "#ffe566", fixed=True),
          Cube((-2.2, -20.6, -0.6), (4.4, 1.2, 1.2), "#5ad1ff", fixed=True),
          Cube((-0.6, -20.6, -2.2), (1.2, 1.2, 4.4), "#ff9e3d", fixed=True)]
HATS.append(hat("party_hat", "Party Hat", "#ff5fa8", [P1("hat", party)], tint="#ff5fa8"))

HATS.append(hat("beanie", "Beanie", "#e0484f", [P1("hat", [
    Cube((-4.5, -11.5, -4.5), (9, 4, 9), "#e0484f", details=[("front", i, 0, 1, 4, "#b03a40") for i in (1, 3, 5, 7)]
                                                           + [("left", i, 0, 1, 4, "#b03a40") for i in (1, 3, 5, 7)]),
    Cube((-4.9, -9.6, -4.9), (9.8, 1.8, 9.8), "#e0484f", details=[("front", i, 0, 1, 2, "#b03a40") for i in (0, 2, 4, 6, 8)]),
    Cube((-1.3, -14.4, -1.3), (2.6, 2.6, 2.6), "#ffffff", fixed=True),
    Cube((-1.9, -14, -0.9), (3.8, 1.6, 1.8), "#ffffff", fixed=True), Cube((-0.9, -14, -1.9), (1.8, 1.6, 3.8), "#ffffff", fixed=True),
])], tint="#e0484f"))

HATS.append(hat("headphones", "Headphones", "#ff5b8a", [P1("hp", [
    Cube((-4.9, -10.6, -0.9), (9.8, 1.2, 1.8), "#26262e", fixed=True),
    Cube((-5.6, -10.0, -0.9), (1.2, 6.8, 1.8), "#26262e", fixed=True), Cube((4.4, -10.0, -0.9), (1.2, 6.8, 1.8), "#26262e", fixed=True),
    Cube((-7.0, -6.8, -2.6), (2.4, 5.2, 5.2), "#ff5b8a"), Cube((4.6, -6.8, -2.6), (2.4, 5.2, 5.2), "#ff5b8a"),
    Cube((-4.9, -6.3, -2.2), (0.7, 4.2, 4.4), "#26262e", fixed=True), Cube((4.2, -6.3, -2.2), (0.7, 4.2, 4.4), "#26262e", fixed=True),
    Cube((-7.2, -5.2, -1.2), (0.4, 2.4, 2.4), "#ffd1e0", fixed=True), Cube((6.8, -5.2, -1.2), (0.4, 2.4, 2.4), "#ffd1e0", fixed=True),
])], tint="#ff5b8a"))

dev_r = chain("hornr", (-2.8, -8, -1), [(2.2, 1.9, 28, -8), (2.0, 1.6, 22, -6), (1.8, 1.2, 25, -4), (1.5, 0.8, 25, 0)], "#c0243a", side=-1)
dev_l = chain("hornl", (2.8, -8, -1), [(2.2, 1.9, 28, -8), (2.0, 1.6, 22, -6), (1.8, 1.2, 25, -4), (1.5, 0.8, 25, 0)], "#c0243a", side=1)
HATS.append(hat("devil_horns", "Devil Horns", "#c0243a", list(dev_r) + list(dev_l), tint="#c0243a"))

ant = []
for side, name in ((-1, "antr"), (1, "antl")):
    beam = chain(name, (side * 2.8, -8, 0.5), [(3.2, 1.4, 28, -6), (3.0, 1.3, -22, -6), (3.0, 1.2, -18, 0), (2.4, 1.0, -22, 0)], "#8a5f35", side=side)
    ant += list(beam)
    for k, (j, ang, ln) in enumerate(((1, 65, 2.8), (2, 60, 2.6), (3, 55, 2.0))):
        jx, jy, jz = beam.joints[j]
        ant.append(Part(f"{name}t{k}", (jx, jy, jz), (-0.1, 0, side * ang * D), [Cube((-0.5, -ln, -0.5), (1, ln, 1), "#a07040")]))
HATS.append(hat("antlers", "Antlers", "#8a5f35", ant, tint="#8a5f35"))

fc = ring("band", -8.7, 4.6, 1.0, 1.0, "#4fa845", n=8)
fcols = ["#ff6fb5", "#ffd93d", "#ffffff", "#7ab8ff", "#c07aff", "#ff8a3d"]
for i, col in enumerate(fcols):
    phi = 2 * math.pi * (i + 0.5) / len(fcols)
    fc.append(Part(f"fl{i}", (4.6 * math.cos(phi), -9.2, 4.6 * math.sin(phi)), (0, -(phi + math.pi / 2), 0), [
        Cube((-1, -1, -0.8), (2, 2, 1.6), col, fixed=True), Cube((-0.4, -0.4, -1.0), (0.8, 0.8, 0.6), "#ffe566", fixed=True),
        Cube((-2.2, -0.4, -0.3), (1.2, 0.8, 0.6), "#4fa845"), Cube((1.0, -0.4, -0.3), (1.2, 0.8, 0.6), "#4fa845")]))
HATS.append(hat("flower_crown", "Flower Crown", "#ff6fb5", fc, tint="#4fa845"))

HATS.append(hat("knight_helmet", "Knight Helmet", "#9aa0aa", [P1("helm", [
    Cube((-4.6, -8.6, -4.6), (9.2, 8.6, 9.2), "#9aa0aa"),
    Cube((-4.2, -11.4, -4.2), (8.4, 3, 8.4), "#a8aeb8"),
    Cube((-0.6, -12.6, -4.4), (1.2, 1.4, 8.8), "#7f858f"),
    Cube((-3.6, -5.8, -4.75), (7.2, 1, 0.3), "#15151a", fixed=True),
    Cube((-0.5, -5.8, -4.75), (1, 5.5, 0.3), "#15151a", fixed=True),
    Cube((1.6, -3.6, -4.75), (1.2, 0.5, 0.3), "#15151a", fixed=True), Cube((-2.8, -3.6, -4.75), (1.2, 0.5, 0.3), "#15151a", fixed=True),
    Cube((-4.7, -8.8, -4.7), (9.4, 0.8, 9.4), "#e8c040", fixed=True),
    Cube((-0.9, -14.2, -3.2), (1.8, 2, 2), "#c0243a", fixed=True), Cube((-0.9, -14.8, -0.6), (1.8, 2.2, 2.4), "#c0243a", fixed=True),
    Cube((-0.9, -14.4, 1.6), (1.8, 2.4, 2.4), "#c0243a", fixed=True), Cube((-0.9, -13.4, 3.6), (1.8, 2.4, 2.2), "#c0243a", fixed=True),
])], tint="#9aa0aa"))

cat = [Cube((-4.4, -8.9, -4.4), (8.8, 1, 8.8), "#3a3a46")]
ear_r = [Cube((-4, -11, -3.2), (3, 3, 2.2), "#3a3a46"), Cube((-3.6, -12.2, -3), (2.2, 1.4, 1.8), "#3a3a46"),
         Cube((-3.2, -13.2, -2.8), (1.4, 1.2, 1.4), "#3a3a46"),
         Cube((-3.5, -10.8, -3.35), (1.8, 2.4, 0.3), "#ff8fc7", fixed=True), Cube((-3.1, -12, -3.15), (1.0, 1.0, 0.3), "#ff8fc7", fixed=True)]
cat += ear_r + mirror_x(ear_r)
cat += [Cube((-0.8, -9.1, -5.0), (1.6, 1.6, 1.2), "#e8c040", fixed=True), Cube((-0.3, -8.6, -5.1), (0.6, 0.6, 0.3), "#6a4a10", fixed=True)]
HATS.append(hat("cat_ears", "Cat Ears", "#ff8fc7", [P1("hat", cat)], tint="#3a3a46"))

halo = ring("h", -14, 4.3, 0.9, 1.0, "#ffe566") + ring("g", -14, 3.2, 0.5, 0.5, "#fff4b8", n=8)
halo += [Part(f"sp{i}", (5.4 * math.cos(a), -14 + dy, 5.4 * math.sin(a)), (0, 0, 0), [Cube((-0.4, -0.4, -0.4), (0.8, 0.8, 0.8), "#ffffff", fixed=True)])
         for i, (a, dy) in enumerate(((0.4, -1), (2.0, 0.8), (3.6, -0.6), (5.2, 0.9)))]
HATS.append(hat("halo", "Halo", "#fff4a0", halo, amp=0.6, anim="bob", tint="#ffe566"))

# ------------------------------------------------------------------ wings
WINGS = []


def wing(id, name, accent, parts, amp=1.0, tint=None):
    return Cos(id, name, "WINGS", "BODY", "flap", accent, parts, amp, tint)


prim_len = [6, 9, 11, 12.5, 13, 12.5, 11, 8.5]
prim_ang = [75, 60, 45, 30, 15, 0, -15, -30]
angel = lambda s: (fan_wing("right" if s < 0 else "left", s,
                            [(prim_len[i], 2.3, prim_ang[i], lerp_color("#ffffff", "#dfe9ff", i / 7), 0.0, False) for i in range(8)]
                            + [(prim_len[i] - 3, 2.8, prim_ang[i] + 4, "#ffffff", 0.9, False) for i in range(8)]))
WINGS.append(wing("angel_wings", "Angel Wings", "#eaf4ff", both(angel), tint="#ffffff"))

WINGS.append(wing("demon_wings", "Demon Wings", "#c0243a", both(lambda s: membrane_wing(
    "right" if s < 0 else "left", s, [(8, 80), (13, 55), (15, 30), (14, 5), (10, -20)], "#9a1b3a", "#2a0a12")), amp=1.2, tint="#9a1b3a"))
WINGS.append(wing("dragon_wings", "Dragon Wings", "#3a9a45", both(lambda s: membrane_wing(
    "right" if s < 0 else "left", s, [(9, 75), (14, 50), (16, 25), (14, 0)], "#3a9a45", "#24592b")), amp=0.9, tint="#3a9a45"))
WINGS.append(wing("bat_wings", "Bat Wings", "#6a6a85", both(lambda s: membrane_wing(
    "right" if s < 0 else "left", s, [(6, 70), (9, 45), (10, 20), (8, -5)], "#2e2e3d", "#15151f", fold=0.4)), amp=1.5, tint="#2e2e3d"))

rain = ["#ff4d4d", "#ff9e3d", "#ffe94d", "#5fe06a", "#4dc3ff", "#a066ff", "#ff6fb5"]
rl, ra = [8, 10, 11, 11, 10, 9, 7], [60, 45, 30, 15, 0, -15, -30]
WINGS.append(wing("rainbow_wings", "Rainbow Wings", "#ffb347", both(lambda s: fan_wing(
    "right" if s < 0 else "left", s, [(rl[i], 2.4, ra[i], rain[i], 0.0, True) for i in range(7)]
    + [(rl[i] - 3, 2.4, ra[i] + 3, lerp_color(rain[i], "#ffffff", 0.45), 0.9, True) for i in range(7)]))))

pl = [7, 10, 13, 15, 16, 15, 13, 10, 8]
pa = [80, 65, 50, 35, 20, 5, -10, -25, -40]
WINGS.append(wing("phoenix_wings", "Phoenix Wings", "#ff7a2f", both(lambda s: fan_wing(
    "right" if s < 0 else "left", s,
    [(pl[i], 2.5, pa[i], lerp_color("#ffe066", "#ff3d2e", i / 8), 0.0, True) for i in range(9)]
    + [(pl[i] - 4, 2.8, pa[i] + 4, lerp_color("#fff2a8", "#ff8a3d", i / 8), 0.9, True) for i in range(9)])), amp=1.3))


def butterfly(s):
    nm = "right" if s < 0 else "left"
    out = []
    for key, (L, H, ang, y0) in {"up": (11, 9, 30, -6), "low": (8, 7, -35, -1)}.items():
        if s < 0:
            pos, roll, yaw, sx = (-L, y0, 0), ang * D, 0.5, -1
        else:
            pos, roll, yaw, sx = (0, y0, 0), -ang * D, -0.5, 1
        cubes = [Cube(pos, (L, H, 0.8), "#ff9e3d")]
        x_edge = pos[0] - 0.3 if s < 0 else pos[0] + L - 0.3
        cubes.append(Cube((x_edge, y0, -0.1), (0.6, H, 1.0), "#2a1a10", fixed=True))
        cubes.append(Cube((pos[0], y0 - 0.3, -0.1), (L, 0.6, 1.0), "#2a1a10", fixed=True))
        cubes.append(Cube((pos[0], y0 + H - 0.3, -0.1), (L, 0.6, 1.0), "#2a1a10", fixed=True))
        for k in range(3):
            fx = pos[0] + (L * (0.25 + 0.22 * k) if s > 0 else L * (0.75 - 0.22 * k) - 1.6)
            cubes.append(Cube((fx, y0 + 2 + (k % 2) * 3, 0.8), (1.6, 1.6, 0.3), "#ffffff", fixed=True))
        out.append(Part(f"{nm}_{key}", (s * 2.0, 2.0, 2.5), (0, yaw, roll), cubes))
    return out


WINGS.append(wing("butterfly_wings", "Butterfly Wings", "#ff9e3d", both(butterfly), amp=0.9, tint="#ff9e3d"))


def robot(s):
    nm = "right" if s < 0 else "left"
    out = []
    for i, (L, ang) in enumerate(((12, 35), (10, 10), (8, -15))):
        pos = (-L, -1.5, 0) if s < 0 else (0, -1.5, 0)
        strip = (-L + 1, -0.3, 1.2) if s < 0 else (1, -0.3, 1.2)
        out.append(Part(f"{nm}_{i}", (s * 2.0, 2.0, 2.5 + i * 0.05), (0, 0.5 * (1 if s < 0 else -1), (ang if s < 0 else -ang) * D), [
            Cube(pos, (L, 3, 1.2), "#8a929c", fixed=True), Cube(strip, (L - 2, 0.6, 0.3), "#38e0ff"),
            Cube((pos[0] + (0 if s < 0 else L - 1), -1.2, -0.1), (1, 2.4, 1.4), "#5a626c", fixed=True)]))
    return out


WINGS.append(wing("robot_wings", "Robot Wings", "#38e0ff", both(robot), amp=0.6, tint="#38e0ff"))

# ------------------------------------------------------------------ capes
CAPES = []


def cape_part(color, trim=None, stars=(), name="cape"):
    cubes = [Cube((-5, 0, 0), (10, 16, 1), color)]
    if trim:
        cubes += [Cube((-5.2, -0.2, -0.1), (10.4, 1.2, 1.2), trim, fixed=True), Cube((-5.2, 14.8, -0.1), (10.4, 1.2, 1.2), trim, fixed=True),
                  Cube((-5.3, 0, -0.1), (0.7, 16, 1.2), trim, fixed=True), Cube((4.6, 0, -0.1), (0.7, 16, 1.2), trim, fixed=True)]
    for (x, y, col) in stars:
        cubes.append(Cube((x, y, 1.0), (0.9, 0.9, 0.25), col, fixed=True))
    return Part(name, (0, 0, 2), (0.14, 0, 0), cubes)


def cape(id, name, accent, parts, tint):
    return Cos(id, name, "CAPE", "BODY", "sway", accent, parts, 1.0, tint)


CAPES.append(cape("red_cape", "Red Cape", "#d6303b", [cape_part("#c42631", "#ffd24a")], "#c42631"))
fur = [Cube((-6, -1, -3), (12, 2, 6), "#f4f1ea", fixed=True), Cube((-6.2, 0.8, -3), (2.2, 6, 1.2), "#f4f1ea", fixed=True),
       Cube((4.0, 0.8, -3), (2.2, 6, 1.2), "#f4f1ea", fixed=True), Cube((-5.8, 3, -3.1), (1, 1, 0.3), "#3a3a44", fixed=True),
       Cube((4.8, 5, -3.1), (1, 1, 0.3), "#3a3a44", fixed=True),
       Cube((-1, 0.2, -3.1), (2, 2, 0.6), "#e8c040", fixed=True), Cube((-0.4, 0.8, -3.2), (0.8, 0.8, 0.3), "#e03a3a", fixed=True),
       Cube((-3.8, 1.0, -3.0), (2.8, 0.5, 0.4), "#e8c040", fixed=True), Cube((1.0, 1.0, -3.0), (2.8, 0.5, 0.4), "#e8c040", fixed=True)]
CAPES.append(cape("royal_cape", "Royal Cape", "#7a3fd1", [cape_part("#5b2aa8", "#ffd24a"), P1("collar", fur)], "#5b2aa8"))
stars = [(-3.5, 2.5, "#ffffff"), (1.5, 4.5, "#cfe0ff"), (-1.5, 7.5, "#ffffff"), (2.8, 9.5, "#cfe0ff"), (-3.0, 11.5, "#ffffff"), (0.5, 13.5, "#cfe0ff")]
CAPES.append(cape("midnight_cape", "Midnight Cape", "#2f4cc9", [cape_part("#141a3d", "#4a5fd1", stars)], "#141a3d"))

tat = []
for i, (x, L) in enumerate(zip((-4, -2, 0, 2, 4), (14, 16, 12, 15, 13))):
    tat.append(Part(f"cape_{i}", (x, 0, 2), (0.14 + 0.015 * i, 0, 0), [Cube((-1.05, 0, 0), (2.1, L, 1), "#6b2a2a"),
                                                                     Cube((-0.4, L - 1.2, 1.0), (0.8, 1.2, 0.3), "#3a1414", fixed=True)]))
tat.append(P1("collar", [Cube((-5.2, -0.6, -2.5), (10.4, 1.4, 5), "#3a2a22", fixed=True), Cube((-4.4, 0.4, -2.8), (1.2, 4, 0.5), "#3a2a22", fixed=True),
                         Cube((3.2, 0.4, -2.8), (1.2, 4, 0.5), "#3a2a22", fixed=True), Cube((-0.8, 0.2, -2.9), (1.6, 1.6, 0.5), "#b8b8c0", fixed=True)]))
CAPES.append(cape("tattered_cape", "Tattered Cape", "#8a3a3a", tat, "#6b2a2a"))

kn = [cape_part("#b02a35", "#f4f1ea"),
      P1("armor", [Cube((4.2, -1.6, -2.8), (4.6, 2.2, 5.6), "#9aa0aa", fixed=True), Cube((4.6, -2.6, -2.2), (3.8, 1.4, 4.4), "#b4bac4", fixed=True),
                   Cube((-8.8, -1.6, -2.8), (4.6, 2.2, 5.6), "#9aa0aa", fixed=True), Cube((-8.4, -2.6, -2.2), (3.8, 1.4, 4.4), "#b4bac4", fixed=True),
                   Cube((-4.4, 0.2, -2.9), (8.8, 0.9, 0.5), "#8a8d96", fixed=True), Cube((-1, 0.0, -3.1), (2, 2, 0.6), "#e8c040", fixed=True),
                   Cube((8.2, -3.4, -0.6), (1, 1.6, 1.2), "#d0d4dc", fixed=True), Cube((-9.2, -3.4, -0.6), (1, 1.6, 1.2), "#d0d4dc", fixed=True)])]
CAPES.append(cape("knight_cloak", "Knight Cloak", "#c0343f", kn, "#b02a35"))

hood = P1("hood", [Cube((-4.9, -9.0, 3.3), (9.8, 9.0, 1.4), "#2f5b3a"), Cube((-4.9, -10.2, -4.9), (9.8, 1.4, 10), "#2f5b3a"),
                   Cube((4.1, -9.6, -4.5), (1.2, 9.6, 8.6), "#2f5b3a"), Cube((-5.3, -9.6, -4.5), (1.2, 9.6, 8.6), "#2f5b3a"),
                   Cube((-4.0, -9.2, -4.8), (8, 0.7, 0.5), "#3f7a4e", fixed=True), Cube((-0.8, -12, 3.4), (1.6, 2.2, 1.4), "#2f5b3a"),
                   Cube((-0.6, -1.0, -2.8), (1.2, 1.2, 0.6), "#e8c040", fixed=True)])
CAPES.append(cape("hooded_cloak", "Hooded Cloak", "#3f7a4e", [cape_part("#2f5b3a", "#1f3d27"), hood], "#2f5b3a"))

# ------------------------------------------------------------------ jackets
JACKETS = []


def jacket(id, name, accent, cubes, tint, extra=()):
    return Cos(id, name, "JACKET", "BODY", "none", accent, [P1("jacket", cubes)] + list(extra), 1.0, tint)


def body(color, infl=0.6):
    return Cube((-4, 0, -2), (8, 12, 4), color, inflate=infl)


DEN = "#3b6aa8"
den = [body(DEN), Cube((-4, 11, -2), (8, 1.2, 4), DEN, inflate=0.75),
       Cube((-4, -1.4, 1), (8, 1.8, 1.2), DEN, inflate=0.7), Cube((-4.2, -1.4, -2), (1.2, 1.8, 4), DEN, inflate=0.7), Cube((3.0, -1.4, -2), (1.2, 1.8, 4), DEN, inflate=0.7),
       Cube((4.1, -0.6, -2.4), (4.4, 1.4, 4.8), DEN), Cube((-8.5, -0.6, -2.4), (4.4, 1.4, 4.8), DEN),
       Cube((-3.6, 2, -2.9), (2.8, 2.6, 0.4), DEN), Cube((0.8, 2, -2.9), (2.8, 2.6, 0.4), DEN),
       Cube((-3.6, 2, -3.05), (2.8, 0.3, 0.2), "#cdd6e0", fixed=True), Cube((0.8, 2, -3.05), (2.8, 0.3, 0.2), "#cdd6e0", fixed=True)]
den += [Cube((-0.4, y, -2.95), (0.8, 0.8, 0.3), "#d0d4dc", fixed=True) for y in (1.0, 4.0, 7.0, 10.0)]
JACKETS.append(jacket("denim_jacket", "Denim Jacket", "#3c6fb5", den, DEN))

LEA = "#5a3520"
lea = [body(LEA), Cube((-4, 10, -2), (8, 1.4, 4), "#2a1a10", inflate=0.8, fixed=True), Cube((-0.8, 10.2, -2.95), (1.6, 1.0, 0.4), "#e8c040", fixed=True),
       Cube((-0.3, 0, -3.0), (0.6, 10, 0.3), "#c0c4cc", fixed=True),
       Cube((-4, -1.4, 1), (8, 1.8, 1.2), LEA, inflate=0.7), Cube((-4.2, -1.4, -2), (1.2, 1.8, 4), LEA, inflate=0.7), Cube((3.0, -1.4, -2), (1.2, 1.8, 4), LEA, inflate=0.7),
       Cube((4.1, -0.7, -2.5), (4.4, 1.5, 5), LEA), Cube((-8.5, -0.7, -2.5), (4.4, 1.5, 5), LEA),
       Cube((4.3, -1.0, -0.4), (1, 1, 0.8), "#c0c4cc", fixed=True), Cube((-5.3, -1.0, -0.4), (1, 1, 0.8), "#c0c4cc", fixed=True),
       Cube((2.2, 6, -2.95), (1.8, 0.4, 0.3), "#c0c4cc", fixed=True), Cube((-4.0, 6, -2.95), (1.8, 0.4, 0.3), "#c0c4cc", fixed=True)]
lapels = [Part("lapel_l", (1.3, -0.8, -2.65), (0, 0, -0.35), [Cube((0, 0, -0.3), (3.2, 6.5, 0.7), LEA)]),
          Part("lapel_r", (-1.3, -0.8, -2.65), (0, 0, 0.35), [Cube((-3.2, 0, -0.3), (3.2, 6.5, 0.7), LEA)])]
JACKETS.append(jacket("leather_jacket", "Leather Jacket", "#7a4a2b", lea, LEA, lapels))

HOO = "#c9343c"
hoo = [body(HOO), Cube((-4, 11, -2), (8, 1.4, 4), HOO, inflate=0.8),
       Cube((-3, 6, -2.95), (6, 3.5, 0.5), HOO), Cube((-2.6, 6, -3.2), (5.2, 0.4, 0.2), "#7a1d24", fixed=True),
       Cube((-1.8, 0.4, -3.0), (0.4, 4.5, 0.3), "#f4f4f4", fixed=True), Cube((1.4, 0.4, -3.0), (0.4, 4.5, 0.3), "#f4f4f4", fixed=True),
       Cube((-2.1, 4.7, -3.0), (1, 0.8, 0.3), "#f4f4f4", fixed=True), Cube((1.1, 4.7, -3.0), (1, 0.8, 0.3), "#f4f4f4", fixed=True),
       Cube((-4.5, -3.0, 0.8), (9, 4.2, 3.4), HOO), Cube((-3.5, -4.0, 1.6), (7, 2, 2.6), HOO),
       Cube((4.1, -0.6, -2.4), (4.4, 1.4, 4.8), HOO), Cube((-8.5, -0.6, -2.4), (4.4, 1.4, 4.8), HOO)]
JACKETS.append(jacket("red_hoodie", "Red Hoodie", "#e0484f", hoo, HOO))

TR = "#b59a6a"
tr = [body(TR), Cube((-4.4, 11, -2.65), (8.8, 6, 1), TR, inflate=0.1), Cube((-4.4, 11, 1.65), (8.8, 6, 1), TR, inflate=0.1),
      Cube((-4, 8.4, -2), (8, 1.2, 4), "#4a3420", inflate=0.8, fixed=True), Cube((-0.9, 8.5, -2.95), (1.8, 1.0, 0.4), "#e8c040", fixed=True),
      Cube((-4.5, -2.0, -2.2), (9, 2.6, 4.4), TR, inflate=0.3),
      Cube((4.1, -0.7, -2.5), (4.4, 1.5, 5), TR), Cube((-8.5, -0.7, -2.5), (4.4, 1.5, 5), TR)]
tr += [Cube((x, y, -3.0), (0.8, 0.8, 0.3), "#4a3420", fixed=True) for x in (-2.2, 1.4) for y in (1.5, 4.5)]
tr += [Cube((x, y, -3.0), (0.8, 0.8, 0.3), "#4a3420", fixed=True) for x in (-2.2, 1.4) for y in (11.5, 14.5)]
trl = [Part("lapel_l", (1.6, -1.0, -2.65), (0, 0, -0.4), [Cube((0, 0, -0.3), (3.4, 7, 0.7), TR)]),
       Part("lapel_r", (-1.6, -1.0, -2.65), (0, 0, 0.4), [Cube((-3.4, 0, -0.3), (3.4, 7, 0.7), TR)])]
JACKETS.append(jacket("trench_coat", "Trench Coat", "#b59a6a", tr, TR, trl))

ST = "#9aa0aa"
kp = [body(ST, 0.7), Cube((-0.4, 0, -3.0), (0.8, 12, 0.3), "#e8c040", fixed=True), Cube((-4.2, 0.2, -3.0), (8.4, 0.6, 0.3), "#e8c040", fixed=True),
      Cube((4.2, -1.4, -2.8), (4.6, 2.2, 5.6), ST), Cube((4.6, -2.4, -2.2), (3.8, 1.4, 4.4), ST),
      Cube((-8.8, -1.4, -2.8), (4.6, 2.2, 5.6), ST), Cube((-8.4, -2.4, -2.2), (3.8, 1.4, 4.4), ST),
      Cube((-4, 9.6, -2), (8, 1.4, 4), "#4a3420", inflate=0.85, fixed=True), Cube((-1, 9.7, -3.0), (2, 1.2, 0.4), "#e8c040", fixed=True),
      Cube((-3, 11.4, -2.95), (6, 4.5, 0.6), "#c0243a", fixed=True), Cube((-3, 11.4, 2.35), (6, 4.5, 0.6), "#c0243a", fixed=True),
      Cube((-4.5, -1.6, -2.2), (9, 2, 4.4), ST, inflate=0.3)]
JACKETS.append(jacket("knight_plate", "Knight Plate", "#9aa0aa", kp, ST))

TX = "#1c1c24"
tx = [body(TX), Cube((-1.8, 0, -3.0), (3.6, 10.5, 0.3), "#f4f4f4", fixed=True),
      Cube((-2.3, -0.5, -3.15), (2, 1.5, 0.6), "#c0243a", fixed=True), Cube((0.3, -0.5, -3.15), (2, 1.5, 0.6), "#c0243a", fixed=True),
      Cube((-0.5, -0.3, -3.25), (1, 1, 0.7), "#8a1a2a", fixed=True),
      Cube((-3.8, 12, 1.4), (3.5, 6, 0.9), TX), Cube((0.3, 12, 1.4), (3.5, 6, 0.9), TX),
      Cube((4.1, -0.6, -2.4), (4.4, 1.4, 4.8), TX), Cube((-8.5, -0.6, -2.4), (4.4, 1.4, 4.8), TX),
      Cube((-4, -1.4, 1), (8, 1.8, 1.2), TX, inflate=0.7)]
tx += [Cube((-0.25, y, -3.1), (0.5, 0.5, 0.3), "#d0d4dc", fixed=True) for y in (4.0, 6.5, 9.0)]
txl = [Part("lapel_l", (1.9, -0.6, -2.7), (0, 0, -0.18), [Cube((0, 0, -0.3), (2.3, 8.5, 0.7), "#2a2a36")]),
       Part("lapel_r", (-1.9, -0.6, -2.7), (0, 0, 0.18), [Cube((-2.3, 0, -0.3), (2.3, 8.5, 0.7), "#2a2a36")])]
JACKETS.append(jacket("tuxedo", "Tuxedo", "#2a2a36", tx, TX, txl))

PF = "#e0484f"
pf = [Cube((-4, 3 * i, -2), (8, 2.8, 4), PF, inflate=0.9) for i in range(4)]
pf += [Cube((-4, -1.8, -2), (8, 2, 4), PF, inflate=0.9), Cube((-0.3, 0, -3.15), (0.6, 12, 0.3), "#d0d4dc", fixed=True),
       Cube((4.2, -0.8, -2.8), (4.4, 2.2, 5.6), PF, inflate=0.2), Cube((-8.6, -0.8, -2.8), (4.4, 2.2, 5.6), PF, inflate=0.2)]
JACKETS.append(jacket("puffer_jacket", "Puffer Jacket", "#e0484f", pf, PF))

HATS_ALL = HATS


# ------------------------------------------------------------------ pets (ride on a shoulder and bob)
PETS = [
    # ---- pets (ride on the right shoulder and bob)
    pet("cube_cat", "Cube Cat", "#ffa447", [
        Cube((-2, -3, -2), (4, 3, 4), "#f2933c", details=[("top", 1, 0, 2, 4, "#c9731f")]),
        Cube((-1.75, -6.5, -2.4), (3.5, 3.5, 3.5), "#f6a04d", details=[("front", 0, 1, 1, 1, "#1a1a1a"), ("front", 2, 1, 1, 1, "#1a1a1a"), ("front", 1, 2, 1, 1, "#ff8fa8")]),
        Cube((-1.75, -8, -1.9), (1, 1.5, 1), "#e48a33"), Cube((0.75, -8, -1.9), (1, 1.5, 1), "#e48a33"),
        Cube((-0.5, -2.5, 2), (1, 1, 3), "#e48a33"),
    ]),
    pet("slime_buddy", "Slime Buddy", "#7be06a", [
        Cube((-2.5, -5, -2.5), (5, 5, 5), "#6fd45e", details=[("front", 1, 2, 1, 1, "#1d3a1a"), ("front", 3, 2, 1, 1, "#1d3a1a"), ("front", 2, 3, 1, 1, "#2f6b27")]),
        Cube((-1.5, -4, -1.5), (3, 3, 3), "#4fb040"),
    ], amp=1.4),
    pet("bumble_bee", "Bumble Bee", "#ffd93d", [
        Cube((-2, -4, -2.5), (4, 3, 5), "#ffd93d", details=[("top", 0, 1, 4, 1, "#2a2a2a"), ("top", 0, 3, 4, 1, "#2a2a2a"), ("front", 0, 1, 1, 1, "#1a1a1a"), ("front", 3, 1, 1, 1, "#1a1a1a")]),
        Cube((-2.5, -5.5, -0.5), (2, 1, 3), "#e8f6ff"), Cube((0.5, -5.5, -0.5), (2, 1, 3), "#e8f6ff"),
        Cube((-0.5, -3, 2.5), (1, 1, 1.5), "#2a2a2a"),
    ], pivot=(-7, -3, 0), amp=2.0),
]

EYE = "#15151a"


def eyes(y, a=0, b=3, color=EYE):
    return [("front", a, y, 1, 1, color), ("front", b, y, 1, 1, color)]


MORE_PETS = [
    pet("wolf_pup", "Wolf Pup", "#a9acb3", [
        Cube((-2, -3, -2), (4, 3, 4), "#8c8f96"),
        Cube((-1.75, -6.5, -3.6), (3.5, 3.5, 3.5), "#a9acb3", details=eyes(1)),
        Cube((-0.75, -4.8, -5.2), (1.5, 1.5, 1.6), "#d6d8dc", details=[("front", 0, 0, 2, 1, "#222222")]),
        Cube((-1.75, -8, -2.6), (1, 1.6, 1), "#6d7076"), Cube((0.75, -8, -2.6), (1, 1.6, 1), "#6d7076"),
        Cube((-0.5, -3, 2), (1, 1, 3), "#8c8f96"),
    ]),
    pet("fox_kit", "Fox Kit", "#f08a35", [
        Cube((-2, -3, -2), (4, 3, 4), "#e8782a"),
        Cube((-1.75, -6.5, -3.6), (3.5, 3.5, 3.5), "#f08a35", details=eyes(1) + [("front", 1, 2, 2, 2, "#ffffff")]),
        Cube((-1.75, -8, -2.6), (1, 1.6, 1), "#e8782a", details=[("top", 0, 0, 1, 1, "#222222")]),
        Cube((0.75, -8, -2.6), (1, 1.6, 1), "#e8782a", details=[("top", 0, 0, 1, 1, "#222222")]),
        Cube((-0.75, -3.5, 2), (1.5, 1.5, 3), "#e8782a", details=[("back", 0, 0, 2, 2, "#ffffff")]),
    ]),
    pet("panda_cub", "Panda Cub", "#f4f4f4", [
        Cube((-2, -3, -2), (4, 3, 4), "#f4f4f4", details=[("left", 0, 0, 4, 3, "#2a2a2a"), ("right", 0, 0, 4, 3, "#2a2a2a")]),
        Cube((-1.75, -6.5, -2.6), (3.5, 3.5, 3.5), "#f8f8f8", details=eyes(1, 0, 3, "#2a2a2a") + [("front", 1, 2, 2, 1, "#2a2a2a")]),
        Cube((-1.9, -7.8, -1.6), (1.2, 1.2, 1.2), "#2a2a2a"), Cube((0.7, -7.8, -1.6), (1.2, 1.2, 1.2), "#2a2a2a"),
    ]),
    pet("piglet", "Piglet", "#f4a4b4", [
        Cube((-2, -3, -2.5), (4, 3, 5), "#f4a4b4"),
        Cube((-1.75, -6.5, -3.8), (3.5, 3.5, 3.5), "#f6b0be", details=eyes(1)),
        Cube((-1, -5, -4.8), (2, 1.5, 1), "#e58a9c", details=[("front", 0, 0, 1, 1, "#a0505f"), ("front", 1, 0, 1, 1, "#a0505f")]),
        Cube((-1.75, -7.4, -3), (1, 1, 0.8), "#e58a9c"), Cube((0.75, -7.4, -3), (1, 1, 0.8), "#e58a9c"),
        Cube((-0.4, -2.6, 2.5), (0.8, 0.8, 1), "#e58a9c"),
    ]),
    pet("bunny", "Bunny", "#ffffff", [
        Cube((-2, -3, -2), (4, 3, 4), "#f1f1f1"),
        Cube((-1.75, -6.5, -3), (3.5, 3.5, 3.5), "#f6f6f6", details=eyes(1, 0, 3) + [("front", 1, 2, 2, 1, "#ffb6c8")]),
        Cube((-1.5, -10.5, -1.6), (1, 3.5, 1), "#f6f6f6", details=[("front", 0, 0, 1, 3, "#ffb6c8")]),
        Cube((0.5, -10.5, -1.6), (1, 3.5, 1), "#f6f6f6", details=[("front", 0, 0, 1, 3, "#ffb6c8")]),
        Cube((-0.75, -2.5, 2), (1.5, 1.5, 1.5), "#ffffff"),
    ]),
    pet("baby_dragon", "Baby Dragon", "#4fa845", [
        Cube((-2, -3, -2.5), (4, 3, 5), "#4fa845"),
        Cube((-1.75, -6.5, -4.2), (3.5, 3.5, 3.5), "#5cb852", details=eyes(1, 0, 3, "#ffd84d")),
        Cube((-1.5, -8, -2.8), (0.7, 1.5, 0.7), "#e8e0c8"), Cube((0.8, -8, -2.8), (0.7, 1.5, 0.7), "#e8e0c8"),
        Cube((-0.5, -2.5, 2.5), (1, 1, 3.5), "#3e8a38"),
        Cube((-0.5, -3.8, -1), (1, 0.8, 0.8), "#2f7a2a"), Cube((-0.5, -3.8, 0.6), (1, 0.8, 0.8), "#2f7a2a"),
        Cube((-4, -4, 0), (2, 0.8, 3), "#3e8a38"), Cube((2, -4, 0), (2, 0.8, 3), "#3e8a38"),
    ]),
    pet("parrot", "Parrot", "#d9332a", [
        Cube((-1.5, -5, -1.5), (3, 5, 3), "#d9332a"),
        Cube((-1.25, -7.4, -1.8), (2.5, 2.5, 2.5), "#e24a3a", details=eyes(1, 0, 2, "#ffffff")),
        Cube((-0.5, -6.4, -3.4), (1, 1.2, 1.2), "#ffc83a"),
        Cube((-2.3, -4.5, -1), (0.8, 3, 2), "#2e63d6"), Cube((1.5, -4.5, -1), (0.8, 3, 2), "#2e63d6"),
        Cube((-0.7, -1.2, 1.3), (1.4, 0.8, 3.5), "#2e63d6"),
    ]),
    pet("penguin", "Penguin", "#2b2e36", [
        Cube((-2, -5, -1.5), (4, 5, 3), "#22252b", details=[("front", 1, 1, 2, 3, "#f4f4f4")]),
        Cube((-1.75, -7.5, -1.75), (3.5, 2.5, 3.5), "#22252b", details=eyes(1, 0, 3, "#ffffff")),
        Cube((-0.7, -6.3, -3.2), (1.4, 0.8, 1.4), "#ff9a2e"),
        Cube((-2, -0.6, -2.2), (1.6, 0.6, 2), "#ff9a2e"), Cube((0.4, -0.6, -2.2), (1.6, 0.6, 2), "#ff9a2e"),
    ]),
    pet("frog", "Frog", "#4fb04a", [
        Cube((-2.5, -2.5, -2), (5, 2.5, 4), "#4fb04a", details=[("front", 1, 2, 3, 1, "#2f6b2a")]),
        Cube((-2.2, -4, -1.6), (1.5, 1.5, 1.5), "#6fd060", details=[("front", 0, 0, 1, 1, EYE)]),
        Cube((0.7, -4, -1.6), (1.5, 1.5, 1.5), "#6fd060", details=[("front", 1, 0, 1, 1, EYE)]),
    ], amp=1.6),
    pet("axolotl", "Axolotl", "#ffb3c7", [
        Cube((-1.5, -2.5, -3), (3, 2.5, 6), "#ffb3c7"),
        Cube((-2, -4.5, -3.8), (4, 2.5, 3), "#ffc2d1", details=eyes(1, 0, 3)),
        Cube((-2.8, -4.2, -3.2), (0.8, 1.6, 0.6), "#ff7aa0"), Cube((-2.8, -3, -3.2), (0.8, 1.2, 0.6), "#ff7aa0"),
        Cube((2, -4.2, -3.2), (0.8, 1.6, 0.6), "#ff7aa0"), Cube((2, -3, -3.2), (0.8, 1.2, 0.6), "#ff7aa0"),
        Cube((-0.5, -2.5, 3), (1, 2, 3), "#ff9fb8"),
    ]),
    pet("ghost", "Ghost", "#f2f4f8", [
        Cube((-2, -6, -2), (4, 5, 4), "#f2f4f8", details=[("front", 0, 1, 1, 2, EYE), ("front", 3, 1, 1, 2, EYE), ("front", 1, 3, 2, 1, "#6b7080")]),
        Cube((-2, -1, -2), (1.3, 1, 4), "#e4e7ee"), Cube((-0.65, -1, -2), (1.3, 1, 4), "#e4e7ee"), Cube((0.7, -1, -2), (1.3, 1, 4), "#e4e7ee"),
        Cube((-3, -4, -0.5), (1, 2, 1), "#e4e7ee"), Cube((2, -4, -0.5), (1, 2, 1), "#e4e7ee"),
    ], amp=2.2),
    pet("creeper_buddy", "Creeper Buddy", "#5ab04a", [
        Cube((-2, -8, -2), (4, 4, 4), "#5ab04a", details=eyes(1) + [("front", 1, 2, 2, 1, EYE), ("front", 1, 3, 1, 1, EYE), ("front", 2, 3, 1, 1, EYE)]),
        Cube((-1.5, -4, -1), (3, 3, 2), "#4a9a3c"),
        Cube((-1.5, -1, -1.6), (1.4, 1, 1.4), "#3f8a33"), Cube((0.1, -1, -1.6), (1.4, 1, 1.4), "#3f8a33"),
        Cube((-1.5, -1, 0.2), (1.4, 1, 1.4), "#3f8a33"), Cube((0.1, -1, 0.2), (1.4, 1, 1.4), "#3f8a33"),
    ]),
    pet("chick", "Chick", "#ffe85a", [
        Cube((-1.5, -3, -1.5), (3, 3, 3), "#ffe85a", details=eyes(1, 0, 2)),
        Cube((-0.5, -2.2, -2.6), (1, 0.8, 1.1), "#ff9a2e"),
        Cube((-2.2, -2.5, -0.5), (0.7, 1.5, 1.5), "#ffd028"), Cube((1.5, -2.5, -0.5), (0.7, 1.5, 1.5), "#ffd028"),
        Cube((-1, -0.5, -0.5), (0.6, 0.5, 1), "#ff9a2e"), Cube((0.4, -0.5, -0.5), (0.6, 0.5, 1), "#ff9a2e"),
    ], amp=1.6),
    pet("robot_buddy", "Robot Buddy", "#9aa4b0", [
        Cube((-2, -3, -2), (4, 3, 4), "#9aa4b0"),
        Cube((-2, -6.5, -2), (4, 3.5, 4), "#b9c3ce", details=eyes(1, 0, 3, "#38e0ff") + [("front", 1, 3, 2, 1, "#4a5560")]),
        Cube((-0.25, -9, -0.25), (0.5, 2.5, 0.5), "#6b7580"), Cube((-0.6, -10, -0.6), (1.2, 1.2, 1.2), "#ff4d4d"),
    ]),
    pet("pumpkin_pal", "Pumpkin Pal", "#e8821e", [
        Cube((-2.5, -4, -2.5), (5, 4, 5), "#e8821e", details=[("front", 1, 1, 1, 1, "#2a1500"), ("front", 3, 1, 1, 1, "#2a1500"), ("front", 1, 3, 3, 1, "#2a1500")]),
        Cube((-0.5, -5.5, -0.5), (1, 1.5, 1), "#4a8a2a"),
    ]),
    pet("snowman", "Mini Snowman", "#f4f8ff", [
        Cube((-2, -3, -2), (4, 3, 4), "#f4f8ff"),
        Cube((-1.5, -6, -1.5), (3, 3, 3), "#f8fbff", details=eyes(1, 0, 2)),
        Cube((-0.4, -4.8, -2.7), (0.8, 0.8, 1.3), "#ff8a2a"),
        Cube((-1.6, -7.6, -1.6), (3.2, 1.6, 3.2), "#1e1e24"),
        Cube((-3.4, -4.6, -0.3), (2, 0.4, 0.4), "#6b4a2a"), Cube((1.4, -4.6, -0.3), (2, 0.4, 0.4), "#6b4a2a"),
    ]),
    pet("owl", "Owl", "#8a6540", [
        Cube((-2, -4.5, -1.5), (4, 4.5, 3), "#8a6540", details=[("front", 1, 2, 2, 2, "#d9c7a0")]),
        Cube((-2, -6.5, -1.8), (4, 2.5, 3.6), "#9a7248", details=eyes(1, 0, 3, "#ffffff")),
        Cube((-2, -7.6, -1), (0.9, 1.4, 0.9), "#6d4e30"), Cube((1.1, -7.6, -1), (0.9, 1.4, 0.9), "#6d4e30"),
        Cube((-0.5, -5.3, -2.7), (1, 1, 1), "#ffb02e"),
        Cube((-2.7, -4, -1), (0.7, 3, 2), "#6d4e30"), Cube((2, -4, -1), (0.7, 3, 2), "#6d4e30"),
    ]),
]


COSMETICS = HATS + WINGS + CAPES + JACKETS + PETS + MORE_PETS


def main():
    tex_dir = os.path.join(ROOT, "textures", "cosmetics")
    os.makedirs(os.path.join(ROOT, "cosmetics"), exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    for f in os.listdir(tex_dir):
        if f.endswith(".png"):
            os.remove(os.path.join(tex_dir, f))
    out = []
    for cos in COSMETICS:
        cubes = [c for p in cos.parts for c in p.cubes]
        tw, th = pack(cubes)
        paint(cos, tw, th)
        tint = ""
        if cos.tint:
            tint = "#%02x%02x%02x" % shade(rgb(cos.tint), 255 / 245)
        out.append({
            "id": cos.id, "name": cos.name, "category": cos.category, "attach": cos.attach,
            "anim": cos.anim, "amp": cos.amp, "accent": cos.accent, "tintRef": tint,
            "texture": cos.id, "texW": tw, "texH": th,
            "parts": [{
                "name": p.name, "pivot": [round(v, 3) for v in p.pivot], "rot": [round(v, 4) for v in p.rot],
                "cubes": [{"uv": list(c.uv), "pos": [round(v, 3) for v in c.pos], "size": list(c.size),
                           "inflate": c.inflate, "tint": c.tint} for c in p.cubes],
            } for p in cos.parts],
        })
    with open(os.path.join(ROOT, "cosmetics", "cosmetics.json"), "w") as f:
        json.dump({"cosmetics": out}, f, separators=(",", ":"))
    n = sum(len(p["cubes"]) for c in out for p in c["parts"])
    print("wrote", len(out), "cosmetics,", n, "cubes")


if __name__ == "__main__":
    main()
