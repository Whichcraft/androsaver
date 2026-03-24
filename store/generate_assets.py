#!/usr/bin/env python3
"""Generate Play Store assets for AndroSaver."""

from PIL import Image, ImageDraw, ImageFont
import math, os

OUT = os.path.dirname(__file__)

# Palette
BG       = (15, 15, 25)       # near-black blue
ACCENT   = (80, 160, 255)     # sky blue
WHITE    = (255, 255, 255)
SUBTEXT  = (160, 180, 210)

FONT_BOLD   = "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/ubuntu/Ubuntu-R.ttf"


def draw_background(draw, w, h):
    """Subtle radial-ish gradient via concentric ellipses."""
    for i in range(60, 0, -1):
        t = i / 60
        r = int(15 + t * 20)
        g = int(15 + t * 25)
        b = int(25 + t * 50)
        margin = (1 - t) * min(w, h) * 0.6
        x0, y0 = margin, margin * 0.5
        x1, y1 = w - margin, h - margin * 0.5
        if x1 > x0 and y1 > y0:
            draw.ellipse([x0, y0, x1, y1], fill=(r, g, b))


def draw_play_icon(draw, cx, cy, size, color=ACCENT):
    """Draw a circular frame with a photo/slideshow symbol inside."""
    r = size // 2
    # Outer circle
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    # Inner white circle
    ir = int(r * 0.85)
    draw.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=BG)
    # Mountain/landscape silhouette (simple photo icon)
    # Sun
    sr = int(ir * 0.22)
    sx, sy = cx - int(ir * 0.25), cy - int(ir * 0.25)
    draw.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=ACCENT)
    # Mountain polygon
    pts = [
        (cx - ir + 4, cy + ir - 4),
        (cx - int(ir * 0.3), cy - int(ir * 0.1)),
        (cx + int(ir * 0.15), cy + int(ir * 0.25)),
        (cx + int(ir * 0.4), cy - int(ir * 0.2)),
        (cx + ir - 4, cy + ir - 4),
    ]
    draw.polygon(pts, fill=ACCENT)


def font(path, size):
    return ImageFont.truetype(path, size)


