package com.meteorologia.client;

import com.meteorologia.Meteorologia;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Registers the mod's core shaders. A failing shader is skipped instead of crashing the game. */
@EventBusSubscriber(modid = Meteorologia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModShaders {
    private ModShaders() {}

    /** Ray marched tornadoes and supercells. */
    public static ShaderInstance storm;
    /** Realistic sun + volumetric clouds. */
    public static ShaderInstance sky;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "storm"),
                            DefaultVertexFormat.POSITION),
                    shader -> storm = shader);
        } catch (Exception ex) {
            storm = null;
            Meteorologia.LOGGER.error("Failed to load the storm ray marching shader; using particles instead.", ex);
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "sky"),
                            DefaultVertexFormat.POSITION),
                    shader -> sky = shader);
        } catch (Exception ex) {
            sky = null;
            Meteorologia.LOGGER.error("Failed to load the sky shader; no sun or clouds will be drawn.", ex);
        }
    }
}
