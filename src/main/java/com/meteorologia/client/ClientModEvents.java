package com.meteorologia.client;

import com.meteorologia.Meteorologia;
import com.meteorologia.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = Meteorologia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.BALLOON.get(), BalloonRenderer::new);
        event.registerEntityRenderer(ModRegistry.DOMINATOR_ENTITY.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModRegistry.TIV_ENTITY.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModRegistry.CHASER_SUV_ENTITY.get(), VehicleRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BalloonRenderer.LAYER, BalloonModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(VehicleKeys.ANCHOR);
        event.register(VehicleKeys.LIGHTS);
        event.register(VehicleKeys.HORN);
    }
}
