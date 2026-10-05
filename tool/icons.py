"""Make the platforms' icons from the one drawing, ui/src/jvmMain/resources/yemoja.svg.

The window draws the SVG itself, and the website copies it. A Windows installer wants a .ico, a Linux
desktop a .png and an Android app an icon in its own vector format, so both are written here from the same shapes, and run
again whenever the drawing changes:

    python tool/icons.py

writes ui/icons/yemoja.ico, ui/icons/yemoja.png for Linux, and the Android launcher icon under
android/src/main/res.

Only what the drawing uses is read: a rect with rounded corners, circles, and paths of M, L, H, V,
C, c, s and Z. Pillow does the drawing for the .ico, four times larger and then scaled down, which
is what gives its edges their smoothing.
"""

import io
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG = os.path.join(ROOT, "ui", "src", "jvmMain", "resources", "yemoja.svg")
ICO = os.path.join(ROOT, "ui", "icons", "yemoja.ico")
PNG = os.path.join(ROOT, "ui", "icons", "yemoja.png")
RES = os.path.join(ROOT, "android", "src", "main", "res")
NS = "{http://www.w3.org/2000/svg}"

# Every size Windows asks an icon for, from a list's small glyph to a large tile.
SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]

# How much larger the .ico is drawn than its largest size, before being scaled down.
SUPER = 4

# How much of Android's 108 by 108 icon the drawing's square takes: a little over the 72 shown.
FILLS = 0.72


def numbers(text):
    return [float(n) for n in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", text)]


def points_of(d, steps=24):
    """The outlines a path draws, as lists of points, its curves cut into [steps] straight pieces."""
    shapes, points = [], []
    x = y = 0.0
    last_control = None
    for command, args in re.findall(r"([MLHVCcSsZz])([^MLHVCcSsZz]*)", d):
        values = numbers(args)
        if command == "M":
            if points:
                shapes.append(points)
            x, y = values[0], values[1]
            points = [(x, y)]
            last_control = None
        elif command == "L":
            for i in range(0, len(values), 2):
                x, y = values[i], values[i + 1]
                points.append((x, y))
            last_control = None
        elif command == "H":
            x = values[0]
            points.append((x, y))
            last_control = None
        elif command == "V":
            y = values[0]
            points.append((x, y))
            last_control = None
        elif command in "CcSs":
            relative = command.islower()
            size = 6 if command in "Cc" else 4
            for i in range(0, len(values), size):
                chunk = values[i:i + size]
                if command in "Cc":
                    c1 = (chunk[0], chunk[1])
                    c2, end = (chunk[2], chunk[3]), (chunk[4], chunk[5])
                    if relative:
                        c1 = (x + c1[0], y + c1[1])
                else:
                    c2, end = (chunk[0], chunk[1]), (chunk[2], chunk[3])
                    c1 = (2 * x - last_control[0], 2 * y - last_control[1]) if last_control else (x, y)
                if relative:
                    c2 = (x + c2[0], y + c2[1])
                    end = (x + end[0], y + end[1])
                for step in range(1, steps + 1):
                    t = step / steps
                    u = 1 - t
                    points.append((
                        u ** 3 * x + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t ** 3 * end[0],
                        u ** 3 * y + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t ** 3 * end[1],
                    ))
                last_control = c2
                x, y = end
        elif command in "Zz":
            if points:
                shapes.append(points)
            points = []
            last_control = None
    if points:
        shapes.append(points)
    return shapes


def drawing():
    """The drawing's shapes in order, as (kind, attributes, clipped to the tile) triples."""
    root = ET.parse(SVG).getroot()
    shapes = []
    for element in root:
        if element.tag == NS + "g":
            for inner in element:
                shapes.append((inner.tag[len(NS):], inner.attrib, True))
        elif element.tag in (NS + "rect", NS + "path", NS + "circle"):
            shapes.append((element.tag[len(NS):], element.attrib, False))
    return shapes


def ico():
    """The drawing at every size Windows asks for, in one .ico."""
    side = SIZES[-1] * SUPER
    scale = side / 256
    picture = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    tile = Image.new("L", (side, side), 0)
    ImageDraw.Draw(tile).rounded_rectangle([0, 0, side - 1, side - 1], radius=56 * scale, fill=255)
    for kind, attributes, clipped in drawing():
        layer = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        pen = ImageDraw.Draw(layer)
        fill = attributes["fill"]
        if kind == "rect":
            radius = float(attributes.get("rx", 0)) * scale
            pen.rounded_rectangle([0, 0, side - 1, side - 1], radius=radius, fill=fill)
        elif kind == "circle":
            cx, cy, r = (float(attributes[k]) * scale for k in ("cx", "cy", "r"))
            pen.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)
        else:
            for outline in points_of(attributes["d"]):
                pen.polygon([(px * scale, py * scale) for px, py in outline], fill=fill)
        if clipped:
            layer.putalpha(Image.composite(layer.getchannel("A"), Image.new("L", (side, side), 0), tile))
        picture = Image.alpha_composite(picture, layer)
    os.makedirs(os.path.dirname(ICO), exist_ok=True)
    picture.resize((SIZES[-1], SIZES[-1]), Image.LANCZOS).save(ICO, sizes=[(s, s) for s in SIZES])
    print(ICO)
    # A Linux desktop takes one picture and scales it, so the largest size goes alone.
    picture.resize((SIZES[-1], SIZES[-1]), Image.LANCZOS).save(PNG)
    print(PNG)


