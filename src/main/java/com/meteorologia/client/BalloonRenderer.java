package com.meteorologia.client;

import com.meteorologia.Meteorologia;
import com.meteorologia.entity.HotAirBalloon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Procedural hot air balloon: a lathe-turned onion-shaped envelope made of coloured gores,
 * suspension ropes, and the basket model. Each balloon gets one of several colour schemes.
 */
public class BalloonRenderer extends EntityRenderer<HotAirBalloon> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "hot_air_balloon"), "main");
    private static final ResourceLocation BASKET_TEX =
            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "textures/entity/hot_air_balloon.png");
    private static final ResourceLocation ENVELOPE_TEX =
            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "textures/entity/balloon_envelope.png");

    // ---- envelope profile: {height, radius} control points in blocks (throat at the bottom, crown on top)
    private static final float[][] PROFILE = {
            {3.90F, 0.70F}, {4.30F, 1.20F}, {4.95F, 2.05F}, {5.75F, 2.95F}, {6.70F, 3.50F}, {7.75F, 3.70F},
            {8.80F, 3.50F}, {9.75F, 2.95F}, {10.55F, 2.15F}, {11.20F, 1.20F}, {11.60F, 0.00F}
    };
    private static final int SUBDIV = 3;
    private static final int GORES = 20;
    private static final float[] RING_Y;
    private static final float[] RING_R;
    private static final float[] RING_NR;   // radial component of the surface normal
    private static final float[] RING_NY;   // vertical component of the surface normal

    // ---- colour schemes: stripes, crown (top) and skirt (bottom)
    private record Scheme(int[] stripes, int crown, int skirt) {}
    private static final Scheme[] SCHEMES = {
            new Scheme(new int[] {0xFFD93A2E, 0xFFF5F2E9}, 0xFF1F3A7A, 0xFF1F3A7A),                       // red / white / navy
            new Scheme(new int[] {0xFFD93A2E, 0xFFF08C23, 0xFFF5D21E, 0xFF3FA84A, 0xFF2E6FD0, 0xFF7A3FB5}, 0xFFF5F2E9, 0xFFF5F2E9), // rainbow
            new Scheme(new int[] {0xFF1E9C94, 0xFFF5F2E9, 0xFFE8B730, 0xFFF5F2E9}, 0xFFE8B730, 0xFF146B66),   // teal / gold
            new Scheme(new int[] {0xFF1B1B1F, 0xFFF08C23}, 0xFFF08C23, 0xFF1B1B1F),                       // black / orange
            new Scheme(new int[] {0xFF2E6FD0, 0xFFF5F2E9, 0xFF2E6FD0, 0xFFE8503F}, 0xFFE8503F, 0xFF173A75),   // blue / red accents
    };

    static {
        int n = (PROFILE.length - 1) * SUBDIV + 1;
        RING_Y = new float[n];
        RING_R = new float[n];
        for (int seg = 0; seg < PROFILE.length - 1; seg++) {
            float[] p0 = PROFILE[Math.max(seg - 1, 0)];
            float[] p1 = PROFILE[seg];
            float[] p2 = PROFILE[seg + 1];
            float[] p3 = PROFILE[Math.min(seg + 2, PROFILE.length - 1)];
            for (int k = 0; k < SUBDIV; k++) {
                float t = k / (float) SUBDIV;
                int idx = seg * SUBDIV + k;
                RING_Y[idx] = Mth.lerp(t, p1[0], p2[0]);
                RING_R[idx] = Math.max(0F, catmullRom(p0[1], p1[1], p2[1], p3[1], t));
            }
        }
        RING_Y[n - 1] = PROFILE[PROFILE.length - 1][0];
        RING_R[n - 1] = PROFILE[PROFILE.length - 1][1];

        RING_NR = new float[n];
        RING_NY = new float[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(i - 1, 0);
            int b = Math.min(i + 1, n - 1);
            float dy = RING_Y[b] - RING_Y[a];
            float dr = RING_R[b] - RING_R[a];
            float len = Mth.sqrt(dy * dy + dr * dr);
            if (len < 1.0E-5F) {
                len = 1F;
            }
            RING_NR[i] = dy / len;
            RING_NY[i] = -dr / len;
        }
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * ((2F * p1) + (-p0 + p2) * t + (2F * p0 - 5F * p1 + 4F * p2 - p3) * t2
                + (-p0 + 3F * p1 - 3F * p2 + p3) * t3);
    }

    private final BalloonModel model;

    public BalloonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BalloonModel(context.bakeLayer(LAYER));
    }

    @Override
    public void render(HotAirBalloon entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float time = entity.tickCount + partialTick;
        float phase = entity.getId() * 1.7F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // gentle swing; the burner makes the whole thing tremble slightly
        float swingX = Mth.sin(time * 0.045F + phase) * 1.1F;
        float swingZ = Mth.cos(time * 0.037F + phase) * 0.9F;
        if (entity.isBurning()) {
            swingX += Mth.sin(time * 1.3F) * 0.25F;
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(swingX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(swingZ));

        // basket, posts and burner
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer basket = buffer.getBuffer(model.renderType(BASKET_TEX));
        model.renderToBuffer(poseStack, basket, packedLight, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();

        // envelope and ropes
        float inflate = 1.0F + 0.012F * Mth.sin(time * 0.11F + phase)
                + (entity.isBurning() ? 0.02F * Mth.sin(time * 1.9F) : 0.0F);
        VertexConsumer env = buffer.getBuffer(RenderType.entityCutoutNoCull(ENVELOPE_TEX));
        Scheme scheme = SCHEMES[Math.floorMod(entity.getUUID().hashCode(), SCHEMES.length)];
        drawEnvelope(poseStack, env, packedLight, scheme, inflate);
        drawRopes(poseStack, env, packedLight, inflate);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void drawEnvelope(PoseStack poseStack, VertexConsumer vc, int light, Scheme scheme, float inflate) {
        PoseStack.Pose pose = poseStack.last();
        int rings = RING_Y.length;
        for (int g = 0; g < GORES; g++) {
            float a0 = g / (float) GORES * Mth.TWO_PI;
            float a1 = (g + 1) / (float) GORES * Mth.TWO_PI;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            int stripe = scheme.stripes()[g % scheme.stripes().length];
            for (int i = 0; i < rings - 1; i++) {
                float v0 = i / (float) (rings - 1);
                float v1 = (i + 1) / (float) (rings - 1);
                int color = v0 < 0.12F ? scheme.skirt() : (v1 > 0.9F ? scheme.crown() : stripe);
                float r0 = RING_R[i] * inflate;
                float r1 = RING_R[i + 1] * inflate;
                vertex(vc, pose, light, color, r0 * c0, RING_Y[i], r0 * s0, 0F, v0, RING_NR[i] * c0, RING_NY[i], RING_NR[i] * s0);
                vertex(vc, pose, light, color, r0 * c1, RING_Y[i], r0 * s1, 1F, v0, RING_NR[i] * c1, RING_NY[i], RING_NR[i] * s1);
                vertex(vc, pose, light, color, r1 * c1, RING_Y[i + 1], r1 * s1, 1F, v1, RING_NR[i + 1] * c1, RING_NY[i + 1], RING_NR[i + 1] * s1);
                vertex(vc, pose, light, color, r1 * c0, RING_Y[i + 1], r1 * s0, 0F, v1, RING_NR[i + 1] * c0, RING_NY[i + 1], RING_NR[i + 1] * s0);
            }
        }
    }

    /** Four suspension ropes from the burner frame up to the envelope throat. */
    private static void drawRopes(PoseStack poseStack, VertexConsumer vc, int light, float inflate) {
        PoseStack.Pose pose = poseStack.last();
        float ty = RING_Y[0];
        float tr = RING_R[0] * inflate * 0.72F;
        int rope = 0xFF5A4128;
        float w = 0.03F;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x0 = sx * 0.78F;
                float z0 = sz * 0.78F;
                float y0 = 2.92F;
                float x1 = sx * tr;
                float z1 = sz * tr;
                // two crossed quads so the rope is visible from every side
                vertex(vc, pose, light, rope, x0 - w, y0, z0, 0.5F, 0.5F, 0F, 0F, 1F);
                vertex(vc, pose, light, rope, x0 + w, y0, z0, 0.5F, 0.5F, 0F, 0F, 1F);
                vertex(vc, pose, light, rope, x1 + w, ty, z1, 0.5F, 0.5F, 0F, 0F, 1F);
                vertex(vc, pose, light, rope, x1 - w, ty, z1, 0.5F, 0.5F, 0F, 0F, 1F);
                vertex(vc, pose, light, rope, x0, y0, z0 - w, 0.5F, 0.5F, 1F, 0F, 0F);
                vertex(vc, pose, light, rope, x0, y0, z0 + w, 0.5F, 0.5F, 1F, 0F, 0F);
                vertex(vc, pose, light, rope, x1, ty, z1 + w, 0.5F, 0.5F, 1F, 0F, 0F);
                vertex(vc, pose, light, rope, x1, ty, z1 - w, 0.5F, 0.5F, 1F, 0F, 0F);
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, int argb,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(HotAirBalloon entity) {
        return BASKET_TEX;
    }
}
