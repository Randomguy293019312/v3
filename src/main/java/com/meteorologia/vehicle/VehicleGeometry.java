package com.meteorologia.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural 3D models of the chase vehicles, as lists of quads. Pure Java (no Minecraft
 * classes) so the models can be previewed and tested outside the game; the client renderer
 * just feeds these quads to a vertex consumer.
 *
 * Model space: +Z = forward, +X = LEFT of the vehicle, +Y = up, origin on the ground at the centre.
 */
public final class VehicleGeometry {
    private VehicleGeometry() {}

    /** Surface materials; {@code cell} is the index of the 16x16 tile in the 64x64 atlas. */
    public enum Mat {
        PAINT(0), GLASS(1), TIRE(2), RIM(3), STEEL(4), GRILLE(5), LIGHT(6), TAIL(7),
        ARMOR(8), PLATE(9), BLACK(10), HAZARD(11), PAINT2(12), RUBBER(13), SPIKE(14), LIGHTBAR(15);

        public final int cell;

        Mat(int cell) {
            this.cell = cell;
        }

        public boolean emissive() {
            return this == LIGHT || this == TAIL || this == LIGHTBAR;
        }

        public boolean translucent() {
            return this == GLASS;
        }
    }

    public static final class Quad {
        public final float[] p = new float[12];   // 4 vertices, xyz
        public final float[] uv = new float[8];   // 4 vertices, uv in 0..1 inside the material tile
        public final Mat mat;
        public final int color;                   // ARGB tint
        public float nx;
        public float ny;
        public float nz;

        Quad(Mat mat, int color) {
            this.mat = mat;
            this.color = color;
        }
    }

    public static final class Model {
        public final List<Quad> body = new ArrayList<>();
        public final List<Quad> wheel = new ArrayList<>();   // one wheel, centred on its axle, axis = X
        public final List<Quad> spike = new ArrayList<>();   // one anchor spike (tip at y = 0)
        /** Wheel axle positions {x, y, z}; the first two (z > 0) are the steering wheels. */
        public float[][] wheelPos = new float[0][];
        public float[][] spikePos = new float[0][];
        public float wheelHalfWidth;
        public float wheelRadius;
        public int paint;
    }

    // =====================================================================
    //  Builder
    // =====================================================================
    private static final class B {
        final List<Quad> out;

        B(List<Quad> out) {
            this.out = out;
        }

        /** Adds a quad (vertices counter-clockwise seen from outside). */
        Quad quad(Mat m, int color, float[] a, float[] b, float[] c, float[] d) {
            Quad q = new Quad(m, color);
            float[][] v = {a, b, c, d};
            for (int i = 0; i < 4; i++) {
                q.p[i * 3] = v[i][0];
                q.p[i * 3 + 1] = v[i][1];
                q.p[i * 3 + 2] = v[i][2];
            }
            float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
            System.arraycopy(uv, 0, q.uv, 0, 8);
            normal(q);
            out.add(q);
            return q;
        }

        static void normal(Quad q) {
            float ax = q.p[3] - q.p[0], ay = q.p[4] - q.p[1], az = q.p[5] - q.p[2];
            float bx = q.p[9] - q.p[0], by = q.p[10] - q.p[1], bz = q.p[11] - q.p[2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6F) {
                len = 1F;
            }
            q.nx = nx / len;
            q.ny = ny / len;
            q.nz = nz / len;
        }

        /** Makes the quad's normal point away from {@code c}, flipping the winding if needed. */
        static void faceOutward(Quad q, float cx, float cy, float cz) {
            float qx = (q.p[0] + q.p[3] + q.p[6] + q.p[9]) / 4F - cx;
            float qy = (q.p[1] + q.p[4] + q.p[7] + q.p[10]) / 4F - cy;
            float qz = (q.p[2] + q.p[5] + q.p[8] + q.p[11]) / 4F - cz;
            if (q.nx * qx + q.ny * qy + q.nz * qz < 0F) {
                for (int k = 0; k < 3; k++) {          // reverse vertex order: 0,1,2,3 -> 3,2,1,0
                    float t = q.p[k];
                    q.p[k] = q.p[9 + k];
                    q.p[9 + k] = t;
                    t = q.p[3 + k];
                    q.p[3 + k] = q.p[6 + k];
                    q.p[6 + k] = t;
                }
                q.nx = -q.nx;
                q.ny = -q.ny;
                q.nz = -q.nz;
            }
        }

