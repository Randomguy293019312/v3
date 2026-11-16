package com.meteorologia;

import com.meteorologia.client.ClientStormData;
import com.meteorologia.entity.ChaserVehicle;
import com.meteorologia.net.StormSyncPayload;
import com.meteorologia.net.VehicleActionPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Meteorologia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class Network {
    private Network() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC,
                (payload, context) -> ClientStormData.apply(payload));
        registrar.playToServer(VehicleActionPayload.TYPE, VehicleActionPayload.CODEC, (payload, context) -> {
            Player player = context.player();
            if (player.getVehicle() instanceof ChaserVehicle vehicle && vehicle.getFirstPassenger() == player) {
                vehicle.handleAction(payload.action(), player);
            }
        });
    }
}
