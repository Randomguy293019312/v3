#version 150

// ===========================================================================
//  Meteorology - volumetric ray marching of tornadoes and supercells
//
//  Draws a full-screen quad. For each pixel it rebuilds the world-space ray,
//  intersects it with a bounding cylinder and marches through it accumulating
//  density. The game's depth buffer is used so the terrain hides the storm.
//
//  Env.z = 0 -> tornado mode     Env.z = 1 -> distant supercell mode
//  Style.x = 0 -> SMOOTH (cheap, fluid)     Style.x = 1 -> DETAILED (expensive)
// ===========================================================================

uniform sampler2D DepthSampler;

uniform mat4 CamRot;   // view -> world rotation
uniform vec4 Proj;     // 1/P00, 1/P11, P22, P32
uniform vec4 Origin;   // xyz = storm origin relative to the camera
uniform vec4 StormA;   // tornado: x ground radius, y height to the cloud base, z strength 0..1, w time (s)
                       // supercell: x ground plane (world Y), w time
uniform vec4 StormB;   // x wall cloud radius, y darkness 0..1, z spin (+1 counter-clockwise), w seed
uniform vec4 SupA;     // supercell: x radius, y base height, z top height, w wind direction (rad)
                       // tornado: x = funnel reach (0 = only at the cloud base, 1 = touches the ground)
                       //          y,z,w = funnel profile: radius = R0 * (y + z * (h/H)^w)
uniform vec4 Env;      // x daylight, y steps, z mode, w haze distance
uniform vec4 SunDir;   // xyz sun direction
uniform vec4 SkyCol;   // rgb sky colour
uniform vec4 Style;    // x = 0 smooth / 1 detailed

in vec2 ndc;
out vec4 fragColor;

// minimum funnel radius as a function of distance: keeps the tornado visible from afar
float gMinRad = 0.0;
// 0 = smooth, 1 = detailed
float gDetail = 0.0;
// octaves used by the supercell (reduced with distance)
float gLodOct = 4.0;

// --------------------------------------------------------------- noise ----
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float fbmN(vec3 p, int oct) {
    float amp = 0.5;
    float sum = 0.0;
    float norm = 0.0;
    for (int i = 0; i < 5; i++) {
        if (i >= oct) {
            break;
        }
        sum += amp * vnoise(p);
        norm += amp;
        p = p * 2.03 + vec3(17.1, 3.7, 9.2);
        amp *= 0.5;
    }
    return sum / norm;
}

