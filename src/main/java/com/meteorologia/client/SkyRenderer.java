package com.meteorologia.client;

import com.meteorologia.Config;
import com.meteorologia.Meteorologia;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.shaders.Uniform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the realistic sun and the volumetric clouds right after the sky (before terrain).
 * The vanilla sun and clouds are hidden by transparent override textures shipped with the mod
 * (assets/minecraft/textures/environment/sun.png and clouds.png).
 */
@EventBusSubscriber(modid = Meteorologia.MODID, value = Dist.CLIENT)
public final class SkyRenderer {
    private SkyRenderer() {}

    private static final Matrix4f CAM_ROT = new Matrix4f();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        ShaderInstance shader = ModShaders.sky;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (shader == null || level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        boolean sunOn = Config.SUN_ENABLED.get();
        float rain = level.getRainLevel(1.0F);
        float storm = ClientStormData.stormLevel;
        float coverage = Mth.clamp((float) (double) Config.CLOUD_AMOUNT.get() + rain * 0.55F + storm * 0.35F, 0F, 1F);
        boolean cloudsOn = Config.CLOUDS_ENABLED.get() && coverage > 0.02F;
        if (!sunOn && !cloudsOn) {
            return;
        }

        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Matrix4f proj = event.getProjectionMatrix();
        CAM_ROT.identity().rotation(camera.rotation());

        // sun direction (same convention as the vanilla sky: it rises in +X and sets in -X)
        float angle = level.getTimeOfDay(1.0F) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float daylight = Mth.clamp(sunY * 1.6F + 0.3F, 0.05F, 1.0F) * (1.0F - 0.4F * rain);
        float sunRadius = (float) Math.toRadians(Config.SUN_SIZE.get() / 2.0);

        float realism = (float) (double) Config.CLOUD_REALISM.get();
        float thickness = 12.0F + 150.0F * (float) Math.pow(realism, 0.8);
        float dark = Mth.clamp(rain * 0.7F + storm * 0.5F, 0F, 0.95F);
        double time = (level.getGameTime() + ClientStormData.subTick()) / 20.0;
        Vec3 sky = level.getSkyColor(cam, 1.0F);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> shader);

        Uniform rot = shader.getUniform("CamRot");
        if (rot != null) {
            rot.set(CAM_ROT);
        }
        StormRenderer.set(shader, "Proj", 1.0F / proj.m00(), 1.0F / proj.m11(), 0.0F, 0.0F);
        StormRenderer.set(shader, "CamPos", (float) cam.x, (float) cam.y, (float) cam.z, (float) (time % 3600.0));
        StormRenderer.set(shader, "SunDir", sunX, sunY, 0.0F, sunRadius);
        StormRenderer.set(shader, "SunPar", (float) (double) Config.SUN_GLOW.get(), daylight, sunOn ? 1.0F : 0.0F, 0.0F);
        StormRenderer.set(shader, "Cloud0", coverage, realism, (float) (int) Config.CLOUD_HEIGHT.get(), thickness);
        StormRenderer.set(shader, "Cloud1", (float) ClientStormData.cloudOffX, (float) ClientStormData.cloudOffZ,
                dark, cloudsOn ? 1.0F : 0.0F);
        StormRenderer.set(shader, "SkyCol", (float) sky.x, (float) sky.y, (float) sky.z, 1.0F);
        StormRenderer.set(shader, "Env", Config.CLOUD_QUALITY.get(), 0.0F, 0.0F, 0.0F);
        StormRenderer.drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
