import sys
exec(open("/home/claude/tools/run_gl.py").read().split("sun = (0.35")[0])
from PIL import Image, ImageDraw
sun = (0.35, 0.8, 0.2, 0); sky = (0.5, 0.6, 0.8, 1)
SHAPES = {"rope": (0.30, 0.30, 1.2), "cone": (0.42, 1.2, 1.3), "stovepipe": (0.80, 0.30, 1.2), "wedge": (1.0, 0.75, 1.3)}
def reach_from(str_):                      # igual ao StormRenderer.java
    x = min(max(str_ / 0.6, 0.0), 1.0); return 1 - (1 - x) ** 2
def tor(name, shape, R0, dist, look_h=60, H=110, haze=9000, str_=1.0, forming=False, lod=True):
    cam = (0, 6, -dist); cloudR = min(max(R0 * 6 + 90, 110), 480)
    k = min(max(dist / 1200.0, 1.0), 6.0) if lod else 1.0
    R0, cloudR, H = R0 * k, cloudR * k, H * k ** 0.7; look_h = look_h * k ** 0.7
    sh = SHAPES[shape]; reach = reach_from(str_) if forming else 1.0
    run(name, cam, (0, look_h, 0), 0.0, {"Origin": (-cam[0], -cam[1], -cam[2], 0), "StormA": (R0, H, str_, 3.0),
        "StormB": (cloudR, 0.55 + 0.45 * str_, 1.0, 0.0), "SupA": (reach, sh[0], sh[1], sh[2]),
        "Env": (0.75, 48, 0, haze), "SunDir": sun, "SkyCol": sky})
def sup(name, dist, look=(150, 330, 0), R=380, hb=180, ht=950, ground=64.0, wind=-0.45, fov_look=None):
    cam = (0, 70, -dist)
    run(name, cam, look, ground, {"Origin": (0 - cam[0], -cam[1], 0 - cam[2], 0), "StormA": (ground, 0, 0, 3.0),
        "StormB": (0, 1.0, 1.0, 0.0), "SupA": (R, hb, ht, wind), "Env": (0.8, 56, 1, 14000), "SunDir": (0.5, 0.75, 0.3, 0), "SkyCol": sky})
if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "tor"):
        tor("r_cone", "cone", 16, 330); tor("r_wedge", "wedge", 60, 600, look_h=70); tor("r_pipe", "stovepipe", 24, 360)
        tor("r_rope", "rope", 6, 260); tor("r_far", "cone", 18, 5000, look_h=90); tor("r_close", "cone", 16, 110, look_h=40)
        names = ["r_cone", "r_wedge", "r_pipe", "r_rope", "r_far", "r_close"]
        sheet = Image.new("RGB", (960, 810))
        for i, n in enumerate(names):
            im = Image.open(f"/tmp/gl_{n}.png").resize((480, 270)); ImageDraw.Draw(im).text((6, 6), n, fill=(255, 255, 255)); sheet.paste(im, ((i % 2) * 480, (i // 2) * 270))
        sheet.save("/tmp/real_tor.png")
    if which in ("all", "sup"):
        sup("r_sup1", 1500); sup("r_sup2", 3200, look=(150, 400, 0)); sup("r_sup3", 900, look=(150, 300, 0))
        names = ["r_sup1", "r_sup2", "r_sup3"]
        sheet = Image.new("RGB", (960, 540))
        for i, n in enumerate(names):
            im = Image.open(f"/tmp/gl_{n}.png").resize((480, 270)); ImageDraw.Draw(im).text((6, 6), n, fill=(255, 255, 255)); sheet.paste(im, ((i % 2) * 480, (i // 2) * 270))
        sheet.save("/tmp/real_sup.png")
    if which in ("all", "form"):
        for i, s_ in enumerate([0.12, 0.25, 0.4, 0.6]): tor(f"f{i}", "wedge", 60, 600, look_h=70, str_=s_, forming=True)
        sheet = Image.new("RGB", (960, 540))
        for i in range(4):
            im = Image.open(f"/tmp/gl_f{i}.png").resize((480, 270)); ImageDraw.Draw(im).text((6, 6), f"wedge forming str={[0.12,0.25,0.4,0.6][i]} reach={reach_from([0.12,0.25,0.4,0.6][i]):.2f}", fill=(255, 255, 255)); sheet.paste(im, ((i % 2) * 480, (i // 2) * 270))
        sheet.save("/tmp/real_form.png")