vec2 rot2(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// -------------------------------------------------------- field: tornado ----
// dF = condensation funnel, dD = debris / dust, dC = wall cloud.
// SMOOTH style: few octaves, soft edges, a gentle swirl. DETAILED style adds striations,
// sub-vortices, flying debris, rain veil and scud.
void tornadoField(vec3 p, bool detail, out float dF, out float dD, out float dC) {
    dF = 0.0;
    dD = 0.0;
    dC = 0.0;

    float t = StormA.w;
    float R0 = StormA.x;
    float H = StormA.y;
    float str = StormA.z;
    float spin = StormB.z;
    float reach = SupA.x;
    float h = p.y;
    float hq = gDetail;

    // ---- funnel
    if (h > -4.0 && h < H + 10.0) {
        float hn = clamp(h / H, 0.0, 1.0);
        float tip = (1.0 - clamp(reach, 0.0, 1.0)) * H * 0.92;
        float vis = smoothstep(tip - 4.0, tip + 12.0 + R0 * 0.35, h);
        float swayAmp = (0.25 + 1.4 * (1.0 - str)) * min(R0, 24.0) * (0.3 + 2.2 * hn * hn);
        vec2 c = vec2(sin(h * 0.021 + t * 0.35), cos(h * 0.017 + t * 0.3 + 1.3)) * swayAmp;
        c += vec2(sin(h * 0.047 + t * 0.55), cos(h * 0.041 - t * 0.45)) * R0 * 0.05;
        vec2 q = p.xz - c;
        float rr = length(q);
        float thick = mix(0.30, 1.0, smoothstep(0.0, 0.85, str));
        float wv = 1.0 + 0.16 * sin(h * 0.017 + t * 0.2 + StormB.w) + 0.08 * sin(h * 0.043 - t * 0.3);
        float rad = R0 * thick * (SupA.y + SupA.z * pow(hn, SupA.w)) * wv + 0.8;
        rad = max(rad, min(gMinRad, R0 * 2.0 + 6.0));
        if (rr < rad * 1.8 && vis > 0.0) {
            float a2 = atan(q.y, q.x + 0.0001) + spin * (t * 2.2 - hn * 2.6);
            float n;
            if (hq > 0.5) {
                vec3 npA = vec3(cos(a2) * 1.6 + rr * 0.02, sin(a2) * 1.6, h * 0.018 - t * 1.1);
                float n1 = fbmN(npA, detail ? 5 : 2);
                float n2 = 0.5;
                if (detail) {
                    n2 = vnoise(vec3(cos(a2) * 2.6, sin(a2) * 2.6, h * 0.004 - t * 0.9));
                }
                n = 0.62 * n1 + 0.38 * n2;
            } else {
                vec3 npA = vec3(cos(a2) * 1.1 + rr * 0.012, sin(a2) * 1.1, h * 0.010 - t * 0.7);
                n = detail ? 0.6 * vnoise(npA) + 0.4 * vnoise(npA * 2.3 + 4.0) : vnoise(npA);
            }
            float edge = rr / rad;
            float e0 = mix(0.20, 0.52, hq);
            float body = 1.0 - smoothstep(e0, 1.0, edge + (n - 0.5) * mix(0.45, 0.85, hq));
            float swirl = 1.0 + 0.12 * (1.0 - hq) * sin(a2 * 2.0 + h * 0.045 - t * 1.5);
            dF = body * vis * (0.50 + 0.9 * n) * 3.2 * swirl * smoothstep(0.0, 0.06, str)
                    * (1.0 - smoothstep(H * 0.72, H + 4.0, h));

            if (hq > 0.5 && detail && R0 > 30.0) {
                for (int k = 0; k < 3; k++) {
                    float ak = spin * (t * 1.6 + float(k) * 2.0944) + h * 0.01;
                    vec2 vc = c + vec2(cos(ak), sin(ak)) * rad * 0.58;
                    float dv = length(p.xz - vc) / (rad * 0.16);
                    dF += exp(-dv * dv) * 2.5 * vis * smoothstep(0.0, 0.06, str);
                }
            }
        }
    }

    // ---- debris sheath and ground dust
    float gb = smoothstep(0.05, 0.6, str) * smoothstep(0.5, 0.9, reach);
    if (gb > 0.0 && h > -3.0 && h < 90.0 + R0 * 2.5) {
        vec2 q = p.xz;
        float rr = length(q);
        float dr = R0 * (1.3 + 0.6 * gb) + h * 0.35;
        if (rr < dr * 1.5) {
            vec2 qr = rot2(q, spin * (t * 1.4 - rr * 0.012));
            vec3 np = vec3(qr * 0.05, h * 0.025 - t * 0.4);
            float n = fbmN(np, hq > 0.5 ? (detail ? 4 : 2) : (detail ? 2 : 1));
            float vf = exp(-max(h, 0.0) / (10.0 + R0 * 0.75));
            float rm = 1.0 - smoothstep(0.25, 1.0, rr / dr + (0.5 - n) * mix(0.45, 0.7, hq));
            dD = rm * vf * gb * 1.0;
            if (hq > 0.5 && detail) {
                vec3 cell = floor(vec3(qr.x, h, qr.y) / 3.5);
                dD += step(0.965, hash13(cell)) * rm * vf * gb * 2.4;
            }
        }
    }

    // ---- rotating wall cloud
    float Rc = StormB.x;
    vec2 pq = p.xz;
    float prr = length(pq);
    if (h > H - 60.0 && h < H + 170.0 && prr < Rc * 1.15) {
        float rn = prr / Rc;
        float base = H - 50.0 * exp(-rn * rn * 8.0) * (0.4 + 0.6 * str);
        float top = H + 22.0 + 30.0 * (1.0 - rn);
        vec2 qr = rot2(pq, spin * (t * 0.10 + (1.0 - rn) * 1.3));
        vec3 np = vec3(qr * 0.016, h * 0.018 + 4.0 + StormB.w);
        float n = fbmN(np, hq > 0.5 ? (detail ? 5 : 2) : (detail ? 2 : 1));
        float vert = smoothstep(base - 6.0, base + 22.0, h) * (1.0 - smoothstep(top - 40.0, top, h));
        float rm = 1.0 - smoothstep(mix(0.2, 0.5, hq), 1.0, rn + (0.5 - n) * mix(0.35, 0.5, hq));
        dC = vert * rm * (0.35 + 0.9 * n);
    }
    if (hq > 0.5) {
        if (h > -2.0 && h < H - 10.0 && prr < Rc * 0.95) {
            vec2 qr = rot2(pq, spin * t * 0.05);
            float sn = vnoise(vec3(qr * 0.035, h * 0.0025 + t * 0.05));
            dC += (1.0 - smoothstep(0.2, 1.0, prr / (Rc * 0.95))) * smoothstep(0.25, 0.85, sn) * 0.03;
        }
        if (h > H - 75.0 && h < H - 4.0 && prr < Rc * 1.3) {
            float ap = atan(pq.y, pq.x + 0.0001) + spin * t * 0.05;
            float sc = fbmN(vec3(cos(ap) * 5.0 + prr * 0.004, sin(ap) * 5.0, h * 0.03 + StormB.w), 3);
            dC += smoothstep(0.55, 0.90, sc) * smoothstep(H - 75.0, H - 30.0, h)
                    * (1.0 - smoothstep(H - 20.0, H - 4.0, h)) * (1.0 - smoothstep(0.35, 1.1, prr / Rc)) * 0.30;
        }
    }
}

// ------------------------------------------------------ field: supercell ----
// Modelled on a real "shelf + tower" supercell: a thin, flat, dark shelf with a crisp edge; a few
// stacked pancake ledges above it; a wide, almost columnar tower of rounded turrets; a smooth creamy
// dome at the top; bright puffs sitting on the shelf edge; and rain curtains hanging from the base.
//   dc  = cloud density, dp = rain density, dsh = the part of dc that belongs to the dark shelf
void superField(vec3 p, bool detail, out float dc, out float dp, out float dsh) {
    dc = 0.0;
    dp = 0.0;
    dsh = 0.0;
    float R = SupA.x;
    float hb = SupA.y;
    float ht = SupA.z;
    float h = p.y;
    if (h > ht + 40.0) {
        return;
    }
    float t = StormA.w;
    float hh = h - hb;                                    // height above the cloud base
    float hn = clamp(hh / (ht - hb), 0.0, 1.0);
    vec2 wd = vec2(cos(SupA.w), sin(SupA.w));
    vec2 sc = wd * (R * 0.2);                             // centre of the shelf
    vec2 q0 = p.xz - sc;
    float rs = length(q0);
    if (rs > R * 2.7) {
        return;
    }

    // ---- rain curtains hanging under the shelf (streaky, in patches)
    if (detail && h < hb + 10.0 && h > StormA.x) {
        float pr = rs / (R * 1.75);
        if (pr < 1.0) {
            float down = clamp((hb - h) / max(hb - StormA.x, 1.0), 0.0, 1.0);
            float attach = 1.0 - smoothstep(hb - 5.0, hb + 12.0, h);
            float patchN = smoothstep(0.34, 0.64, vnoise(vec3(q0 * 0.0034, 3.1)));
            float streak = smoothstep(0.42, 0.74, vnoise(vec3(q0.x * 0.024, h * 0.0007, q0.y * 0.024 + t * 0.03)));
            dp = patchN * streak * attach * (1.0 - smoothstep(0.65, 1.0, pr)) * (0.22 + 0.32 * down);
        }
    }
    if (hh < -24.0) {
        return;
    }

    // ---- thin flat shelf with a crisp edge
    float shelf = (1.0 - smoothstep(0.80, 1.0, rs / (R * 2.15)))
            * smoothstep(-24.0, -17.0, hh) * (1.0 - smoothstep(14.0, 30.0, hh));

    // ---- stacked pancake ledges just above the shelf
    float tiers = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float h0 = 18.0 + 26.0 * fk;
        float rk = R * (1.95 - 0.30 * fk);
        float band = smoothstep(h0 - 3.0, h0 + 1.0, hh) * (1.0 - smoothstep(h0 + 16.0, h0 + 22.0, hh));
        tiers = max(tiers, (1.0 - smoothstep(0.78, 1.0, rs / rk)) * band * 0.9);
    }

    // ---- tower: wide and almost columnar
    vec2 tc = wd * (hn * hn * R * 0.15);
    float rr = length(p.xz - tc);
    float colR = R * (1.55 + 0.12 * sin(hn * 3.14159) - 0.28 * hn * hn);
    float col = (1.0 - smoothstep(0.55, 1.0, rr / colR)) * smoothstep(5.0, 45.0, hh) * (1.0 - smoothstep(0.90, 1.0, hn));

    // ---- smooth creamy dome / anvil at the top (a little wider than the tower, smaller than the shelf)
    float domeR = colR * (1.0 + 0.22 * smoothstep(0.76, 0.90, hn)) * (1.0 - 0.60 * smoothstep(0.93, 1.0, hn));
    float dome = (1.0 - smoothstep(0.6, 1.0, rr / domeR)) * smoothstep(0.74, 0.80, hn) * (1.0 - smoothstep(0.96, 1.0, hn));

    // ---- bright puffs sitting on the shelf edge
    float flank = 0.0;
    if (hh < 190.0 && rs > R) {
        for (int k = 0; k < 8; k++) {
            float fk = float(k);
            float a = fk * 0.7854 + StormB.w * 0.1 + 0.4 * sin(fk * 5.1);
            float rad = R * (1.60 + 0.45 * fract(fk * 0.618 + 0.2));
            vec2 fc = sc + vec2(cos(a), sin(a)) * rad;
            float fr = length(p.xz - fc) / (R * (0.36 + 0.16 * fract(fk * 0.37 + 0.1)));
            float ftop = 50.0 + 130.0 * fract(fk * 0.81 + 0.3);
            float fv = smoothstep(0.0, 22.0, hh) * (1.0 - smoothstep(ftop - 45.0, ftop, hh));
            flank = max(flank, (1.0 - smoothstep(0.35, 1.0, fr)) * fv);
        }
    }

    float others = max(max(col, dome), max(tiers, flank));
    float shape = max(shelf * 0.95, others);
    if (shape <= 0.0) {
        return;
    }

    // ---- rounded turrets: fbm + "billow" noise (sharp creases between lumps)
    int oct = detail ? int(gLodOct) : 2;
    vec3 np = vec3(p.x * 0.0115, p.y * 0.0078, p.z * 0.0115) + vec3(0.0, -t * 0.012, StormB.w);
    float n = fbmN(np, oct);
    float nn = n;
    if (detail) {
        float bl = abs(2.0 * vnoise(np * 1.9 + 7.0) - 1.0);
        nn = 0.45 * n + 0.55 * bl;
    }
    float isShelf = step(others + 0.001, shelf * 0.95);
    float amp = mix(1.55, 0.55, smoothstep(0.76, 0.95, hn));
    amp = mix(amp, 0.28, isShelf);
    float d = shape * 1.15 - (1.0 - nn) * amp;
    dc = min(max(d + 0.14, 0.0) * 4.5, 1.7);
    dsh = dc * smoothstep(0.0, 0.10, shelf * 0.95 - others);
}

