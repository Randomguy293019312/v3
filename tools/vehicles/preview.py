import json, math
from PIL import Image, ImageDraw
D = json.load(open('/tmp/vehicles.json'))
MC = {"GLASS": (38, 52, 66), "TIRE": (28, 28, 31), "RIM": (178, 182, 192), "STEEL": (150, 155, 165), "GRILLE": (18, 18, 20), "LIGHT": (255, 244, 205),
      "TAIL": (205, 25, 25), "ARMOR": (72, 76, 83), "PLATE": (232, 232, 232), "BLACK": (16, 16, 18), "HAZARD": (232, 178, 24), "RUBBER": (34, 34, 38),
      "SPIKE": (165, 170, 180), "LIGHTBAR": (255, 140, 0)}
def col(m, c):
    if m in ("PAINT", "PAINT2"):
        return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    if m == "HAZARD" and c != 0xFFFFFFFF: return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    return MC[m]
def rotY(p, a): c, s = math.cos(a), math.sin(a); return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)
def rotX(p, a): c, s = math.cos(a), math.sin(a); return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)
def draw_vehicle(d, yaw, pitch, W=520, H=330, scale=70, steer=0.0, anchor=0.0, wheelrot=0.0):
    img = Image.new("RGBA", (W, H), (150, 185, 225, 255)); dr = ImageDraw.Draw(img, "RGBA")
    dr.rectangle([0, int(H * 0.68), W, H], fill=(96, 128, 78, 255))
    polys = []
    L = (0.45, 0.8, 0.35); ln = math.sqrt(sum(x * x for x in L)); L = tuple(x / ln for x in L)
    def add(quad, offset=(0, 0, 0), rot=None, spike=False):
        pts = []
        for i in range(4):
            p = quad["p"][i * 3:i * 3 + 3]
            if rot: p = rot(p)
            p = (p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]); pts.append(p)
        n = quad["n"]
        if rot: n = rot(n)
        # camera transform
        cp = [rotX(rotY(p, math.radians(yaw)), -math.radians(pitch)) for p in pts]; cn = rotX(rotY(n, math.radians(yaw)), -math.radians(pitch))
        if cn[2] > 0.0 and quad["m"] not in ("GLASS",) and False: return
        depth = sum(p[2] for p in cp) / 4
        lam = max(0.0, n[0] * L[0] + n[1] * L[1] + n[2] * L[2]); shade = 0.42 + 0.62 * lam
        base = col(quad["m"], quad["c"]); em = quad["m"] in ("LIGHT", "TAIL", "LIGHTBAR")
        rgb = tuple(min(255, int(x * (1.0 if em else shade))) for x in base)
        a = 150 if quad["m"] == "GLASS" else 255
        screen = [(W / 2 + p[0] * scale, H * 0.68 - p[1] * scale + 0) for p in cp]
        polys.append((depth, screen, rgb + (a,), cn[2]))
    for q in d["body"]: add(q)
    for (wx, wy, wz) in d["wheelPos"]:
        front = wz > 0
        def rot(p, front=front):
            p = rotX(p, 0) ; x, y, z = p
            a = wheelrot; y2 = y * math.cos(a) - z * math.sin(a); z2 = y * math.sin(a) + z * math.cos(a)
            if front and steer: c, s = math.cos(steer), math.sin(steer); x, z2 = x * c + z2 * s, -x * s + z2 * c
            return (x, y2, z2)
        for q in d["wheel"]: add(q, (wx, wy, wz), rot)
    for (sx, sy, sz) in d["spikePos"]:
        for q in d["spike"]: add(q, (sx, sy - anchor * 0.75, sz))
    polys.sort(key=lambda t: -t[0])    # far first (camera looks down -z after rotation? use depth sign)
    # camera looks along +z after rotation -> larger z is farther
    for depth, screen, rgb, nz in polys:
        dr.polygon(screen, fill=rgb)
    return img
for name, label in [("DOMINATOR", "Dominator (armoured chaser)"), ("TIV", "TIV (tornado intercept vehicle)"), ("CHASER_SUV", "Chaser SUV")]:
    d = D[name]
    ims = [draw_vehicle(d, 215, 14), draw_vehicle(d, 90, 4, scale=62), draw_vehicle(d, 330, 18)]
    sheet = Image.new("RGB", (520 * 3, 330 + 26), (30, 32, 38)); dd = ImageDraw.Draw(sheet)
    for i, im in enumerate(ims): sheet.paste(im.convert("RGB"), (i * 520, 26))
    dd.text((8, 6), f"{label} - front 3/4, side, rear 3/4   (body quads: {d['quadsBody']})", fill=(255, 255, 255))
    sheet.save(f"/tmp/veh_{name}.png")
print("ok")
