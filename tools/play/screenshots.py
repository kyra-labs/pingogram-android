#!/usr/bin/env python3
"""Pads docs/screenshots/ to what Google Play accepts (docs/PLAY_STORE.md §4), or to exactly 9:16 for other stores.

Play takes screenshots whose long side is at most twice the short side; the phone screenshots are 540 x 1171 (2.17:1).
Each one is padded, centred, to 9:16 on its own background (the colour of its top-left pixel, the screen's surface),
at its own resolution: nothing is scaled, so nothing blurs. Pictures already within 2:1 (the tablet ones) are copied.

With --exact, every picture is padded to exactly 9:16 (16:9 when it is wider than tall) instead, the smallest such
size that holds it, as AppGallery asks (docs/STORES.md): 540 x 1171 becomes 666 x 1184.

Usage: python3 tools/play/screenshots.py [--exact] [source_dir] [output_dir]
Defaults: docs/screenshots and build/play/screenshots. Needs Pillow (pip install pillow).
"""

import math
import sys
from pathlib import Path

from PIL import Image

MAX_RATIO = 2.0
TARGET_RATIO = 16 / 9


def pad(image: Image.Image) -> Image.Image:
    width, height = image.size
    long_side, short_side = max(width, height), min(width, height)
    if long_side / short_side <= MAX_RATIO:
        return image
    background = image.getpixel((0, 0))
    if height > width:
        size = (round(height / TARGET_RATIO), height)
    else:
        size = (width, round(width / TARGET_RATIO))
    canvas = Image.new(image.mode, size, background)
    canvas.paste(image, ((size[0] - width) // 2, (size[1] - height) // 2))
    return canvas


def pad_exact(image: Image.Image) -> Image.Image:
    width, height = image.size
    short_unit, long_unit = 9, 16
    if height >= width:
        unit = max(math.ceil(width / short_unit), math.ceil(height / long_unit))
        size = (short_unit * unit, long_unit * unit)
    else:
        unit = max(math.ceil(width / long_unit), math.ceil(height / short_unit))
        size = (long_unit * unit, short_unit * unit)
    if size == (width, height):
        return image
    canvas = Image.new(image.mode, size, image.getpixel((0, 0)))
    canvas.paste(image, ((size[0] - width) // 2, (size[1] - height) // 2))
    return canvas


def main() -> int:
    arguments = [argument for argument in sys.argv[1:] if argument != "--exact"]
    exact = len(arguments) != len(sys.argv) - 1
    source = Path(arguments[0] if len(arguments) > 0 else "docs/screenshots")
    output = Path(arguments[1] if len(arguments) > 1 else "build/play/screenshots")
    output.mkdir(parents=True, exist_ok=True)
    pictures = sorted(source.glob("*.png"))
    if not pictures:
        print(f"No PNG files in {source}", file=sys.stderr)
        return 1
    for picture in pictures:
        with Image.open(picture) as image:
            padded = (pad_exact if exact else pad)(image.convert("RGB"))
            padded.save(output / picture.name)
            print(f"{picture.name}: {image.size[0]}x{image.size[1]} -> {padded.size[0]}x{padded.size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
