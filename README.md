# Meteorology — a weather mod for Minecraft

Ray marched tornadoes and supercells, a realistic sun and volumetric clouds, heavy rain, a weather
radar, a weather station, hot air balloons, **drivable storm-chasing vehicles** and survival items.

| | |
|---|---|
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge 21.1.92** (or any newer 21.1.x) |
| Java | 21 |
| Version | **1.2.0** |

## Building the `.jar`
* **GitHub Actions (nothing to install):** push this whole folder (including `.github/`) to a repository.
  Open the **Actions** tab → *Build do mod* → download the **`meteorologia-jar`** artifact.
* **IntelliJ IDEA:** *File → Open* this folder, then *Gradle → Tasks → build → build*.
* **Command line:** with JDK 21 and Gradle 8.12+: `gradle build`.

The jar ends up in `build/libs/meteorologia-1.2.0.jar`. Drop it into `.minecraft/mods/`.

## What's new in 1.2
* **Storm-chasing vehicles**: the **Dominator** (armoured, fast), the **TIV** (heavy tornado intercept vehicle) and the
  **Chaser SUV** (light and quick). Realistic driving (bicycle-model steering, tyre grip limits, understeer, wet / icy roads),
  steering wheels, spinning wheels, body roll, headlights, brake lights, hull damage, fuel and a driver HUD. See *Vehicles* below.
* **Tornadoes and supercells look more like real ones**: striated, ragged funnels with a light condensation cone and a darker debris
  sheath, flying debris, a rain veil, sub-vortices in wedges, a ragged wall cloud; supercells with a cauliflower tower, corkscrew
  striations, a flat dark base, mammatus, a flanking line of smaller towers and a **precipitation core**.
* **Bug fix: wedges floating before touching down.** Large tornadoes were created beyond the loaded chunks, where the server did not
  know the ground height. Now the terrain height is estimated from the world generator, tornadoes spawn inside the loaded area, and
  the funnel descends and lands in about 3.5 s.
* **New: Tornado Probe** block (records the peak wind, comparator output), **Fuel Canister**, vehicle **horn and lights**,
  and a **driver HUD** (speed, fuel, hull, anchors, nearest tornado).

## Vehicles
| Vehicle | Seats | Top speed | Hull | Notes |
|---|---|---|---|---|
| **Dominator Armored Chaser** | 3 | 130 km/h | 260 (armour 60%) | Fast and armoured, can deploy ground anchors |
| **TIV Intercept Vehicle** | 4 | 105 km/h | 520 (armour 80%) | Tall armoured body, camera pod, **anchor spikes** that hold it against EF0-EF3 tornadoes |
| **Chaser SUV** | 4 | 140 km/h | 160 (armour 25%) | Cheap and quick, roof mast with an anemometer |

**Controls:** **W/S** throttle, brake and reverse · **A/D** steer · **Shift** get off ·
**R** deploy / retract anchors (must be stopped) · **K** lights · **H** horn (all rebindable in *Controls > Meteorology*).

**Fuel:** vehicles start with 40% of a tank. Right click with a **Fuel Canister** to refuel (+1000). **Repair:** right click with an
**Iron Ingot** (+15% hull). Sneak + right click on an empty vehicle picks it up. At 0 hull a vehicle is wrecked until repaired.
**Anchors:** an anchored TIV is only moved by EF4+ tornadoes (and only partly); an anchored Dominator holds almost as well.
Passengers inside are protected by the vehicle's armour; a tornado damages the hull instead of the riders.
**Driving feel:** about 0-100 km/h in 6 s (SUV), 7 s (Dominator), 100 km/h to a stop in ~42 m; rain makes the road slippery.

Crafting (blast furnace + coal blocks as engine and tyres):
```
Chaser SUV   IGI / IFI / CIC        I iron ingot, G glass, F blast furnace, C coal block
Dominator    KGK / IFI / CKC        K iron block, G glass, F blast furnace, I iron ingot, C coal block
TIV          OGO / KFK / CKC        O obsidian, G glass, K iron block, F blast furnace, C coal block
Fuel Canister (x2)   _I_ / ICI / III     I iron ingot, C coal
Tornado Probe        IGI / ICR / OOO     I iron ingot, G glass, C compass, R redstone, O obsidian
```
(or take them from the *Meteorology* creative tab).

