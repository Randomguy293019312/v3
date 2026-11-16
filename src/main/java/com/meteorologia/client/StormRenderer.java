package com.meteorologia.client;

import com.meteorologia.Config;
import com.meteorologia.Meteorologia;
import com.meteorologia.weather.Supercell;
import com.meteorologia.weather.Tornado;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.shaders.Uniform;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws tornadoes and supercells with ray marching: one full-screen quad per
 * visible storm, with the depth buffer copied so terrain correctly hides the cloud.
 */
@EventBusSubscriber(modid = Meteorologia.MODID, value = Dist.CLIENT)
public final class StormRenderer {
    private StormRenderer() {}

    private static TextureTarget depthCopy;
    private static final Matrix4f CAM_ROT = new Matrix4f();

    private record Draw(Tornado tornado, Supercell supercell, double dist) {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        ShaderInstance shader = ModShaders.storm;
        if (shader == null || !Config.ENABLE_SHADER.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (ClientStormData.tornadoes().isEmpty() && ClientStormData.supercells().isEmpty())) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Frustum frustum = event.getFrustum();
        double el = ClientStormData.elapsedTicks();

        // ---- pick what is visible
        List<Draw> draws = new ArrayList<>();
        for (Supercell s : ClientStormData.supercells()) {
            double sx = s.x + s.vx * el;
            double sz = s.z + s.vz * el;
            double reach = s.radius * 2.8;
            double dist = Math.hypot(sx - cam.x, sz - cam.z);
            if (dist > 14000.0) {
                continue;
            }
            AABB box = new AABB(sx - reach, s.baseY - 60, sz - reach, sx + reach, s.topY + 70, sz + reach);
            if (frustum.isVisible(box)) {
                draws.add(new Draw(null, s, dist));
            }
        }
        for (Tornado t : ClientStormData.tornadoes()) {
            double tx = t.x + t.vx * el;
            double tz = t.z + t.vz * el;
            double dist = Math.hypot(tx - cam.x, tz - cam.z);
            if (dist > Config.TORNADO_VIEW_DISTANCE.get()) {
                continue;
            }
            double lod = lodScale(dist);
            double reach = Math.max(cloudRadius(t.radius) * 1.15, t.radius * 5.0 + 40.0) * lod;
            AABB box = new AABB(tx - reach, t.y - 8, tz - reach, tx + reach,
                    t.y + t.height * Math.pow(lod, 0.75) + 170, tz + reach);
            if (frustum.isVisible(box)) {
                draws.add(new Draw(t, null, dist));
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        draws.sort((a, b) -> Double.compare(b.dist, a.dist)); // farthest first

        // ---- copy the depth buffer (avoids reading and writing the same buffer)
        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null) {
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        // ---- uniformes comuns
        Matrix4f proj = event.getProjectionMatrix();
        CAM_ROT.identity().rotation(camera.rotation());

        float rainLevel = level.getRainLevel(1.0F);
        float tod = level.getTimeOfDay(1.0F);
        float angle = tod * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float daylight = Mth.clamp(sunY * 1.6F + 0.3F, 0.05F, 1.0F) * (1.0F - 0.35F * rainLevel);
        Vec3 sky = level.getSkyColor(cam, 1.0F).scale(0.7);
        float time = (float) ((System.nanoTime() / 1.0e9) % 3600.0);
        int steps = Config.STORM_STEPS.get();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());

        set(shader, "Proj", 1.0F / proj.m00(), 1.0F / proj.m11(), proj.m22(), proj.m32());
        Uniform rot = shader.getUniform("CamRot");
        if (rot != null) {
            rot.set(CAM_ROT);
        }
        set(shader, "SunDir", sunX, sunY, 0.0F, 0.0F);
        set(shader, "SkyCol", (float) sky.x, (float) sky.y, (float) sky.z, 1.0F);

        for (Draw d : draws) {
            if (d.tornado != null) {
                Tornado t = d.tornado;
                float str = ClientStormData.strength(t, el);
                double tx = t.x + t.vx * el;
                double tz = t.z + t.vz * el;
                // far tornadoes are scaled up (LOD) so they never shrink below visibility
                float lod = (float) lodScale(d.dist);
                float r0 = t.radius * lod;
                float h = t.height * (float) Math.pow(lod, 0.75);
                float cloudR = (float) cloudRadius(t.radius) * lod;
                set(shader, "Origin", (float) (tx - cam.x), (float) (t.y - cam.y), (float) (tz - cam.z), 0.0F);
                set(shader, "StormA", r0, h, str, time);
                set(shader, "StormB", cloudR, 0.55F + 0.45F * str, 1.0F, (t.id * 7.31F) % 50.0F);
                // funnel reach: descends while forming, then stays on the ground until it dissipates
                // fast at first, gentle landing: touches the ground at str = 0.6 (about 3.5 s)
                float reach = 1.0F;
                if (t.dStrength > 0.00005F) {
                    float x = Mth.clamp(str / 0.6F, 0.0F, 1.0F);
                    reach = 1.0F - (1.0F - x) * (1.0F - x);
                }
                float[] prof = funnelProfile(t.shape);
                set(shader, "SupA", reach, prof[0], prof[1], prof[2]);
                set(shader, "Env", daylight, steps, 0.0F, 9000.0F);
            } else {
                Supercell s = d.supercell;
                float str = ClientStormData.strength(s, el);
                double sx = s.x + s.vx * el;
                double sz = s.z + s.vz * el;
                float radius = s.radius * (0.35F + 0.65F * str);
                float top = s.baseY + (s.topY - s.baseY) * (0.3F + 0.7F * str);
                set(shader, "Origin", (float) (sx - cam.x), (float) (-cam.y), (float) (sz - cam.z), 0.0F);
                set(shader, "StormA", 62.0F, 0.0F, 0.0F, time);
                set(shader, "StormB", 0.0F, 0.6F + 0.4F * str, 1.0F, (s.id * 13.7F) % 50.0F);
                set(shader, "SupA", radius, s.baseY, top, s.windAngle);
                set(shader, "Env", daylight, Math.min(steps, 56), 1.0F, 14000.0F);
            }
            drawFullscreenQuad();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 1 within 1000 blocks, growing linearly with distance up to 7x. */
    private static double lodScale(double dist) {
        return Math.min(Math.max(dist / 1000.0, 1.0), 7.0);
    }

    /** Radius of the tornado's own wall cloud: it blends into the supercell base above it. */
    private static double cloudRadius(float tornadoRadius) {
        return Math.min(Math.max(tornadoRadius * 2.6 + 70.0, 90.0), 240.0);
    }

    /** Funnel radius profile {base, flare, power}: radius = R * (base + flare * (h/H)^power). */
    private static float[] funnelProfile(int shape) {
        switch (shape) {
            case Tornado.SHAPE_ROPE: return new float[] {0.30F, 0.30F, 1.2F};
            case Tornado.SHAPE_STOVEPIPE: return new float[] {0.80F, 0.30F, 1.2F};
            case Tornado.SHAPE_WEDGE: return new float[] {1.00F, 0.75F, 1.3F};
            default: return new float[] {0.42F, 1.20F, 1.3F};   // cone: roughly linear taper
        }
    }

    static void set(ShaderInstance shader, String name, float a, float b, float c, float d) {
        Uniform u = shader.getUniform(name);
        if (u != null) {
            u.set(a, b, c, d);
        }
    }

    static void drawFullscreenQuad() {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1.0F, -1.0F, 0.0F);
        b.addVertex(1.0F, -1.0F, 0.0F);
        b.addVertex(1.0F, 1.0F, 0.0F);
        b.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
