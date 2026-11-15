import json, os, random, math
from PIL import Image, ImageDraw

ROOT = "/home/claude/meteorologia/src/main"
RES = ROOT + "/resources"
A = RES + "/assets/meteorologia"
D = RES + "/data/meteorologia"
random.seed(7)

def wj(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

# ------------------------------------------------------------ texturas ------
def noisy(img, amt=10):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            n = random.randint(-amt, amt)
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)

def save(img, rel):
    p = A + "/textures/" + rel
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)

def new(color, size=16):
    return Image.new("RGBA", (size, size), color)

# --- blocos
im = new((58, 64, 72, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(30, 34, 40, 255))
d.ellipse([3, 3, 12, 12], fill=(8, 40, 22, 255), outline=(60, 220, 110, 255))
d.line([8, 8, 12, 4], fill=(120, 255, 160, 255)); d.point((8, 8), fill=(255, 255, 255, 255))
d.rectangle([1, 1, 3, 2], fill=(200, 60, 50, 255)); noisy(im, 6); save(im, "block/radar.png")

im = new((236, 236, 242, 255)); d = ImageDraw.Draw(im)
for x in range(0, 16, 4): d.rectangle([x, 0, x + 1, 15], fill=(200, 40, 40, 255))
d.rectangle([0, 0, 15, 15], outline=(150, 150, 160, 255)); noisy(im, 5); save(im, "block/anemometer.png")

im = new((172, 128, 62, 255)); d = ImageDraw.Draw(im)
d.ellipse([3, 3, 12, 12], fill=(245, 240, 225, 255), outline=(60, 40, 20, 255))
d.line([8, 8, 11, 5], fill=(190, 30, 30, 255)); d.point((8, 8), fill=(0, 0, 0, 255))
for a in range(0, 360, 45):
    x = 7.5 + 4.6 * math.cos(math.radians(a)); y = 7.5 + 4.6 * math.sin(math.radians(a))
    d.point((int(x), int(y)), fill=(40, 40, 40, 255))
noisy(im, 6); save(im, "block/barometer.png")

im = new((238, 240, 244, 255)); d = ImageDraw.Draw(im)
for y in range(1, 16, 2): d.line([0, y, 15, y], fill=(176, 180, 190, 255))
d.rectangle([0, 0, 15, 15], outline=(120, 124, 134, 255)); noisy(im, 4); save(im, "block/thermometer.png")

im = new((74, 104, 146, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(36, 52, 80, 255))
for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]: d.point((x, y), fill=(220, 230, 245, 255))
d.rectangle([5, 5, 10, 10], fill=(210, 225, 245, 255), outline=(30, 40, 60, 255))
d.line([6, 9, 9, 6], fill=(200, 40, 40, 255)); noisy(im, 6); save(im, "block/weather_station.png")

im = new((160, 208, 236, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(80, 130, 170, 255))
for y in range(2, 15, 3): d.line([0, y, 4, y], fill=(30, 60, 100, 255))
d.rectangle([6, 8, 15, 15], fill=(70, 140, 220, 200)); noisy(im, 5); save(im, "block/rain_gauge.png")

im = new((196, 40, 40, 255)); d = ImageDraw.Draw(im)
for y in range(0, 16, 4): d.rectangle([0, y, 15, y + 1], fill=(240, 240, 240, 255))
d.rectangle([0, 0, 15, 15], outline=(110, 20, 20, 255)); noisy(im, 6); save(im, "block/storm_siren.png")

# --- itens
def item(name, fn):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im); fn(d); save(im, "item/" + name + ".png")

def radio(d):
    d.rectangle([3, 6, 12, 14], fill=(54, 58, 66, 255), outline=(24, 26, 30, 255))
    d.line([11, 6, 13, 1], fill=(190, 190, 200, 255))
    d.rectangle([4, 8, 8, 12], fill=(90, 96, 106, 255))
    for y in (9, 11): d.line([5, y, 7, y], fill=(30, 30, 34, 255))
    d.rectangle([10, 8, 11, 9], fill=(60, 230, 110, 255)); d.rectangle([10, 11, 11, 12], fill=(230, 60, 60, 255))
item("weather_radio", radio)

def meter(d):
    d.rectangle([4, 3, 11, 14], fill=(236, 196, 52, 255), outline=(110, 84, 16, 255))
    d.rectangle([5, 4, 10, 8], fill=(16, 40, 28, 255)); d.line([6, 7, 9, 5], fill=(80, 255, 140, 255))
    d.rectangle([6, 10, 9, 12], fill=(90, 90, 96, 255))
item("handheld_meter", meter)

def kit(d):
    d.rectangle([2, 5, 13, 13], fill=(214, 48, 48, 255), outline=(110, 18, 18, 255))
    d.rectangle([7, 6, 8, 12], fill=(250, 250, 250, 255)); d.rectangle([5, 8, 10, 9], fill=(250, 250, 250, 255))
    d.rectangle([6, 3, 9, 5], fill=(150, 150, 156, 255), outline=(70, 70, 76, 255))
item("emergency_kit", kit)

def tank(d):
    d.rectangle([5, 4, 10, 14], fill=(170, 176, 190, 255), outline=(70, 74, 86, 255))
    d.rectangle([5, 7, 10, 9], fill=(214, 60, 40, 255)); d.rectangle([6, 2, 9, 3], fill=(90, 94, 104, 255))
    d.point((7, 11), fill=(255, 255, 255, 255))
item("propane_tank", tank)

def bar(d):
    d.rectangle([2, 6, 13, 10], fill=(110, 70, 34, 255), outline=(60, 36, 16, 255))
    d.rectangle([2, 6, 5, 10], fill=(220, 60, 50, 255)); d.line([3, 8, 4, 8], fill=(255, 230, 120, 255))
    for x in (7, 9, 11): d.point((x, 8), fill=(200, 150, 80, 255))
item("energy_bar", bar)

def balloon(d):
    d.ellipse([2, 0, 13, 10], fill=(226, 70, 56, 255), outline=(110, 24, 20, 255))
    for x in (5, 8, 11): d.line([x, 1, x, 9], fill=(250, 250, 250, 255))
    d.line([5, 10, 6, 12], fill=(80, 60, 40, 255)); d.line([10, 10, 9, 12], fill=(80, 60, 40, 255))
    d.rectangle([5, 12, 10, 14], fill=(150, 104, 56, 255), outline=(80, 54, 26, 255))
item("hot_air_balloon", balloon)

# --- entity textures
# basket / frame / burner sheet (128x128), layout matches BalloonModel
im = Image.new("RGBA", (128, 128), (0, 0, 0, 0)); px = im.load()
def put(x, y, c, n=8):
    r = random.randint(-n, n); px[x, y] = (max(0, min(255, c[0] + r)), max(0, min(255, c[1] + r)), max(0, min(255, c[2] + r)), 255)
for y in range(0, 40):                      # wicker weave (walls)
    for x in range(0, 128):
        base = (176, 128, 64) if ((x // 3 + y // 3) % 2 == 0) else (146, 102, 48)
        if (x % 3 == 0) or (y % 3 == 0): base = (120, 82, 38)
        put(x, y, base)
for y in range(40, 68):                     # wood planks (floor)
    for x in range(0, 128):
        base = (130, 92, 52) if (y // 4) % 2 == 0 else (112, 78, 42)
        if y % 4 == 0: base = (84, 58, 30)
        put(x, y, base)
for y in range(68, 78):                     # darker rim wicker
    for x in range(0, 128):
        base = (140, 96, 44) if ((x // 2 + y // 2) % 2 == 0) else (110, 74, 34)
        put(x, y, base)
for y in range(80, 128):                    # posts (wood) + metal
    for x in range(0, 128):
        if x < 20: put(x, y, (104, 74, 42))
        else: put(x, y, (120, 124, 134), 10)
for y in range(96, 112):                    # copper coil
    for x in range(60, 90): put(x, y, (190, 112, 60), 10)
save(im, "entity/hot_air_balloon.png")

# envelope fabric (64x64): white ripstop weave, dark seams on panel edges, horizontal load tapes
im = Image.new("RGBA", (64, 64), (255, 255, 255, 255)); px = im.load()
for y in range(64):
    for x in range(64):
        v = 244 + (6 if (x // 2 + y // 2) % 2 == 0 else -4) + random.randint(-3, 3)
        if x % 8 == 0 or y % 8 == 0: v -= 10          # ripstop grid
        px[x, y] = (v, v, v, 255)
for y in range(64):                                    # vertical seams (each gore edge)
    for x in (0, 1, 62, 63): px[x, y] = (150, 150, 150, 255)
for y in (18, 19, 40, 41):                             # horizontal load tapes
    for x in range(64): px[x, y] = (190, 190, 190, 255)
save(im, "entity/balloon_envelope.png")

# fully transparent overrides: hide the vanilla sun and clouds (the mod draws its own)
def override(rel, size):
    p = RES + "/assets/minecraft/textures/" + rel
    os.makedirs(os.path.dirname(p), exist_ok=True)
    Image.new("RGBA", (size, size), (0, 0, 0, 0)).save(p)
override("environment/sun.png", 32)
override("environment/clouds.png", 256)

# --- vehicle atlas (64x64 = 4x4 cells of 16x16), cell index == VehicleGeometry.Mat.cell
atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
def cell_paint(idx, fn):
    sub = Image.new("RGBA", (16, 16), (0, 0, 0, 255)); d = ImageDraw.Draw(sub); fn(d, sub)
    atlas.paste(sub, ((idx % 4) * 16, (idx // 4) * 16))
def noise_fill(sub, base, n=5):
    px = sub.load()
    for y in range(16):
        for x in range(16):
            r = random.randint(-n, n); px[x, y] = (max(0, min(255, base[0] + r)), max(0, min(255, base[1] + r)), max(0, min(255, base[2] + r)), 255)
def c_paint(d, sub):   # 0 PAINT: white with a faint panel gap on the border
    noise_fill(sub, (246, 246, 246), 4); d.rectangle([0, 0, 15, 15], outline=(214, 214, 218, 255))
def c_glass(d, sub):   # 1 GLASS: dark blue, diagonal reflections
    noise_fill(sub, (52, 72, 92), 3)
    for k in (3, 4, 10, 11): d.line([k, 15, k + 7, 0], fill=(110, 140, 168, 255))
def c_tire(d, sub):    # 2 TIRE tread: grooves along the circumference + staggered blocks
    noise_fill(sub, (34, 34, 38), 3)
    for x in (3, 8, 13): d.line([x, 0, x, 15], fill=(18, 18, 20, 255))
    for y in range(0, 16, 4):
        d.rectangle([4, y, 7, y + 1], fill=(50, 50, 55, 255)); d.rectangle([9, y + 2, 12, y + 3], fill=(50, 50, 55, 255))
def c_rim(d, sub):     # 3 RIM: circular, five spokes, centre cap
    d.rectangle([0, 0, 15, 15], fill=(26, 26, 30, 255)); d.ellipse([0, 0, 15, 15], fill=(196, 200, 210, 255))
    for k in range(5):
        a0 = k * 72 + 20
        d.pieslice([2, 2, 13, 13], a0, a0 + 32, fill=(58, 60, 68, 255))
    d.ellipse([5, 5, 10, 10], fill=(170, 174, 184, 255)); d.ellipse([7, 7, 8, 8], fill=(90, 92, 100, 255))
def c_steel(d, sub):   # 4 STEEL: brushed metal
    noise_fill(sub, (170, 175, 185), 8)
    for y in range(0, 16, 2): d.line([0, y, 15, y], fill=(150, 155, 165, 255))
def c_grille(d, sub):  # 5 GRILLE: slats
    noise_fill(sub, (16, 16, 18), 2)
    for y in range(1, 16, 3): d.line([1, y, 14, y], fill=(110, 115, 125, 255))
    d.rectangle([0, 0, 15, 15], outline=(60, 62, 68, 255))
def c_light(d, sub):   # 6 LIGHT: reflector
    d.rectangle([0, 0, 15, 15], fill=(236, 220, 160, 255)); d.ellipse([2, 2, 13, 13], fill=(255, 248, 215, 255)); d.ellipse([5, 5, 10, 10], fill=(255, 255, 255, 255))
def c_tail(d, sub):    # 7 TAIL
    noise_fill(sub, (190, 24, 24), 4)
    for y in (4, 8, 12): d.line([0, y, 15, y], fill=(130, 12, 12, 255))
    d.rectangle([3, 5, 12, 10], fill=(235, 70, 60, 255))
def c_armor(d, sub):   # 8 ARMOR: dark plate with rivets
    noise_fill(sub, (74, 78, 86), 5); d.rectangle([0, 0, 15, 15], outline=(46, 49, 55, 255))
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13), (8, 2), (8, 13)]: d.point((x, y), fill=(150, 154, 164, 255))
def c_plate(d, sub):   # 9 PLATE: licence plate
    d.rectangle([0, 0, 15, 15], fill=(238, 238, 238, 255), outline=(30, 30, 34, 255))
    for x in range(2, 14, 3): d.rectangle([x, 5, x + 1, 10], fill=(34, 34, 40, 255))
def c_black(d, sub): noise_fill(sub, (22, 22, 24), 3)     # 10 BLACK
def c_hazard(d, sub):  # 11 HAZARD: diagonal yellow / black stripes
    d.rectangle([0, 0, 15, 15], fill=(244, 196, 22, 255))
    for k in range(-16, 32, 8): d.polygon([(k, 0), (k + 4, 0), (k + 4 - 16, 16), (k - 16, 16)], fill=(24, 24, 26, 255))
def c_paint2(d, sub):  # 12 PAINT2
    noise_fill(sub, (244, 244, 244), 4)
    for y in (5, 10): d.line([0, y, 15, y], fill=(222, 222, 226, 255))
def c_rubber(d, sub):  # 13 RUBBER sidewall (ring from the rim edge to the tread)
    d.rectangle([0, 0, 15, 15], fill=(38, 38, 42, 255)); d.ellipse([1, 1, 14, 14], outline=(56, 56, 62, 255)); d.ellipse([3, 3, 12, 12], outline=(28, 28, 32, 255))
def c_spike(d, sub):   # 14 SPIKE
    noise_fill(sub, (168, 172, 182), 6)
    for y in (3, 8, 13): d.line([0, y, 15, y], fill=(90, 94, 104, 255))
def c_lightbar(d, sub):  # 15 LIGHTBAR: amber lenses
    d.rectangle([0, 0, 15, 15], fill=(255, 168, 28, 255))
    for x in (0, 5, 10, 15): d.line([x, 0, x, 15], fill=(150, 90, 10, 255))
    d.rectangle([2, 5, 13, 10], fill=(255, 214, 110, 255))
for idx, fn in enumerate([c_paint, c_glass, c_tire, c_rim, c_steel, c_grille, c_light, c_tail, c_armor, c_plate, c_black, c_hazard, c_paint2, c_rubber, c_spike, c_lightbar]):
    cell_paint(idx, fn)
save(atlas, "entity/vehicle.png")

# --- vehicle / fuel icons
def icon_dominator(d):
    d.rectangle([1, 7, 14, 11], fill=(28, 30, 36, 255)); d.polygon([(4, 7), (6, 4), (10, 4), (12, 7)], fill=(40, 44, 52, 255))
    d.polygon([(5, 7), (6, 5), (9, 5), (9, 7)], fill=(90, 120, 150, 255)); d.line([2, 8, 13, 8], fill=(232, 178, 24, 255))
    for x in (4, 11): d.ellipse([x - 1, 10, x + 2, 13], fill=(18, 18, 20, 255), outline=(150, 154, 164, 255))
    d.rectangle([1, 5, 3, 6], fill=(28, 30, 36, 255)); d.line([8, 1, 8, 4], fill=(150, 154, 164, 255))
def icon_tiv(d):
    d.rectangle([1, 8, 14, 12], fill=(48, 54, 62, 255)); d.rectangle([3, 4, 12, 8], fill=(60, 66, 76, 255))
    d.rectangle([4, 5, 7, 6], fill=(80, 110, 140, 255)); d.rectangle([9, 5, 11, 6], fill=(80, 110, 140, 255))
    d.rectangle([6, 2, 9, 4], fill=(20, 20, 24, 255)); d.line([2, 11, 13, 11], fill=(232, 178, 24, 255))
    for x in (4, 11): d.ellipse([x - 2, 10, x + 2, 14], fill=(18, 18, 20, 255), outline=(150, 154, 164, 255))
def icon_suv(d):
    d.rectangle([1, 7, 14, 11], fill=(238, 238, 238, 255), outline=(150, 150, 156, 255)); d.rectangle([3, 4, 12, 7], fill=(240, 240, 240, 255), outline=(150, 150, 156, 255))
    d.rectangle([4, 5, 6, 6], fill=(70, 100, 130, 255)); d.rectangle([8, 5, 11, 6], fill=(70, 100, 130, 255)); d.line([1, 9, 14, 9], fill=(232, 115, 28, 255))
    for x in (4, 11): d.ellipse([x - 1, 10, x + 2, 13], fill=(18, 18, 20, 255), outline=(150, 154, 164, 255))
    d.rectangle([5, 3, 10, 3], fill=(255, 150, 20, 255)); d.line([8, 0, 8, 3], fill=(60, 60, 66, 255))
def icon_fuel(d):
    d.rectangle([3, 4, 12, 14], fill=(196, 40, 36, 255), outline=(110, 18, 16, 255)); d.rectangle([9, 2, 12, 4], fill=(196, 40, 36, 255), outline=(110, 18, 16, 255))
    d.rectangle([4, 7, 11, 11], fill=(226, 80, 72, 255)); d.line([5, 9, 10, 9], fill=(250, 220, 90, 255)); d.rectangle([5, 2, 7, 3], fill=(60, 60, 66, 255))
item("dominator", icon_dominator); item("tiv", icon_tiv); item("chaser_suv", icon_suv); item("fuel_canister", icon_fuel)

# --- tornado probe block texture (dome pod, yellow band, bolts)
im = new((52, 56, 64, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(30, 32, 38, 255)); d.rectangle([0, 6, 15, 8], fill=(232, 178, 24, 255))
for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]: d.point((x, y), fill=(150, 154, 164, 255))
d.ellipse([5, 9, 10, 14], fill=(34, 36, 42, 255)); noisy(im, 5); save(im, "block/tornado_probe.png")

# ------------------------------------------------------------- modelos ------
FACES = ["north", "east", "south", "west", "up", "down"]
def el(f, t): return {"from": f, "to": t, "faces": {k: {"texture": "#t"} for k in FACES}}

BLOCKS = {
    "radar": [[0,0,0,16,5,16],[6,5,6,10,10,10],[2,10,2,14,13,14],[4,13,4,12,16,12]],
    "anemometer": [[7,0,7,9,11,9],[1,10,7,15,11,9],[7,10,1,9,11,15],[0,9,6,3,12,10],[13,9,6,16,12,10],[6,9,0,10,12,3],[6,9,13,10,12,16]],
    "barometer": [[2,0,2,14,2,14],[4,2,6,12,12,10]],
    "thermometer": [[3,0,3,5,5,5],[11,0,3,13,5,5],[3,0,11,5,5,13],[11,0,11,13,5,13],[2,5,2,14,13,14],[1,13,1,15,14,15]],
    "weather_station": [[4,0,4,12,6,12],[7,6,7,9,16,9],[1,14,7,15,15,9]],
    "rain_gauge": [[4,0,4,12,10,12],[3,10,3,13,12,13]],
    "storm_siren": [[3,0,3,13,3,13],[5,3,5,11,9,11],[2,9,2,14,13,14],[5,13,5,11,16,11]],
    "tornado_probe": [[1,0,1,15,2,15],[2,2,2,14,6,14],[4,6,4,12,9,12],[7.5,9,7.5,8.5,14,8.5]],
}
for name, boxes in BLOCKS.items():
    tex = "meteorologia:block/" + name
    wj(A + f"/models/block/{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex, "t": tex},
        "elements": [el(b[0:3], b[3:6]) for b in boxes]})
    wj(A + f"/blockstates/{name}.json", {"variants": {"": {"model": "meteorologia:block/" + name}}})
    wj(A + f"/models/item/{name}.json", {"parent": "meteorologia:block/" + name})
    wj(D + f"/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": "meteorologia:" + name}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

ITEMS = ["weather_radio", "handheld_meter", "emergency_kit", "propane_tank", "energy_bar", "hot_air_balloon", "fuel_canister", "dominator", "tiv", "chaser_suv"]
for name in ITEMS:
    wj(A + f"/models/item/{name}.json", {"parent": "minecraft:item/generated",
                                          "textures": {"layer0": "meteorologia:item/" + name}})

# --------------------------------------------------------------- language ----
en = {
 "block.meteorologia.radar": "Weather Radar",
 "block.meteorologia.anemometer": "Anemometer",
 "block.meteorologia.barometer": "Barometer",
 "block.meteorologia.thermometer": "Thermo-hygrometer",
 "block.meteorologia.weather_station": "Weather Station",
 "block.meteorologia.rain_gauge": "Rain Gauge",
 "block.meteorologia.storm_siren": "Storm Siren",
 "item.meteorologia.weather_radio": "Weather Radio",
 "item.meteorologia.handheld_meter": "Handheld Meter",
 "item.meteorologia.emergency_kit": "Emergency Kit",
 "item.meteorologia.propane_tank": "Propane Tank",
 "item.meteorologia.energy_bar": "Energy Bar",
 "item.meteorologia.hot_air_balloon": "Hot Air Balloon",
 "entity.meteorologia.hot_air_balloon": "Hot Air Balloon",
 "itemGroup.meteorologia": "Meteorology",
 "meteorologia.configuration.title": "Meteorology Settings",
 "block.meteorologia.tornado_probe": "Tornado Probe",
 "item.meteorologia.fuel_canister": "Fuel Canister",
 "item.meteorologia.dominator": "Dominator Armored Chaser",
 "item.meteorologia.tiv": "TIV Intercept Vehicle",
 "item.meteorologia.chaser_suv": "Chaser SUV",
 "entity.meteorologia.dominator": "Dominator Armored Chaser",
 "entity.meteorologia.tiv": "TIV Intercept Vehicle",
 "entity.meteorologia.chaser_suv": "Chaser SUV",
 "key.categories.meteorologia": "Meteorology",
 "key.meteorologia.anchor": "Deploy / Retract Anchors",
 "key.meteorologia.lights": "Vehicle Lights",
 "key.meteorologia.horn": "Vehicle Horn",
}
cfg = {
 "naturalStorms": ("Natural storms", "Supercells and tornadoes form on their own when it rains or thunders."),
 "stormFrequency": ("Storm frequency", "Multiplier for natural storm frequency (0 = never)."),
 "maxSupercells": ("Max supercells", "Maximum simultaneous supercells."),
 "blockDamage": ("Tornado block damage", "Tornadoes destroy blocks depending on their EF rating."),
 "maxDebris": ("Max debris", "Maximum flying debris blocks per tornado. 0 disables debris."),
 "lightning": ("Lightning", "Supercells strike lightning near players."),
 "vehicleDamage": ("Vehicle damage", "Vehicles take damage from crashes and tornado debris."),
 "rayMarching": ("Ray marched storms", "Draw tornadoes and supercells with the ray marching shader."),
 "stormSteps": ("Storm quality", "Ray marching steps per pixel for storms. Lower is faster."),
 "tornadoViewDistance": ("Tornado view distance", "How far (in blocks) tornadoes are drawn. Far ones are scaled up to stay visible."),
 "stormFog": ("Storm fog", "Dense fog during heavy rain and near storms."),
 "sunEnabled": ("Realistic sun", "Draw the realistic sun. The vanilla sun is hidden by this mod."),
 "sunSize": ("Sun size", "Apparent diameter of the sun disc in degrees."),
 "sunGlow": ("Sun glow", "Strength of the halo around the sun."),
 "cloudsEnabled": ("Volumetric clouds", "Draw the volumetric clouds. The vanilla clouds are hidden by this mod."),
 "cloudAmount": ("Cloud amount", "Sky coverage: 0 = clear, 1 = overcast. Rain adds to it automatically."),
 "cloudRealism": ("Cloud realism", "0 = blocky vanilla-like clouds, 1 = fully realistic billowing clouds."),
 "cloudHeight": ("Cloud height", "Altitude (Y) of the cloud base."),
 "cloudSpeed": ("Cloud speed", "Cloud drift speed multiplier."),
 "cloudQuality": ("Cloud quality", "Cloud ray marching steps. Lower is faster, higher is smoother."),
}
for k, (name, tip) in cfg.items():
    en["meteorologia.configuration." + k] = name
    en["meteorologia.configuration." + k + ".tooltip"] = tip
wj(A + "/lang/en_us.json", en)
pt_old = A + "/lang/pt_br.json"
if os.path.exists(pt_old): os.remove(pt_old)

# -------------------------------------------------------------- receitas ----
def shaped(name, pattern, key, count=1, cat="misc"):
    wj(D + f"/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": cat,
        "pattern": pattern, "key": key, "result": {"id": "meteorologia:" + name, "count": count}})
def i(x): return {"item": "minecraft:" + x}
def m(x): return {"item": "meteorologia:" + x}

shaped("radar", ["IGI", "ICI", "IRI"], {"I": i("iron_ingot"), "G": i("glass"), "C": i("compass"), "R": i("redstone")}, cat="redstone")
shaped("anemometer", [" I ", "ISI", " S "], {"I": i("iron_ingot"), "S": i("stick")}, cat="redstone")
shaped("barometer", ["III", "IGI", "IRI"], {"I": i("iron_ingot"), "G": i("glass_pane"), "R": i("redstone")}, cat="redstone")
shaped("thermometer", ["CGC", "CRC", "CCC"], {"C": i("copper_ingot"), "G": i("glass_pane"), "R": i("redstone")}, cat="redstone")
shaped("weather_station", ["ABT", "III"], {"A": m("anemometer"), "B": m("barometer"), "T": m("thermometer"), "I": i("iron_ingot")}, cat="redstone")
shaped("rain_gauge", ["G G", "G G", "III"], {"G": i("glass"), "I": i("iron_ingot")}, cat="redstone")
shaped("storm_siren", ["IBI", "IRI", "III"], {"I": i("iron_ingot"), "B": i("bell"), "R": i("redstone")}, cat="redstone")
shaped("weather_radio", ["  I", "ICR", "III"], {"I": i("iron_ingot"), "C": i("compass"), "R": i("redstone")}, cat="equipment")
shaped("handheld_meter", [" I ", "IRI", " C "], {"I": i("iron_ingot"), "R": i("redstone"), "C": i("compass")}, cat="equipment")
shaped("propane_tank", ["IBI", "I I", "III"], {"I": i("iron_ingot"), "B": i("blaze_powder")}, count=2)
shaped("hot_air_balloon", ["WWW", "WWW", "SCS"], {"W": {"tag": "minecraft:wool"}, "S": i("string"), "C": i("barrel")}, cat="equipment")
shaped("fuel_canister", [" I ", "ICI", "III"], {"I": i("iron_ingot"), "C": i("coal")}, count=2)
shaped("chaser_suv", ["IGI", "IFI", "CIC"], {"I": i("iron_ingot"), "G": i("glass"), "F": i("blast_furnace"), "C": i("coal_block")}, cat="equipment")
shaped("dominator", ["KGK", "IFI", "CKC"], {"K": i("iron_block"), "G": i("glass"), "I": i("iron_ingot"), "F": i("blast_furnace"), "C": i("coal_block")}, cat="equipment")
shaped("tiv", ["OGO", "KFK", "CKC"], {"O": i("obsidian"), "G": i("glass"), "K": i("iron_block"), "F": i("blast_furnace"), "C": i("coal_block")}, cat="equipment")
shaped("tornado_probe", ["IGI", "ICR", "OOO"], {"I": i("iron_ingot"), "G": i("glass"), "C": i("compass"), "R": i("redstone"), "O": i("obsidian")}, cat="equipment")
wj(D + "/recipe/emergency_kit.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [i("golden_apple"), i("bread"), i("paper")], "result": {"id": "meteorologia:emergency_kit", "count": 1}})
wj(D + "/recipe/energy_bar.json", {"type": "minecraft:crafting_shapeless", "category": "food",
    "ingredients": [i("wheat"), i("sugar"), i("cocoa_beans")], "result": {"id": "meteorologia:energy_bar", "count": 2}})

# ------------------------------------------------------------------ tags ----
wj(RES + "/data/minecraft/tags/block/mineable/pickaxe.json",
   {"replace": False, "values": ["meteorologia:" + n for n in BLOCKS]})

print("assets ok")