# ── App Icon 512×512 ─────────────────────────────────────────────────────────
def make_icon():
    W, H = 512, 512
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    draw_background(draw, W, H)
    draw_play_icon(draw, W // 2, H // 2, 340)
    img.save(os.path.join(OUT, "icon_512.png"))
    print("icon_512.png")


# ── TV Banner 320×180 ────────────────────────────────────────────────────────
def make_tv_banner():
    W, H = 320, 180
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    draw_background(draw, W, H)
    draw_play_icon(draw, 70, H // 2, 110)
    f_title = font(FONT_BOLD, 30)
    f_sub   = font(FONT_REGULAR, 13)
    draw.text((140, 60), "AndroSaver", font=f_title, fill=WHITE)
    draw.text((141, 100), "Photo Screensaver for TV", font=f_sub, fill=SUBTEXT)
    img.save(os.path.join(OUT, "tv_banner_320x180.png"))
    print("tv_banner_320x180.png")


# ── Feature Graphic 1024×500 ─────────────────────────────────────────────────
def make_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    draw_background(draw, W, H)
    draw_play_icon(draw, W // 2, H // 2, 280)
    f_title = font(FONT_BOLD, 72)
    f_sub   = font(FONT_REGULAR, 28)
    # Measure and centre text
    title = "AndroSaver"
    bbox = draw.textbbox((0, 0), title, font=f_title)
    tw = bbox[2] - bbox[0]
    draw.text(((W - tw) // 2, H - 160), title, font=f_title, fill=WHITE)
    sub = "Photo Screensaver for Android TV"
    sbbox = draw.textbbox((0, 0), sub, font=f_sub)
    sw = sbbox[2] - sbbox[0]
    draw.text(((W - sw) // 2, H - 75), sub, font=f_sub, fill=SUBTEXT)
    img.save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("feature_graphic_1024x500.png")


# ── Screenshot (1920×1080 TV) ────────────────────────────────────────────────
def make_screenshot():
    W, H = 1920, 1080
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    draw_background(draw, W, H)

    # Simulated photo frame
    fw, fh = 900, 560
    fx, fy = (W - fw) // 2, 120
    draw.rectangle([fx - 4, fy - 4, fx + fw + 4, fy + fh + 4], fill=ACCENT)
    draw.rectangle([fx, fy, fx + fw, fy + fh], fill=(30, 35, 55))
    # Fake landscape in frame
    draw.rectangle([fx, fy + fh // 2, fx + fw, fy + fh], fill=(20, 60, 40))
    pts = [
        (fx, fy + fh),
        (fx + 200, fy + 200),
        (fx + 400, fy + 350),
        (fx + 600, fy + 150),
        (fx + fw, fy + fh),
    ]
    draw.polygon(pts, fill=(30, 80, 50))
    sr = 60
    draw.ellipse([fx + 650, fy + 60, fx + 650 + sr*2, fy + 60 + sr*2], fill=(255, 220, 80))

    f_title = font(FONT_BOLD, 64)
    f_sub   = font(FONT_REGULAR, 32)
    f_small = font(FONT_REGULAR, 24)

    draw.text((fx, fy + fh + 40), "AndroSaver", font=f_title, fill=WHITE)
    draw.text((fx, fy + fh + 115), "Your photos. Your TV. Always on.", font=f_sub, fill=SUBTEXT)

    # Feature pills
    features = ["Google Drive", "Synology NAS", "6 Transitions", "Fully Customizable"]
    px = fx
    for feat in features:
        bbox = draw.textbbox((0, 0), feat, font=f_small)
        bw = bbox[2] - bbox[0] + 28
        draw.rounded_rectangle([px, fy + fh + 170, px + bw, fy + fh + 205], radius=12, fill=ACCENT)
        draw.text((px + 14, fy + fh + 172), feat, font=f_small, fill=BG)
        px += bw + 16

    img.save(os.path.join(OUT, "screenshot_tv_1920x1080.png"))
    print("screenshot_tv_1920x1080.png")


TABLET_BG     = (18, 18, 28)
SURFACE       = (28, 30, 45)
SURFACE2      = (35, 38, 58)
DIVIDER       = (45, 50, 75)
TOGGLE_ON     = (80, 160, 255)
TOGGLE_OFF    = (60, 65, 90)
STATUS_BAR    = (10, 10, 18)
NAV_BAR       = (10, 10, 18)


def tablet_font(size, bold=False):
    path = FONT_BOLD if bold else FONT_REGULAR
    return ImageFont.truetype(path, size)


def draw_status_bar(draw, w, y=0, h=36):
    draw.rectangle([0, y, w, y + h], fill=STATUS_BAR)
    f = tablet_font(14)
    draw.text((16, y + 10), "9:41", font=f, fill=WHITE)
    draw.text((w - 80, y + 10), "  ▲ 100%", font=f, fill=WHITE)


def draw_nav_bar(draw, w, y, h=48):
    draw.rectangle([0, y, w, y + h], fill=NAV_BAR)
    cx = w // 2
    draw.text((cx - 60, y + 12), "◀   ●   ■", font=tablet_font(18), fill=SUBTEXT)


def draw_toggle(draw, x, y, on=True):
    W, H = 52, 28
    color = TOGGLE_ON if on else TOGGLE_OFF
    draw.rounded_rectangle([x, y, x + W, y + H], radius=14, fill=color)
    knob_x = x + W - 18 if on else x + 4
    draw.ellipse([knob_x, y + 4, knob_x + 20, y + 24], fill=WHITE)


def draw_row(draw, w, y, label, sublabel=None, toggle=None, f_label=None, f_sub=None):
    """Draw a single settings row."""
    row_h = 72 if sublabel else 56
    draw.rectangle([0, y, w, y + row_h], fill=SURFACE)
    draw.rectangle([0, y + row_h - 1, w, y + row_h], fill=DIVIDER)
    lx = 24
    ty = y + 12 if sublabel else y + 18
    draw.text((lx, ty), label, font=f_label or tablet_font(18, bold=True), fill=WHITE)
    if sublabel:
        draw.text((lx, ty + 26), sublabel, font=f_sub or tablet_font(14), fill=SUBTEXT)
    if toggle is not None:
        draw_toggle(draw, w - 80, y + (row_h - 28) // 2, on=toggle)
    return row_h


def draw_section_header(draw, w, y, text):
    draw.rectangle([0, y, w, y + 36], fill=TABLET_BG)
    draw.text((24, y + 10), text.upper(), font=tablet_font(12), fill=ACCENT)
    return 36


def draw_tablet_frame(W, H):
    """Return image + draw + content bounds (after status/nav bars)."""
    img = Image.new("RGB", (W, H), TABLET_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw, W)
    draw_nav_bar(draw, W, H - 48)
    return img, draw


# ── Tablet Screenshot 1: Main Settings ───────────────────────────────────────
def make_tablet_screenshot_settings():
    W, H = 1280, 800
    img, draw = draw_tablet_frame(W, H)

    # App bar
    draw.rectangle([0, 36, W, 36 + 56], fill=SURFACE2)
    draw.text((24, 50), "AndroSaver", font=tablet_font(22, bold=True), fill=WHITE)
    draw.text((24, 76), "Settings", font=tablet_font(13), fill=SUBTEXT)

    y = 92
    y += draw_section_header(draw, W, y, "Image Sources")
    y += draw_row(draw, W, y, "Google Drive", "Stream photos from your Drive folder", toggle=True)
    y += draw_row(draw, W, y, "Google Drive Setup", "OAuth credentials and folder ID")
    y += draw_row(draw, W, y, "Synology NAS", "Stream photos from your NAS", toggle=False)
    y += draw_row(draw, W, y, "Synology NAS Setup", "Host, credentials, and folder path")

    y += draw_section_header(draw, W, y, "Slideshow")
    y += draw_row(draw, W, y, "Time per Image", "10 seconds")
    y += draw_row(draw, W, y, "Transition Speed", "1.5 seconds")
    y += draw_row(draw, W, y, "Transition Effect", "Crossfade")

    img.save(os.path.join(OUT, "screenshot_tablet_settings.png"))
    print("screenshot_tablet_settings.png")


# ── Tablet Screenshot 2: Google Drive Setup ──────────────────────────────────
def make_tablet_screenshot_drive():
    W, H = 1280, 800
    img, draw = draw_tablet_frame(W, H)

    # App bar with back arrow
    draw.rectangle([0, 36, W, 36 + 56], fill=SURFACE2)
    draw.text((24, 52), "←  Google Drive Setup", font=tablet_font(22, bold=True), fill=WHITE)

    y = 92
    y += draw_section_header(draw, W, y, "OAuth Credentials")

    # Input fields
    for label, placeholder, filled in [
        ("OAuth Client ID", "Paste your client ID here", True),
        ("OAuth Client Secret", "Paste your client secret here", True),
        ("Folder ID", "Leave blank for root of My Drive", False),
    ]:
        draw.rectangle([0, y, W, y + 80], fill=SURFACE)
        draw.rectangle([0, y + 79, W, y + 80], fill=DIVIDER)
        draw.text((24, y + 10), label, font=tablet_font(13), fill=SUBTEXT)
        val = "••••••••••••••••••••••••" if filled else placeholder
        clr = WHITE if filled else (80, 90, 120)
        draw.text((24, y + 34), val, font=tablet_font(18), fill=clr)
        y += 80

    y += 24

    # Authorize button
    bw, bh = 380, 52
    bx = (W - bw) // 2
    draw.rounded_rectangle([bx, y, bx + bw, y + bh], radius=10, fill=ACCENT)
    label = "Authorize with Google"
    bbox = draw.textbbox((0, 0), label, font=tablet_font(18, bold=True))
    lw = bbox[2] - bbox[0]
    draw.text((bx + (bw - lw) // 2, y + 14), label, font=tablet_font(18, bold=True), fill=BG)

    y += bh + 28
    draw_section_header(draw, W, y, "Status")
    y += 36
    draw.rectangle([0, y, W, y + 56], fill=SURFACE)
    draw.text((24, y + 18), "✓  Connected as user@gmail.com", font=tablet_font(16), fill=(100, 220, 120))

    img.save(os.path.join(OUT, "screenshot_tablet_drive_setup.png"))
    print("screenshot_tablet_drive_setup.png")


# ── Tablet Screenshot 3: Screensaver running ─────────────────────────────────
def make_tablet_screenshot_screensaver():
    """Simulate the screensaver displayed on a TV, viewed from a tablet."""
    W, H = 1280, 800
    img = Image.new("RGB", (W, H), (5, 5, 10))
    draw = ImageDraw.Draw(img)

    # TV bezel
    tv_w, tv_h = 980, 580
    tx, ty = (W - tv_w) // 2, 60
    draw.rounded_rectangle([tx - 16, ty - 12, tx + tv_w + 16, ty + tv_h + 50], radius=18, fill=(30, 30, 35))
    # Screen
    draw.rectangle([tx, ty, tx + tv_w, ty + tv_h], fill=(20, 25, 40))
    # Sky gradient (fake)
    for i in range(tv_h // 2):
        t = i / (tv_h // 2)
        c = (int(30 + t * 20), int(60 + t * 40), int(120 + t * 60))
        draw.line([tx, ty + i, tx + tv_w, ty + i], fill=c)
    # Ground
    draw.rectangle([tx, ty + tv_h // 2, tx + tv_w, ty + tv_h], fill=(15, 45, 30))
    # Mountains
    mountain_pts = [
        (tx, ty + tv_h),
        (tx + 160, ty + 260),
        (tx + 330, ty + 380),
        (tx + 520, ty + 200),
        (tx + 700, ty + 310),
        (tx + 860, ty + 240),
        (tx + tv_w, ty + tv_h),
    ]
    draw.polygon(mountain_pts, fill=(25, 65, 45))
    # Sun
    draw.ellipse([tx + 760, ty + 60, tx + 860, ty + 160], fill=(255, 210, 60))
    # Stand
    draw.rectangle([tx + tv_w // 2 - 40, ty + tv_h + 50, tx + tv_w // 2 + 40, ty + tv_h + 80], fill=(25, 25, 30))
    draw.rectangle([tx + tv_w // 2 - 90, ty + tv_h + 78, tx + tv_w // 2 + 90, ty + tv_h + 90], fill=(25, 25, 30))

    # Caption below
    f_title = tablet_font(28, bold=True)
    f_sub = tablet_font(16)
    caption = "Your photos, beautifully displayed"
    bbox = draw.textbbox((0, 0), caption, font=f_title)
    cw = bbox[2] - bbox[0]
    draw.text(((W - cw) // 2, ty + tv_h + 100), caption, font=f_title, fill=WHITE)
    sub = "AndroSaver turns your TV into a living photo frame"
    sbbox = draw.textbbox((0, 0), sub, font=f_sub)
    sw = sbbox[2] - sbbox[0]
    draw.text(((W - sw) // 2, ty + tv_h + 140), sub, font=f_sub, fill=SUBTEXT)

    img.save(os.path.join(OUT, "screenshot_tablet_screensaver.png"))
    print("screenshot_tablet_screensaver.png")


if __name__ == "__main__":
    make_icon()
    make_tv_banner()
    make_feature_graphic()
    make_screenshot()
    make_tablet_screenshot_settings()
    make_tablet_screenshot_drive()
    make_tablet_screenshot_screensaver()
    print("Done — assets saved to store/")