        void box(float x0, float y0, float z0, float x1, float y1, float z1, Mat m, int color) {
            float[][] bot = {{x1, y0, z0}, {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}};
            float[][] top = {{x1, y1, z0}, {x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}};
            loft(bot, top, m, color);
        }

        /**
         * Hexahedron from a bottom and a top ring. Ring order: back-left, back-right, front-right,
         * front-left (left = +X, front = +Z). Faces: 0 back, 1 right, 2 front, 3 left.
         */
        void loft(float[][] bot, float[][] top, Mat m, int color) {
            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < 4; i++) {
                cx += bot[i][0] + top[i][0];
                cy += bot[i][1] + top[i][1];
                cz += bot[i][2] + top[i][2];
            }
            cx /= 8F;
            cy /= 8F;
            cz /= 8F;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                Quad q = quad(m, color, bot[i], bot[j], top[j], top[i]);
                faceOutward(q, cx, cy, cz);
            }
            faceOutward(quad(m, color, top[0], top[1], top[2], top[3]), cx, cy, cz);
            faceOutward(quad(m, color, bot[0], bot[1], bot[2], bot[3]), cx, cy, cz);
        }

        /** A rectangle lying on face {@code f} of a loft, pushed out by {@code off} (windows, decals). */
        void patch(float[][] bot, float[][] top, int f, float u0, float u1, float v0, float v1, float off,
                   Mat m, int color) {
            int j = (f + 1) % 4;
            float[] p00 = lerpFace(bot, top, f, j, u0, v0);
            float[] p10 = lerpFace(bot, top, f, j, u1, v0);
            float[] p11 = lerpFace(bot, top, f, j, u1, v1);
            float[] p01 = lerpFace(bot, top, f, j, u0, v1);
            Quad q = quad(m, color, p00, p10, p11, p01);
            // push outwards along the normal, away from the loft centre
            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < 4; i++) {
                cx += bot[i][0] + top[i][0];
                cy += bot[i][1] + top[i][1];
                cz += bot[i][2] + top[i][2];
            }
            faceOutward(q, cx / 8F, cy / 8F, cz / 8F);
            for (int i = 0; i < 4; i++) {
                q.p[i * 3] += q.nx * off;
                q.p[i * 3 + 1] += q.ny * off;
                q.p[i * 3 + 2] += q.nz * off;
            }
        }

        static float[] lerpFace(float[][] bot, float[][] top, int i, int j, float u, float v) {
            float[] r = new float[3];
            for (int k = 0; k < 3; k++) {
                float b = bot[i][k] + (bot[j][k] - bot[i][k]) * u;
                float t = top[i][k] + (top[j][k] - top[i][k]) * u;
                r[k] = b + (t - b) * v;
            }
            return r;
        }

        /** Flat decal facing +Z (front) or -Z (rear) at depth {@code z}. */
        void zFace(float x0, float x1, float y0, float y1, float z, boolean front, Mat m, int color) {
            Quad q;
            if (front) {
                q = quad(m, color, new float[] {x1, y0, z}, new float[] {x0, y0, z}, new float[] {x0, y1, z}, new float[] {x1, y1, z});
            } else {
                q = quad(m, color, new float[] {x0, y0, z}, new float[] {x1, y0, z}, new float[] {x1, y1, z}, new float[] {x0, y1, z});
            }
            q.nx = 0;
            q.ny = 0;
            q.nz = front ? 1F : -1F;
        }

        /** Flat decal on a side face (x constant); {@code left} faces +X. */
        void xFace(float z0, float z1, float y0, float y1, float x, boolean left, Mat m, int color) {
            Quad q;
            if (left) {
                q = quad(m, color, new float[] {x, y0, z1}, new float[] {x, y0, z0}, new float[] {x, y1, z0}, new float[] {x, y1, z1});
            } else {
                q = quad(m, color, new float[] {x, y0, z0}, new float[] {x, y0, z1}, new float[] {x, y1, z1}, new float[] {x, y1, z0});
            }
            q.nx = left ? 1F : -1F;
            q.ny = 0;
            q.nz = 0;
        }

        /** A wheel: tyre tread, two rubber sidewalls and two rim discs. Axis = X, centred on the origin. */
        void wheel(float radius, float halfWidth, int seg) {
            float rimR = radius * 0.62F;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (Math.PI * 2 * i / seg);
                float a1 = (float) (Math.PI * 2 * (i + 1) / seg);
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                // tread
                Quad t = quad(Mat.TIRE, 0xFFFFFFFF,
                        new float[] {-halfWidth, c0 * radius, s0 * radius}, new float[] {halfWidth, c0 * radius, s0 * radius},
                        new float[] {halfWidth, c1 * radius, s1 * radius}, new float[] {-halfWidth, c1 * radius, s1 * radius});
                faceOutward(t, 0, 0, 0);
                for (int side = -1; side <= 1; side += 2) {
                    float x = side * halfWidth;
                    // sidewall: ring between rimR and radius
                    Quad w = quad(Mat.RUBBER, 0xFFFFFFFF,
                            new float[] {x, c0 * rimR, s0 * rimR}, new float[] {x, c0 * radius, s0 * radius},
                            new float[] {x, c1 * radius, s1 * radius}, new float[] {x, c1 * rimR, s1 * rimR});
                    w.nx = side;
                    w.ny = 0;
                    w.nz = 0;
                    // rim disc (a fan drawn as degenerate quads)
                    float xr = side * (halfWidth + 0.004F);
                    Quad r = quad(Mat.RIM, 0xFFFFFFFF,
                            new float[] {xr, 0, 0}, new float[] {xr, c0 * rimR, s0 * rimR},
                            new float[] {xr, c1 * rimR, s1 * rimR}, new float[] {xr, c1 * rimR, s1 * rimR});
                    r.nx = side;
                    r.ny = 0;
                    r.nz = 0;
                    float[] uvs = {0.5F, 0.5F, 0.5F + 0.5F * c0, 0.5F + 0.5F * s0, 0.5F + 0.5F * c1, 0.5F + 0.5F * s1,
                            0.5F + 0.5F * c1, 0.5F + 0.5F * s1};
                    System.arraycopy(uvs, 0, r.uv, 0, 8);
                }
            }
        }
    }

    // =====================================================================
    //  Models
    // =====================================================================
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GLASS_TINT = 0x99FFFFFF;
    private static final int YELLOW = 0xFFE8B21C;
    private static final int ORANGE = 0xFFE8731C;

    private static Model[] cache;

    public static synchronized Model get(VehicleType t) {
        if (cache == null) {
            cache = new Model[VehicleType.values().length];
        }
        if (cache[t.ordinal()] == null) {
            switch (t) {
                case DOMINATOR: cache[t.ordinal()] = dominator(); break;
                case TIV: cache[t.ordinal()] = tiv(); break;
                default: cache[t.ordinal()] = suv(); break;
            }
        }
        return cache[t.ordinal()];
    }

    private static float[][] ring(float y, float xl, float zb, float xr, float zf, float yFront, float xlFront) {
        return new float[][] {{xl, y, zb}, {-xl, y, zb}, {-xlFront, yFront, zf}, {xlFront, yFront, zf}};
    }

    // ------------------------------------------------------------------ Dominator
    private static Model dominator() {
        Model m = new Model();
        m.paint = 0xFF1C1E23;
        B b = new B(m.body);
        int paint = m.paint;
        int paint2 = 0xFF2B2E36;

        b.box(-0.9F, 0.30F, -2.2F, 0.9F, 0.43F, 2.2F, Mat.BLACK, WHITE);                      // underbody
        b.box(-1.05F, 0.42F, -2.45F, 1.05F, 0.98F, 2.35F, Mat.PAINT, paint);                  // chassis tub

        // hood sloping down to the nose
        float[][] hb = {{1.0F, 0.98F, 0.80F}, {-1.0F, 0.98F, 0.80F}, {-1.0F, 0.98F, 2.35F}, {1.0F, 0.98F, 2.35F}};
        float[][] ht = {{0.92F, 1.16F, 0.85F}, {-0.92F, 1.16F, 0.85F}, {-0.88F, 1.02F, 2.30F}, {0.88F, 1.02F, 2.30F}};
        b.loft(hb, ht, Mat.PAINT, paint);
        b.patch(hb, ht, 3, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, Mat.PAINT, paint);
        // hazard stripe along the hood
        float[] s0 = {0.22F, 1.175F, 0.9F}, s1 = {-0.22F, 1.175F, 0.9F}, s2 = {-0.20F, 1.035F, 2.25F}, s3 = {0.20F, 1.035F, 2.25F};
        b.quad(Mat.HAZARD, WHITE, s0, s1, s2, s3);

        // armoured nose: ram bar, grille, headlights, plate
        b.box(-1.08F, 0.42F, 2.35F, 1.08F, 1.02F, 2.62F, Mat.ARMOR, WHITE);
        b.zFace(-0.62F, 0.62F, 0.62F, 0.90F, 2.625F, true, Mat.GRILLE, WHITE);
        b.zFace(0.70F, 0.98F, 0.80F, 0.95F, 2.626F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.98F, -0.70F, 0.80F, 0.95F, 2.626F, true, Mat.LIGHT, WHITE);
        b.zFace(0.66F, 0.90F, 0.50F, 0.58F, 2.626F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.90F, -0.66F, 0.50F, 0.58F, 2.626F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.18F, 0.18F, 0.44F, 0.56F, 2.627F, true, Mat.PLATE, WHITE);

        // cabin with a steeply raked windshield
        float[][] cb = {{1.0F, 0.98F, -1.05F}, {-1.0F, 0.98F, -1.05F}, {-1.0F, 0.98F, 0.80F}, {1.0F, 0.98F, 0.80F}};
        float[][] ct = {{0.86F, 1.68F, -1.0F}, {-0.86F, 1.68F, -1.0F}, {-0.86F, 1.68F, 0.05F}, {0.86F, 1.68F, 0.05F}};
        b.loft(cb, ct, Mat.PAINT2, paint2);
        b.patch(cb, ct, 2, 0.07F, 0.93F, 0.08F, 0.90F, 0.012F, Mat.GLASS, GLASS_TINT);             // windshield
        b.patch(cb, ct, 0, 0.12F, 0.88F, 0.20F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);             // rear glass
        for (int side = 1; side <= 3; side += 2) {
            b.patch(cb, ct, side, 0.10F, 0.47F, 0.20F, 0.84F, 0.012F, Mat.GLASS, GLASS_TINT);
            b.patch(cb, ct, side, 0.53F, 0.90F, 0.20F, 0.84F, 0.012F, Mat.GLASS, GLASS_TINT);
        }
        // roof gear: light bar, antennas, weather mast with anemometer
        b.box(-0.62F, 1.68F, -0.10F, 0.62F, 1.76F, 0.10F, Mat.LIGHTBAR, WHITE);
        b.box(0.46F, 1.68F, -0.76F, 0.50F, 2.55F, -0.72F, Mat.STEEL, WHITE);
        b.box(-0.50F, 1.68F, -0.80F, -0.46F, 2.30F, -0.76F, Mat.STEEL, WHITE);
        b.box(-0.03F, 1.68F, -0.55F, 0.03F, 2.25F, -0.49F, Mat.STEEL, WHITE);
        b.box(-0.22F, 2.25F, -0.53F, 0.22F, 2.29F, -0.51F, Mat.BLACK, WHITE);
        b.box(-0.02F, 2.25F, -0.74F, 0.02F, 2.29F, -0.30F, Mat.BLACK, WHITE);
        b.box(-0.10F, 2.29F, -0.58F, -0.04F, 2.35F, -0.52F, Mat.BLACK, WHITE);
        b.box(0.04F, 2.29F, -0.50F, 0.10F, 2.35F, -0.44F, Mat.BLACK, WHITE);

        // rear deck, wing and tail lights
        b.box(-1.05F, 0.98F, -2.45F, 1.05F, 1.20F, -1.05F, Mat.PAINT2, paint2);
        b.box(0.62F, 1.20F, -2.30F, 0.72F, 1.50F, -2.18F, Mat.BLACK, WHITE);
        b.box(-0.72F, 1.20F, -2.30F, -0.62F, 1.50F, -2.18F, Mat.BLACK, WHITE);
        b.box(-1.08F, 1.50F, -2.46F, 1.08F, 1.57F, -1.96F, Mat.PAINT, paint);
        b.zFace(0.50F, 0.98F, 0.64F, 0.80F, -2.452F, false, Mat.TAIL, WHITE);
        b.zFace(-0.98F, -0.50F, 0.64F, 0.80F, -2.452F, false, Mat.TAIL, WHITE);
        b.zFace(-0.20F, 0.20F, 0.50F, 0.62F, -2.453F, false, Mat.PLATE, WHITE);

        // side armour and fender flares
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 1.05F : -1.13F, x1 = side > 0 ? 1.13F : -1.05F;
            b.box(x0, 0.44F, -0.95F, x1, 0.86F, 0.95F, Mat.ARMOR, WHITE);
            b.xFace(-0.9F, 0.9F, 0.50F, 0.58F, side * 1.135F, side > 0, Mat.HAZARD, WHITE);
            for (float wz : new float[] {1.55F, -1.55F}) {
                float fx0 = side > 0 ? 1.05F : -1.22F, fx1 = side > 0 ? 1.22F : -1.05F;
                b.box(fx0, 0.80F, wz - 0.62F, fx1, 1.00F, wz + 0.62F, Mat.PAINT2, paint2);
            }
        }
        m.wheelRadius = 0.46F;
        m.wheelHalfWidth = 0.18F;
        m.wheelPos = new float[][] {{1.0F, 0.46F, 1.55F}, {-1.0F, 0.46F, 1.55F}, {1.0F, 0.46F, -1.55F}, {-1.0F, 0.46F, -1.55F}};
        new B(m.wheel).wheel(m.wheelRadius, m.wheelHalfWidth, 20);
        anchorSpikes(m, new float[][] {{0.82F, 0.42F, 2.05F}, {-0.82F, 0.42F, 2.05F}, {0.82F, 0.42F, -2.0F}, {-0.82F, 0.42F, -2.0F}}, 0.5F);
        return m;
    }

    // ------------------------------------------------------------------ TIV
    private static Model tiv() {
        Model m = new Model();
        m.paint = 0xFF2D3238;
        B b = new B(m.body);
        int paint = m.paint;
        int armor = 0xFF3A4048;

        b.box(-1.15F, 0.40F, -2.6F, 1.15F, 0.58F, 2.45F, Mat.BLACK, WHITE);                  // underbody
        b.box(-1.25F, 0.55F, -2.8F, 1.25F, 1.45F, 2.55F, Mat.PAINT, paint);                  // hull

        // sloped front deck
        float[][] nb = {{1.2F, 1.45F, 1.3F}, {-1.2F, 1.45F, 1.3F}, {-1.2F, 1.45F, 2.55F}, {1.2F, 1.45F, 2.55F}};
        float[][] nt = {{1.1F, 1.85F, 1.35F}, {-1.1F, 1.85F, 1.35F}, {-1.05F, 1.62F, 2.45F}, {1.05F, 1.62F, 2.45F}};
        b.loft(nb, nt, Mat.PAINT, paint);

        // armoured cabin
        float[][] cb = {{1.2F, 1.45F, -2.0F}, {-1.2F, 1.45F, -2.0F}, {-1.2F, 1.45F, 1.3F}, {1.2F, 1.45F, 1.3F}};
        float[][] ct = {{1.0F, 2.38F, -1.9F}, {-1.0F, 2.38F, -1.9F}, {-1.0F, 2.38F, 0.25F}, {1.0F, 2.38F, 0.25F}};
        b.loft(cb, ct, Mat.ARMOR, armor);
        // windshield slits and side slits
        b.patch(cb, ct, 2, 0.06F, 0.30F, 0.30F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
        b.patch(cb, ct, 2, 0.36F, 0.64F, 0.30F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
        b.patch(cb, ct, 2, 0.70F, 0.94F, 0.30F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
        b.patch(cb, ct, 0, 0.18F, 0.82F, 0.45F, 0.80F, 0.012F, Mat.GLASS, GLASS_TINT);
        for (int side = 1; side <= 3; side += 2) {
            b.patch(cb, ct, side, 0.08F, 0.30F, 0.42F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
            b.patch(cb, ct, side, 0.38F, 0.60F, 0.42F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
            b.patch(cb, ct, side, 0.68F, 0.92F, 0.42F, 0.82F, 0.012F, Mat.GLASS, GLASS_TINT);
        }
        // roof plate, camera pod, light bar, antennas
        b.box(-1.05F, 2.38F, -1.95F, 1.05F, 2.46F, 0.30F, Mat.ARMOR, WHITE);
        b.box(-0.38F, 2.46F, -1.0F, 0.38F, 2.92F, -0.15F, Mat.BLACK, WHITE);
        b.zFace(-0.22F, 0.22F, 2.55F, 2.80F, -0.148F, true, Mat.STEEL, WHITE);
        b.box(-0.95F, 2.38F, 0.30F, 0.95F, 2.46F, 0.46F, Mat.LIGHTBAR, WHITE);
        b.box(0.70F, 2.46F, -1.7F, 0.74F, 3.25F, -1.66F, Mat.STEEL, WHITE);
        b.box(-0.74F, 2.46F, -1.7F, -0.70F, 3.05F, -1.66F, Mat.STEEL, WHITE);
        b.box(-0.55F, 2.46F, -1.45F, -0.15F, 2.56F, -1.20F, Mat.STEEL, WHITE);

        // side armour panels between the wheels
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 1.25F : -1.37F, x1 = side > 0 ? 1.37F : -1.25F;
            b.box(x0, 0.58F, -1.0F, x1, 1.50F, 1.15F, Mat.ARMOR, WHITE);
            b.xFace(-0.95F, 1.10F, 0.60F, 0.76F, side * 1.375F, side > 0, Mat.HAZARD, WHITE);
            for (float wz : new float[] {1.9F, -1.75F}) {
                float fx0 = side > 0 ? 1.25F : -1.45F, fx1 = side > 0 ? 1.45F : -1.25F;
                b.box(fx0, 1.20F, wz - 0.78F, fx1, 1.45F, wz + 0.78F, Mat.ARMOR, WHITE);
            }
        }
        // ram bumper, grille, headlights
        b.box(-1.32F, 0.55F, 2.55F, 1.32F, 1.32F, 2.88F, Mat.ARMOR, WHITE);
        b.zFace(-0.80F, 0.80F, 0.80F, 1.15F, 2.885F, true, Mat.GRILLE, WHITE);
        b.zFace(0.88F, 1.22F, 1.00F, 1.20F, 2.886F, true, Mat.LIGHT, WHITE);
        b.zFace(-1.22F, -0.88F, 1.00F, 1.20F, 2.886F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.2F, 0.2F, 0.62F, 0.74F, 2.887F, true, Mat.PLATE, WHITE);
        b.zFace(-1.3F, 1.3F, 0.56F, 0.64F, 2.887F, true, Mat.HAZARD, WHITE);
        // rear
        b.zFace(0.7F, 1.15F, 0.80F, 0.98F, -2.802F, false, Mat.TAIL, WHITE);
        b.zFace(-1.15F, -0.7F, 0.80F, 0.98F, -2.802F, false, Mat.TAIL, WHITE);
        b.zFace(-0.25F, 0.25F, 0.65F, 0.80F, -2.803F, false, Mat.PLATE, WHITE);

        m.wheelRadius = 0.62F;
        m.wheelHalfWidth = 0.25F;
        m.wheelPos = new float[][] {{1.2F, 0.62F, 1.9F}, {-1.2F, 0.62F, 1.9F}, {1.2F, 0.62F, -1.75F}, {-1.2F, 0.62F, -1.75F}};
        new B(m.wheel).wheel(m.wheelRadius, m.wheelHalfWidth, 20);
        anchorSpikes(m, new float[][] {{1.0F, 0.56F, 2.35F}, {-1.0F, 0.56F, 2.35F}, {1.0F, 0.56F, -2.45F}, {-1.0F, 0.56F, -2.45F}}, 0.6F);
        return m;
    }

    // ------------------------------------------------------------------ Chaser SUV
    private static Model suv() {
        Model m = new Model();
        m.paint = 0xFFEDEDED;
        B b = new B(m.body);
        int paint = m.paint;
        int trim = 0xFF1E2024;

        b.box(-0.88F, 0.32F, -2.2F, 0.88F, 0.42F, 2.1F, Mat.BLACK, WHITE);
        b.box(-0.98F, 0.40F, -2.35F, 0.98F, 1.12F, 2.0F, Mat.PAINT, paint);                  // body
        float[][] hb = {{0.96F, 1.12F, 0.70F}, {-0.96F, 1.12F, 0.70F}, {-0.96F, 1.12F, 2.0F}, {0.96F, 1.12F, 2.0F}};
        float[][] ht = {{0.90F, 1.28F, 0.78F}, {-0.90F, 1.28F, 0.78F}, {-0.90F, 1.17F, 1.98F}, {0.90F, 1.17F, 1.98F}};
        b.loft(hb, ht, Mat.PAINT, paint);
        // front: bumper, bull bar, grille, lights
        b.box(-1.0F, 0.42F, 2.0F, 1.0F, 0.92F, 2.32F, Mat.BLACK, WHITE);
        b.box(-0.80F, 0.45F, 2.32F, -0.72F, 1.25F, 2.40F, Mat.STEEL, WHITE);
        b.box(0.72F, 0.45F, 2.32F, 0.80F, 1.25F, 2.40F, Mat.STEEL, WHITE);
        b.box(-0.80F, 1.18F, 2.32F, 0.80F, 1.25F, 2.40F, Mat.STEEL, WHITE);
        b.zFace(-0.52F, 0.52F, 0.70F, 1.05F, 2.003F, true, Mat.GRILLE, WHITE);
        b.zFace(0.60F, 0.92F, 0.82F, 1.02F, 2.003F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.92F, -0.60F, 0.82F, 1.02F, 2.003F, true, Mat.LIGHT, WHITE);
        b.zFace(-0.2F, 0.2F, 0.50F, 0.62F, 2.322F, true, Mat.PLATE, WHITE);

        // cabin
        float[][] cb = {{0.95F, 1.12F, -2.25F}, {-0.95F, 1.12F, -2.25F}, {-0.95F, 1.12F, 0.75F}, {0.95F, 1.12F, 0.75F}};
        float[][] ct = {{0.88F, 1.98F, -2.2F}, {-0.88F, 1.98F, -2.2F}, {-0.88F, 1.98F, 0.05F}, {0.88F, 1.98F, 0.05F}};
        b.loft(cb, ct, Mat.PAINT, paint);
        b.patch(cb, ct, 2, 0.06F, 0.94F, 0.08F, 0.90F, 0.012F, Mat.GLASS, GLASS_TINT);
        b.patch(cb, ct, 0, 0.10F, 0.90F, 0.18F, 0.86F, 0.012F, Mat.GLASS, GLASS_TINT);
        for (int side = 1; side <= 3; side += 2) {
            b.patch(cb, ct, side, 0.05F, 0.33F, 0.22F, 0.86F, 0.012F, Mat.GLASS, GLASS_TINT);
            b.patch(cb, ct, side, 0.36F, 0.64F, 0.22F, 0.86F, 0.012F, Mat.GLASS, GLASS_TINT);
            b.patch(cb, ct, side, 0.67F, 0.95F, 0.22F, 0.86F, 0.012F, Mat.GLASS, GLASS_TINT);
        }
        // side stripe along the belt line
        b.xFace(-2.3F, 1.95F, 0.78F, 0.90F, 0.983F, true, Mat.HAZARD, ORANGE);
        b.xFace(-2.3F, 1.95F, 0.78F, 0.90F, -0.983F, false, Mat.HAZARD, ORANGE);
        // roof rack, light bar, weather mast with anemometer cups
        b.box(-0.80F, 1.98F, -1.9F, -0.74F, 2.06F, 0.0F, Mat.STEEL, WHITE);
        b.box(0.74F, 1.98F, -1.9F, 0.80F, 2.06F, 0.0F, Mat.STEEL, WHITE);
        b.box(-0.80F, 2.04F, -1.2F, 0.80F, 2.08F, -1.14F, Mat.STEEL, WHITE);
        b.box(-0.80F, 2.04F, -0.2F, 0.80F, 2.08F, -0.14F, Mat.STEEL, WHITE);
        b.box(-0.60F, 2.06F, -0.10F, 0.60F, 2.16F, 0.08F, Mat.LIGHTBAR, WHITE);
        b.box(-0.03F, 2.08F, -1.65F, 0.03F, 2.75F, -1.59F, Mat.STEEL, WHITE);
        b.box(-0.24F, 2.75F, -1.63F, 0.24F, 2.78F, -1.61F, Mat.BLACK, WHITE);
        b.box(-0.02F, 2.75F, -1.84F, 0.02F, 2.78F, -1.40F, Mat.BLACK, WHITE);
        b.box(-0.30F, 2.78F, -1.68F, -0.20F, 2.86F, -1.58F, Mat.BLACK, WHITE);
        b.box(0.20F, 2.78F, -1.64F, 0.30F, 2.86F, -1.54F, Mat.BLACK, WHITE);
        b.box(-0.16F, 2.78F, -1.94F, -0.08F, 2.86F, -1.86F, Mat.BLACK, WHITE);
        // rear
        b.box(-1.0F, 0.42F, -2.5F, 1.0F, 0.80F, -2.35F, Mat.BLACK, WHITE);
        b.zFace(0.52F, 0.95F, 0.86F, 1.04F, -2.352F, false, Mat.TAIL, WHITE);
        b.zFace(-0.95F, -0.52F, 0.86F, 1.04F, -2.352F, false, Mat.TAIL, WHITE);
        b.zFace(-0.2F, 0.2F, 0.52F, 0.64F, -2.503F, false, Mat.PLATE, WHITE);
        // fender flares
        for (int side = -1; side <= 1; side += 2) {
            for (float wz : new float[] {1.5F, -1.5F}) {
                float fx0 = side > 0 ? 0.98F : -1.10F, fx1 = side > 0 ? 1.10F : -0.98F;
                b.box(fx0, 0.74F, wz - 0.56F, fx1, 0.94F, wz + 0.56F, Mat.BLACK, WHITE);
            }
        }
        m.wheelRadius = 0.43F;
        m.wheelHalfWidth = 0.15F;
        m.wheelPos = new float[][] {{0.95F, 0.43F, 1.5F}, {-0.95F, 0.43F, 1.5F}, {0.95F, 0.43F, -1.5F}, {-0.95F, 0.43F, -1.5F}};
        new B(m.wheel).wheel(m.wheelRadius, m.wheelHalfWidth, 20);
        m.spikePos = new float[0][];
        return m;
    }

    /** One spike model (tip at y = 0, shaft above) and its mounting points. */
    private static void anchorSpikes(Model m, float[][] pos, float length) {
        B b = new B(m.spike);
        float[][] cb = {{0.09F, length * 0.45F, -0.09F}, {-0.09F, length * 0.45F, -0.09F}, {-0.09F, length * 0.45F, 0.09F}, {0.09F, length * 0.45F, 0.09F}};
        float[][] ct = {{0.012F, 0F, -0.012F}, {-0.012F, 0F, -0.012F}, {-0.012F, 0F, 0.012F}, {0.012F, 0F, 0.012F}};
        b.loft(ct, cb, Mat.SPIKE, WHITE);
        b.box(-0.06F, length * 0.45F, -0.06F, 0.06F, length * 1.4F, 0.06F, Mat.STEEL, WHITE);
        b.box(-0.11F, length * 1.4F, -0.11F, 0.11F, length * 1.4F + 0.06F, 0.11F, Mat.BLACK, WHITE);
        m.spikePos = pos;
    }
}