def android():
    """
    The launcher icon in Android's adaptive form: the tile's blue behind, the sea and the fish in
    front, and the same shapes alone for a phone that tints its icons.

    A launcher shows the middle 72 of the icon's 108 through whatever mask it uses, so the
    drawing's square is drawn a little larger than that, [FILLS] of the whole, and centred: the
    sea reaches every edge of what is shown, and the fish sits well within the 66 a launcher
    promises never to cut.
    """
    shapes = drawing()
    background = next(a["fill"] for kind, a, _ in shapes if kind == "rect")
    paths = []
    for kind, attributes, _ in shapes:
        if kind == "rect":
            continue
        if kind == "circle":
            cx, cy, r = (float(attributes[k]) for k in ("cx", "cy", "r"))
            d = f"M {cx - r},{cy} a {r},{r} 0 1,0 {2 * r},0 a {r},{r} 0 1,0 {-2 * r},0 Z"
        else:
            d = attributes["d"]
        paths.append((d, attributes["fill"]))
    header = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Written by tool/icons.py from ui/src/jvmMain/resources/yemoja.svg. -->\n'

    def vector(coloured):
        lines = [
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            '    android:width="108dp"',
            '    android:height="108dp"',
            '    android:viewportWidth="256"',
            '    android:viewportHeight="256">',
        ]
        shift = 256 * (1 - FILLS) / 2
        lines.append(
            f'    <group android:scaleX="{FILLS}" android:scaleY="{FILLS}" '
            f'android:translateX="{shift:g}" android:translateY="{shift:g}">'
        )
        for d, fill in paths:
            colour = fill if coloured else "#FFFFFFFF"
            lines.append(f'        <path android:fillColor="{colour}" android:pathData="{d}" />')
        lines.append("    </group>")
        lines.append("</vector>")
        return header + "\n".join(lines) + "\n"

    def write(path, text):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with io.open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(text)
        print(path)

    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), vector(True))
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), vector(False))
    write(os.path.join(RES, "values", "ic_launcher_background.xml"), header +
          f'<resources>\n    <color name="ic_launcher_background">{background}</color>\n</resources>\n')
    adaptive = header + (
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        '</adaptive-icon>\n'
    )
    write(os.path.join(RES, "mipmap-anydpi", "ic_launcher.xml"), adaptive)


if __name__ == "__main__":
    ico()
    android()
