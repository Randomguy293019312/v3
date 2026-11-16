package com.meteorologia.client;

import com.meteorologia.Meteorologia;
import com.meteorologia.entity.ChaserVehicle;
import com.meteorologia.vehicle.VehicleGeometry;
import com.meteorologia.vehicle.VehicleType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Draws the chase vehicles from the procedural quads in {@link VehicleGeometry}: body, glass (translucent),
 * four wheels (the front pair steers), the hydraulic anchor spikes and the emissive lights.
 */
public class VehicleRenderer extends EntityRenderer<ChaserVehicle> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "textures/entity/vehicle.png");
    private static final float INSET = 0.5F / 16F;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.3F;
    }

    private static boolean reportedFailure;

    @Override
    public void render(ChaserVehicle entity, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        try {
            renderVehicle(entity, pt, ps, buffers, light);
        } catch (RuntimeException ex) {
            // never take the whole game down because of a drawing error
            if (!reportedFailure) {
                reportedFailure = true;
                Meteorologia.LOGGER.error("Failed to render a vehicle (further errors are not logged)", ex);
            }
        }
        super.render(entity, entityYaw, pt, ps, buffers, light);
    }

    /**
     * IMPORTANT: a MultiBufferSource ends the previous buffer as soon as another RenderType is requested,
     * so we must never hold two consumers at once. The vehicle is drawn in two passes: opaque, then glass.
     */
    private void renderVehicle(ChaserVehicle entity, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        VehicleType type = entity.getVehicleType();
        VehicleGeometry.Model model = VehicleGeometry.get(type);
        float yaw = entity.getRenderYaw(pt);
        float steer = entity.getRenderSteer(pt);
        float wheelRot = entity.getWheelRot(pt);
        float anchor = entity.getAnchorAnim(pt);
        boolean lights = entity.lightsOn();
        boolean braking = entity.isBraking();
        boolean wrecked = entity.isWrecked();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));

        // the body rolls to the outside of a corner
        float roll = wrecked ? 0F : Mth.clamp(steer * entity.getSyncedSpeed() * 18F, -4F, 4F);
        ps.translate(0F, 0.6F, 0F);
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.translate(0F, -0.6F, 0F);

        // ---- pass 1: everything opaque (body, wheels, anchor spikes)
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        draw(model.body, solid, false, ps.last(), light, lights, braking, wrecked);
        for (int i = 0; i < model.wheelPos.length; i++) {
            float[] wp = model.wheelPos[i];
            ps.pushPose();
            ps.translate(wp[0], wp[1], wp[2]);
            if (wp[2] > 0F) {
                ps.mulPose(Axis.YP.rotation(steer));
            }
            ps.mulPose(Axis.XP.rotation(wheelRot));
            draw(model.wheel, solid, false, ps.last(), light, false, false, wrecked);
            ps.popPose();
        }
        if (anchor > 0.02F) {
            for (float[] sp : model.spikePos) {
                ps.pushPose();
                ps.translate(sp[0], sp[1] - anchor * 0.75F, sp[2]);
                draw(model.spike, solid, false, ps.last(), light, false, false, wrecked);
                ps.popPose();
            }
        }

        // ---- pass 2: the translucent glass (requesting this buffer closes the opaque one)
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        draw(model.body, glass, true, ps.last(), light, false, false, wrecked);

        ps.popPose();
    }

    /** Draws only the quads whose translucency matches {@code glassPass}, into the given consumer. */
    private static void draw(List<VehicleGeometry.Quad> quads, VertexConsumer vc, boolean glassPass,
                             PoseStack.Pose pose, int light, boolean lights, boolean braking, boolean wrecked) {
        for (VehicleGeometry.Quad q : quads) {
            if (q.mat.translucent() != glassPass) {
                continue;
            }
            int l = light;
            switch (q.mat) {
                case LIGHT: case LIGHTBAR:
                    if (lights && !wrecked) {
                        l = LightTexture.FULL_BRIGHT;
                    }
                    break;
                case TAIL:
                    if ((lights || braking) && !wrecked) {
                        l = LightTexture.FULL_BRIGHT;
                    }
                    break;
                default:
                    break;
            }
            int color = q.color;
            if (wrecked && !glassPass) {
                color = darken(color);
            }
            put(vc, pose, q, l, color);
        }
    }

    private static int darken(int argb) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 255) * 55 / 100;
        int g = ((argb >> 8) & 255) * 55 / 100;
        int b = (argb & 255) * 55 / 100;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void put(VertexConsumer vc, PoseStack.Pose pose, VehicleGeometry.Quad q, int light, int color) {
        int cell = q.mat.cell;
        float cu = (cell & 3) * 0.25F;
        float cv = (cell >> 2) * 0.25F;
        float span = 0.25F * (1F - 2F * INSET);
        for (int i = 0; i < 4; i++) {
            float u = cu + 0.25F * INSET + q.uv[i * 2] * span;
            float v = cv + 0.25F * INSET + q.uv[i * 2 + 1] * span;
            vc.addVertex(pose, q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2])
                    .setColor(color)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, q.nx, q.ny, q.nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ChaserVehicle entity) {
        return TEXTURE;
    }
}
