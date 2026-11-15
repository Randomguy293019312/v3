import sys, math
exec(open("/home/claude/tools/run_gl.py").read().split("W, H = 640, 360")[0].replace('fsrc = open(D + "storm.fsh").read()', 'fsrc = open(D + "sky.fsh").read()'))
import numpy as np
from PIL import Image, ImageDraw
W, H = 640, 360
P00 = 1.0 / (math.tan(math.radians(35)) * W / H); P11 = 1.0 / math.tan(math.radians(35))

def cam_rot(pitch_deg):
    p = math.radians(pitch_deg)
    fwd = np.array([0, math.sin(p), math.cos(p)]); right = np.array([1.0, 0, 0]); up = np.cross(right, fwd)
    # (x direita = +x quando olhando +z? usamos right = (-1,0,0) para base destra)
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right); up = np.cross(right, fwd)
    rot = np.eye(4); rot[:3, 0] = right; rot[:3, 1] = up; rot[:3, 2] = -fwd
    return rot, fwd, right, up

def rays(rot):
    ys, xs = np.mgrid[0:H, 0:W]
    nx = (xs + 0.5) / W * 2 - 1; ny = 1 - (ys + 0.5) / H * 2
    vd = np.stack([nx / P00, ny / P11, -np.ones_like(nx)], -1)
    rd = vd @ rot[:3, :3].T
    return rd / np.linalg.norm(rd, axis=-1, keepdims=True)

def shot(name, pitch, sun_elev, realism, coverage, dark=0.0, base=190.0, thick=None, steps=32, sunsize=4.0, glow=1.0):
    rot, fwd, _, _ = cam_rot(pitch)
    rd = rays(rot)
    e = math.radians(sun_elev); sun = (0.0, math.sin(e), math.cos(e))
    day = float(np.clip(sun[1] * 1.6 + 0.3, 0.05, 1.0))
    thick = thick if thick is not None else 12 + 150 * realism ** 0.8
    u = {"CamRot": rot, "Proj": (1 / P00, 1 / P11, 0, 0), "CamPos": (0, 70, 0, 3.0),
         "SunDir": (*sun, math.radians(sunsize / 2)), "SunPar": (glow, day, 1, 0),
         "Cloud0": (coverage, realism, base, thick), "Cloud1": (123.0, 45.0, dark, 1), "SkyCol": (0.55, 0.65, 0.85, 1),
         "Env": (steps, 0, 0, 0)}
    px = render(pr, W, H, u, np.ones((H, W), np.float32)).astype(np.float32) / 255.0
    # fundo: ceu estilo vanilla
    t = np.clip(rd[..., 1] * 1.6, 0, 1)[..., None]
    sky = np.array([0.74, 0.82, 0.95]) * (1 - t) + np.array([0.42, 0.58, 0.92]) * t
    sky = sky * (0.35 + 0.65 * day) * (1 - 0.55 * dark)
    sky = np.where((rd[..., 1] < 0)[..., None], np.array([0.30, 0.42, 0.22]) * (0.4 + 0.6 * day), sky)  # chao
    out = px[..., :3] + sky * (1 - px[..., 3:4])
    img = (np.clip(out, 0, 1) ** (1 / 1.5) * 255).astype(np.uint8)
    Image.fromarray(img).save(f"/tmp/sky_{name}.png")
    print(name, "alpha medio", round(float(px[..., 3].mean()), 3))

shot("noon", 22, 58, 0.8, 0.45)
shot("golden", 8, 12, 0.8, 0.40)
shot("sunset", 4, 2, 0.8, 0.40)
shot("blocky", 22, 58, 0.0, 0.45)
shot("mid", 22, 58, 0.5, 0.25)
shot("storm", 22, 40, 0.8, 0.9, dark=0.85)
labels = ["noon  realism .8", "golden hour (sun 12 deg)", "sunset (sun 2 deg)", "realism 0 (blocky)", "realism .5, less clouds", "overcast / storm"]
names = ["noon", "golden", "sunset", "blocky", "mid", "storm"]
sheet = Image.new("RGB", (960, 810))
for i, n in enumerate(names):
    im = Image.open(f"/tmp/sky_{n}.png").resize((480, 270)); ImageDraw.Draw(im).text((6, 6), labels[i], fill=(255, 255, 255))
    sheet.paste(im, ((i % 2) * 480, (i // 2) * 270))
sheet.save("/tmp/sky_sheet.png")
