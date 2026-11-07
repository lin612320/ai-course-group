# -*- coding: utf-8 -*-
"""生成演示用背景：浅色内容页 + 深色封面。

光斑用"按强度混合"而不是加法叠加——白底上加色会被截断，看不出来。
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 2667, 1500
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
nx, ny = xx / W, yy / H


def lerp(c1, c2, t):
    c1 = np.array(c1, dtype=np.float32)
    c2 = np.array(c2, dtype=np.float32)
    return c1 + (c2 - c1) * t[..., None]


def glow_mix(im, cx, cy, sigma, color, strength):
    """把一块颜色按高斯权重混合进去，strength 0~1。"""
    d2 = (nx - cx) ** 2 + (ny - cy) ** 2
    g = (np.exp(-d2 / (2 * sigma * sigma)) * strength)[..., None]
    return im * (1 - g) + np.array(color, dtype=np.float32) * g


def grid(im, step, alpha, color):
    ov = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for x in range(0, W, step):
        d.line([(x, 0), (x, H)], fill=color + (alpha,), width=1)
    for y in range(0, H, step):
        d.line([(0, y), (W, y)], fill=color + (alpha,), width=1)
    return Image.alpha_composite(im.convert('RGBA'), ov).convert('RGB')


# ---------------- 浅色内容页 ----------------
t = np.clip(nx * 0.45 + ny * 0.55, 0, 1)
img = lerp((255, 255, 255), (223, 234, 250), t)
img = glow_mix(img, 0.94, 0.02, 0.30, (172, 205, 252), 0.85)
img = glow_mix(img, 0.02, 0.98, 0.30, (198, 224, 240), 0.75)
img = glow_mix(img, 0.46, 0.40, 0.46, (255, 255, 255), 0.55)
img = np.clip(img, 0, 255).astype(np.uint8)
im = Image.fromarray(img, 'RGB').filter(ImageFilter.GaussianBlur(0.3))
im = grid(im, 74, 38, (128, 162, 212))
im.save('assets/bg_light.png')
print('bg_light.png', im.size, im.getpixel((W - 60, 60)), im.getpixel((40, H - 40)))

# ---------------- 深色封面 ----------------
t = np.clip(nx * 0.30 + ny * 0.70, 0, 1)
img = lerp((6, 13, 27), (24, 45, 78), t ** 0.9)
img = glow_mix(img, 0.10, 0.34, 0.24, (0, 158, 176), 0.80)
img = glow_mix(img, 0.80, 0.10, 0.28, (40, 104, 232), 0.78)
img = glow_mix(img, 0.97, 0.90, 0.30, (8, 22, 44), 0.85)
img = np.clip(img, 0, 255).astype(np.uint8)
im = Image.fromarray(img, 'RGB').filter(ImageFilter.GaussianBlur(0.4))
im = grid(im, 96, 16, (170, 205, 255))
im.save('assets/bg_dark2.png')
print('bg_dark2.png', im.size, im.getpixel((260, 500)), im.getpixel((W - 300, 150)))