void main() {
    gDetail = Style.x;

    // ---- camera ray in world space
    vec3 vd = vec3(ndc.x * Proj.x, ndc.y * Proj.y, -1.0);
    vec3 rdU = (CamRot * vec4(vd, 0.0)).xyz;
    float rdLen = length(rdU);
    vec3 rd = rdU / rdLen;
    vec3 ro = -Origin.xyz;

    // ---- terrain depth (distance along the ray)
    vec2 uv = ndc * 0.5 + 0.5;
    float dpt = texture(DepthSampler, uv).r;
    float tScene = 1.0e9;
    if (dpt < 0.99999) {
        float zn = dpt * 2.0 - 1.0;
        float lin = Proj.w / (zn + Proj.z);
        tScene = lin * rdLen;
    }

    // ---- bounding volume (vertical cylinder)
    float rb;
    float y0;
    float y1;
    if (Env.z < 0.5) {
        rb = max(StormB.x * 1.15, StormA.x * 5.0 + 40.0);
        y0 = -6.0;
        y1 = StormA.y + 160.0;
    } else {
        rb = SupA.x * 2.8;
        y0 = StormA.x;              // ground plane (precipitation reaches it)
        y1 = SupA.z + 40.0;
    }

    float a = dot(rd.xz, rd.xz);
    float b = dot(ro.xz, rd.xz);
    float c = dot(ro.xz, ro.xz) - rb * rb;
    float tmin = 0.0;
    float tmax = tScene;
    if (a > 1.0e-6) {
        float disc = b * b - a * c;
        if (disc < 0.0) {
            discard;
        }
        float sq = sqrt(disc);
        tmin = max(tmin, (-b - sq) / a);
        tmax = min(tmax, (-b + sq) / a);
    } else if (c > 0.0) {
        discard;
    }
    if (abs(rd.y) > 1.0e-5) {
        float t0 = (y0 - ro.y) / rd.y;
        float t1 = (y1 - ro.y) / rd.y;
        tmin = max(tmin, min(t0, t1));
        tmax = min(tmax, max(t0, t1));
    } else if (ro.y < y0 || ro.y > y1) {
        discard;
    }
    if (tmax <= tmin) {
        discard;
    }

    // ---- ray marching
    int steps = int(Env.y);
    float range = tmax - tmin;
    float dtBase = range / float(steps);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float t = tmin + dtBase * jitter;

    vec3 col = vec3(0.0);
    float trans = 1.0;
    float tFirst = -1.0;
    vec3 sun = normalize(SunDir.xyz);

    int maxIter = int(float(steps) * (gDetail > 0.5 ? 1.9 : 1.2));
    float dtCur = dtBase;
    for (int i = 0; i < 160; i++) {
        if (i >= maxIter || t > tmax || trans < 0.03) {
            break;
        }
        vec3 p = ro + rd * t;

        if (Env.z < 0.5) {
            // finer steps near the axis of the tornado, where the thin features are
            float axisD = length(p.xz);
            dtCur = mix(dtBase * (gDetail > 0.5 ? 0.3 : 0.6), dtBase, smoothstep(StormA.x * 1.5, StormA.x * 4.5 + 20.0, axisD));

            gMinRad = t * 0.0035;
            float dF;
            float dD;
            float dC;
            tornadoField(p, true, dF, dD, dC);
            float dens = dF + dD + dC;
            if (dens > 0.003) {
                float lF;
                float lD;
                float lC;
                tornadoField(p + sun * 14.0, false, lF, lD, lC);
                float shadow = exp(-(lF + lD + lC) * 1.6);
                float wsum = dens + 0.0001;
                vec3 cloudCol = mix(vec3(0.50, 0.53, 0.58), vec3(0.20, 0.22, 0.25), StormB.y);
                float deb = clamp(0.05 + 0.50 * StormA.z * (1.0 - smoothstep(0.0, StormA.y * 0.45, p.y)), 0.0, 1.0);
                // wide tornadoes (wedges) are dark all the way up: debris and rain wrap the whole funnel
                deb = clamp(deb + 0.50 * smoothstep(0.9, 1.0, SupA.y) * StormA.z, 0.0, 1.0);
                vec3 funnelCol = mix(vec3(0.74, 0.75, 0.78), vec3(0.30, 0.28, 0.26), deb);
                // the funnel merges into the dark wall cloud near the top
                funnelCol = mix(funnelCol, cloudCol * 1.1, smoothstep(0.50, 0.98, p.y / StormA.y));
                vec3 alb = (funnelCol * dF + vec3(0.34, 0.30, 0.26) * dD + cloudCol * dC) / wsum;
                float amb = mix(0.55, 0.28, StormB.y) * Env.x + 0.035;
                vec3 lit = alb * (amb + vec3(1.0, 0.95, 0.88) * 0.55 * Env.x * shadow);
                // the wall cloud is thick and self-shadowed: darken it to match the supercell base behind it
                lit *= mix(1.0, 0.55, clamp(dC / wsum, 0.0, 1.0) * StormB.y);
                float aStep = 1.0 - exp(-dens * 0.075 * dtCur);
                col += trans * aStep * lit;
                trans *= (1.0 - aStep);
                if (tFirst < 0.0) {
                    tFirst = t;
                }
            }
        } else {
            dtCur = dtBase;
            gLodOct = t < 2500.0 ? (gDetail > 0.5 ? 5.0 : 4.0) : (gDetail > 0.5 ? 4.0 : 3.0);
            float dc;
            float dp;
            float dsh;
            superField(p, true, dc, dp, dsh);
            float dens = dc + dp;
            if (dens > 0.004) {
                float lc;
                float lp;
                float ls;
                float lc2;
                float lp2;
                float ls2;
                superField(p + sun * 26.0, false, lc, lp, ls);
                superField(p + sun * 90.0, false, lc2, lp2, ls2);
                float shadow = exp(-lc * 1.7 - lc2 * 1.1);
                float hgt = clamp((p.y - SupA.y) / (SupA.z - SupA.y), 0.0, 1.0);
                vec3 shelfCol = mix(vec3(0.20, 0.22, 0.26), vec3(0.10, 0.11, 0.13), StormB.y);
                // cool light blue in the lower tower, bright white above, warm cream at the very top
                vec3 cAlb = mix(vec3(0.80, 0.85, 0.94), vec3(1.0, 0.99, 0.96), smoothstep(0.0, 0.30, hgt));
                cAlb *= mix(vec3(1.0), vec3(1.04, 0.92, 0.72), smoothstep(0.45, 0.90, hgt));
                vec3 pAlb = mix(vec3(0.58, 0.62, 0.68), vec3(0.42, 0.52, 0.48), smoothstep(0.20, 0.42, dp));
                vec3 alb = (cAlb * (dc - dsh) + shelfCol * dsh + pAlb * dp) / (dens + 0.0001);
                vec3 ambC = vec3(0.55, 0.66, 0.92) * ((0.42 + 0.20 * hgt) * Env.x) + 0.04;
                vec3 lit = alb * (ambC + vec3(1.0, 0.92, 0.74) * 1.15 * Env.x * shadow);
                float aStep = 1.0 - exp(-(dc * 0.012 + dp * 0.0075) * dtCur);
                col += trans * aStep * lit;
                trans *= (1.0 - aStep);
                if (tFirst < 0.0) {
                    tFirst = t;
                }
            }
        }
        t += dtCur;
    }

    float alpha = 1.0 - trans;
    if (alpha < 0.003) {
        discard;
    }

    // ---- atmospheric haze with distance
    float tt = tFirst < 0.0 ? tmin : tFirst;
    float hz = 1.0 - exp(-tt / max(Env.w, 1.0));
    vec3 hazeCol = SkyCol.rgb * 0.9;
    col = mix(col, hazeCol * alpha, hz * 0.75);

    fragColor = vec4(col, alpha);
}
