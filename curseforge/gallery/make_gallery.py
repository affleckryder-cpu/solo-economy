"""Turns raw in-game screenshots into captioned 1920x1080 gallery images, in the roadmap's style.

Add a shot to SHOTS and run:  python curseforge/gallery/make_gallery.py
Each output is a JPEG well under CurseForge's 2 MB limit.
"""
import os
import subprocess
import tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SCREENSHOTS = os.path.join(ROOT, 'run', 'screenshots')
LOGO = os.path.join(ROOT, 'curseforge', 'logo.png')
CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'

# name: (screenshot file, crop box in the screenshot's own pixels or None for the whole thing,
#        title, subtitle)
SHOTS = {
    '01-market-stall-and-broker': (
        '2026-10-04_03.33.01.png', (104, 0, 2536, 1368),
        'Market Stall and Broker',
        'Craft a stall, hire a Broker, and trade at prices that move'),
}

PAGE = '''<!DOCTYPE html><html><head><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=VT323&family=Silkscreen:wght@400;700&display=swap" rel="stylesheet">
<style>
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  html, body {{ width: 1920px; height: 1080px; overflow: hidden; background: #000; }}
  .shot {{ position: absolute; inset: 0; background: url("{shot}") center / cover no-repeat; image-rendering: auto; }}
  .fade {{ position: absolute; left: 0; right: 0; bottom: 0; height: 260px;
           background: linear-gradient(rgba(10, 14, 12, 0), rgba(10, 14, 12, 0.55) 45%, rgba(10, 14, 12, 0.9)); }}
  .bar {{ position: absolute; left: 40px; right: 40px; bottom: 32px; height: 112px; display: flex; align-items: center; gap: 22px;
          background: #2b2b2b; border: 4px solid #141414; outline: 6px solid #c6c6c6; box-shadow: 0 0 0 10px #000; padding: 0 28px; }}
  .bar img {{ width: 78px; height: 78px; image-rendering: pixelated; }}
  h1 {{ font-family: "Silkscreen", monospace; font-size: 44px; line-height: 1; letter-spacing: -3px; color: #fff; text-shadow: 4px 4px 0 #000; }}
  p {{ font-family: "VT323", monospace; font-variant-ligatures: none; font-size: 33px; line-height: 1; color: #b4b4b4; margin-top: 8px; text-shadow: 3px 3px 0 #000; }}
  .tag {{ margin-left: auto; font-family: "Silkscreen", monospace; font-size: 26px; letter-spacing: -1px; color: #55ff55; text-shadow: 3px 3px 0 #000; white-space: nowrap; }}
</style></head><body>
  <div class="shot"></div><div class="fade"></div>
  <div class="bar"><img src="{logo}" alt=""><div><h1>{title}</h1><p>{subtitle}</p></div><div class="tag">Solo Economy</div></div>
</body></html>'''


def url(path):
    return 'file:///' + path.replace('\\', '/').replace(' ', '%20')


def main():
    work = tempfile.mkdtemp(prefix='soloeconomy-gallery-')
    for name, (source, box, title, subtitle) in SHOTS.items():
        shot = Image.open(os.path.join(SCREENSHOTS, source)).convert('RGB')
        if box:
            shot = shot.crop(box)
        shot_path = os.path.join(work, name + '-shot.png')
        shot.resize((1920, 1080), Image.LANCZOS).save(shot_path)

        html_path = os.path.join(work, name + '.html')
        with open(html_path, 'w', encoding='utf-8') as page:
            page.write(PAGE.format(shot=url(shot_path), logo=url(LOGO), title=title, subtitle=subtitle))

        png_path = os.path.join(work, name + '.png')
        subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                        '--window-size=1920,1080', '--virtual-time-budget=8000', '--screenshot=' + png_path, url(html_path)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        out = os.path.join(HERE, name + '.jpg')
        Image.open(png_path).convert('RGB').save(out, quality=90, optimize=True)
        print('%s  %d KB' % (os.path.basename(out), os.path.getsize(out) // 1024))


if __name__ == '__main__':
    main()
