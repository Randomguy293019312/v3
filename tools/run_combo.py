import sys, math
exec(open("/home/claude/tools/run_gl4.py").read().split('if __name__')[0])
def layer(cam, look, ground, uni):
    rot, depth, hit, rdU = scene(np.array(cam, float), look, ground)
    u = dict(uni); u["CamRot"] = rot; u["Proj"] = (1 / P00, 1 / P11, P22, P32)
    px = render(pr, W, H, u, depth).astype(np.float32) / 255.0
    return px, background(rdU, hit, np.array(cam))
def combo(name, cam, look, sc_pos, tor_pos, R=380, base=170, top=800, R0=24, shape="cone", wind=0.5, str_=1.0, tdist_scale=True):
    ground = 0.0
    # supercelula
    uni_s = {"Origin": (sc_pos[0] - cam[0], -cam[1], sc_pos[1] - cam[2], 0), "StormA": (ground, 0, 0, 3.0), "StormB": (0, 1.0, 1.0, 0.0),
             "SupA": (R, base, top, wind), "Env": (0.8, 56, 1, 14000), "SunDir": (0.5, 0.75, 0.3, 0), "SkyCol": sky}
    pxs, bg = layer(cam, look, ground, uni_s)
    # tornado
    dist = math.hypot(tor_pos[0] - cam[0], tor_pos[1] - cam[2]); k = min(max(dist / 1000.0, 1.0), 7.0) if tdist_scale else 1.0
    Ht = base * k ** 0.75; R0k = R0 * k; cloudR = min(max(R0 * 3 + 60, 80), 260) * k
    sh = SHAPES[shape]
    uni_t = {"Origin": (tor_pos[0] - cam[0], -cam[1], tor_pos[1] - cam[2], 0), "StormA": (R0k, Ht, str_, 3.0),
             "StormB": (cloudR, 0.55 + 0.45 * str_, 1.0, 0.0), "SupA": (1.0, sh[0], sh[1], sh[2]), "Env": (0.75, 48, 0, 12000), "SunDir": sun, "SkyCol": sky}
    pxt, _ = layer(cam, look, ground, uni_t)
    out = pxt[..., :3] + (pxs[..., :3] + bg * (1 - pxs[..., 3:4])) * (1 - pxt[..., 3:4])
    img = (np.clip(out, 0, 1) ** (1 / 1.6) * 255).astype(np.uint8)
    Image.fromarray(img).save(f"/tmp/combo_{name}.png"); print(name, "ok")
combo("a", (-40, 6, -1000), (60, 150, 100), (0, 0), (110, 120), wind=-0.4)
combo("b", (-40, 6, -520), (60, 140, 100), (0, 0), (110, 120), R0=40, shape="cone", wind=-0.4)
combo("c", (-60, 6, -700), (60, 130, 100), (0, 0), (110, 120), R0=70, shape="wedge", wind=-0.4)
