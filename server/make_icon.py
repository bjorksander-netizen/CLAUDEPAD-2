#!/usr/bin/env python3
"""Buat icon.ico CLAUDEPAD (piringan ungu + trackpad & kursor) tanpa aset biner."""
from PIL import Image, ImageDraw

def draw(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 108.0
    # latar gradasi ungu (didekati dengan beberapa lapis)
    for i in range(size):
        t = i / size
        r = int(0x8B + (0x3B - 0x8B) * t)
        g = int(0x7B + (0x2F - 0x7B) * t)
        b = int(0xFF + (0xA8 - 0xFF) * t)
        d.line([(0, i), (size, i)], fill=(r, g, b, 255))
    # bulatkan sudut
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size-1, size-1],
                                           radius=int(18*s), fill=255)
    img.putalpha(mask)
    d = ImageDraw.Draw(img)
    # trackpad
    d.rounded_rectangle([24*s, 32*s, 84*s, 76*s], radius=int(6*s),
                        fill=(255, 255, 255, 240))
    # kursor panah
    d.polygon([(46*s,40*s),(46*s,60*s),(51.5*s,54.5*s),(55.5*s,63*s),
               (60*s,60.5*s),(56*s,52.5*s),(63.5*s,52*s)], fill=(0x4B,0x3F,0xCC,255))
    return img

imgs = [draw(sz) for sz in (256, 128, 64, 48, 32, 16)]
imgs[0].save("icon.ico", sizes=[(i.width, i.height) for i in imgs])
print("icon.ico dibuat")
