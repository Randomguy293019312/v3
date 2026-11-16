#version 150

// ===========================================================================
//  Meteorology - realistic sun + volumetric clouds (ray marching)
//
//  Drawn right after the sky, BEFORE the terrain, so it needs no depth buffer.
//  The vanilla sun and clouds are hidden by transparent textures.
//
//  Cloud0: x coverage 0..1, y realism 0..1, z base height, w thickness
//  Cloud1: xy wind offset, z darkness (rain/storm), w enabled
//  SunPar: x halo strength, y daylight 0..1, z sun enabled
//  SunDir: xyz direction, w angular radius of the disc (rad)
// ===========================================================================

uniform mat4 CamRot;
uniform vec4 Proj;
uniform vec4 CamPos;
uniform vec4 SunDir;
uniform vec4 SunPar;
uniform vec4 Cloud0;
uniform vec4 Cloud1;
uniform vec4 SkyCol;
uniform vec4 Env;

in vec2 ndc;
out vec4 fragColor;

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
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

float fbm2(vec2 p, int oct) {
    float amp = 0.5;
    float sum = 0.0;
    float norm = 0.0;
    for (int i = 0; i < 4; i++) {
        if (i >= oct) {
            break;
        }
        sum += amp * vnoise2(p);
        norm += amp;
        p = p * 2.07 + vec2(11.3, 5.7);
        amp *= 0.5;
    }
    return sum / norm;
}

