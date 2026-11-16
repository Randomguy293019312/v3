package com.meteorologia.net;

import com.meteorologia.Meteorologia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the driver pressed a vehicle key (anchors / lights / horn). */
public record VehicleActionPayload(int action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VehicleActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Meteorologia.MODID, "vehicle_action"));

    public static final StreamCodec<FriendlyByteBuf, VehicleActionPayload> CODEC =
            new StreamCodec<FriendlyByteBuf, VehicleActionPayload>() {
                @Override
                public VehicleActionPayload decode(FriendlyByteBuf buf) {
                    return new VehicleActionPayload(buf.readVarInt());
                }

                @Override
                public void encode(FriendlyByteBuf buf, VehicleActionPayload payload) {
                    buf.writeVarInt(payload.action);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