## What's new in 1.1
* **Everything is in English**: messages, item names, config, README. Commands are now **`/storm …`**.
* **Tornado width and shape.** Tornadoes are no longer all thin ropes: choose the width and the shape
  (`rope`, `cone`, `stovepipe`, `wedge`) or let the mod pick from the EF rating and the width.
* **Visible from much farther.** Tornadoes are drawn up to 8 km (configurable, up to 16 km) and are scaled up
  with distance so they never shrink to a speck. Supercells are drawn up to 14 km.
* **Supercells are now smaller on top and larger at the bottom**: a huge dark base shelf, a tower that tapers
  upwards and only a small anvil.
* **More realistic hot air balloon**: onion-shaped envelope made of coloured gores (5 colour schemes, picked per balloon),
  suspension ropes, a hollow wicker basket with a rim, corner posts, a burner frame and burner, gentle swinging,
  a "breathing" envelope and it turns towards its direction of travel.
* **Realistic sun** (replaces the vanilla one): glowing disc with limb darkening, soft halo, orange at sunrise/sunset.
* **Volumetric clouds** (replace the vanilla ones): billowing, lit by the sun, darker under rain and storms.
  They can be tuned from *less realistic* (blocky, vanilla-like) to *fully realistic*, and thinned out or removed.

## Commands (OP level 2)
```
/storm tornado create [ef 0-5] [width 4-400] [auto|rope|cone|stovepipe|wedge]
/storm tornado clear
/storm supercell create [distance]      supercell that approaches you (default 1500 blocks)
/storm supercell clear
/storm rain light|moderate|heavy|clear
/storm start | /storm stop              heavy rain + an approaching supercell / end everything
/storm wind                             wind at your position
/storm info                             active storms (distance, direction, ETA)
```
`width` is the diameter of the damage swath in blocks. Examples:
```
/storm tornado create 1 12 rope          a thin rope tornado
/storm tornado create 3 40 cone          a classic cone
/storm tornado create 4 70 stovepipe     a straight, cylindrical funnel
/storm tornado create 5 150 wedge        a huge wedge
/storm tornado create 3                  EF3 with a random width and shape
```
Without a width the size is random and typical for the EF rating (EF0 ~16 blocks … EF5 ~120 blocks, ±40%).

## Settings (in game: *Mods → Meteorology → Config*, or the `.toml` files in `config/`)
**Sun**: `sunEnabled`, `sunSize` (apparent diameter in degrees), `sunGlow`.
**Clouds**: `cloudsEnabled`, `cloudAmount` (0 = clear … 1 = overcast; rain adds to it), `cloudRealism`
(0 = blocky vanilla-like … 1 = fully realistic), `cloudHeight`, `cloudSpeed`, `cloudQuality`.
**Storms**: `rayMarching`, `stormSteps` (quality/performance), `tornadoViewDistance`, `stormFog`.
**World/server**: `naturalStorms`, `stormFrequency`, `maxSupercells`, `blockDamage`, `maxDebris`, `lightning`, `vehicleDamage`.

> **Upgrading from 1.0:** the config keys were renamed. Delete `config/meteorologia-*.toml` once so fresh ones are created.

### About the sun and clouds
The vanilla sun and clouds are hidden with transparent override textures
(`assets/minecraft/textures/environment/sun.png` and `clouds.png`), and the mod draws its own right after the sky.
Setting `sunEnabled` or `cloudsEnabled` to false therefore means **no** sun / **no** clouds, not the vanilla ones.
If you want the vanilla ones back, delete those two files from the jar (or put a resource pack above the mod).
Shader packs (Iris/Optifine) may conflict with the mod's shaders.

## Features
### Storms
* **Tornadoes** EF0–EF5, ray marched: condensation funnel with rotation and sway, dust/debris at the base and a rotating
  wall cloud. They form by descending from the cloud, mature, then thin out into a rope and dissipate.
