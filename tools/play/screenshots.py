#!/usr/bin/env python3
"""Pads docs/screenshots/ to what Google Play accepts (docs/PLAY_STORE.md §4).

Play takes screenshots whose long side is at most twice the short side; the phone screenshots are 540 x 1171 (2.17:1).
Each one is padded, centred, to 9:16 on its own background (the colour of its top-left pixel, the screen's surface),
at its own resolution: nothing is scaled, so nothing blurs. Pictures already within 2:1 (the tablet ones) are copied.

Usage: python3 tools/play/screenshots.py [source_dir] [output_dir]
Defaults: docs/screenshots and build/play/screenshots. Needs Pillow (pip install pillow).
"""

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


def main() -> int:
    source = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots")
    output = Path(sys.argv[2] if len(sys.argv) > 2 else "build/play/screenshots")
    output.mkdir(parents=True, exist_ok=True)
    pictures = sorted(source.glob("*.png"))
    if not pictures:
        print(f"No PNG files in {source}", file=sys.stderr)
        return 1
    for picture in pictures:
        with Image.open(picture) as image:
            padded = pad(image.convert("RGB"))
            padded.save(output / picture.name)
            print(f"{picture.name}: {image.size[0]}x{image.size[1]} -> {padded.size[0]}x{padded.size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