float fbm3(vec3 p, int oct) {
    float amp = 0.5;
    float sum = 0.0;
    float norm = 0.0;
    for (int i = 0; i < 4; i++) {
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

// cloud density at point p (world coordinates)
float cloudDensity(vec3 p, int oct) {
    float realism = Cloud0.y;
    float y01 = (p.y - Cloud0.z) / Cloud0.w;
    if (y01 <= 0.0 || y01 >= 1.0) {
        return 0.0;
    }
    vec2 xz = p.xz + Cloud1.xy;

    // low realism -> square cells, vanilla style
    float blocky = 1.0 - smoothstep(0.0, 0.35, realism);
    if (blocky > 0.0) {
        vec2 cell = (floor(xz / 14.0) + 0.5) * 14.0;
        xz = mix(xz, cell, blocky);
    }

    float big = fbm2(xz * 0.0017, 3);
    float mid = vnoise2(xz * 0.0058);
    float shape = big * 0.72 + mid * 0.28;
    float thr = 1.0 - Cloud0.x;
    float m = smoothstep(thr - 0.10, thr + 0.10, shape);
    m = mix(m, step(0.5, m), blocky);
    if (m <= 0.0) {
        return 0.0;
    }

    // flat base, rounded top that rises where the cloud is denser
    float top = mix(1.0, 0.30 + 0.70 * m, 1.0 - blocky);
    float vprof = smoothstep(0.0, 0.12, y01) * (1.0 - smoothstep(top * 0.55, top, y01));
    float d = m * vprof;

    // 3D erosion: more detail with more realism
    if (realism > 0.12) {
        vec3 q = vec3(xz.x, p.y * 1.3, xz.y) * 0.016;
        float n = oct >= 2 ? fbm3(q, oct) : vnoise(q);
        d = d * 1.25 - (1.0 - n) * 0.75 * smoothstep(0.12, 0.7, realism);
    }
    return clamp(d, 0.0, 1.0) * 1.9;
}

void main() {
    vec3 vd = vec3(ndc.x * Proj.x, ndc.y * Proj.y, -1.0);
    vec3 rd = normalize((CamRot * vec4(vd, 0.0)).xyz);
    vec3 sun = normalize(SunDir.xyz);
    float cosang = dot(rd, sun);

    // ------------------------------------------------------------- SUN ----
    vec3 sunCol = vec3(0.0);
    if (SunPar.z > 0.5 && cosang > 0.0) {
        float ang = acos(clamp(cosang, -1.0, 1.0));
        float r = ang / SunDir.w;                       // 1.0 = edge of the disc
        float vis = smoothstep(-0.12, 0.06, sun.y);
        vec3 tint = mix(vec3(1.0, 0.42, 0.14), vec3(1.0, 0.94, 0.84), smoothstep(0.0, 0.42, sun.y));
        float rc = min(r, 1.0);
        float disc = 1.0 - smoothstep(0.93, 1.0, r);
        float limb = 1.0 - 0.55 * (1.0 - sqrt(max(1.0 - rc * rc, 0.0)));
        float bloom = 0.95 / (1.0 + r * r * 0.55) + 0.28 * exp(-r * 0.16);
        float mie = pow(max(cosang, 0.0), 48.0) * 0.45;
        vec3 hdr = tint * (disc * limb * 10.0 + SunPar.x * (bloom + mie));
        sunCol = (1.0 - exp(-hdr * 0.85)) * vis;
    }

    // -------------------------------------------------------------- CLOUDS ----
    vec3 cloudCol = vec3(0.0);
    float trans = 1.0;
    float y0 = Cloud0.z - CamPos.y;
    float y1 = y0 + Cloud0.w;
    if (Cloud1.w > 0.5 && rd.y > 0.012 && y1 > 0.0) {
        float ta = y0 / rd.y;
        float tb = y1 / rd.y;
        float t0 = max(min(ta, tb), 0.0);
        float t1 = min(max(ta, tb), 9000.0);
        if (t1 > t0) {
            int steps = int(Env.x);
            int oct = Cloud0.y > 0.75 ? 4 : (Cloud0.y > 0.45 ? 3 : (Cloud0.y > 0.12 ? 2 : 1));
            float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
            float t = t0 + jitter * clamp(t0 * 0.05, 5.0, 60.0);
            float tFirst = -1.0;
            float dark = Cloud1.z;
            float dayL = SunPar.y;
            for (int i = 0; i < 64; i++) {
                if (i >= steps || t > t1 || trans < 0.03) {
                    break;
                }
                float dt = clamp(t * 0.05, 5.0, 220.0);
                vec3 p = CamPos.xyz + rd * t;
                float d = cloudDensity(p, oct);
                if (d > 0.01) {
                    float dl = cloudDensity(p + sun * 24.0, 1);
                    float shade = exp(-dl * 1.7);
                    float y01 = clamp((p.y - Cloud0.z) / Cloud0.w, 0.0, 1.0);
                    vec3 amb = mix(vec3(0.52, 0.58, 0.70), vec3(0.82, 0.87, 0.96), y01) * (0.30 + 0.70 * dayL);
                    float silver = 0.85 + 0.9 * pow(max(cosang, 0.0), 7.0);
                    vec3 sunL = mix(vec3(1.0, 0.55, 0.28), vec3(1.0, 0.96, 0.88), smoothstep(0.0, 0.4, sun.y)) * dayL;
                    vec3 lit = amb + sunL * shade * silver;
                    float blockyTerm = 1.0 - smoothstep(0.0, 0.35, Cloud0.y);
                    lit *= mix(1.0, 0.58, blockyTerm) * mix(1.0, mix(0.72, 1.0, y01), blockyTerm);
                    lit = mix(lit, lit * vec3(0.34, 0.37, 0.42), dark);
                    float a = 1.0 - exp(-d * 0.05 * dt);
                    cloudCol += trans * a * lit;
                    trans *= (1.0 - a);
                    if (tFirst < 0.0) {
                        tFirst = t;
                    }
                }
                t += dt;
            }
            // dissolve into the horizon
            float tt = tFirst < 0.0 ? t0 : tFirst;
            float fade = exp(-tt / 7000.0);
            fade *= smoothstep(0.012, 0.07, rd.y);
            cloudCol *= fade;
            trans = mix(1.0, trans, fade);
        }
    }

    float alpha = 1.0 - trans;
    vec3 rgb = sunCol * trans + cloudCol;
    if (alpha < 0.002 && max(rgb.r, max(rgb.g, rgb.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha);
}