* **Supercells** visible from far away that approach the player, with a dark base, a tapering tower and a small anvil.
* **Heavy rain** that is *local* (only where you are), with dense fog and darkening. Heavy rain and thunderstorms make
  supercells and tornadoes more likely.
* **Tornado effects**: they pull, spin and fling entities, hurt (EF1+), tear blocks out depending on the EF rating and throw debris.
  Being **4+ blocks below the surface protects you**. Sneaking reduces the wind force.

### Blocks
| Block | Function |
|---|---|
| **Weather Radar** | Right click opens the radar: supercell echoes, tornado markers, sweep, 1–8 km range. |
| **Anemometer** | Wind reading + redstone signal (0–15). |
| **Barometer** | Pressure in hPa; signal rises as pressure drops. |
| **Thermo-hygrometer** | Temperature and humidity; signal from temperature. |
| **Weather Station** | Everything at once + threat list; signal 15 = tornado close. |
| **Rain Gauge** | Collects rain (mm); comparator reads it; sneak + click empties it. |
| **Storm Siren** | Sounds by itself with a tornado within 1.5 km, or with redstone. |
| **Tornado Probe** | A heavy instrument pod that records the **peak wind** (30 km/h per step). Right click reads it, sneak + right click resets. Comparator output. |

### Survival items
* **Weather Radio**: right click for a forecast; in the hotbar it warns about nearby tornadoes by itself.
* **Handheld Meter**: wind, pressure, temperature and humidity where you stand.
* **Emergency Kit**: Absorption, Resistance, Regeneration and Slow Falling for 2 minutes.
* **Energy Bar** (quick food) and **Propane Tank** (balloon fuel).

### Hot air balloon
Place it by clicking a block, right click it to board (2 seats).
**W** fires the burner (rise), **S** vents (descend), **Shift** to get off. Right click with a **Propane Tank** to refuel.
The balloon drifts with the wind of its altitude, and the wind direction **rotates with height**: you steer by
climbing and descending. Tornadoes pull on it.

## Status — please read
**Validated by actually running it:**
* Both **shaders** (`storm.fsh`, `sky.fsh`) compile and run on a real OpenGL context (Mesa llvmpipe) with the same uniforms the Java
  code sends: tornado shapes and widths, the formation sequence of a wedge, far-away LOD, the supercell with the tornado under it, the
  sun at several elevations and the clouds at several realism levels. This testing found and fixed several real problems
  (stacked-disc banding on the funnel, a "wine glass" funnel top, a ring where the funnel met the cloud). See `docs/` and `tools/`.
* The vehicle code that does not depend on Minecraft (**types, driving physics and the 3D geometry**) is plain Java and was compiled and
  **executed**: acceleration, braking distances and turning radii were simulated and tuned, and the three models were rendered from the
  real geometry data (`docs/preview_vehicles.png`).
* All 42 Java files parse, the JSON is valid, and imports and internal references are consistent.

**Not validated:**
* Compiling against Minecraft/NeoForge (done by you via the build) and anything that needs the running game: how the vehicles *feel*,
  performance, balance, the key packets, riders' positions in the seats.
* The shaders on real GPUs, with Sodium/Iris, or in the "Fabulous" graphics mode.

If the build fails or something looks wrong in game, send the error log / a screenshot.

## Tips
* **Performance:** lower `stormSteps` and `cloudQuality`, or set `cloudRealism` to 0 for cheap blocky clouds.
* If the storm shader fails to load, tornadoes fall back to particles; if the sky shader fails, no sun/clouds are drawn.
* Vanilla weather is global: a supercell right above someone turns on rain/thunder for everyone. The local rain and fog are visual only.
* Storms are not saved when you close the world.

## Layout
```
src/main/java/com/meteorologia/
  weather/   StormManager (server), WindModel, Supercell, Tornado
  client/    StormRenderer + SkyRenderer (ray marching), ClientEvents, RadarScreen, balloon model/renderer
  block/ item/ entity/ command/ net/
src/main/resources/assets/meteorologia/shaders/core/   storm.fsh (storms), sky.fsh (sun + clouds)
```
